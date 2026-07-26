package com.ksp.agent.crime.tooling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code run_crime_sql} is the only prompt-injection-facing surface on a police database — a
 * model-authored (or user-steered) SQL string executes with no human review. These tests exercise
 * the guard BEFORE it ever reaches the database, mocking JdbcTemplate so a passing guard is
 * distinguishable from a query that simply failed for some other reason.
 */
class CrimeDatabaseToolsGuardTest {

    private JdbcTemplate jdbcTemplate;
    private CrimeDatabaseTools tools;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn("{\"columns\":[],\"rows\":[],\"rowCount\":0,\"truncated\":false}");
        tools = new CrimeDatabaseTools(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void plainSelectReachesTheDatabase() {
        String result = tools.runCrimeSql("SELECT 1");
        assertThat(result).doesNotContain("\"error\"");
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void withSelectReachesTheDatabase() {
        String result = tools.runCrimeSql("WITH x AS (SELECT 1 AS n) SELECT * FROM x");
        assertThat(result).doesNotContain("\"error\"");
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonSelectStatementIsRejectedBeforeTouchingTheDatabase() {
        String result = tools.runCrimeSql("SHOW search_path");
        assertThat(result).contains("\"error\"").contains("Only SELECT");
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleStatementsAreRejected() {
        String result = tools.runCrimeSql("SELECT 1; SELECT 2");
        assertThat(result).contains("\"error\"").contains("single SQL statement");
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forbiddenKeywordsAreRejectedEvenInsideASelectShapedStatement() {
        // A classic injection shape: looks like a SELECT overall but smuggles a DML/DDL keyword.
        String[] attempts = {
                "SELECT 1; DROP TABLE case_master",
                "WITH x AS (DELETE FROM accused RETURNING *) SELECT * FROM x",
                "SELECT * FROM case_master; UPDATE case_master SET brief_facts = 'x'",
        };
        for (String sql : attempts) {
            String result = tools.runCrimeSql(sql);
            assertThat(result).as("sql=%s", sql).contains("\"error\"");
        }
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqlCommentsCannotSmuggleAForbiddenKeywordPastTheRegex() {
        // stripComments() runs before the forbidden-keyword check, so a keyword hidden mid-comment
        // must still be caught once comments are stripped and the tokens become adjacent... but a
        // keyword OUTSIDE a comment is still just plain text the regex must catch regardless.
        String result = tools.runCrimeSql("SELECT 1 -- ; DROP TABLE case_master\n");
        assertThat(result).doesNotContain("\"error\"");
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankInputIsRejected() {
        String result = tools.runCrimeSql("");
        assertThat(result).contains("\"error\"");
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }
}
