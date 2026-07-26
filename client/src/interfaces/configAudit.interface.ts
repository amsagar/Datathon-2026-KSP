// Config audit + revision history DTOs. Mirrors the backend
// com.ksp.agent.audit.config.* package (ConfigAuditController @ /api/v1/config-audit).
// Kept separate from user.interface.ts's legacy AuditEntryDto/AuditPage, which cover the
// older, unrelated user_audit_log feed served at /api/v1/audit.
//
// IMPORTANT unit note (verified against the actual backend code, not assumed): every
// `createdAt` below is epoch **seconds**, not millis. ConfigAuditService.recordEvent /
// recordRevision / seedRevisionIfAbsent (service/src/main/java/com/ksp/agent/audit/config/
// service/ConfigAuditService.java) all write `Instant.now().getEpochSecond()`, and
// CONFIG_AUDIT_EVENT.FIND_FEED in sql.properties compares `created_at` directly against the
// `from`/`to` bigint params with no scaling — so those filters are epoch seconds too. This
// differs from the legacy `AuditEntryDto.createdAt`, which is epoch millis
// (`System.currentTimeMillis()` in AuditService.record). Multiply this feed's createdAt by
// 1000 before constructing a `Date`, and do the inverse when merge-sorting against the
// legacy feed.

/** All 9 config resource types the backend emits feed events for. */
export type ResourceType =
  | 'assistant'
  | 'skill'
  | 'tool'
  | 'tool_group'
  | 'tool_auth'
  | 'document'
  | 'response_style'
  | 'mcp_server'
  | 'mcp_tool';

/** The 3 resource types that also get full version snapshots + revert. */
export type VersionedResourceType = 'assistant' | 'skill' | 'response_style';

export const VERSIONED_RESOURCE_TYPES: VersionedResourceType[] = [
  'assistant',
  'skill',
  'response_style',
];

export const RESOURCE_TYPES: ResourceType[] = [
  'assistant',
  'skill',
  'tool',
  'tool_group',
  'tool_auth',
  'document',
  'response_style',
  'mcp_server',
  'mcp_tool',
];

export type AuditAction =
  | 'create'
  | 'update'
  | 'delete'
  | 'enable'
  | 'disable'
  | 'set_default'
  | 'discover'
  | 'file_edit'
  | 'tool_enable'
  | 'tool_disable'
  | 'revert';

export interface RevisionSummaryDto {
  id: number;
  version: number;
  action: string;
  actor: string;
  summary: string;
  /** True only for skill revisions with an archived file bundle (contentRef) to diff. */
  hasContent: boolean;
  /** Epoch seconds — see file header. */
  createdAt: number;
}

export interface RevisionDto {
  id: number;
  resourceType: string;
  resourceId: string;
  assistantId: string;
  version: number;
  action: string;
  actor: string;
  /** The resource's own GET-response DTO, serialized as JSON. Shape varies per resourceType. */
  snapshot: unknown;
  contentRef: string | null;
  summary: string;
  /** Epoch seconds — see file header. */
  createdAt: number;
}

export interface RevisionFileDto {
  path: string;
  /** Null when binary is true. */
  content: string | null;
  binary: boolean;
}

export interface ConfigAuditEventDto {
  id: number;
  resourceType: string;
  resourceId: string;
  assistantId: string | null;
  resourceName: string | null;
  action: string;
  actor: string;
  summary: string | null;
  /** Epoch seconds — see file header. */
  createdAt: number;
}

export interface AuditAccessSettingsDto {
  nonAdminReadEnabled: boolean;
}

export interface ConfigAuditFeedPage {
  items: ConfigAuditEventDto[];
  total: number;
}

/** Filters for the config-audit feed. `from`/`to`, if ever wired up, are epoch seconds. */
export interface ConfigAuditFeedParams {
  resourceType?: string;
  actor?: string;
  resourceId?: string;
  from?: number;
  to?: number;
  page?: number;
  size?: number;
}
