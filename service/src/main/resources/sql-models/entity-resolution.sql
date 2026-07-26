-- ============================================================================
-- Entity resolution: replaces the assumption that "person_uid" is a given input
-- field with a real, derived cross-case identity layer.
--
-- The official ER diagram's Accused entity is AccusedMasterID, CaseMasterID, AccusedName,
-- AgeYear, GenderID, PersonID — and PersonID is documented as "Accused Sorting like A1, A2, A3…",
-- a WITHIN-CASE ordinal, not a cross-case person key. There is no official field that says "this
-- accused in case X is the same real person as that accused in case Y". `accused.person_uid`
-- (added by this project, not the official schema) is populated by the synthetic data generator
-- as ground truth for the demo — on real production data nothing will populate it, and every
-- feature that joins on it (repeat-offender detection, co-offender networks, organized-crime
-- groups, offender risk scoring, financial linkage) would silently return nothing.
--
-- accused_identity below computes a person_uid instead of assuming one, from the only
-- officially-available signal: normalised name + gender + an age-derived birth-year estimate
-- (bucketed to absorb ±1 year of reporting drift, since age at FIR time is often an
-- eyewitness/officer estimate, not a verified DOB). offender_risk_score is then redefined to
-- source identity from this view rather than from accused.person_uid directly — every downstream
-- query that already joins on offender_risk_score.person_uid needs no further change.
--
-- Ground truth: accused.person_uid (assigned by the generator) is kept as-is and is NOT read by
-- this file. It exists solely so an evaluation query can compare accused_identity's clusters
-- against it (precision/recall) — see documents/SCHEMA_FIDELITY.md.
--
-- Load order: after fir-schema.sql (needs accused/case_master), before scripts/fir-seed.sql
-- (accused_identity is a view — created empty here, populated implicitly once fir-seed.sql loads
-- data) and before financial-crime-model.sql (which seeds financial_account by querying the
-- redefined offender_risk_score, so it must see the NEW identity-derived values).
-- ============================================================================

CREATE OR REPLACE VIEW accused_identity AS
WITH normalized AS (
    SELECT
        a.accused_master_id,
        a.case_master_id,
        lower(regexp_replace(btrim(a.accused_name), '\s+', ' ', 'g')) AS name_key,
        a.gender_id,
        a.age_year,
        -- Estimated birth year, bucketed into 2-year windows so age misreported by ±1 year
        -- across cases doesn't split one real person into separate identities.
        floor((extract(year FROM cm.crime_registered_date)::int - coalesce(a.age_year, 0)) / 2.0)::int
            AS birth_year_bucket
    FROM accused a
    JOIN case_master cm ON cm.case_master_id = a.case_master_id
    WHERE a.accused_name IS NOT NULL AND btrim(a.accused_name) <> ''
),
clustered AS (
    SELECT n.*,
           count(*) OVER (PARTITION BY name_key, gender_id, birth_year_bucket) AS cluster_size
    FROM normalized n
)
SELECT
    accused_master_id,
    case_master_id,
    -- Stable across re-runs (pure function of the blocking key), unlike a sequence/UUID.
    md5(name_key || '|' || coalesce(gender_id::text, '?') || '|' || birth_year_bucket::text) AS person_uid,
    name_key,
    gender_id,
    birth_year_bucket,
    cluster_size,
    -- Honest, coarse confidence: a name+gender+age-bucket match is the strongest signal available
    -- without an official person key, but common names collide more as the cluster grows, so
    -- confidence decreases with cluster size rather than being reported as certainty.
    CASE
        WHEN cluster_size = 1 THEN 1.00
        WHEN cluster_size <= 3 THEN 0.85
        WHEN cluster_size <= 6 THEN 0.65
        ELSE 0.45
    END AS confidence,
    'name+gender+age_bucket(2y)' AS method
FROM clustered;

