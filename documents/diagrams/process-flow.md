# Process flow / use-case diagrams (PPT slide 5)

## 1. A chat turn, end to end

```mermaid
sequenceDiagram
    actor User
    participant UI as React SPA
    participant CC as ChatController
    participant SG as ScopeGuardService
    participant G1 as RoleGatedToolCallback<br/>(tool-level gate)
    participant G2 as SqlTableGateToolCallback<br/>(SQL-level gate)
    participant Tool as Crime tool<br/>(run_crime_sql, summarize_case, ...)
    participant DB as Crime Postgres
    participant LLM as Zoho Catalyst QuickML
    participant Audit as chat_tool_event

    User->>UI: types a question (en or kn)
    UI->>CC: GET /api/chat/stream?...&lang=kn
    CC->>CC: capture roles + userId on request thread<br/>(SecurityContextHolder is empty on tool threads)
    CC->>SG: classify message vs. assistant role
    SG-->>CC: ALLOW / BLOCK (localized redirect if kn)
    alt out of scope
        CC-->>UI: SSE "message" (redirect), "done"
    else in scope
        CC->>LLM: effectiveSystemPrompt + tool schemas
        LLM->>CC: tool call (e.g. run_crime_sql)
        CC->>G1: wrapToolCallback(...) — role check (closed-over, eager)
        alt tool-level denied
            G1-->>LLM: {"error": "...requires an investigative role..."}
        else tool-level allowed
            G1->>G2: delegate — blocks restricted tables by name in free-form SQL
            alt SQL-level denied
                G2-->>LLM: {"error": "...requires an investigative role..."}
            else SQL-level allowed
                G2->>Tool: delegate.call(input)
                Tool->>DB: guarded SELECT (single-stmt, no DML/DDL, timeout, row cap)
                DB-->>Tool: rows
                Tool-->>G2: JSON result
            end
        end
        G1->>Audit: persist tool_call + tool_result (turn-indexed)
        LLM->>CC: final answer (in requested language)
        CC-->>UI: SSE "message" chunks, "tool", "tool_result", "done"
    end
```

## 2. Real early-warning alerts (Phase 4.12)

```mermaid
flowchart LR
    Cron["Catalyst Cron<br/>(external scheduler, e.g. every 15-30 min)"] -->|"POST /internal/cron/alert-evaluation<br/>X-Cron-Secret"| Job[AlertEvaluationJob]
    Job --> Spike["evaluateCrimeSpikes()<br/>district×crime-head volume vs. baseline"]
    Job --> Surge["evaluateRepeatOffenderSurges()<br/>high-risk repeat offenders, last 90 days"]
    Job --> Gang["evaluateGangActivity()<br/>tight co-offender clusters, cohesion >= 0.5"]
    Spike --> Dedup{"alert already OPEN/ACKNOWLEDGED<br/>for this dedup_key?"}
    Surge --> Dedup
    Gang --> Dedup
    Dedup -->|yes| Skip["no-op — condition already tracked"]
    Dedup -->|no| Open["INSERT ... ON CONFLICT DO NOTHING<br/>status=OPEN"]
    Open --> List["GET /api/alerts<br/>(every role can view)"]
    List --> Act["POST /alerts/{id}/acknowledge|assign|resolve<br/>(investigative roles only)"]
```

## 3. Supervisor accountability review (Phase 4.4)

```mermaid
flowchart LR
    Sup[Supervisor/Admin] -->|"GET /api/sessions/all"| List[ChatSessionController]
    List --> Find["repository.findMostRecentAcrossUsers()<br/>no per-user filter — oversight, not ownership"]
    Sup -->|"GET /api/sessions/{id}/messages"| Msg[ChatSessionServiceImpl.messages]
    Msg --> RoleCheck{"caller has ADMIN/SUPERVISOR?"}
    RoleCheck -->|no| Owner["requireSession(id, callerUserId)<br/>404 if not the owner (unchanged behavior)"]
    RoleCheck -->|yes| CrossUser["repository.findOwner(id)<br/>existence check only, any owner"]
    CrossUser --> AuditLog["auditService.record(actor,<br/>'VIEW_OTHER_USER_SESSION', sessionId, ownerId)<br/>— only when actor != owner"]
    AuditLog --> Full["full transcript incl. every tool_call/<br/>tool_result — the actual SQL + rows returned"]
```

## 4. Config governance — every configuration change is versioned

Applies uniformly across the generic agent platform, not just the crime domain: editing any
Assistant, Skill, or Response Style produces the same auditable trail.

```mermaid
flowchart TB
    A1["Admin edits an<br/>Assistant / Skill / Response Style"] --> V{{"SkillUpdateProposalValidator<br/>(skills only) — rejects header<br/>changes or dropped rows"}}
    A1 -- "assistant / style" --> SNAP
    V -- "valid" --> SNAP[("config_revision<br/>auto-versioned snapshot")]
    SNAP --> DIFF["RevisionHistory UI<br/>line-level diff view"]
    DIFF --> REV["One-click<br/>Revert to this version"]
    REV -.-> SNAP
    SNAP --> GATE{{"AuditAccessSettingsService<br/>toggle: can non-admins read this feed?"}}
```

- `ResourceType` covers `assistant, skill, tool, tool_group, tool_auth, document, response_style,
  mcp_server, mcp_tool`, but only `assistant`, `skill`, and `response_style` are fully versioned
  (snapshot + revert) — the rest get feed events only.
- Revert replays the stored snapshot back through the same update path the PUT/PATCH endpoint uses
  (skills are special-cased via `SkillService.revertToVersion` since their content lives in blob
  storage, not a DB row).
- `AuditAccessSettingsService` is an admin-toggleable setting controlling whether non-admin roles get
  read-only access to the feed/revisions; revert itself stays admin-only always.
