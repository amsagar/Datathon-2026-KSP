package com.ksp.agent.chat.tooling;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * run_crime_sql is arbitrary text-to-SQL — restricting the purpose-built financial tools is
 * meaningless unless this table-name check ALSO blocks a non-investigative caller from just
 * naming financial_transaction/financial_account/offender_risk_score directly in hand-written SQL.
 */
class SqlTableGateToolCallbackTest {

    @Test
    void blocksRestrictedTableForNonInvestigativeCaller() {
        ToolCallback delegate = mock(ToolCallback.class);
        SqlTableGateToolCallback gated = new SqlTableGateToolCallback(delegate, false);

        String result = gated.call("{\"sql\":\"SELECT * FROM financial_transaction\"}");

        assertThat(result).contains("\"error\"").contains("investigative role");
        verify(delegate, never()).call(anyString());
    }

    @Test
    void blocksCaseInsensitiveAndAnyOfTheThreeRestrictedTables() {
        ToolCallback delegate = mock(ToolCallback.class);
        SqlTableGateToolCallback gated = new SqlTableGateToolCallback(delegate, false);

        assertThat(gated.call("{\"sql\":\"select * FROM FINANCIAL_ACCOUNT\"}")).contains("\"error\"");
        assertThat(gated.call("{\"sql\":\"select * from offender_risk_score\"}")).contains("\"error\"");
        verify(delegate, never()).call(anyString());
    }

    @Test
    void allowsRestrictedTableForInvestigativeCaller() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call(anyString())).thenReturn("{\"rows\":[]}");
        SqlTableGateToolCallback gated = new SqlTableGateToolCallback(delegate, true);

        String result = gated.call("{\"sql\":\"SELECT * FROM financial_transaction\"}");

        assertThat(result).isEqualTo("{\"rows\":[]}");
        verify(delegate).call("{\"sql\":\"SELECT * FROM financial_transaction\"}");
    }

    @Test
    void allowsAnyRoleWhenNoRestrictedTableIsNamed() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call(anyString())).thenReturn("{\"rows\":[]}");
        SqlTableGateToolCallback gated = new SqlTableGateToolCallback(delegate, false);

        String result = gated.call("{\"sql\":\"SELECT * FROM case_master\"}");

        assertThat(result).isEqualTo("{\"rows\":[]}");
        verify(delegate).call(anyString());
    }
}
