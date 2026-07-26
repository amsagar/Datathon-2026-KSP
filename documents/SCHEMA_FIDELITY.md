# Schema fidelity — official ER diagram vs. implementation

Source of truth: `documents/Police_FIR_ER_Diagram.pdf` (organizer-provided, 9 pages: table
definitions + a relationship matrix). This document maps every official entity/column to what the
implementation actually does, so a reviewer can see at a glance where the build matches, renames,
derives, omits, or adds beyond the official schema — and why.

**Load order for the crime database:** `fir-schema.sql` → `entity-resolution.sql` →
`official-tables-backfill.sql` → `financial-crime-model.sql` → `post-reseed-features.sql` run
automatically on every backend boot (`ServiceConfig.crimeDataSourceInitializer`, idempotent). Only
the bulk synthetic dataset, `scripts/fir-seed.sql`, must be loaded manually — see
[`DEPLOY.md`](../DEPLOY.md). The order matters internally: `entity-resolution.sql` redefines
`offender_risk_score` before `financial-crime-model.sql`'s seed step queries it,
`official-tables-backfill.sql` needs `arrest_surrender`/`case_master` data to have anything to
backfill (a harmless no-op before `fir-seed.sql` has run), and `post-reseed-features.sql`'s
`financial_transaction_risk` view needs the `financial_transaction` table to exist.

## Legend

- **match** — same table/column, translated to PostgreSQL naming (`CamelCase` → `snake_case`) and
  types (`NVARCHAR(MAX)`→`TEXT`, `BIT`→`BOOLEAN`, `DATETIME`→`TIMESTAMP`).
- **derived** — the official field doesn't exist; a value is computed from officially-available
  data instead of being invented or copied from elsewhere.
- **not-implemented** — official field/table, not built.
- **added-beyond-spec** — implementation-only, absent from the official ER diagram.

## Core case tables

