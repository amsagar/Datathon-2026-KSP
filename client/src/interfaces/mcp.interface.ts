export type McpTransport = 'streamable_http' | 'sse';

export type McpAuthType =
  | 'none'
  | 'api_key_header'
  | 'bearer_token'
  | 'basic_auth'
  | 'oauth_client_credentials'
  | 'oauth_auth_code';

export interface McpServerToolDto {
  id: string;
  serverId: string;
  name: string;
  description: string;
  inputSchema: string;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface McpServerDto {
  id: string;
  assistantId: string;
  name: string;
  description: string;
  transport: McpTransport;
  url: string;
  sseEndpoint?: string | null;
  authType: McpAuthType;
  authConfig?: string | null;
  hasSecret: boolean;
  enabled: boolean;
  status?: string | null;
  statusDetail?: string | null;
  createdAt: number;
  updatedAt: number;
  tools?: McpServerToolDto[];
}

export interface CreateMcpServerRequest {
  name: string;
  description?: string;
  transport: McpTransport;
  url: string;
  sseEndpoint?: string;
  authType: McpAuthType;
  authConfig?: string;
  secret?: string;
  enabled?: boolean;
}

export type UpdateMcpServerRequest = Partial<CreateMcpServerRequest>;

export interface UpdateMcpToolRequest {
  enabled: boolean;
}
