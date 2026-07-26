import makeApiRequest, { qs, httpClient } from './makeApiRequest';
import { API_ENDPOINTS } from '@constants/apiEndpoints';
import { directApiUrl } from '@config/runtimeConfig';
import type {
  ChatSessionDto,
  ChatMessageDto,
  UpdateSessionRequest,
  SubmitClarificationRequest,
  SubmitSkillUpdateDecisionRequest,
  ShareLinkDto,
  SharedChatDto,
} from '@interfaces/chat.interface';
import type {
  AssistantDto,
  BuiltinToolDto,
  CreateAssistantRequest,
  UpdateAssistantRequest,
} from '@interfaces/assistant.interface';
import type {
  AgentToolDto,
  CreateToolRequest,
  UpdateToolRequest,
  TestToolRequest,
  ImportToolsRequest,
  ToolImportKind,
} from '@interfaces/tool.interface';
import type {
  ToolAuthProfileDto,
  CreateAuthProfileRequest,
  UpdateAuthProfileRequest,
} from '@interfaces/auth.interface';
import type {
  PlatformSkillDto,
  SkillDto,
  SkillFileContent,
  SkillFileNode,
  UpdateSkillRequest,
} from '@interfaces/skill.interface';
import type {
  DocumentDto,
  UpdateDocumentRequest,
} from '@interfaces/document.interface';
import type {
  ResponseStyleDto,
  CreateStyleRequest,
  UpdateStyleRequest,
} from '@interfaces/style.interface';
import type {
  ToolGroupDto,
  CreateToolGroupRequest,
  UpdateToolGroupRequest,
  ImportToolsResult,
} from '@interfaces/toolGroup.interface';
import type {
  McpServerDto,
  McpServerToolDto,
  CreateMcpServerRequest,
  UpdateMcpServerRequest,
  UpdateMcpToolRequest,
} from '@interfaces/mcp.interface';
import type { UserProfileResponse } from '@apiCalls/auth';
import type {
  UserDto,
  CreateUserRequest,
  CreateUserResponse,
  UpdateUserRequest,
  ResetPasswordResponse,
  ProfileUpdateRequest,
  ChangePasswordRequest,
  AuditPage,
  UserListParams,
} from '@interfaces/user.interface';
import type {
  ResourceType,
  RevisionSummaryDto,
  RevisionDto,
  RevisionFileDto,
  ConfigAuditFeedPage,
  ConfigAuditFeedParams,
  AuditAccessSettingsDto,
} from '@interfaces/configAudit.interface';

const E = API_ENDPOINTS;

const blobObjectUrl = async (url: string): Promise<string | null> => {
  try {
    const { data } = await httpClient.get<Blob>(url, { responseType: 'blob' });
    if (!data || data.size === 0) return null;
    return URL.createObjectURL(data);
  } catch {
    return null;
  }
};

export const authApi = {
  me: () => makeApiRequest<UserProfileResponse>({}, E.AUTH_ME),
  photoObjectUrl: () =>
    blobObjectUrl(
      typeof E.AUTH_PHOTO.url === 'function' ? E.AUTH_PHOTO.url() : E.AUTH_PHOTO.url
    ),
  updateMe: (body: ProfileUpdateRequest) =>
    makeApiRequest<UserProfileResponse>(body, E.AUTH_UPDATE_ME),
  changePassword: (body: ChangePasswordRequest) =>
    makeApiRequest<void>(body, E.AUTH_CHANGE_PASSWORD),
  uploadPhoto: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return makeApiRequest<void>(fd, E.AUTH_UPLOAD_PHOTO);
  },
};

// User photos change rarely: cache the object-url promise per user so a table
// of N rows fetches each photo once per app lifetime (also dedupes concurrent
// mounts). The cache owns the object URLs — callers must NOT revoke them.
// Failed/empty lookups (null) are evicted so a transient error can retry.
const userPhotoCache = new Map<string, Promise<string | null>>();

