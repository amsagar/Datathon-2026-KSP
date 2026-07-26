export interface ToolGroupDto {
  id: string;
  assistantId: string;
  name: string;
  description: string;
  sourceType?: string;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface CreateToolGroupRequest {
  name: string;
  description?: string;
  enabled?: boolean;
}

export type UpdateToolGroupRequest = Partial<CreateToolGroupRequest>;

export interface ImportToolsResult {
  count: number;
  tools: import('@interfaces/tool.interface').AgentToolDto[];
  groupId?: string | null;
  groupName?: string | null;
}
