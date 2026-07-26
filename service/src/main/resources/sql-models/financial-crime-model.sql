-- Financial-crime schema on the CRIME datasource. Not part of the official ER diagram — a
-- proposed integration schema (see documents/SCHEMA_FIDELITY.md). Loaded after fir-schema.sql AND
-- entity-resolution.sql (which redefines offender_risk_score this seed depends on) AND
-- scripts/fir-seed.sql (accused/case data must already exist for offender_risk_score to return
-- rows). Idempotent: CREATE ... IF NOT EXISTS, and seeds only run when the tables are still empty.

CREATE TABLE IF NOT EXISTS financial_account (
    account_id        BIGSERIAL PRIMARY KEY,
    account_no        VARCHAR(32) NOT NULL,
    bank_name         VARCHAR(120),
    account_type      VARCHAR(32),
    holder_name       VARCHAR(200),
    -- Links an account to a resolved offender identity: offender_risk_score.person_uid, which is
    -- itself derived by accused_identity (see entity-resolution.sql) — NOT accused.person_uid
    -- directly. Joining against `accused.person_uid` here would silently match nothing, since that
    -- column is the generator's ground-truth label, a different value space from the derived uid.
    holder_person_uid VARCHAR(64),
    is_flagged        BOOLEAN NOT NULL DEFAULT FALSE,
    opened_date       DATE
);
-- Denormalized link to the FIR that flagged this account (one of possibly several cases the
-- holder appears in — the most recent). Lets a query walk straight from a case to its financial
-- accounts without resolving identity first; added per Phase 3 so "money attaches to an FIR"
-- instead of only to a floating person_uid label. ALTER, not baked into CREATE TABLE IF NOT
-- EXISTS above — that body never re-runs once the table already exists (already-deployed
-- environments predate this column and would otherwise silently never get it).
ALTER TABLE financial_account ADD COLUMN IF NOT EXISTS case_master_id INT REFERENCES case_master (case_master_id);
CREATE INDEX IF NOT EXISTS idx_fin_account_person ON financial_account (holder_person_uid);
CREATE INDEX IF NOT EXISTS idx_fin_account_case ON financial_account (case_master_id);

CREATE TABLE IF NOT EXISTS financial_transaction (
    txn_id          BIGSERIAL PRIMARY KEY,
    from_account_id BIGINT REFERENCES financial_account (account_id),
    to_account_id   BIGINT REFERENCES financial_account (account_id),
    amount          NUMERIC(14, 2) NOT NULL,
    txn_date        TIMESTAMP NOT NULL,
    txn_type        VARCHAR(24),
    is_suspicious   BOOLEAN NOT NULL DEFAULT FALSE
);
-- Case the transaction was flagged under, if any (see financial_account.case_master_id above for
-- why this is an ALTER, not part of the CREATE TABLE IF NOT EXISTS body).
ALTER TABLE financial_transaction ADD COLUMN IF NOT EXISTS case_master_id INT REFERENCES case_master (case_master_id);
CREATE INDEX IF NOT EXISTS idx_fin_txn_from ON financial_transaction (from_account_id);
CREATE INDEX IF NOT EXISTS idx_fin_txn_to   ON financial_transaction (to_account_id);

-- One account per prolific offender (case_count >= 2), flagged when they have heinous cases.
-- Scaled up from the original 80 to give the network/graph views more to work with.
INSERT INTO financial_account (account_no, bank_name, account_type, holder_name, holder_person_uid, case_master_id, is_flagged, opened_date)
SELECT 'AC' || lpad((row_number() OVER (ORDER BY ors.case_count DESC, ors.person_uid))::text, 8, '0'),
       (ARRAY['State Bank of India','Canara Bank','Union Bank','HDFC Bank','ICICI Bank','Axis Bank'])[1 + (abs(hashtext(ors.person_uid)) % 6)],
       (ARRAY['Savings','Current'])[1 + (abs(hashtext(ors.person_uid)) % 2)],
       ors.accused_name, ors.person_uid,
       (SELECT ai.case_master_id FROM accused_identity ai
        WHERE ai.person_uid = ors.person_uid ORDER BY ai.case_master_id DESC LIMIT 1),
       ors.heinous_count > 0,
       DATE '2018-01-01' + (abs(hashtext(ors.person_uid)) % 2200)
