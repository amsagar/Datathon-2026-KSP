export type HttpToolMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface AgentToolDto {
  id: string;
  assistantId: string;
  name: string;
  description: string;
  method: HttpToolMethod;
  host: string;
  endpoint: string;
  requestSchema: string;
  sourceType?: string;
  authProfileId?: string | null;
  authType?: string | null;
  authConfig?: string | null;
  groupId?: string | null;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface CreateToolRequest {
  name: string;
  description: string;
  method: HttpToolMethod;
  host: string;
  endpoint: string;
  requestSchema: string;
  authProfileId?: string | null;
  authType?: string | null;
  authConfig?: string | null;
  groupId?: string | null;
  enabled?: boolean;
}

export type UpdateToolRequest = Partial<CreateToolRequest>;

export interface TestToolRequest {
  input: string;
}

export interface ImportToolsRequest {
  content?: string;
  host?: string;
  specUrl?: string;
  groupName?: string;
}

export type ToolImportKind = 'curl' | 'openapi' | 'postman';
