export interface PlatformSkillDto {
  id: string;
  name: string;
  description: string;
  /** Ships enabled by default; active for assistants that haven't customized their selection. */
  defaultEnabled: boolean;
  /** Resolved for the requested assistant. */
  active: boolean;
}

export interface SkillDto {
  id: string;
  assistantId: string;
  name: string;
  description: string;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface UpdateSkillRequest {
  name?: string;
  description?: string;
  enabled?: boolean;
}

export interface SkillFileNode {
  path: string;
  name: string;
  type: 'file' | 'folder';
  children: SkillFileNode[];
}

export interface SkillFileContent {
  path: string;
  content: string;
}

export interface UpdateSkillFileRequest {
  content: string;
}
