package com.ksp.agent.crime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.1: {@code accused_identity} derives cross-case offender identity from name/gender/age
 * instead of assuming a person key is supplied — this is a focused correctness test on that
 * clustering logic (hand-crafted cases, not the full 18k-row seed; see
 * {@code AnalyticsRepository}/manual verification notes in documents/SCHEMA_FIDELITY.md for the
 * measured 96%+ precision/recall against the generator's ground truth on the full dataset).
 *
 * <p>Runs against a real disposable Postgres (no mocking a SQL view) but skips
 * {@code @SpringBootTest} entirely — plain JDBC, no Spring context — so it's fast and only
 * exercises the two schema files this behavior actually depends on.
 */
@Testcontainers
class EntityResolutionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("entity_resolution_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("sql-models/fir-schema.sql")));
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("sql-models/entity-resolution.sql")));
        seedHandCraftedCases();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Three cases, four accused rows:
     * - Case 1001: "Ravi Kumar", male, age 30 (registered 2024) — repeat offender's first case.
     * - Case 1002: "Ravi Kumar", male, age 31 (registered 2025) — same person, one year older,
     *   one year later — should cluster together (within the 2-year birth-year-bucket tolerance).
     * - Case 1003: "Suresh Gowda", male, age 40 — an unrelated person, different name.
     * - Case 1003: "Ravi Kumar", female, age 30 — same NAME but different gender — must NOT cluster
     *   with the male "Ravi Kumar" rows (gender is part of the blocking key).
     */
    private static void seedHandCraftedCases() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    INSERT INTO case_master (case_master_id, crime_no, case_no, crime_registered_date, police_station_id, case_category_id, gravity_offence_id, crime_major_head_id, crime_minor_head_id, case_status_id, incident_from_date, incident_to_date)
                    VALUES
                    (1001, 'C1001', '1001', '2024-01-15', NULL, NULL, NULL, NULL, NULL, NULL, '2024-01-15 10:00', '2024-01-15 11:00'),
                    (1002, 'C1002', '1002', '2025-01-15', NULL, NULL, NULL, NULL, NULL, NULL, '2025-01-15 10:00', '2025-01-15 11:00'),
                    (1003, 'C1003', '1003', '2024-06-01', NULL, NULL, NULL, NULL, NULL, NULL, '2024-06-01 10:00', '2024-06-01 11:00')
                    """);
            st.execute("""
                    INSERT INTO accused (accused_master_id, case_master_id, accused_name, age_year, gender_id, person_id, person_uid)
                    VALUES
                    (1, 1001, 'Ravi Kumar', 30, 1, 'A1', 'GROUND-TRUTH-RAVI'),
                    (2, 1002, 'Ravi Kumar', 31, 1, 'A1', 'GROUND-TRUTH-RAVI'),
                    (3, 1003, 'Suresh Gowda', 40, 1, 'A1', 'GROUND-TRUTH-SURESH'),
                    (4, 1003, 'Ravi Kumar', 30, 2, 'A2', 'GROUND-TRUTH-RAVI-F')
                    """);
        }
    }

    private String personUidFor(int accusedMasterId) throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT person_uid FROM accused_identity WHERE accused_master_id = " + accusedMasterId)) {
            assertThat(rs.next()).as("accused_identity row for accused_master_id=%d", accusedMasterId).isTrue();
            return rs.getString("person_uid");
        }
    }

    @Test
    void sameNameGenderAndConsistentAgeAcrossCasesClusterTogether() throws Exception {
        String ravi1 = personUidFor(1);
        String ravi2 = personUidFor(2);
        assertThat(ravi1).isEqualTo(ravi2);
    }

    @Test
    void differentNamesNeverCluster() throws Exception {
        String ravi = personUidFor(1);
        String suresh = personUidFor(3);
        assertThat(ravi).isNotEqualTo(suresh);
    }

    @Test
    void sameNameButDifferentGenderDoesNotCluster() throws Exception {
        String raviMale = personUidFor(1);
        String raviFemale = personUidFor(4);
        assertThat(raviMale).isNotEqualTo(raviFemale);
    }

    @Test
    void singletonIdentityGetsFullConfidence() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT confidence, cluster_size FROM accused_identity WHERE accused_master_id = 3")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cluster_size")).isEqualTo(1);
            assertThat(rs.getDouble("confidence")).isEqualTo(1.0);
        }
    }

    @Test
    void multiCaseClusterGetsLowerThanFullConfidence() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT confidence, cluster_size FROM accused_identity WHERE accused_master_id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cluster_size")).isEqualTo(2);
            assertThat(rs.getDouble("confidence")).isLessThan(1.0);
        }
    }

    @Test
    void derivedIdentityIsIndependentOfTheGroundTruthColumn() throws Exception {
        // accused_identity must not simply echo accused.person_uid — it's a real independent
        // derivation. The ground-truth values seeded above ("GROUND-TRUTH-...") should never
        // appear as a derived person_uid (the view emits an md5 hash instead).
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT person_uid FROM accused_identity WHERE accused_master_id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("person_uid")).isNotEqualTo("GROUND-TRUTH-RAVI");
        }
    }
}
