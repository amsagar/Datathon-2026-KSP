# Architecture diagram (PPT slide 7)

The backend is a **generic, multi-tenant AI agent platform** (`assistant/`, `skill/`, `style/`,
`tool/` incl. `tool/auth`, `mcp/`, `document/`, `audit/config/`, `chat/usage`) with the
**Crime-Intelligence Assistant** as one configured Assistant running on it (`crime/`,
`analytics/` are the only domain-specific packages). "Crime Intelligence" is a row in the
`Assistant` table, not a special case — the same platform could stand up a new Assistant for
another KSP division with no new backend engineering.

```mermaid
flowchart TB
    subgraph Client["React 18 + TypeScript SPA"]
        direction LR
        UIChat["Chat workspace<br/>SSE · EN/Kannada"]
        UIAnalytics["Analytics panel<br/>Dashboard · Map · Network · Financial"]
        UISettings["Settings<br/>Assistants · Skills · Styles ·<br/>HTTP Tools · Usage · Audit log"]
    end

    subgraph Backend["Spring Boot 4 (Java 25) + Spring AI"]
        Chat["ChatController<br/>scope guard · tool-calling"]
        RoleGate["RoleGatedToolCallback +<br/>SqlTableGateToolCallback<br/>— single funnel, every tool call"]

        subgraph Platform["GENERIC AGENT PLATFORM — reusable for any KSP division"]
            direction LR
            Assist["AssistantService<br/>multi-tenant: name, system<br/>prompt, tools per assistant"]
            Skill["SkillService<br/>sandboxed, user-uploaded,<br/>safe self-edit"]
            Style["ResponseStyleService<br/>swappable tone/persona"]
            HttpT["HttpTool + AuthProfile<br/>AES-256-GCM encrypted secrets"]
            CfgAudit["ConfigAuditService /<br/>ConfigRevertService<br/>versioned, diffable, revertible"]
            Usage["Usage Analytics<br/>token/cost by user·model·assistant"]
        end

        subgraph CrimeApp["CRIME-INTELLIGENCE ASSISTANT — one Assistant configured on the platform"]
            direction LR
            CrimeTools["CrimeDatabaseTools /<br/>CrimeAnalyticsTools<br/>run_crime_sql, forecast,<br/>money trail, offender groups"]
            AnalyticsAPI["AnalyticsController<br/>trends · hotspots · forecast ·<br/>network · outcomes"]
            AlertJob["AlertEvaluationJob<br/>cron-triggered, dedup-safe"]
        end

        Audit["AuditService<br/>persisted reasoning trail"]
    end

    subgraph AppDB["App Postgres"]
        PlatformTables[("assistant, skill, response_style,<br/>agent_tool, tool_auth_profile,<br/>config_revision, llm_usage_event")]
        CrimeAppTables[("chat_session, chat_tool_event,<br/>audit_log, alert, app_user")]
    end

    subgraph CrimeDB["Crime Postgres — read-mostly FIR data"]
        Core[("case_master, accused, victim,<br/>arrest_surrender, chargesheet_details, ...")]
        Derived[("accused_identity — entity resolution<br/>case_mo_features, financial_transaction_risk")]
    end

    LLM["Zoho Catalyst QuickML<br/>OpenAI-compatible LLM"]

    UIChat -->|"/api/chat/stream (SSE)"| Chat
    UIAnalytics -->|"/api/analytics/*"| AnalyticsAPI
    UISettings -->|"/api/assistants,/skills,/styles,<br/>/tools,/usage,/audit"| Platform

    Chat --> RoleGate
    RoleGate --> CrimeTools
    RoleGate --> HttpT
    Chat -->|effectiveSystemPrompt| LLM

    CrimeTools --> Core
    CrimeTools --> Derived
    AnalyticsAPI --> Core
    AnalyticsAPI --> Derived
    AlertJob --> Core
    AlertJob --> CrimeAppTables

    Platform --> PlatformTables
    Chat --> Audit --> CrimeAppTables
    Assist -.->|"configures"| Chat

    style Platform fill:#eaf2ff,stroke:#1f2a44,stroke-width:1.5px
    style CrimeApp fill:#fdecea,stroke:#8f1218,stroke-width:1.5px
```

## Notes for the slide

- **Platform vs. application layer**: `Assistant/Skill/ResponseStyle/HttpTool+AuthProfile/
  ConfigAudit/UsageAnalytics` are generic services with no crime-domain knowledge — they'd exist
  identically if this were deployed for any other department. `CrimeDatabaseTools/AnalyticsController/
  AlertEvaluationJob` are the one Assistant's domain-specific tool/API layer, funneled through the
  exact same `RoleGate`/`Audit` spine as every other tool call.
- **Role-gating happens twice, independently**: once at the tool level
  (`RoleGatedToolCallback` — hides `offender_profile`/`detect_offender_groups`/
  `list_account_transactions`/`trace_money_network`/`suspicious_transactions` entirely from
  non-investigative roles) and once at the SQL level (`SqlTableGateToolCallback` — blocks
  `financial_transaction`/`financial_account`/`offender_risk_score` by name inside arbitrary
  `run_crime_sql` text, since tool-hiding alone means nothing against free-form SQL).
- **`accused_identity` is a derived layer, not a source table** — it computes cross-case offender
  identity from name/gender/age instead of assuming a person key exists in the source data (the
  official schema has none). Everything downstream (`offender_risk_score`, network, financial
  linkage) joins through it.
- **Two independent Postgres databases**, deliberately: app/platform data (assistants, skills,
  styles, tool auth profiles, config revisions, usage events, sessions, audit, alerts) is never
  mixed with the read-mostly crime FIR data, which loads/updates via its own idempotent
  schema-init pipeline (`ServiceConfig.crimeDataSourceInitializer`) separate from the app
  datasource's.
- **Secrets never sit inline**: `ToolAuthProfile` credentials for external HTTP integrations are
  AES-256-GCM encrypted at rest (`EncryptionService`, keyed by `AGENT_ENCRYPTION_KEY`), so an
  Assistant can call another government system's API without a hardcoded secret anywhere in
  config or source.
