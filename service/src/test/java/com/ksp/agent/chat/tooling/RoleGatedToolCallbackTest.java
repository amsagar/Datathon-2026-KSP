package com.ksp.agent.chat.tooling;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2.4's RBAC fix for investigative crime tools (offender_profile, detect_offender_groups,
 * list_account_transactions, trace_money_network, suspicious_transactions): the delegate must
 * never execute when the caller's eagerly-captured role set doesn't qualify.
 */
class RoleGatedToolCallbackTest {

    @Test
    void delegatesWhenAllowed() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("offender_profile");
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.call(anyString())).thenReturn("{\"ok\":true}");

        RoleGatedToolCallback gated = new RoleGatedToolCallback(delegate, true);
        String result = gated.call("{}");

        assertThat(result).isEqualTo("{\"ok\":true}");
        verify(delegate).call("{}");
    }

    @Test
    void deniesWithoutTouchingDelegateWhenNotAllowed() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("trace_money_network");
        when(delegate.getToolDefinition()).thenReturn(def);

        RoleGatedToolCallback gated = new RoleGatedToolCallback(delegate, false);
        String result = gated.call("{}");

        assertThat(result).contains("\"error\"").contains("investigative role");
        verify(delegate, never()).call(anyString());
        verify(delegate, never()).call(anyString(), any());
    }
}
