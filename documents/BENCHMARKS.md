# Benchmarks

Measured against the seeded 18,000-case synthetic dataset (`scripts/fir-seed.sql`,
`python3 scripts/generate_fir_data.py`), loaded into a disposable local Postgres 16
(`pgvector/pgvector:pg16`) — never the shared/live database. All timings are wall-clock query time
only (`psql \timing`), not including HTTP/serialization overhead.

## 1. Entity resolution (`accused_identity`) — precision/recall

Methodology: `accused_identity_eval` (in `entity-resolution.sql`) compares every pair of accused
rows sharing the SAME derived `person_uid` (name + gender + 2-year age-bucket clustering) against
every pair sharing the same generator-assigned ground-truth `accused.person_uid` (evaluation-only
signal, never used by the application — see `documents/SCHEMA_FIDELITY.md`).

| Dataset generation | Ground-truth same-person pairs | Derived same-person pairs | True positives | Recall | Precision |
|---|---|---|---|---|---|
| Pre-Phase-3 generator | 42,763 | 42,429 | 41,402 | 96.82% | 97.58% |
| Post-Phase-3 generator (mixed gender, station coords, etc.) | 40,003 | 39,742 | 38,592 | 96.47% | 97.11% |

**Acceptance test**: `ALTER TABLE accused DROP COLUMN person_uid CASCADE` — `offender_risk_score`,
`accused_identity`, and every dependent feature (network, offender profile, financial linkage)
continued to return data afterward. Only the ground-truth-dependent `accused_identity_eval` view
itself was dropped (as expected — it exists solely to score against that column).

Known failure modes (see `documents/SCHEMA_FIDELITY.md`): two different real people sharing an
unusually common name/age/gender will merge; one real person whose name is recorded inconsistently
across cases (spelling/transliteration variance) or whose age drifts more than 2 years will split.
`confidence`/`cluster_size` on every row surface this rather than hiding it.

## 2. Crime-volume forecast — backtest MAPE/RMSE

Methodology: `CrimeForecaster.forecast()` now always computes a holdout backtest (see
`CrimeForecasterTest` for the unit-level proof — a perfectly flat series backtests to ~0% error).
This is a real number from the live statewide monthly series (90 months, 2019-01 through 2026-06):

| Series | Method | Holdout | MAPE | RMSE |
|---|---|---|---|---|
| Statewide monthly case volume | Holt-Winters | 6 months | **11.31%** | **27.12 cases/month** |

This number is not a one-off: every `/forecast` and `/forecast/hotspots` response includes this
same `backtest` field, computed fresh against whatever date range/district/crime-head filter was
requested — it's a live metric, not a static claim. The "Predicted hotspots" dashboard panel
surfaces it directly as "Backtest error (MAPE)" per district.

## 3. Dashboard query latency (18k-case dataset, disposable local Postgres, 5-run samples)

| Query | Backs | Min | Max | Notes |
|---|---|---|---|---|
| Monthly trends by crime head | `GET /analytics/trends` | 24.9 ms | 33.0 ms | Full-range, no filters |
| Offender risk ranking (top 50) | `GET /analytics/risk-scores` | 130.3 ms | 161.4 ms | Sources `offender_risk_score`, which joins through `accused_identity` |
| Per-district monthly totals | `GET /analytics/forecast/hotspots` | 18.1 ms | 24.1 ms | The single grouped query that replaced the original per-district N+1 loop |

## 4. Not measured in this environment: text-to-SQL (`run_crime_sql`) latency

Requires a live call to the deployed LLM (Zoho Catalyst QuickML) — this environment has no network
egress to that paid third-party endpoint, and calling it isn't appropriate without the deployment's
own credentials/rate-limit budget. Once deployed, `llm_usage` already records per-call latency for
every tool-calling turn (`LlmUsageRecorder`) — a real p50/p95 can be pulled directly from that table
after a short demo/usage period, e.g.:

```sql
SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50,
       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95
FROM llm_usage WHERE source = 'chat';
```

## Reproducing these numbers

```bash
docker run -d --name ksp-bench -e POSTGRES_PASSWORD=test -e POSTGRES_DB=ksp_test -p 55432:5432 pgvector/pgvector:pg16
psql -h localhost -p 55432 -U postgres -d ksp_test -f service/src/main/resources/sql-models/fir-schema.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -f service/src/main/resources/sql-models/entity-resolution.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -f scripts/fir-seed.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -f service/src/main/resources/sql-models/official-tables-backfill.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -f service/src/main/resources/sql-models/financial-crime-model.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -f service/src/main/resources/sql-models/post-reseed-features.sql
psql -h localhost -p 55432 -U postgres -d ksp_test -c "SELECT * FROM accused_identity_eval;"
```
