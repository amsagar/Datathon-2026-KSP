import type { ToolImportKind } from '@interfaces/tool.interface';

export const TOOL_IMPORT_KINDS: { value: ToolImportKind; label: string }[] = [
  { value: 'openapi', label: 'OpenAPI / Swagger spec' },
  { value: 'postman', label: 'Postman collection' },
];

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;

export const TOOL_AUTH_TYPES = [
  { value: 'none', label: 'None' },
  { value: 'api_key_header', label: 'API key (header)' },
  { value: 'bearer_token', label: 'Bearer token' },
  { value: 'basic_auth', label: 'Basic auth' },
  { value: 'oauth_client_credentials', label: 'OAuth client credentials' },
] as const;

export const MCP_TRANSPORTS = [
  { value: 'streamable_http', label: 'Streamable HTTP' },
  { value: 'sse', label: 'Server-Sent Events' },
] as const;

export const MCP_AUTH_TYPES = [
  { value: 'none', label: 'None' },
  { value: 'api_key_header', label: 'API key (header)' },
  { value: 'bearer_token', label: 'Bearer token' },
  { value: 'basic_auth', label: 'Basic auth' },
  { value: 'oauth_client_credentials', label: 'OAuth client credentials' },
  { value: 'oauth_auth_code', label: 'OAuth authorization code' },
] as const;
