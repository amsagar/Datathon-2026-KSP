package com.ksp.agent.applicationconfig.constants;

public final class ApiConstants {

    private ApiConstants() {
    }

    /** Protected API surface (APIM / auth typically apply here). */
    public static final String BASE_PATH = "/api";

    /** Unprotected health checks — see {@link com.ksp.agent.applicationconfig.controller.HealthController}. */
    public static final String HEALTH_PATH = "/health";
    public static final String CHAT_PATH = BASE_PATH + "/chat";
    public static final String SESSIONS_PATH = BASE_PATH + "/sessions";
    public static final String SHARES_PATH = BASE_PATH + "/shares";
    public static final String ASSISTANTS_PATH = BASE_PATH + "/assistants";
    public static final String TOOLS_PATH = BASE_PATH + "/tools";
    public static final String TOOL_GROUPS_PATH = BASE_PATH + "/tool-groups";
    public static final String TOOL_AUTH_PATH = BASE_PATH + "/tool-auth";
    public static final String SKILLS_PATH = BASE_PATH + "/skills";
    public static final String DOCUMENTS_PATH = BASE_PATH + "/documents";
    public static final String RESPONSE_STYLES_PATH = BASE_PATH + "/response-styles";
    public static final String MCP_SERVERS_PATH = BASE_PATH + "/mcp-servers";
    public static final String USAGE_PATH = BASE_PATH + "/usage";
    public static final String MEMORIES_PATH = BASE_PATH + "/memories";
    public static final String ANALYTICS_PATH = BASE_PATH + "/analytics";
    public static final String LLM_PROVIDERS_PATH = BASE_PATH + "/llm-providers";
    public static final String USERS_PATH = BASE_PATH + "/v1/users";
    public static final String AUDIT_PATH = BASE_PATH + "/v1/audit";
    public static final String CONFIG_AUDIT_PATH = BASE_PATH + "/v1/config-audit";
    public static final String ALERTS_PATH = BASE_PATH + "/alerts";
}
