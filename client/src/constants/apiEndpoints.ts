import type ApiRequestConfig from '@interfaces/apiEndpoints.interface';

/**
 * All paths are routed through the webpack devServer proxy (`/api -> :8080`)
 * so the client only ever knows about relative URLs. The Spring Boot
 * controllers all mount under /api (see ApiConstants.java in the backend).
 */
export const BASE_PATH = '/api';

export const DEFAULT_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
};

export const JSON_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
};

type EndpointMap = Record<string, ApiRequestConfig>;

export const API_ENDPOINTS = {
  // ---------- chat sessions ----------
  LIST_SESSIONS: {
    url: (query: string) => `${BASE_PATH}/sessions${query}`,
    method: 'GET',
  },
  CREATE_SESSION: {
    url: (query: string) => `${BASE_PATH}/sessions${query}`,
    method: 'POST',
  },
  GET_SESSION_MESSAGES: {
    url: (id: string) => `${BASE_PATH}/sessions/${id}/messages`,
    method: 'GET',
  },
  UPDATE_SESSION: {
    url: (id: string) => `${BASE_PATH}/sessions/${id}`,
    method: 'PATCH',
  },
  DELETE_SESSION: {
    url: (id: string) => `${BASE_PATH}/sessions/${id}`,
    method: 'DELETE',
  },
  TRUNCATE_SESSION: {
    url: (idAndQuery: string) =>
      `${BASE_PATH}/sessions/${idAndQuery}`,
    method: 'POST',
  },

  // ---------- chat share ----------
  CREATE_SHARE: {
    url: (id: string) => `${BASE_PATH}/sessions/${id}/share`,
    method: 'POST',
  },
  GET_SHARE: {
    url: (id: string) => `${BASE_PATH}/sessions/${id}/share`,
    method: 'GET',
  },
  VIEW_SHARE: {
    url: (shareId: string) => `${BASE_PATH}/shares/${shareId}`,
    method: 'GET',
  },
  REVOKE_SHARE: {
    url: (shareId: string) => `${BASE_PATH}/shares/${shareId}`,
    method: 'DELETE',
  },

  // ---------- chat stream (SSE — URL built in chatStream.ts via directApiUrl) ----------
  CHAT_STREAM: {
    url: (query: string) => `${BASE_PATH}/chat/stream${query}`,
    method: 'GET',
  },
  CHAT_CLARIFICATIONS: {
    url: () => `${BASE_PATH}/chat/clarifications`,
    method: 'POST',
  },
  CHAT_SKILL_UPDATE_DECISIONS: {
    url: () => `${BASE_PATH}/chat/skill-update-decisions`,
    method: 'POST',
  },

  // ---------- auth / SSO ----------
  AUTH_ME: {
    url: () => `${BASE_PATH}/v1/auth/me`,
    method: 'GET',
  },
  AUTH_PHOTO: {
    url: () => `${BASE_PATH}/v1/auth/photo`,
    method: 'GET',
  },
  AUTH_UPDATE_ME: {
    url: () => `${BASE_PATH}/v1/auth/me`,
    method: 'PATCH',
  },
  AUTH_CHANGE_PASSWORD: {
    url: () => `${BASE_PATH}/v1/auth/me/change-password`,
    method: 'POST',
  },
  AUTH_UPLOAD_PHOTO: {
    url: () => `${BASE_PATH}/v1/auth/me/photo`,
    method: 'POST',
  },

  // ---------- user management (admin) ----------
  USERS_LIST: {
    url: (query: string) => `${BASE_PATH}/v1/users${query}`,
    method: 'GET',
  },
  GET_USER: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}`,
    method: 'GET',
  },
  CREATE_USER: {
    url: () => `${BASE_PATH}/v1/users`,
    method: 'POST',
  },
  UPDATE_USER: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}`,
    method: 'PATCH',
  },
  DELETE_USER: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}`,
    method: 'DELETE',
  },
  RESET_USER_PASSWORD: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}/reset-password`,
    method: 'POST',
  },
  UPLOAD_USER_PHOTO: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}/photo`,
    method: 'POST',
  },
  GET_USER_PHOTO: {
    url: (id: string) => `${BASE_PATH}/v1/users/${id}/photo`,
    method: 'GET',
  },

  // ---------- audit log (admin) ----------
  AUDIT_LIST: {
    url: (query: string) => `${BASE_PATH}/v1/audit${query}`,
    method: 'GET',
  },

  // ---------- assistants ----------
  LIST_ASSISTANTS: {
    url: () => `${BASE_PATH}/assistants`,
    method: 'GET',
  },
  GET_ASSISTANT: {
    url: (id: string) => `${BASE_PATH}/assistants/${id}`,
    method: 'GET',
  },
  CREATE_ASSISTANT: {
    url: () => `${BASE_PATH}/assistants`,
    method: 'POST',
  },
  UPDATE_ASSISTANT: {
    url: (id: string) => `${BASE_PATH}/assistants/${id}`,
    method: 'PATCH',
  },
  DELETE_ASSISTANT: {
    url: (id: string) => `${BASE_PATH}/assistants/${id}`,
    method: 'DELETE',
  },
  LIST_BUILTIN_TOOLS: {
    url: () => `${BASE_PATH}/assistants/builtin-tools`,
    method: 'GET',
  },

  // ---------- tools ----------
  LIST_TOOLS: {
    url: (query: string) => `${BASE_PATH}/tools${query}`,
    method: 'GET',
  },
  GET_TOOL: {
    url: (id: string) => `${BASE_PATH}/tools/${id}`,
    method: 'GET',
  },
  CREATE_TOOL: {
    url: (query: string) => `${BASE_PATH}/tools${query}`,
    method: 'POST',
  },
  UPDATE_TOOL: {
    url: (id: string) => `${BASE_PATH}/tools/${id}`,
    method: 'PATCH',
  },
  DELETE_TOOL: {
    url: (id: string) => `${BASE_PATH}/tools/${id}`,
    method: 'DELETE',
  },
  TEST_TOOL: {
    url: (id: string) => `${BASE_PATH}/tools/${id}/test`,
    method: 'POST',
  },
  IMPORT_TOOLS: {
    url: (kindAndQuery: string) =>
      `${BASE_PATH}/tools/import/${kindAndQuery}`,
    method: 'POST',
  },

  // ---------- tool groups ----------
  LIST_TOOL_GROUPS: {
    url: (query: string) => `${BASE_PATH}/tool-groups${query}`,
    method: 'GET',
  },
  GET_TOOL_GROUP: {
    url: (id: string) => `${BASE_PATH}/tool-groups/${id}`,
    method: 'GET',
  },
  CREATE_TOOL_GROUP: {
    url: (query: string) => `${BASE_PATH}/tool-groups${query}`,
    method: 'POST',
  },
  UPDATE_TOOL_GROUP: {
    url: (id: string) => `${BASE_PATH}/tool-groups/${id}`,
    method: 'PATCH',
  },
  DELETE_TOOL_GROUP: {
    url: (id: string) => `${BASE_PATH}/tool-groups/${id}`,
    method: 'DELETE',
  },
  BACKFILL_TOOL_GROUPS: {
    url: (query: string) => `${BASE_PATH}/tool-groups/backfill${query}`,
    method: 'POST',
  },

  // ---------- tool auth profiles ----------
  LIST_AUTH_PROFILES: {
    url: (query: string) => `${BASE_PATH}/tool-auth${query}`,
    method: 'GET',
  },
  GET_AUTH_PROFILE: {
    url: (id: string) => `${BASE_PATH}/tool-auth/${id}`,
    method: 'GET',
  },
  CREATE_AUTH_PROFILE: {
    url: (query: string) => `${BASE_PATH}/tool-auth${query}`,
    method: 'POST',
  },
  UPDATE_AUTH_PROFILE: {
    url: (id: string) => `${BASE_PATH}/tool-auth/${id}`,
    method: 'PATCH',
  },
  DELETE_AUTH_PROFILE: {
    url: (id: string) => `${BASE_PATH}/tool-auth/${id}`,
    method: 'DELETE',
  },

  // ---------- skills ----------
  LIST_SKILLS: {
    url: (query: string) => `${BASE_PATH}/skills${query}`,
    method: 'GET',
  },
  LIST_PLATFORM_SKILLS: {
    url: (query: string) => `${BASE_PATH}/skills/platform${query}`,
    method: 'GET',
  },
  GET_SKILL: {
    url: (id: string) => `${BASE_PATH}/skills/${id}`,
    method: 'GET',
  },
  UPLOAD_SKILL: {
    url: (query: string) => `${BASE_PATH}/skills${query}`,
    method: 'POST',
  },
  UPDATE_SKILL_META: {
    url: (id: string) => `${BASE_PATH}/skills/${id}`,
    method: 'PATCH',
  },
  REPLACE_SKILL_FILE: {
    url: (id: string) => `${BASE_PATH}/skills/${id}`,
    method: 'PATCH',
  },
  DELETE_SKILL: {
    url: (id: string) => `${BASE_PATH}/skills/${id}`,
    method: 'DELETE',
  },
  LIST_SKILL_FILES: {
    url: (id: string) => `${BASE_PATH}/skills/${id}/files`,
    method: 'GET',
  },
  GET_SKILL_FILE: {
    url: (encoded: string) => {
      const { id, path } = JSON.parse(encoded) as { id: string; path: string };
      return `${BASE_PATH}/skills/${id}/files/content?path=${encodeURIComponent(path)}`;
    },
    method: 'GET',
  },
  UPDATE_SKILL_FILE: {
    url: (encoded: string) => {
      const { id, path } = JSON.parse(encoded) as { id: string; path: string };
      return `${BASE_PATH}/skills/${id}/files/content?path=${encodeURIComponent(path)}`;
    },
    method: 'PUT',
  },
  DOWNLOAD_SKILL: {
    url: (id: string) => `${BASE_PATH}/skills/${id}/download`,
    method: 'GET',
  },

  // ---------- documents ----------
  LIST_DOCUMENTS: {
    url: (query: string) => `${BASE_PATH}/documents${query}`,
    method: 'GET',
  },
  GET_DOCUMENT: {
    url: (id: string) => `${BASE_PATH}/documents/${id}`,
    method: 'GET',
  },
  UPLOAD_DOCUMENT: {
    url: (query: string) => `${BASE_PATH}/documents${query}`,
    method: 'POST',
  },
  UPDATE_DOCUMENT: {
    url: (id: string) => `${BASE_PATH}/documents/${id}`,
    method: 'PATCH',
  },
  DELETE_DOCUMENT: {
    url: (id: string) => `${BASE_PATH}/documents/${id}`,
    method: 'DELETE',
  },

  // ---------- response styles ----------
  LIST_STYLES: {
    url: (query: string) => `${BASE_PATH}/response-styles${query}`,
    method: 'GET',
  },
  GET_STYLE: {
    url: (id: string) => `${BASE_PATH}/response-styles/${id}`,
    method: 'GET',
  },
  CREATE_STYLE: {
    url: (query: string) => `${BASE_PATH}/response-styles${query}`,
    method: 'POST',
  },
  UPDATE_STYLE: {
    url: (id: string) => `${BASE_PATH}/response-styles/${id}`,
    method: 'PATCH',
  },
  DELETE_STYLE: {
    url: (id: string) => `${BASE_PATH}/response-styles/${id}`,
    method: 'DELETE',
  },
  SET_DEFAULT_STYLE: {
    url: (id: string) => `${BASE_PATH}/response-styles/${id}/default`,
    method: 'POST',
  },

  // ---------- MCP servers ----------
  LIST_MCP_SERVERS: {
    url: (query: string) => `${BASE_PATH}/mcp-servers${query}`,
    method: 'GET',
  },
  GET_MCP_SERVER: {
    url: (id: string) => `${BASE_PATH}/mcp-servers/${id}`,
    method: 'GET',
  },
  CREATE_MCP_SERVER: {
    url: (query: string) => `${BASE_PATH}/mcp-servers${query}`,
    method: 'POST',
  },
  UPDATE_MCP_SERVER: {
    url: (id: string) => `${BASE_PATH}/mcp-servers/${id}`,
    method: 'PATCH',
  },
  DELETE_MCP_SERVER: {
    url: (id: string) => `${BASE_PATH}/mcp-servers/${id}`,
    method: 'DELETE',
  },
  // ---------- usage ----------
  USAGE_SUMMARY: {
    url: (query: string) => `${BASE_PATH}/usage/summary${query}`,
    method: 'GET',
  },
  USAGE_BY_MODEL: {
    url: (query: string) => `${BASE_PATH}/usage/by-model${query}`,
    method: 'GET',
  },
  USAGE_BY_USER: {
    url: (query: string) => `${BASE_PATH}/usage/by-user${query}`,
    method: 'GET',
  },
  USAGE_BY_ASSISTANT: {
    url: (query: string) => `${BASE_PATH}/usage/by-assistant${query}`,
    method: 'GET',
  },
  USAGE_BY_SOURCE: {
    url: (query: string) => `${BASE_PATH}/usage/by-source${query}`,
    method: 'GET',
  },
  USAGE_HOURLY: {
    url: (query: string) => `${BASE_PATH}/usage/hourly${query}`,
    method: 'GET',
  },

  DISCOVER_MCP_SERVER: {
    url: (id: string) => `${BASE_PATH}/mcp-servers/${id}/discover`,
    method: 'POST',
  },
  LIST_MCP_TOOLS: {
    url: (id: string) => `${BASE_PATH}/mcp-servers/${id}/tools`,
    method: 'GET',
  },
  UPDATE_MCP_TOOL: {
    url: (idAndToolId: string) =>
      `${BASE_PATH}/mcp-servers/${idAndToolId}`,
    method: 'PATCH',
  },

  // ---------- config audit (revision history + revert) ----------
  CONFIG_AUDIT_FEED: {
    url: (query: string) => `${BASE_PATH}/v1/config-audit${query}`,
    method: 'GET',
  },
  CONFIG_AUDIT_REVISIONS: {
    url: (typeAndId: string) =>
      `${BASE_PATH}/v1/config-audit/${typeAndId}/revisions`,
    method: 'GET',
  },
  CONFIG_AUDIT_REVISION_GET: {
    url: (typeIdAndVersion: string) =>
      `${BASE_PATH}/v1/config-audit/${typeIdAndVersion}`,
    method: 'GET',
  },
  CONFIG_AUDIT_REVISION_FILES: {
    url: (typeIdAndVersion: string) =>
      `${BASE_PATH}/v1/config-audit/${typeIdAndVersion}/files`,
    method: 'GET',
  },
  CONFIG_AUDIT_REVERT: {
    url: (typeIdAndVersion: string) =>
      `${BASE_PATH}/v1/config-audit/${typeIdAndVersion}/revert`,
    method: 'POST',
  },
  CONFIG_AUDIT_SETTINGS_GET: {
    url: () => `${BASE_PATH}/v1/config-audit/settings`,
    method: 'GET',
  },
  CONFIG_AUDIT_SETTINGS_PUT: {
    url: () => `${BASE_PATH}/v1/config-audit/settings`,
    method: 'PUT',
  },

  // ---------- long-term memory ----------
  LIST_MEMORIES: {
    url: () => `${BASE_PATH}/memories`,
    method: 'GET',
  },
  DELETE_MEMORY: {
    url: (id: string) => `${BASE_PATH}/memories/${id}`,
    method: 'DELETE',
  },
  DELETE_ALL_MEMORIES: {
    url: () => `${BASE_PATH}/memories`,
    method: 'DELETE',
  },
} as const satisfies EndpointMap;