FROM offender_risk_score ors
WHERE ors.case_count >= 2
  AND NOT EXISTS (SELECT 1 FROM financial_account)
ORDER BY ors.case_count DESC, ors.person_uid
LIMIT 300;

-- Transactions between the accounts of co-accused (money moving inside the co-offender network),
-- so the money trail lines up with the criminal network. Joins through accused_identity (not
-- accused.person_uid — see the holder_person_uid column comment above). Direction is randomized
-- per pair (a hash-based coin flip) rather than fixed by account_id order — the previous
-- `fa1.account_id < fa2.account_id` constraint made the whole transaction graph a monotonic DAG,
-- so no cycle or round-trip could ever exist; recursive multi-hop trails need real cycles to walk.
INSERT INTO financial_transaction (from_account_id, to_account_id, amount, txn_date, txn_type, is_suspicious, case_master_id)
SELECT CASE WHEN abs(hashtext(fa1.account_no || fa2.account_no)) % 2 = 0 THEN fa1.account_id ELSE fa2.account_id END,
       CASE WHEN abs(hashtext(fa1.account_no || fa2.account_no)) % 2 = 0 THEN fa2.account_id ELSE fa1.account_id END,
       round((5000 + (abs(hashtext(fa1.account_no || fa2.account_no)) % 495000))::numeric, 2),
       TIMESTAMP '2023-01-01' + (abs(hashtext(fa1.account_no || fa2.account_no)) % 900) * INTERVAL '1 day',
       (ARRAY['NEFT','IMPS','UPI','RTGS'])[1 + (abs(hashtext(fa1.account_no || fa2.account_no)) % 4)],
       (abs(hashtext(fa1.account_no || fa2.account_no)) % 5 = 0),
       ai1.case_master_id
FROM financial_account fa1
JOIN accused_identity ai1 ON ai1.person_uid = fa1.holder_person_uid
JOIN accused_identity ai2 ON ai2.case_master_id = ai1.case_master_id AND ai2.person_uid <> ai1.person_uid
JOIN financial_account fa2 ON fa2.holder_person_uid = ai2.person_uid
WHERE fa1.account_no < fa2.account_no  -- still one row per unordered pair, direction chosen above
  AND NOT EXISTS (SELECT 1 FROM financial_transaction)
LIMIT 4000;

-- Explicit layering rings: for every co-accused cluster with >= 3 linked accounts, chain them in a
-- closed loop (acc[1] -> acc[2] -> ... -> acc[n] -> acc[1]) — classic money-laundering "layering",
-- and a guaranteed real cycle for the recursive money-trail CTE to find (left to chance in the
-- randomized-direction pass above, a cycle would only occur if enough pairwise coin flips happened
-- to align; this guarantees at least one exists per qualifying cluster instead of hoping).
WITH cluster AS (
    SELECT ai.case_master_id, fa.account_id,
           row_number() OVER (PARTITION BY ai.case_master_id ORDER BY fa.account_id) AS rn,
           count(*) OVER (PARTITION BY ai.case_master_id) AS cluster_size
    FROM accused_identity ai
    JOIN financial_account fa ON fa.holder_person_uid = ai.person_uid
),
ring_edges AS (
    SELECT c1.account_id AS from_account_id, c2.account_id AS to_account_id, c1.case_master_id,
           c1.rn AS hop
    FROM cluster c1
    JOIN cluster c2 ON c2.case_master_id = c1.case_master_id
                   AND c2.rn = (c1.rn % c1.cluster_size) + 1
    WHERE c1.cluster_size >= 3
)
INSERT INTO financial_transaction (from_account_id, to_account_id, amount, txn_date, txn_type, is_suspicious, case_master_id)
SELECT r.from_account_id, r.to_account_id,
       round((8000 + (abs(hashtext(r.from_account_id::text || '-' || r.to_account_id::text)) % 92000))::numeric, 2),
       TIMESTAMP '2024-06-01' + (r.hop * INTERVAL '2 days'),
       'IMPS',
       TRUE,
       r.case_master_id
FROM ring_edges r
WHERE NOT EXISTS (
    SELECT 1 FROM financial_transaction ft
    WHERE ft.from_account_id = r.from_account_id AND ft.to_account_id = r.to_account_id
)
LIMIT 500;
