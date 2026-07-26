package com.ksp.agent.analytics.repo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate queries over the FIR schema powering the dashboard, hotspot map,
 * network graph and offender risk pages. Every query here targets FIR tables (case_master,
 * crime_head, district, accused, offender_risk_score, ...), which live on the crime datasource,
 * not the app (primary) one — must use {@code crimeJdbcTemplate}, same as CrimeDatabaseTools.
 */
@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(@Qualifier("crimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> trends(String from, String to, Integer districtId, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                SELECT to_char(date_trunc('month', cm.crime_registered_date), 'YYYY-MM') AS period,
                       ch.crime_group_name AS crime_head,
                       count(*) AS count
                FROM case_master cm
                JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR u.district_id = ?::int)
                  AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                GROUP BY 1, 2
                ORDER BY 1, 2
                """, from, to, districtId, districtId, crimeHeadId, crimeHeadId);
    }

    public List<Map<String, Object>> hotspots(String from, String to, Integer districtId, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                SELECT round(cm.latitude, 3)  AS lat,
                       round(cm.longitude, 3) AS lng,
                       count(*)               AS weight,
                       min(ch.crime_group_name) AS crime_head
                FROM case_master cm
                JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                WHERE cm.latitude IS NOT NULL
                  AND cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR u.district_id = ?::int)
                  AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                GROUP BY 1, 2
                HAVING count(*) >= 1
                ORDER BY weight DESC
                LIMIT 2000
                """, from, to, districtId, districtId, crimeHeadId, crimeHeadId);
    }

    public List<Map<String, Object>> districtSummary(String from, String to) {
        return jdbcTemplate.queryForList("""
                SELECT d.district_id, d.district_name, d.district_name_kn, count(*) AS total,
                       count(*) FILTER (WHERE g.lookup_value = 'Heinous') AS heinous,
                       count(DISTINCT cm.police_station_id) AS stations
                FROM case_master cm
                JOIN unit u ON u.unit_id = cm.police_station_id
                JOIN district d ON d.district_id = u.district_id
                LEFT JOIN gravity_offence g ON g.gravity_offence_id = cm.gravity_offence_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                GROUP BY d.district_id, d.district_name, d.district_name_kn
                ORDER BY total DESC
                """, from, to);
    }

    public List<Map<String, Object>> crimeHeads() {
        return jdbcTemplate.queryForList(
                "SELECT crime_head_id, crime_group_name FROM crime_head WHERE active ORDER BY crime_head_id");
    }

    public List<Map<String, Object>> districts() {
        return jdbcTemplate.queryForList(
                "SELECT district_id, district_name, district_name_kn FROM district WHERE active ORDER BY district_name");
    }

    public List<Map<String, Object>> riskScores(Integer limit) {
        return jdbcTemplate.queryForList("""
                SELECT person_uid, accused_name, case_count, heinous_count,
                       chargesheeted_count, last_case_date, risk_score
                FROM offender_risk_score
                ORDER BY risk_score DESC
                LIMIT ?
                """, limit == null || limit <= 0 ? 50 : Math.min(limit, 500));
    }

    /** Cases and co-accused within N hops of a repeat-offender identity (identity resolved via {@code accused_identity}). */
    public List<Map<String, Object>> networkEdges(String personUid) {
        return jdbcTemplate.queryForList("""
                WITH seed_cases AS (
                    SELECT DISTINCT case_master_id FROM accused_identity WHERE person_uid = ?
                ),
                ring1 AS ( -- everyone in those cases
                    SELECT DISTINCT ai.person_uid FROM accused_identity ai
                    JOIN seed_cases s ON s.case_master_id = ai.case_master_id
                ),
                all_cases AS ( -- every case of everyone in ring 1
                    SELECT DISTINCT ai.case_master_id FROM accused_identity ai
                    JOIN ring1 r ON r.person_uid = ai.person_uid
                )
                SELECT ai.person_uid, max(a.accused_name) AS accused_name,
                       a.case_master_id, max(cm.crime_no) AS crime_no,
                       max(ch.crime_group_name) AS crime_head,
                       max(u.unit_name) AS station, max(u.unit_id) AS station_id
                FROM accused a
                JOIN accused_identity ai ON ai.accused_master_id = a.accused_master_id
                JOIN all_cases ac ON ac.case_master_id = a.case_master_id
                JOIN case_master cm ON cm.case_master_id = a.case_master_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                GROUP BY ai.person_uid, a.case_master_id
                LIMIT 800
                """, personUid);
    }

    /**
     * Victims of every case in the given offender identity's network (Area 2: network
     * completeness — the requirement names accused/victims/locations/financial accounts/incidents
     * as node types; the graph previously had two). One row per victim, mirroring
     * {@link #networkEdges}'s own seed_cases/ring1/all_cases reachability so the victim set matches
     * exactly the cases already rendered.
     */
    public List<Map<String, Object>> victimsForNetwork(String personUid) {
        return jdbcTemplate.queryForList("""
                WITH seed_cases AS (
                    SELECT DISTINCT case_master_id FROM accused_identity WHERE person_uid = ?
                ),
                ring1 AS (
                    SELECT DISTINCT ai.person_uid FROM accused_identity ai
                    JOIN seed_cases s ON s.case_master_id = ai.case_master_id
                ),
                all_cases AS (
                    SELECT DISTINCT ai.case_master_id FROM accused_identity ai
                    JOIN ring1 r ON r.person_uid = ai.person_uid
                )
                SELECT v.case_master_id, v.victim_master_id, v.victim_name
                FROM victim v
                JOIN all_cases ac ON ac.case_master_id = v.case_master_id
                LIMIT 800
                """, personUid);
    }

    /** Top co-offending pairs for the whole-state network overview (identity resolved via {@code accused_identity}). */
    public List<Map<String, Object>> topCoOffenders(Integer limit) {
        return jdbcTemplate.queryForList("""
                SELECT ai1.person_uid AS source_uid, max(a1.accused_name) AS source_name,
                       ai2.person_uid AS target_uid, max(a2.accused_name) AS target_name,
                       count(DISTINCT ai1.case_master_id) AS shared_cases
                FROM accused_identity ai1
                JOIN accused a1 ON a1.accused_master_id = ai1.accused_master_id
                JOIN accused_identity ai2 ON ai2.case_master_id = ai1.case_master_id
                                         AND ai2.person_uid > ai1.person_uid
                JOIN accused a2 ON a2.accused_master_id = ai2.accused_master_id
                GROUP BY ai1.person_uid, ai2.person_uid
                HAVING count(DISTINCT ai1.case_master_id) >= 2
                ORDER BY shared_cases DESC
                LIMIT ?
                """, limit == null || limit <= 0 ? 120 : Math.min(limit, 500));
    }

    /** Emerging patterns: crime heads whose last-90-day volume is well above their trailing-year baseline. */
    public List<Map<String, Object>> earlyWarnings() {
        return jdbcTemplate.queryForList("""
                WITH last_date AS (SELECT max(crime_registered_date) AS d FROM case_master),
                recent AS (
                    SELECT u.district_id, cm.crime_major_head_id, count(*) AS recent_count
                    FROM case_master cm JOIN unit u ON u.unit_id = cm.police_station_id, last_date
                    WHERE cm.crime_registered_date > last_date.d - INTERVAL '90 days'
                    GROUP BY 1, 2
                ),
                baseline AS (
                    SELECT u.district_id, cm.crime_major_head_id, count(*) / 4.0 AS avg_quarter
                    FROM case_master cm JOIN unit u ON u.unit_id = cm.police_station_id, last_date
                    WHERE cm.crime_registered_date BETWEEN last_date.d - INTERVAL '455 days'
                                                       AND last_date.d - INTERVAL '90 days'
                    GROUP BY 1, 2
                )
                SELECT d.district_name, d.district_name_kn, ch.crime_group_name AS crime_head,
                       r.recent_count, round(b.avg_quarter, 1) AS baseline_per_quarter,
                       round(r.recent_count / greatest(b.avg_quarter, 1) , 2) AS spike_ratio
                FROM recent r
                JOIN baseline b ON b.district_id = r.district_id
                               AND b.crime_major_head_id = r.crime_major_head_id
                JOIN district d ON d.district_id = r.district_id
                JOIN crime_head ch ON ch.crime_head_id = r.crime_major_head_id
                WHERE r.recent_count >= 8 AND r.recent_count / greatest(b.avg_quarter, 1) >= 1.4
                ORDER BY spike_ratio DESC
                LIMIT 20
                """);
    }

    /**
     * Districts with an unusual number of distinct HIGH-RISK repeat offenders (case_count >=
     * minCaseCount in offender_risk_score) active in the last {@code recentDays} — the
     * offender/gang-focused half of Phase 4.12's real alerts (the existing early-warning query is
     * purely crime-count-based and has no offender/network term at all).
     */
    public List<Map<String, Object>> repeatOffenderSurgeByDistrict(int minCaseCount, int recentDays) {
        return jdbcTemplate.queryForList("""
                WITH recent_cutoff AS (
                    SELECT max(crime_registered_date) - (?::text || ' days')::interval AS cutoff FROM case_master
                ),
                eligible AS (
                    SELECT person_uid FROM offender_risk_score WHERE case_count >= ?
                ),
                recent_repeat AS (
                    SELECT DISTINCT ai.person_uid, u.district_id
                    FROM accused_identity ai
                    JOIN case_master cm ON cm.case_master_id = ai.case_master_id
                    JOIN unit u ON u.unit_id = cm.police_station_id, recent_cutoff
                    WHERE cm.crime_registered_date >= recent_cutoff.cutoff
                      AND ai.person_uid IN (SELECT person_uid FROM eligible)
                )
                SELECT d.district_id, d.district_name, count(DISTINCT rr.person_uid) AS repeat_offender_count
                FROM recent_repeat rr JOIN district d ON d.district_id = rr.district_id
                GROUP BY d.district_id, d.district_name
                HAVING count(DISTINCT rr.person_uid) >= 5
                ORDER BY repeat_offender_count DESC
                """, recentDays, minCaseCount);
    }

    /** Monthly total case counts (one row per month) — the input series for forecasting. */
    public List<Map<String, Object>> monthlyTotals(String from, String to, Integer districtId, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                SELECT to_char(date_trunc('month', cm.crime_registered_date), 'YYYY-MM') AS period,
                       count(*) AS count
                FROM case_master cm
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR u.district_id = ?::int)
                  AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                GROUP BY 1
                ORDER BY 1
                """, from, to, districtId, districtId, crimeHeadId, crimeHeadId);
    }

    /**
     * Monthly case counts PER DISTRICT in one grouped query — the input for predicted hotspots.
     * Deliberately not "call monthlyTotals once per district": {@code unit} has no index on
     * district_id, so looping 31 districts would be an N+1 with a sequential scan on the
     * case_master⋈unit join each time. One query, partitioned by district in Java, instead.
     */
    public List<Map<String, Object>> monthlyTotalsByDistrict(String from, String to, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                SELECT u.district_id, d.district_name, d.district_name_kn,
                       to_char(date_trunc('month', cm.crime_registered_date), 'YYYY-MM') AS period,
                       count(*) AS count
                FROM case_master cm
                JOIN unit u ON u.unit_id = cm.police_station_id
                JOIN district d ON d.district_id = u.district_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                GROUP BY 1, 2, 3, 4
                ORDER BY 1, 4
                """, from, to, crimeHeadId, crimeHeadId);
    }

    /** Month-of-year seasonality: average monthly volume per calendar month (1-12) across the range. */
    public List<Map<String, Object>> seasonality(String from, String to, Integer districtId, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                WITH per_month AS (
                    SELECT extract(month from cm.crime_registered_date)::int AS month_num,
                           date_trunc('month', cm.crime_registered_date) AS m,
                           count(*) AS c
                    FROM case_master cm
                    LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                    WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                      AND (?::int IS NULL OR u.district_id = ?::int)
                      AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                    GROUP BY 1, 2
                )
                SELECT month_num, round(avg(c), 1) AS avg_count, sum(c) AS total
                FROM per_month GROUP BY month_num ORDER BY month_num
                """, from, to, districtId, districtId, crimeHeadId, crimeHeadId);
    }

    /**
     * Every co-offending pair sharing &gt;= minShared cases — the edge list for group detection.
     * Identity resolved via {@code accused_identity} (see entity-resolution.sql), not
     * {@code accused.person_uid} directly. Capped at a fixed 4000 rows (ordered by shared_cases
     * DESC, so the truncation drops the weakest edges first). Callers computing cohesion/edge
     * counts from this list should treat results as a lower bound when the full 4000 rows come back.
     */
    public List<Map<String, Object>> allCoOffenderPairs(int minShared) {
        return jdbcTemplate.queryForList("""
                SELECT ai1.person_uid AS source_uid, max(a1.accused_name) AS source_name,
                       ai2.person_uid AS target_uid, max(a2.accused_name) AS target_name,
                       count(DISTINCT ai1.case_master_id) AS shared_cases
                FROM accused_identity ai1
                JOIN accused a1 ON a1.accused_master_id = ai1.accused_master_id
                JOIN accused_identity ai2 ON ai2.case_master_id = ai1.case_master_id
                                         AND ai2.person_uid > ai1.person_uid
                JOIN accused a2 ON a2.accused_master_id = ai2.accused_master_id
                GROUP BY ai1.person_uid, ai2.person_uid
                HAVING count(DISTINCT ai1.case_master_id) >= ?
                ORDER BY shared_cases DESC
                LIMIT 4000
                """, Math.max(1, minShared));
    }

    /**
     * Demographic cross-tab of accused by a chosen dimension. Only age and gender are supported —
     * they are the demographic columns the accused table actually carries. Caste/religion are
     * deliberately excluded from analytics output (fairness); use for aggregate sociological context,
     * not individual profiling.
     *
     * @param crimeType optional free-text match against crime_sub_head.crime_head_name (e.g. "theft")
     *                  or, failing that, crime_head.crime_group_name (e.g. "crimes against property") —
     *                  a name rather than an ID so the model can pass it straight through without a
     *                  separate lookup call first.
     */
    public List<Map<String, Object>> demographics(String dimension, String from, String to, Integer districtId,
                                                    String crimeType) {
        String select = "gender".equalsIgnoreCase(dimension)
                ? "COALESCE(gm.gender_name, 'Unknown')"
                : """
                    CASE WHEN a.age_year IS NULL THEN 'Unknown'
                         WHEN a.age_year < 18 THEN '<18'
                         WHEN a.age_year < 25 THEN '18-24'
                         WHEN a.age_year < 35 THEN '25-34'
                         WHEN a.age_year < 45 THEN '35-44'
                         WHEN a.age_year < 60 THEN '45-59'
                         ELSE '60+' END""";
        String sql = """
                SELECT %s AS bucket, count(*) AS count
                FROM accused a
                JOIN case_master cm ON cm.case_master_id = a.case_master_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                LEFT JOIN gender_master gm ON gm.gender_id = a.gender_id
                LEFT JOIN crime_sub_head csh ON csh.crime_sub_head_id = cm.crime_minor_head_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = csh.crime_head_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR u.district_id = ?::int)
                  AND (?::text IS NULL OR csh.crime_head_name ILIKE '%%' || ?::text || '%%'
                                        OR ch.crime_group_name ILIKE '%%' || ?::text || '%%')
                GROUP BY 1 ORDER BY count DESC
                """.formatted(select);
        return jdbcTemplate.queryForList(sql, from, to, districtId, districtId, crimeType, crimeType, crimeType);
    }

    /**
     * Complainant occupation breakdown by crime head (Area 4: socio-economic insight). Officially
     * only ComplainantDetails carries occupation/religion/caste — Accused does not — so Area 4 is
     * scoped to complainant occupation here; caste/religion stay excluded from all analytics
     * output as a fairness decision (see demographics()'s javadoc), not a data gap.
     */
    public List<Map<String, Object>> complainantOccupationByCrimeHead(String from, String to, Integer crimeHeadId) {
        return jdbcTemplate.queryForList("""
                SELECT ch.crime_group_name AS crime_head, om.occupation_name, count(*) AS count
                FROM complainant_details cd
                JOIN case_master cm ON cm.case_master_id = cd.case_master_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN occupation_master om ON om.occupation_id = cd.occupation_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR cm.crime_major_head_id = ?::int)
                GROUP BY ch.crime_group_name, om.occupation_name
                ORDER BY ch.crime_group_name, count DESC
                """, from, to, crimeHeadId, crimeHeadId);
    }

    /** Cases legally similar to a seed case (shared act+section overlap), most recent first — no text comparison. */
    public List<Map<String, Object>> similarCases(String crimeNo, int limit) {
        return jdbcTemplate.queryForList("""
                WITH seed AS (
                    SELECT cm.case_master_id, cm.crime_major_head_id, cm.crime_minor_head_id
                    FROM case_master cm WHERE cm.crime_no = ?
                ),
                seed_sections AS (
                    SELECT act_code, section_code FROM act_section_association asa
                    JOIN seed s ON s.case_master_id = asa.case_master_id
                )
                SELECT cm.crime_no, ch.crime_group_name AS crime_head, csh.crime_head_name AS crime_sub_head,
                       d.district_name, cm.crime_registered_date,
                       count(DISTINCT (asa.act_code, asa.section_code)) FILTER (
                           WHERE (asa.act_code, asa.section_code) IN (SELECT act_code, section_code FROM seed_sections)
                       ) AS shared_sections,
                       (cm.crime_minor_head_id = (SELECT crime_minor_head_id FROM seed)) AS same_sub_head,
                       csm.case_status_name, cs.cs_type,
                       (cs.cs_date::date - cm.crime_registered_date) AS disposal_days
                FROM case_master cm
                JOIN seed s ON cm.case_master_id <> s.case_master_id
                LEFT JOIN act_section_association asa ON asa.case_master_id = cm.case_master_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN crime_sub_head csh ON csh.crime_sub_head_id = cm.crime_minor_head_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                LEFT JOIN district d ON d.district_id = u.district_id
                LEFT JOIN case_status_master csm ON csm.case_status_id = cm.case_status_id
                LEFT JOIN chargesheet_details cs ON cs.case_master_id = cm.case_master_id
                WHERE cm.crime_major_head_id = (SELECT crime_major_head_id FROM seed)
                GROUP BY cm.crime_no, ch.crime_group_name, csh.crime_head_name, d.district_name,
                         cm.crime_registered_date, cm.crime_minor_head_id, csm.case_status_name,
                         cs.cs_type, cs.cs_date
                ORDER BY shared_sections DESC, same_sub_head DESC, cm.crime_registered_date DESC
                LIMIT ?
                """, crimeNo, Math.max(1, Math.min(limit, 50)));
    }

    /**
     * Disposal/conviction-rate comparison by crime head and district (Area 6: investigation
     * outcome comparison). {@code cs_type}: A=Chargesheet, B=False Case, C=Undetected (official
     * ChargesheetDetails semantics). Average disposal time is only meaningful over chargesheeted
     * cases (a case with no chargesheet has no disposal date yet).
     */
    public List<Map<String, Object>> outcomeComparison(String from, String to, Integer districtId) {
        return jdbcTemplate.queryForList("""
                SELECT ch.crime_group_name AS crime_head, d.district_name,
                       count(*) AS total_cases,
                       count(*) FILTER (WHERE cs.cs_type = 'A') AS chargesheeted,
                       count(*) FILTER (WHERE cs.cs_type = 'B') AS false_case,
                       count(*) FILTER (WHERE cs.cs_type = 'C') AS undetected,
                       round(100.0 * count(*) FILTER (WHERE cs.cs_type = 'A') / count(*), 1) AS chargesheet_rate_pct,
                       round(avg(cs.cs_date::date - cm.crime_registered_date)
                             FILTER (WHERE cs.cs_type = 'A'), 1) AS avg_disposal_days
                FROM case_master cm
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                LEFT JOIN district d ON d.district_id = u.district_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN chargesheet_details cs ON cs.case_master_id = cm.case_master_id
                WHERE cm.crime_registered_date BETWEEN ?::date AND ?::date
                  AND (?::int IS NULL OR u.district_id = ?::int)
                GROUP BY ch.crime_group_name, d.district_name
                HAVING count(*) >= 10
                ORDER BY chargesheet_rate_pct DESC
                """, from, to, districtId, districtId);
    }

    /**
     * Case-mix profile for one offender identity: their case mix by crime head, the sections they
     * most often attract, and the districts they operate in — a profile beyond a single risk
     * number. Not a modus-operandi signature: the FIR schema has no structured MO field.
     */
    public Map<String, Object> offenderProfile(String personUid) {
        var byHead = jdbcTemplate.queryForList("""
                SELECT ch.crime_group_name AS crime_head, count(*) AS count
                FROM accused_identity ai JOIN case_master cm ON cm.case_master_id = ai.case_master_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                WHERE ai.person_uid = ?
                GROUP BY 1 ORDER BY count DESC LIMIT 10
                """, personUid);
        var bySection = jdbcTemplate.queryForList("""
                SELECT s.section_description AS section, count(*) AS count
                FROM accused_identity ai JOIN act_section_association asa ON asa.case_master_id = ai.case_master_id
                LEFT JOIN section s ON s.act_code = asa.act_code AND s.section_code = asa.section_code
                WHERE ai.person_uid = ?
                GROUP BY 1 ORDER BY count DESC LIMIT 10
                """, personUid);
        var byDistrict = jdbcTemplate.queryForList("""
                SELECT d.district_name AS district, count(*) AS count
                FROM accused_identity ai JOIN case_master cm ON cm.case_master_id = ai.case_master_id
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                LEFT JOIN district d ON d.district_id = u.district_id
                WHERE ai.person_uid = ?
                GROUP BY 1 ORDER BY count DESC LIMIT 10
                """, personUid);
        return Map.of("crimeHeads", byHead, "sections", bySection, "districts", byDistrict);
    }

    // ----- Financial crime / transaction link analysis -----

    /** All transactions touching any account held by the given offender identity (money trail). */
    public List<Map<String, Object>> moneyTrail(String personUid) {
        return jdbcTemplate.queryForList("""
                SELECT ft.txn_id, ff.account_no AS from_account, ff.holder_name AS from_name,
                       ff.holder_person_uid AS from_uid, tt.account_no AS to_account,
                       tt.holder_name AS to_name, tt.holder_person_uid AS to_uid,
                       ft.amount, ft.txn_date, ft.txn_type, ft.is_suspicious
                FROM financial_transaction ft
                JOIN financial_account ff ON ff.account_id = ft.from_account_id
                JOIN financial_account tt ON tt.account_id = ft.to_account_id
                WHERE ff.holder_person_uid = ? OR tt.holder_person_uid = ?
                ORDER BY ft.txn_date
                LIMIT 500
                """, personUid, personUid);
    }

    /**
     * Multi-hop money trail (Area 7): walks outgoing transactions from every account the offender
     * identity holds, up to {@code maxDepth} hops, via a recursive CTE — {@code moneyTrail} above
     * is single-hop only (direct counterparties). Cycle-aware: the path guard blocks revisiting any
     * already-visited account EXCEPT the walk's own origin, so a row can close a cycle back to the
     * seed's starting account (a genuine round-trip/layering finding, flagged
     * {@code closes_cycle}) without ever infinite-looping through some OTHER repeated node — once a
     * row closes a cycle it is not extended further, though sibling branches at the same hop still
     * are. Depth is capped at 4 and results at 300 rows. Empirically measured against the busiest
     * account in the seeded dataset (300 accounts / ~3-4k transactions) — a dense graph, not a
     * theoretical worst case: depth 4 costs ~300k internal rows (&lt;0.5s), depth 5 costs ~3.4M rows
     * (~10x growth per hop). The cap stops one hop short of where a single request starts costing
     * seconds instead of milliseconds.
     */
    public List<Map<String, Object>> multiHopMoneyTrail(String personUid, int maxDepth) {
        int depth = Math.max(1, Math.min(maxDepth, 4));
        return jdbcTemplate.queryForList("""
                WITH RECURSIVE trail AS (
                    SELECT ft.txn_id, ft.from_account_id, ft.to_account_id, ft.amount, ft.txn_date,
                           ARRAY[ft.from_account_id, ft.to_account_id] AS path, 1 AS hop,
                           FALSE AS closes_cycle
                    FROM financial_transaction ft
                    JOIN financial_account fa ON fa.account_id = ft.from_account_id
                    WHERE fa.holder_person_uid = ?
                    UNION ALL
                    SELECT ft.txn_id, ft.from_account_id, ft.to_account_id, ft.amount, ft.txn_date,
                           trail.path || ft.to_account_id, trail.hop + 1,
                           (ft.to_account_id = trail.path[1])
                    FROM trail
                    JOIN financial_transaction ft ON ft.from_account_id = trail.to_account_id
                    WHERE trail.hop < ?
                      AND NOT trail.closes_cycle
                      AND (ft.to_account_id <> ALL(trail.path) OR ft.to_account_id = trail.path[1])
                )
                SELECT t.hop, fa1.account_no AS from_account, fa1.holder_name AS from_name,
                       fa2.account_no AS to_account, fa2.holder_name AS to_name,
                       t.amount, t.txn_date, t.closes_cycle
                FROM trail t
                JOIN financial_account fa1 ON fa1.account_id = t.from_account_id
                JOIN financial_account fa2 ON fa2.account_id = t.to_account_id
                ORDER BY t.hop, t.txn_date
                LIMIT 300
                """, personUid, depth);
    }

    /**
     * Flagged or high-value transactions with both counterparties, most valuable first. Flagged
     * means {@code financial_transaction_risk.is_suspicious_derived} — rules evaluated at query
     * time (structuring/velocity/round-number) — not the seed's {@code is_suspicious} coin flip.
     */
    public List<Map<String, Object>> suspiciousTransactions() {
        return jdbcTemplate.queryForList("""
                SELECT ft.txn_id, ff.holder_name AS from_name, ff.holder_person_uid AS from_uid,
                       tt.holder_name AS to_name, tt.holder_person_uid AS to_uid,
                       ft.amount, ft.txn_date, ft.txn_type,
                       ftr.is_suspicious_derived AS is_suspicious,
                       ftr.is_structuring, ftr.is_high_velocity, ftr.is_round_number
                FROM financial_transaction ft
                JOIN financial_account ff ON ff.account_id = ft.from_account_id
                JOIN financial_account tt ON tt.account_id = ft.to_account_id
                JOIN financial_transaction_risk ftr ON ftr.txn_id = ft.txn_id
                WHERE ftr.is_suspicious_derived OR ft.amount >= 400000
                ORDER BY ft.amount DESC
                LIMIT 100
                """);
    }

    /** Fan-in ("mule") accounts: receiving from many distinct sources — a money-laundering signal. */
    public List<Map<String, Object>> fanInAccounts() {
        return jdbcTemplate.queryForList("""
                SELECT tt.account_no, tt.holder_name, tt.holder_person_uid,
                       count(*) AS incoming_count, count(DISTINCT ft.from_account_id) AS sources,
                       round(sum(ft.amount), 2) AS total_in
                FROM financial_transaction ft
                JOIN financial_account tt ON tt.account_id = ft.to_account_id
                GROUP BY tt.account_no, tt.holder_name, tt.holder_person_uid
                HAVING count(DISTINCT ft.from_account_id) >= 3
                ORDER BY sources DESC, total_in DESC
                LIMIT 50
                """);
    }
}