export const usersApi = {
  list: (params: UserListParams = {}) =>
    makeApiRequest<UserDto[]>(
      {},
      E.USERS_LIST,
      qs({ search: params.search, role: params.role, status: params.status })
    ),
  get: (id: string) => makeApiRequest<UserDto>({}, E.GET_USER, id),
  create: (body: CreateUserRequest) =>
    makeApiRequest<CreateUserResponse>(body, E.CREATE_USER),
  update: (id: string, body: UpdateUserRequest) =>
    makeApiRequest<UserDto>(body, E.UPDATE_USER, id),
  delete: (id: string) => makeApiRequest<void>({}, E.DELETE_USER, id),
  resetPassword: (id: string) =>
    makeApiRequest<ResetPasswordResponse>({}, E.RESET_USER_PASSWORD, id),
  uploadPhoto: async (id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    await makeApiRequest<void>(fd, E.UPLOAD_USER_PHOTO, id);
    usersApi.invalidatePhoto(id); // next photoObjectUrl fetches the new image
  },
  photoObjectUrl: (id: string): Promise<string | null> => {
    const cached = userPhotoCache.get(id);
    if (cached) return cached;
    const promise = blobObjectUrl(
      typeof E.GET_USER_PHOTO.url === 'function'
        ? E.GET_USER_PHOTO.url(id)
        : E.GET_USER_PHOTO.url
    ).then((url) => {
      if (url === null) userPhotoCache.delete(id);
      return url;
    });
    userPhotoCache.set(id, promise);
    return promise;
  },
  invalidatePhoto: (id: string) => {
    userPhotoCache.delete(id);
  },
};

export const auditApi = {
  list: (params: {
    search?: string;
    action?: string;
    limit?: number;
    offset?: number;
  } = {}) =>
    makeApiRequest<AuditPage>(
      {},
      E.AUDIT_LIST,
      qs({
        search: params.search,
        action: params.action,
        limit: params.limit,
        offset: params.offset,
      })
    ),
};

export const configAuditApi = {
  listFeed: (params: ConfigAuditFeedParams = {}) =>
    makeApiRequest<ConfigAuditFeedPage>(
      {},
      E.CONFIG_AUDIT_FEED,
      qs({
        resourceType: params.resourceType,
        actor: params.actor,
        resourceId: params.resourceId,
        from: params.from,
        to: params.to,
        page: params.page,
        size: params.size,
      })
    ),
  listRevisions: (resourceType: ResourceType, resourceId: string) =>
    makeApiRequest<RevisionSummaryDto[]>(
      {},
      E.CONFIG_AUDIT_REVISIONS,
      `${resourceType}/${resourceId}`
    ),
  getRevision: (resourceType: ResourceType, resourceId: string, version: number) =>
    makeApiRequest<RevisionDto>(
      {},
      E.CONFIG_AUDIT_REVISION_GET,
      `${resourceType}/${resourceId}/revisions/${version}`
    ),
  getRevisionFiles: (
    resourceType: ResourceType,
    resourceId: string,
    version: number
  ) =>
    makeApiRequest<RevisionFileDto[]>(
      {},
      E.CONFIG_AUDIT_REVISION_FILES,
      `${resourceType}/${resourceId}/revisions/${version}`
    ),
  revert: (resourceType: ResourceType, resourceId: string, version: number) =>
    makeApiRequest<void>(
      {},
      E.CONFIG_AUDIT_REVERT,
      `${resourceType}/${resourceId}/revisions/${version}`
    ),
  getSettings: () =>
    makeApiRequest<AuditAccessSettingsDto>({}, E.CONFIG_AUDIT_SETTINGS_GET),
  updateSettings: (nonAdminReadEnabled: boolean) =>
    makeApiRequest<AuditAccessSettingsDto>(
      { nonAdminReadEnabled },
      E.CONFIG_AUDIT_SETTINGS_PUT
    ),
};

export const sessionsApi = {
  list: (archived = false) =>
    makeApiRequest<ChatSessionDto[]>({}, E.LIST_SESSIONS, qs({ archived })),
  create: (assistantId?: string, temporary?: boolean) =>
    makeApiRequest<ChatSessionDto>(
      {},
      E.CREATE_SESSION,
      qs({ assistantId, temporary: temporary ? 'true' : undefined })
    ),
  messages: (id: string) =>
    makeApiRequest<ChatMessageDto[]>({}, E.GET_SESSION_MESSAGES, id),
  update: (id: string, patch: UpdateSessionRequest) =>
    makeApiRequest<ChatSessionDto>(patch, E.UPDATE_SESSION, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_SESSION, id),
  truncate: (id: string, messageIndex: number) =>
    makeApiRequest<void>(
      {},
      E.TRUNCATE_SESSION,
      `${id}/truncate${qs({ messageIndex })}`
    ),
  setStyle: (id: string, styleId: string) =>
    makeApiRequest<ChatSessionDto>(
      { styleId: styleId || '' },
      E.UPDATE_SESSION,
      id
    ),
  createShare: (id: string) =>
    makeApiRequest<ShareLinkDto>({}, E.CREATE_SHARE, id),
  // 204 (no share yet) comes back as an empty body; callers treat falsy shareId as "no link".
  getShare: (id: string) =>
    makeApiRequest<ShareLinkDto | ''>({}, E.GET_SHARE, id),
};