| Official (ER diagram) | Implementation | Status | Notes |
|---|---|---|---|
| `CaseMaster` | `case_master` | match | All columns present (`crime_no`, `case_no`, `crime_registered_date`, FKs, `incident_from_date`, `incident_to_date`, `info_received_ps_date`, `latitude`, `longitude`, `brief_facts`). |
| `ComplainantDetails` | `complainant_details` | match | Includes `occupation_id`/`religion_id`/`caste_id`/`gender_id` — the only officially-documented socio-economic fields, and scoped to complainants only (see Area 4 note below). |
| `Victim` | `victim` | match | |
| `Accused` | `accused` | match + added-beyond-spec | `accused_master_id`, `case_master_id`, `accused_name`, `age_year`, `gender_id`, `person_id` all match. `person_uid` is **added-beyond-spec** — see "Entity resolution" below; it is not an official column. |
| `ArrestSurrender` | `arrest_surrender` | match | Direct `accused_master_id` FK retained for the single-accused-per-event case the seed generator produces (see `inv_arrestsurrenderaccused` below for the multi-accused case). |
| `Act` / `Section` / `CrimeHeadActSection` | `act` / `section` / `crime_head_act_section` | match | |
| `CrimeHead` / `CrimeSubHead` | `crime_head` / `crime_sub_head` | match | |
| `CasteMaster` / `ReligionMaster` / `OccupationMaster` / `CaseStatusMaster` / `CaseCategory` / `GravityOffence` | `caste_master` / `religion_master` / `occupation_master` / `case_status_master` / `case_category` / `gravity_offence` | match | |
| `Court` / `District` / `State` / `Unit` / `UnitType` | `court` / `district` / `state` / `unit` / `unit_type` | match + added-beyond-spec | `district.district_name_kn` is **added-beyond-spec**: a hardcoded Kannada transliteration for each of the 31 Karnataka districts, so a user typing a district name in Kannada chat (e.g. "ಬೆಂಗಳೂರು") still matches. Not an official column — there are only 31 fixed values, so this is a lookup table, not a general translation mechanism. |
| `Rank` / `Designation` / `Employee` | `rank` / `designation` / `employee` | match | |
| `ChargesheetDetails` | `chargesheet_details` | match | `cs_type`: `A`=Chargesheet, `B`=False Case, `C`=Undetected, exactly as documented. |
| `Inv_OccuranceTime` (1:1 with CaseMaster) | `inv_occurance_time` | added-beyond-spec (table name matches; columns do not) | The organizer's diagram names this table and its 1:1 cardinality with `CaseMaster` in the relationship matrix **but the table-definitions section has no column list for it** — only `CaseMaster`'s own `IncidentFromDate`/`IncidentToDate`/`latitude`/`longitude` are documented. `occurance_time`, `place_of_occurance`, `is_public_place` are a best-effort reconstruction from the table's stated purpose, not a transcription of a real column list. `occurance_time` is backfilled from `case_master.incident_from_date`'s clock component; `place_of_occurance`/`is_public_place` have no source data yet and are left `NULL`. **If the organizers supply the actual column list, replace this table's shape rather than assume it is correct.** |
| `inv_arrestsurrenderaccused` (junction, "one arrest event can link multiple accused") | `inv_arrestsurrenderaccused` | added-beyond-spec (table name + purpose match; columns inferred) | Same caveat as above: named only in the relationship matrix, no column-level definition given. Implemented as the minimal junction shape a many-to-many needs: `arrest_surrender_id`, `accused_master_id`, plus `is_primary_accused` (carrying forward `arrest_surrender.is_accused`'s per-pair semantic). Backfilled 1:1 from every existing `arrest_surrender.accused_master_id` row, so no data is lost — today's generator still only produces one accused per arrest event, so this table currently has exactly one row per non-null `arrest_surrender.accused_master_id`; it exists so a future multi-accused arrest event has somewhere correct to go. |

## Entity resolution — replacing the synthetic `person_uid` assumption

The official `Accused.PersonID` is documented as *"Accused Sorting like A1, A2, A3…"* — a
**within-case ordinal**, not a cross-case person identity. There is no official field anywhere in
the schema that says "this accused in case X is the same real person as that accused in case Y".

`accused.person_uid` (this project's addition, not in the official ER diagram) is populated by the
synthetic data generator as **ground truth for the demo dataset only**. On real production data,
nothing populates an equivalent field — every feature that assumed it as a given input (repeat-
offender detection, co-offender networks, organized-crime groups, offender risk scoring, financial
linkage) would silently return nothing.

**Fix:** `accused_identity` (`service/src/main/resources/sql-models/entity-resolution.sql`) derives
identity instead of assuming it, from the only officially-available signal: normalised name +
gender + an age-derived birth-year estimate (bucketed into 2-year windows to absorb ±1 year of
reporting drift, since age at FIR time is typically an estimate, not a verified DOB). It reports a
confidence score per identity (1.00 for an unambiguous singleton, decreasing as the matching
cluster grows and name collisions become more likely) — an investigator sees that "same person" is
*inferred*, not certain.

`offender_risk_score` is redefined to source `person_uid` from `accused_identity` rather than from
`accused.person_uid`; every application query that already joined on `offender_risk_score` or
`accused_identity` needs no further change. `AnalyticsRepository`'s co-offender/network/offender-
profile queries and `CrimeDatabaseTools.summarizeCase` were repointed the same way.

**Measured accuracy** (against the generator's ground-truth `person_uid`, via the
`accused_identity_eval` view — evaluation-only, meaningless on real data with no ground truth):
on the ~21k-row seeded dataset, **96.8% recall / 97.6% precision** on same-person pairwise linkage.

**Acceptance test performed:** `ALTER TABLE accused DROP COLUMN person_uid CASCADE` against a
disposable Postgres loaded with the full pipeline — `offender_risk_score`, `accused_identity`, and
`financial_transaction` all continued to return data afterward (only the ground-truth-dependent
`accused_identity_eval` view was dropped, as expected). This demonstrates none of the
production-facing features actually depend on the synthetic column.

**Known limitation:** name+gender+age-bucket blocking will merge two different real people who
share an unusually common name, age, and gender, and will split one real person whose name is
recorded inconsistently across cases (e.g. a transliteration/spelling variant) or whose age drifts
by more than the 2-year tolerance. This is disclosed via the `confidence`/`cluster_size` columns
rather than hidden.

## Derived views added post-reseed (Phase 4.8/4.13)

- **`case_mo_features`** (`post-reseed-features.sql`) — `motive`/`entry_method` derived by matching
  `brief_facts` against the generator's closed vocabulary; `incident_hour`/`time_of_day_bucket`
  derived from the official `incident_from_date` column. Powers MO-trend grouping (Area 3) and
  offender MO signatures (Area 5) without inventing a structured MO column the official schema
  doesn't have. On real (non-synthetic) FIR data, an equivalent view would need real free-text NLP
  against `brief_facts` instead of fixed-vocabulary matching — this view is demo-data-shaped by
  design, not a general solution.
- **`financial_transaction_risk`** (`post-reseed-features.sql`) — replaces the seed's
  `is_suspicious` (a `hashtext(...) % 5 = 0` coin flip) with rules evaluated at query time:
  structuring (amount just under a round reporting threshold), high transaction velocity from one
  account in a day, and round-number amounts. `suspiciousTransactions()`/`fanInAccounts()` should
  prefer this view's `is_suspicious_derived` over the stored column.

## Financial schema — not part of the official ER diagram

`financial_account` / `financial_transaction` (`financial-crime-model.sql`) are a **proposed
integration schema** for Area 7 (financial crime linkage), invented by this project because the
official ER diagram has no financial entities at all. `financial_account.holder_person_uid` links
to `offender_risk_score.person_uid` (itself derived by `accused_identity`) — **not** to
`accused.person_uid` directly; the seed's own transaction-generation query was fixed to join
through `accused_identity` for the same reason (see the column comment in
`financial-crime-model.sql`). A real production integration would instead carry `case_master_id`
directly on the financial tables (planned — see the data-generator overhaul) rather than resolve
identity via name/case linkage.

## Areas where the official schema constrains feature scope

- **No modus-operandi field.** Nothing in the official schema codes MO (weapon, entry method,
  target, time-of-day) as structured data — only `BriefFacts` (free text). MO features must be
  derived from `brief_facts` text, never invented as a new structured column pretending to be
  official. The system prompt explicitly instructs the assistant not to claim structured MO
  comparison for this reason.
- **Socio-economic fields exist only on `ComplainantDetails`**, not `Accused`. Area 4 (demographic/
  socio-economic insight) is scoped to complainant occupation + accused age/gender; caste/religion
  are deliberately excluded from analytics as a fairness decision, not a data gap.
- **`Accused.PersonID` is a within-case ordinal**, not a person key — see "Entity resolution" above.
