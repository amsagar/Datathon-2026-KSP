package com.ksp.agent.chat.tooling;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.regex.Pattern;

/**
 * Blocks {@code run_crime_sql} calls that touch investigative-grade tables (financial
 * transactions/accounts, offender risk scoring) for callers without an investigative role.
 * These tables are already omitted from {@code get_crime_schema}'s doc, but nothing stops the
 * model from naming them directly in hand-written SQL, so gating the tool itself — not just the
 * schema doc — is required. {@code investigativeAccess} is decided once, eagerly, on the request
 * thread and closed over here; see {@link RoleGatedToolCallback} for why.
 */
public class SqlTableGateToolCallback implements ToolCallback {

    private static final Pattern RESTRICTED_TABLES = Pattern.compile(
            "\\b(financial_transaction|financial_account|offender_risk_score)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ToolCallback delegate;
    private final boolean investigativeAccess;

    public SqlTableGateToolCallback(ToolCallback delegate, boolean investigativeAccess) {
        this.delegate = delegate;
        this.investigativeAccess = investigativeAccess;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String denied = denyIfRestricted(toolInput);
        return denied != null ? denied : delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String denied = denyIfRestricted(toolInput);
        return denied != null ? denied : delegate.call(toolInput, toolContext);
    }

    private String denyIfRestricted(String toolInput) {
        if (!investigativeAccess && toolInput != null && RESTRICTED_TABLES.matcher(toolInput).find()) {
            return "{\"error\":\"Access to financial and offender-risk tables requires an "
                    + "investigative role (ADMIN, SUPERVISOR or INVESTIGATOR).\"}";
        }
        return null;
    }
}