export const sharesApi = {
  view: (shareId: string) =>
    makeApiRequest<SharedChatDto>({}, E.VIEW_SHARE, shareId),
  revoke: (shareId: string) =>
    makeApiRequest<void>({}, E.REVOKE_SHARE, shareId),
};

export const chatApi = {
  submitClarifications: (body: SubmitClarificationRequest) =>
    httpClient.post<void>(directApiUrl('/api/chat/clarifications'), body, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),
  submitSkillUpdateDecision: (body: SubmitSkillUpdateDecisionRequest) =>
    httpClient.post<void>(directApiUrl('/api/chat/skill-update-decisions'), body, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),
};

export const assistantsApi = {
  list: () => makeApiRequest<AssistantDto[]>({}, E.LIST_ASSISTANTS),
  get: (id: string) => makeApiRequest<AssistantDto>({}, E.GET_ASSISTANT, id),
  create: (body: CreateAssistantRequest) =>
    makeApiRequest<AssistantDto>(body, E.CREATE_ASSISTANT),
  update: (id: string, body: UpdateAssistantRequest) =>
    makeApiRequest<AssistantDto>(body, E.UPDATE_ASSISTANT, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_ASSISTANT, id),
  builtinTools: () =>
    makeApiRequest<BuiltinToolDto[]>({}, E.LIST_BUILTIN_TOOLS),
};

export const toolsApi = {
  list: (assistantId: string) =>
    makeApiRequest<AgentToolDto[]>({}, E.LIST_TOOLS, qs({ assistantId })),
  get: (id: string) => makeApiRequest<AgentToolDto>({}, E.GET_TOOL, id),
  create: (assistantId: string, body: CreateToolRequest) =>
    makeApiRequest<AgentToolDto>(body, E.CREATE_TOOL, qs({ assistantId })),
  update: (id: string, body: UpdateToolRequest) =>
    makeApiRequest<AgentToolDto>(body, E.UPDATE_TOOL, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_TOOL, id),
  test: (id: string, body: TestToolRequest) =>
    makeApiRequest<unknown>(body, E.TEST_TOOL, id),
  import: (
    kind: ToolImportKind,
    assistantId: string,
    body: ImportToolsRequest
  ) =>
    makeApiRequest<ImportToolsResult>(
      body,
      E.IMPORT_TOOLS,
      `${kind}${qs({ assistantId })}`
    ),
};

export const toolGroupsApi = {
  list: (assistantId: string) =>
    makeApiRequest<ToolGroupDto[]>({}, E.LIST_TOOL_GROUPS, qs({ assistantId })),
  get: (id: string) =>
    makeApiRequest<ToolGroupDto>({}, E.GET_TOOL_GROUP, id),
  create: (assistantId: string, body: CreateToolGroupRequest) =>
    makeApiRequest<ToolGroupDto>(
      body,
      E.CREATE_TOOL_GROUP,
      qs({ assistantId })
    ),
  update: (id: string, body: UpdateToolGroupRequest) =>
    makeApiRequest<ToolGroupDto>(body, E.UPDATE_TOOL_GROUP, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_TOOL_GROUP, id),
  backfill: (assistantId?: string) =>
    makeApiRequest<{ groupsCreated: number }>(
      {},
      E.BACKFILL_TOOL_GROUPS,
      assistantId ? qs({ assistantId }) : ''
    ),
};

export const authProfilesApi = {
  list: (assistantId: string) =>
    makeApiRequest<ToolAuthProfileDto[]>(
      {},
      E.LIST_AUTH_PROFILES,
      qs({ assistantId })
    ),
  get: (id: string) =>
    makeApiRequest<ToolAuthProfileDto>({}, E.GET_AUTH_PROFILE, id),
  create: (assistantId: string, body: CreateAuthProfileRequest) =>
    makeApiRequest<ToolAuthProfileDto>(
      body,
      E.CREATE_AUTH_PROFILE,
      qs({ assistantId })
    ),
  update: (id: string, body: UpdateAuthProfileRequest) =>
    makeApiRequest<ToolAuthProfileDto>(body, E.UPDATE_AUTH_PROFILE, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_AUTH_PROFILE, id),
};

