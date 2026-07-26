-- Phase 4.8/4.13 views. Loaded after fir-schema.sql (needs case_master) and
-- financial-crime-model.sql (needs financial_transaction/financial_account). Idempotent
-- (CREATE OR REPLACE VIEW).

-- ============================================================================
-- 4.8 Modus operandi — derived from BriefFacts (free text), NOT a new official column. The FIR
-- schema has no structured MO field; motive/entry_method are recovered by matching the same
-- closed vocabulary the data generator draws from (scripts/generate_fir_data.py's MOTIVES/
-- ENTRY_METHODS lists) back out of the free-text brief_facts. incident_hour is a genuine official
-- signal (IncidentFromDate), not text-derived. On real production data, an equivalent MO-mining
-- pass would use real free-text NLP against real brief_facts, not this fixed-vocabulary match —
-- this view is demo-data-shaped, and that limitation is intentional and documented here.
-- ============================================================================
CREATE OR REPLACE VIEW case_mo_features AS
SELECT
    cm.case_master_id,
    CASE
        WHEN cm.brief_facts ILIKE '%money dispute%'     THEN 'money dispute'
        WHEN cm.brief_facts ILIKE '%land dispute%'      THEN 'land dispute'
        WHEN cm.brief_facts ILIKE '%old enmity%'        THEN 'old enmity'
        WHEN cm.brief_facts ILIKE '%drunken quarrel%'   THEN 'drunken quarrel'
        WHEN cm.brief_facts ILIKE '%family dispute%'    THEN 'family dispute'
        WHEN cm.brief_facts ILIKE '%business rivalry%'  THEN 'business rivalry'
        ELSE NULL
    END AS motive,
    CASE
        WHEN cm.brief_facts ILIKE '%forcing open the rear window%'   THEN 'forced rear window'
        WHEN cm.brief_facts ILIKE '%breaking the front door lock%'   THEN 'forced front door lock'
        WHEN cm.brief_facts ILIKE '%cutting through the compound grill%' THEN 'cut compound grill'
        WHEN cm.brief_facts ILIKE '%scaling the compound wall%'      THEN 'scaled compound wall'
        WHEN cm.brief_facts ILIKE '%removing a ventilator grill%'    THEN 'removed ventilator grill'
        ELSE NULL
    END AS entry_method,
    extract(hour FROM cm.incident_from_date)::int AS incident_hour,
    CASE
        WHEN extract(hour FROM cm.incident_from_date)::int BETWEEN 22 AND 23
          OR extract(hour FROM cm.incident_from_date)::int BETWEEN 0 AND 4  THEN 'night'
        WHEN extract(hour FROM cm.incident_from_date)::int BETWEEN 5 AND 11 THEN 'morning'
        WHEN extract(hour FROM cm.incident_from_date)::int BETWEEN 12 AND 17 THEN 'afternoon'
        ELSE 'evening'
    END AS time_of_day_bucket
FROM case_master cm
WHERE cm.incident_from_date IS NOT NULL;

COMMENT ON VIEW case_mo_features IS
    'Derived MO signal from brief_facts (motive/entry_method) and incident_from_date (time-of-day) — see documents/SCHEMA_FIDELITY.md.';

-- ============================================================================
-- 4.13 Suspicion detection — replaces the seeded is_suspicious flag (a 20% coin flip with no
-- underlying signal) with real rules evaluated at query time: structuring (many transactions just
-- under a round reporting threshold), velocity (multiple transactions same day), round-number
-- amounts (a classic layering tell), and fan-in degree (handled separately in fanInAccounts(),
-- which already counts distinct senders — this view covers the per-transaction rules).
-- ============================================================================
CREATE OR REPLACE VIEW financial_transaction_risk AS
WITH same_day_counts AS (
    SELECT from_account_id, date_trunc('day', txn_date) AS d, count(*) AS same_day_from_count
    FROM financial_transaction
    GROUP BY 1, 2
)
SELECT
    ft.txn_id,
    ft.amount >= 45000 AND ft.amount < 50000 AS is_structuring,
    sdc.same_day_from_count >= 3 AS is_high_velocity,
    (ft.amount = round(ft.amount, -3)) AS is_round_number,
    (
        ft.amount >= 45000 AND ft.amount < 50000
        OR sdc.same_day_from_count >= 3
        OR ft.amount = round(ft.amount, -3)
    ) AS is_suspicious_derived
FROM financial_transaction ft
LEFT JOIN same_day_counts sdc
    ON sdc.from_account_id = ft.from_account_id AND sdc.d = date_trunc('day', ft.txn_date);

COMMENT ON VIEW financial_transaction_risk IS
    'Real rule-evaluated suspicion signal (structuring/velocity/round-number), computed at query time — replaces the seed''s hashtext-based is_suspicious coin flip. See documents/SCHEMA_FIDELITY.md.';
