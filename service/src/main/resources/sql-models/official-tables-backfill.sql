-- Backfills inv_arrestsurrenderaccused and inv_occurance_time (added in fir-schema.sql, Phase 1.2
-- of the remediation plan) from data already present in arrest_surrender / case_master. Load order:
-- after fir-schema.sql AND scripts/fir-seed.sql (needs their data). Idempotent — WHERE NOT EXISTS
-- guards make re-running a no-op.

-- One junction row per existing arrest_surrender.accused_master_id — preserves today's
-- single-accused-per-event data under the official many-to-many junction shape.
INSERT INTO inv_arrestsurrenderaccused (inv_arrestsurrenderaccused_id, arrest_surrender_id, accused_master_id, is_primary_accused)
SELECT ar.arrest_surrender_id, ar.arrest_surrender_id, ar.accused_master_id, COALESCE(ar.is_accused, TRUE)
FROM arrest_surrender ar
WHERE ar.accused_master_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM inv_arrestsurrenderaccused);

-- One row per case, deriving occurance_time from the existing incident_from_date's clock
-- component (case_master has no separate "place of occurrence" text beyond brief_facts/lat-long,
-- so place_of_occurance/is_public_place stay NULL until a real source field exists).
INSERT INTO inv_occurance_time (case_master_id, occurance_time)
SELECT cm.case_master_id, cm.incident_from_date::time
FROM case_master cm
WHERE cm.incident_from_date IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM inv_occurance_time);