export const skillsApi = {
  list: (assistantId: string) =>
    makeApiRequest<SkillDto[]>({}, E.LIST_SKILLS, qs({ assistantId })),
  listPlatform: (assistantId: string) =>
    makeApiRequest<PlatformSkillDto[]>(
      {},
      E.LIST_PLATFORM_SKILLS,
      qs({ assistantId })
    ),
  upload: (assistantId: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return makeApiRequest<SkillDto>(
      fd,
      E.UPLOAD_SKILL,
      qs({ assistantId })
    );
  },
  updateMeta: (id: string, body: UpdateSkillRequest) =>
    makeApiRequest<SkillDto>(body, E.UPDATE_SKILL_META, id),
  replaceFile: (id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return makeApiRequest<SkillDto>(fd, E.REPLACE_SKILL_FILE, id);
  },
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_SKILL, id),
  listFiles: (id: string) =>
    makeApiRequest<SkillFileNode[]>({}, E.LIST_SKILL_FILES, id),
  getFile: (id: string, path: string) =>
    makeApiRequest<SkillFileContent>(
      {},
      E.GET_SKILL_FILE,
      JSON.stringify({ id, path })
    ),
  updateFile: (id: string, path: string, content: string) =>
    makeApiRequest<SkillDto>(
      { content },
      E.UPDATE_SKILL_FILE,
      JSON.stringify({ id, path })
    ),
  download: async (id: string, filename: string) => {
    const url =
      typeof E.DOWNLOAD_SKILL.url === 'function'
        ? E.DOWNLOAD_SKILL.url(id)
        : E.DOWNLOAD_SKILL.url;
    const { data } = await httpClient.get<Blob>(url, { responseType: 'blob' });
    const safeName = filename.replace(/[^a-zA-Z0-9._-]+/g, '-');
    const objectUrl = URL.createObjectURL(data);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = safeName.endsWith('.zip') ? safeName : `${safeName}.zip`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  },
};

export const documentsApi = {
  list: (assistantId: string) =>
    makeApiRequest<DocumentDto[]>({}, E.LIST_DOCUMENTS, qs({ assistantId })),
  upload: (assistantId: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return makeApiRequest<DocumentDto>(
      fd,
      E.UPLOAD_DOCUMENT,
      qs({ assistantId })
    );
  },
  update: (id: string, body: UpdateDocumentRequest) =>
    makeApiRequest<DocumentDto>(body, E.UPDATE_DOCUMENT, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_DOCUMENT, id),
};

export const stylesApi = {
  list: (assistantId: string) =>
    makeApiRequest<ResponseStyleDto[]>({}, E.LIST_STYLES, qs({ assistantId })),
  get: (id: string) =>
    makeApiRequest<ResponseStyleDto>({}, E.GET_STYLE, id),
  create: (assistantId: string, body: CreateStyleRequest) =>
    makeApiRequest<ResponseStyleDto>(body, E.CREATE_STYLE, qs({ assistantId })),
  update: (id: string, body: UpdateStyleRequest) =>
    makeApiRequest<ResponseStyleDto>(body, E.UPDATE_STYLE, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_STYLE, id),
  setDefault: (id: string) =>
    makeApiRequest<ResponseStyleDto>({}, E.SET_DEFAULT_STYLE, id),
};

export const mcpApi = {
  list: (assistantId: string) =>
    makeApiRequest<McpServerDto[]>(
      {},
      E.LIST_MCP_SERVERS,
      qs({ assistantId })
    ),
  get: (id: string) =>
    makeApiRequest<McpServerDto>({}, E.GET_MCP_SERVER, id),
  create: (assistantId: string, body: CreateMcpServerRequest) =>
    makeApiRequest<McpServerDto>(
      body,
      E.CREATE_MCP_SERVER,
      qs({ assistantId })
    ),
  update: (id: string, body: UpdateMcpServerRequest) =>
    makeApiRequest<McpServerDto>(body, E.UPDATE_MCP_SERVER, id),
  delete: (id: string) =>
    makeApiRequest<void>({}, E.DELETE_MCP_SERVER, id),
  discover: (id: string) =>
    makeApiRequest<McpServerDto>({}, E.DISCOVER_MCP_SERVER, id),
  tools: (id: string) =>
    makeApiRequest<McpServerToolDto[]>({}, E.LIST_MCP_TOOLS, id),
  setToolEnabled: (id: string, toolId: string, body: UpdateMcpToolRequest) =>
    makeApiRequest<McpServerToolDto>(
      body,
      E.UPDATE_MCP_TOOL,
      `${id}/tools/${toolId}`
    ),
};