COMMENT ON VIEW accused_identity IS
    'Derived cross-case offender identity (name+gender+age-bucket clustering). Replaces the assumption that a person key is supplied by the source system — see documents/SCHEMA_FIDELITY.md.';

-- offender_risk_score is defined HERE ONLY (not in fir-schema.sql — see that file's note at the
-- same spot for why keeping two definitions around broke every second app boot), sourcing
-- identity from accused_identity instead of accused.person_uid. Column NAME is unchanged (still
-- emits `person_uid`, now text instead of varchar(40) since it's an md5 hash) so every existing
-- caller of this view needs no change. DROP+CREATE (not OR REPLACE) because both the person_uid
-- column's type AND the column count change across re-runs, either of which CREATE OR REPLACE
-- VIEW rejects.
DROP VIEW IF EXISTS offender_risk_score;
CREATE VIEW offender_risk_score AS
SELECT ai.person_uid,
       max(a.accused_name)                                  AS accused_name,
       count(DISTINCT a.case_master_id)                      AS case_count,
       count(DISTINCT cm.case_master_id)
           FILTER (WHERE g.lookup_value = 'Heinous')         AS heinous_count,
       max(cm.crime_registered_date)                         AS last_case_date,
       count(DISTINCT cs.case_master_id)
           FILTER (WHERE cs.cs_type = 'A')                   AS chargesheeted_count,
       round(
           (count(DISTINCT a.case_master_id) * 10
            + count(DISTINCT cm.case_master_id) FILTER (WHERE g.lookup_value = 'Heinous') * 15
            + CASE WHEN max(cm.crime_registered_date) >=
                        ((SELECT max(crime_registered_date) FROM case_master) - INTERVAL '1 year')
                   THEN 20 ELSE 0 END)
           * (0.5 + 0.5 * count(DISTINCT cs.case_master_id) FILTER (WHERE cs.cs_type = 'A')
                        / greatest(count(DISTINCT a.case_master_id), 1))
       , 1)                                                  AS risk_score,
       max(ai.confidence)                                    AS identity_confidence
FROM accused a
JOIN accused_identity ai ON ai.accused_master_id = a.accused_master_id
JOIN case_master cm ON cm.case_master_id = a.case_master_id
LEFT JOIN gravity_offence g ON g.gravity_offence_id = cm.gravity_offence_id
LEFT JOIN chargesheet_details cs ON cs.case_master_id = a.case_master_id
GROUP BY ai.person_uid;

-- Evaluation view: precision/recall of accused_identity's derived clusters against the
-- generator's ground-truth accused.person_uid (evaluation-only signal — see file header).
-- Only meaningful on the seeded demo dataset, where ground truth exists; a no-op (empty) on real
-- production data where accused.person_uid is never populated.
CREATE OR REPLACE VIEW accused_identity_eval AS
WITH pairs AS (
    SELECT a.accused_master_id, a.person_uid AS ground_truth_uid, ai.person_uid AS derived_uid
    FROM accused a
    JOIN accused_identity ai ON ai.accused_master_id = a.accused_master_id
    WHERE a.person_uid IS NOT NULL
),
same_ground_truth AS (
    SELECT p1.accused_master_id AS a1, p2.accused_master_id AS a2
    FROM pairs p1 JOIN pairs p2
        ON p2.ground_truth_uid = p1.ground_truth_uid AND p2.accused_master_id > p1.accused_master_id
),
same_derived AS (
    SELECT p1.accused_master_id AS a1, p2.accused_master_id AS a2
    FROM pairs p1 JOIN pairs p2
        ON p2.derived_uid = p1.derived_uid AND p2.accused_master_id > p1.accused_master_id
)
SELECT
    (SELECT count(*) FROM same_ground_truth) AS ground_truth_pairs,
    (SELECT count(*) FROM same_derived) AS derived_pairs,
    (SELECT count(*) FROM same_ground_truth sg JOIN same_derived sd
        ON sd.a1 = sg.a1 AND sd.a2 = sg.a2) AS true_positive_pairs;
