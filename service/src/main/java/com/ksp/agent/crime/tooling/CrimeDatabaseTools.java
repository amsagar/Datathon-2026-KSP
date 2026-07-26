package com.ksp.agent.crime.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only text-to-SQL access to the Karnataka Police FIR schema. The model writes a SELECT
 * grounded on {@link #getCrimeSchema()}, {@link #runCrimeSql(String)} executes it with strict
 * guards (single SELECT/WITH statement, read-only connection, row cap, statement timeout) and
 * returns rows as JSON. Registered in {@code BuiltinToolCatalog} under the key {@code crime_db};
 * tool calls are audited in {@code chat_tool_event} like every other tool.
 */
@Component
@Slf4j
public class CrimeDatabaseTools {

    private static final int MAX_ROWS = 200;
    private static final int TIMEOUT_SECONDS = 10;

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|copy|call|do|vacuum|set|merge|comment|lock|listen|notify|refresh|reindex|cluster|checkpoint|prepare|execute|deallocate)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String SCHEMA_DOC = """
            Karnataka Police FIR database (PostgreSQL). All identifiers are snake_case.

            CORE TABLES
            case_master(case_master_id PK, crime_no, case_no, crime_registered_date DATE,
              police_person_id ->employee, police_station_id ->unit, case_category_id ->case_category,
              gravity_offence_id ->gravity_offence, crime_major_head_id ->crime_head,
              crime_minor_head_id ->crime_sub_head, case_status_id ->case_status_master,
              court_id ->court, incident_from_date TS, incident_to_date TS, info_received_ps_date TS,
              latitude, longitude, brief_facts TEXT)  -- one row per FIR/case
            complainant_details(complainant_id PK, case_master_id ->case_master, complainant_name,
              age_year, occupation_id ->occupation_master, religion_id ->religion_master,
              caste_id ->caste_master, gender_id ->gender_master)
            victim(victim_master_id PK, case_master_id, victim_name, age_year, gender_id, victim_police)
            accused(accused_master_id PK, case_master_id, accused_name, age_year, gender_id,
              person_id, person_uid)
              -- person_id: official within-case ordinal (A1, A2, A3...), NOT a cross-case identity.
              -- person_uid: generator ground truth for evaluation only — do NOT use it to link
              --   repeat offenders; join accused_identity(accused_master_id, person_uid, confidence)
              --   instead, which derives identity from name+gender+age (see SCHEMA_FIDELITY.md).
            arrest_surrender(arrest_surrender_id PK, case_master_id, arrest_surrender_type_id
              (1=arrest,2=surrender), arrest_surrender_date DATE, arrest_surrender_state_id,
              arrest_surrender_district_id, police_station_id, io_id ->employee, court_id,
              accused_master_id ->accused, is_accused, is_complainant_accused)
            act_section_association(case_master_id, act_code, section_code, act_order_id, section_order_id)
            chargesheet_details(cs_id PK, case_master_id, cs_date TS,
              cs_type 'A'=Chargesheet 'B'=False Case 'C'=Undetected, police_person_id)

            CLASSIFICATION / MASTERS
            crime_head(crime_head_id PK, crime_group_name)          -- e.g. Crimes Against Body, Cyber Crimes
            crime_sub_head(crime_sub_head_id PK, crime_head_id, crime_head_name, seq_id) -- e.g. Murder, Theft
            act(act_code PK, act_description, short_name)           -- IPC, NDPS, POCSO, IT, MV, ...
            section(act_code, section_code PK pair, section_description) -- IPC 302 Murder, 379 Theft, ...
            case_status_master(case_status_id PK, case_status_name) -- Under Investigation, Charge Sheeted,
              Pending Trial, Convicted, Acquitted, Closed - False Case, Closed - Undetected
            case_category(case_category_id PK, lookup_value)        -- FIR, UDR, Zero FIR, PAR
            gravity_offence(gravity_offence_id PK, lookup_value)    -- Heinous / Non-Heinous
            gender_master(gender_id PK, gender_name); caste_master(caste_master_id PK, caste_master_name)
            religion_master(religion_id PK, religion_name); occupation_master(occupation_id PK, occupation_name)

            GEOGRAPHY / ORGANISATION
            state(state_id PK, state_name); district(district_id PK, district_name, district_name_kn, state_id)
              -- district_name_kn: Kannada name (e.g. 'ಬೆಂಗಳೂರು ನಗರ') for matching Kannada district
              -- mentions in chat — not an official column, added since there are only 31 districts.
            unit(unit_id PK, unit_name, type_id ->unit_type, parent_unit, state_id, district_id)
              -- police stations; join case_master.police_station_id = unit.unit_id,
              --   then unit.district_id = district.district_id for district-level analysis
            unit_type(unit_type_id PK, unit_type_name, city_dist_state, hierarchy)
            court(court_id PK, court_name, district_id, state_id)
            employee(employee_id PK, district_id, unit_id, rank_id ->rank, designation_id ->designation,
              kgid, first_name, employee_dob, gender_id, appointment_date)
            rank(rank_id PK, rank_name, hierarchy); designation(designation_id PK, designation_name)

            ANALYTICS VIEWS
            accused_identity(accused_master_id, case_master_id, person_uid, confidence, method)
              -- Derived cross-case offender identity (name+gender+age-bucket clustering); use this
              --   to link repeat offenders, NOT accused.person_uid (see accused's notes above).
            offender_risk_score(person_uid, accused_name, case_count, heinous_count, last_case_date,
              chargesheeted_count, risk_score, identity_confidence) -- pre-computed repeat-offender risk ranking
            case_mo_features(case_master_id, motive, entry_method, incident_hour, time_of_day_bucket)
              -- MO signal derived from brief_facts text + incident_from_date; NOT an official column.
            financial_transaction_risk(txn_id, is_structuring, is_high_velocity, is_round_number,
              is_suspicious_derived) -- real rule-evaluated suspicion signal; prefer this join over
              the financial_transaction.is_suspicious stored column, which is seed ground truth only.

            FINANCIAL TABLES (not official — a proposed integration schema; requires an
            investigative role, ADMIN/SUPERVISOR/INVESTIGATOR, to query)
            financial_account(account_id PK, account_no, bank_name, account_type, holder_name,
              holder_person_uid ->accused_identity.person_uid, case_master_id ->case_master, is_flagged)
            financial_transaction(txn_id PK, from_account_id, to_account_id, amount, txn_date,
              txn_type, is_suspicious, case_master_id ->case_master)

            TIPS
            - Month buckets: date_trunc('month', crime_registered_date).
            - Co-accused network: self-join accused_identity on case_master_id with different person_uid.
            - District of a case: case_master -> unit (police_station_id) -> district.
            - Text search on facts: brief_facts ILIKE '%...%'.
            - District named in Kannada: match against BOTH district_name and district_name_kn
              (e.g. WHERE district_name ILIKE '%bengaluru%' OR district_name_kn = 'ಬೆಂಗಳೂರು ನಗರ').
            - Investigation outcome: chargesheet_details.cs_type joined to case_master
              (A=Chargesheeted, B=False Case, C=Undetected); disposal time = cs_date - crime_registered_date.
            - Socio-economic angle: complainant_details.occupation_id/religion_id/caste_id — accused
              carries no occupation/religion/caste (official schema doesn't have it there).
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrimeDatabaseTools(@Qualifier("crimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(name = "get_crime_schema", description = """
            Returns the FIR crime database schema: tables, columns, joins and query tips. \
            Call this before writing SQL if you are unsure of table or column names.""")
    public String getCrimeSchema() {
        return SCHEMA_DOC;
    }

    @Tool(name = "run_crime_sql", description = """
            Runs a single read-only SQL SELECT (or WITH ... SELECT) against the Karnataka Police FIR \
            database and returns JSON {columns, rows, rowCount, truncated}. Use for crime statistics, \
            case lookups, FIR details, trends, hotspots, offender histories and network queries. \
            Max 200 rows, 10s timeout; aggregate or LIMIT accordingly. No DML/DDL.""")
    public String runCrimeSql(@ToolParam(description = "A single PostgreSQL SELECT statement") String sql) {
        String cleaned = stripComments(sql == null ? "" : sql).trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        String lower = cleaned.toLowerCase();
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            return error("Only SELECT (or WITH ... SELECT) statements are allowed.");
        }
        if (cleaned.contains(";")) {
            return error("Only a single SQL statement is allowed.");
        }
        if (FORBIDDEN.matcher(cleaned).find()) {
            return error("Statement contains a forbidden keyword. Only read-only SELECT queries are allowed.");
        }
        final String query = cleaned;
        try {
            return jdbcTemplate.execute((ConnectionCallback<String>) con -> {
                boolean originalReadOnly = con.isReadOnly();
                try (Statement st = con.createStatement()) {
                    con.setReadOnly(true);
                    st.setMaxRows(MAX_ROWS + 1);
                    st.setQueryTimeout(TIMEOUT_SECONDS);
                    try (ResultSet rs = st.executeQuery(query)) {
                        return serialize(rs);
                    }
                } finally {
                    con.setReadOnly(originalReadOnly);
                }
            });
        } catch (Exception e) {
            log.warn("run_crime_sql failed: {}", e.getMessage());
            return error("Query failed: " + rootMessage(e));
        }
    }

    @Tool(name = "summarize_case", description = """
            Fetches everything about one FIR/case by its crime number: case details, complainant, \
            victims, accused (with repeat-offender ids), acts/sections, arrests, chargesheet and a \
            merged chronological investigation timeline. Returns JSON. Use before writing a case \
            summary or investigation timeline.""")
    public String summarizeCase(@ToolParam(description = "The crime_no of the case, e.g. 10001100520240001") String crimeNo) {
        String caseSql = """
                SELECT cm.case_master_id, cm.crime_no, cm.case_no, cm.crime_registered_date,
                       cm.incident_from_date, cm.incident_to_date, cm.info_received_ps_date,
                       cm.latitude, cm.longitude,
                       cm.brief_facts, u.unit_name AS police_station, d.district_name,
                       ch.crime_group_name AS crime_head, csh.crime_head_name AS crime_sub_head,
                       cs.case_status_name, g.lookup_value AS gravity, cc.lookup_value AS category,
                       e.first_name AS registered_by, co.court_name
                FROM case_master cm
                LEFT JOIN unit u ON u.unit_id = cm.police_station_id
                LEFT JOIN district d ON d.district_id = u.district_id
                LEFT JOIN crime_head ch ON ch.crime_head_id = cm.crime_major_head_id
                LEFT JOIN crime_sub_head csh ON csh.crime_sub_head_id = cm.crime_minor_head_id
                LEFT JOIN case_status_master cs ON cs.case_status_id = cm.case_status_id
                LEFT JOIN gravity_offence g ON g.gravity_offence_id = cm.gravity_offence_id
                LEFT JOIN case_category cc ON cc.case_category_id = cm.case_category_id
                LEFT JOIN employee e ON e.employee_id = cm.police_person_id
                LEFT JOIN court co ON co.court_id = cm.court_id
                WHERE cm.crime_no = ?
                """;
        try {
            var cases = jdbcTemplate.queryForList(caseSql, crimeNo);
            if (cases.isEmpty()) {
                return error("No case found with crime_no " + crimeNo);
            }
            var caseRow = cases.get(0);
            Object caseId = caseRow.get("case_master_id");
            ObjectNode root = objectMapper.valueToTree(caseRow);
            root.set("complainants", objectMapper.valueToTree(jdbcTemplate.queryForList("""
                    SELECT c.complainant_name, c.age_year, o.occupation_name
                    FROM complainant_details c
                    LEFT JOIN occupation_master o ON o.occupation_id = c.occupation_id
                    WHERE c.case_master_id = ?""", caseId)));
            root.set("victims", objectMapper.valueToTree(jdbcTemplate.queryForList("""
                    SELECT v.victim_name, v.age_year, gm.gender_name
                    FROM victim v LEFT JOIN gender_master gm ON gm.gender_id = v.gender_id
                    WHERE v.case_master_id = ?""", caseId)));
            root.set("accused", objectMapper.valueToTree(jdbcTemplate.queryForList("""
                    SELECT a.accused_name, a.age_year, a.person_id, ai.person_uid, ai.confidence AS identity_confidence,
                           (SELECT count(*) FROM accused_identity ai2
                            WHERE ai2.person_uid = ai.person_uid) AS total_cases
                    FROM accused a
                    LEFT JOIN accused_identity ai ON ai.accused_master_id = a.accused_master_id
                    WHERE a.case_master_id = ?""", caseId)));
            root.set("sections", objectMapper.valueToTree(jdbcTemplate.queryForList("""
                    SELECT asa.act_code, asa.section_code, s.section_description
                    FROM act_section_association asa
                    LEFT JOIN section s ON s.act_code = asa.act_code AND s.section_code = asa.section_code
                    WHERE asa.case_master_id = ? ORDER BY asa.act_order_id""", caseId)));
            var arrests = jdbcTemplate.queryForList("""
                    SELECT ar.arrest_surrender_date, ar.arrest_surrender_type_id, a.accused_name
                    FROM arrest_surrender ar
                    LEFT JOIN accused a ON a.accused_master_id = ar.accused_master_id
                    WHERE ar.case_master_id = ? ORDER BY ar.arrest_surrender_date""", caseId);
            root.set("arrests", objectMapper.valueToTree(arrests));
            var chargesheets = jdbcTemplate.queryForList("""
                    SELECT cs_date, cs_type FROM chargesheet_details WHERE case_master_id = ?""", caseId);
            root.set("chargesheet", objectMapper.valueToTree(chargesheets));
            root.set("timeline", objectMapper.valueToTree(buildTimeline(caseRow, arrests, chargesheets)));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("summarize_case failed for {}: {}", crimeNo, e.getMessage());
            return error("Failed to summarize case: " + rootMessage(e));
        }
    }

    /**
     * Merges every dated event already fetched for this case (registration, incident window,
     * info-received, each arrest/surrender, each chargesheet) into one chronologically sorted
     * timeline — no new queries, reusing rows {@link #summarizeCase} already selected. Dates arrive
     * as a mix of {@code java.sql.Date}/{@code Timestamp} depending on the source column's SQL
     * type, so each is normalised to an ISO-8601 string (which sorts correctly lexically) before
     * merging, rather than compared as raw {@link Object}s.
     */
    private static List<Map<String, Object>> buildTimeline(Map<String, Object> caseRow,
            List<Map<String, Object>> arrests, List<Map<String, Object>> chargesheets) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        addTimelineEvent(timeline, "FIR Registered", caseRow.get("crime_registered_date"));
        addTimelineEvent(timeline, "Incident Start", caseRow.get("incident_from_date"));
        addTimelineEvent(timeline, "Incident End", caseRow.get("incident_to_date"));
        addTimelineEvent(timeline, "Information Received by Police Station", caseRow.get("info_received_ps_date"));
        for (Map<String, Object> arrest : arrests) {
            boolean isArrest = Integer.valueOf(1).equals(arrest.get("arrest_surrender_type_id"));
            String label = (isArrest ? "Arrest" : "Surrender") + ": " + arrest.get("accused_name");
            addTimelineEvent(timeline, label, arrest.get("arrest_surrender_date"));
        }
        for (Map<String, Object> cs : chargesheets) {
            String label = switch (String.valueOf(cs.get("cs_type"))) {
                case "A" -> "Chargesheet Filed";
                case "B" -> "Closed — False Case";
                case "C" -> "Closed — Undetected";
                default -> "Final Report Filed";
            };
            addTimelineEvent(timeline, label, cs.get("cs_date"));
        }
        timeline.sort(Comparator.comparing(e -> (String) e.get("at")));
        return timeline;
    }

    private static void addTimelineEvent(List<Map<String, Object>> timeline, String label, Object rawDate) {
        String iso = toIsoDateTime(rawDate);
        if (iso == null) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", label);
        event.put("at", iso);
        timeline.add(event);
    }

    private static String toIsoDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay().toString();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toString();
        }
        if (value instanceof java.time.LocalDate ld) {
            return ld.atStartOfDay().toString();
        }
        return value.toString();
    }

    private String serialize(ResultSet rs) throws java.sql.SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode columns = root.putArray("columns");
        for (int i = 1; i <= cols; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        ArrayNode rows = root.putArray("rows");
        int count = 0;
        boolean truncated = false;
        while (rs.next()) {
            if (count >= MAX_ROWS) {
                truncated = true;
                break;
            }
            ArrayNode row = rows.addArray();
            for (int i = 1; i <= cols; i++) {
                Object v = rs.getObject(i);
                if (v == null) {
                    row.addNull();
                } else if (v instanceof Number n) {
                    row.add(objectMapper.valueToTree(n).decimalValue());
                } else if (v instanceof Boolean b) {
                    row.add(b);
                } else {
                    row.add(String.valueOf(v));
                }
            }
            count++;
        }
        root.put("rowCount", count);
        root.put("truncated", truncated);
        return root.toString();
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("--[^\\n]*", " ").replaceAll("/\\*.*?\\*/", " ");
    }

    private String error(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", message);
        return node.toString();
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
