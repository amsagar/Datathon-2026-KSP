export interface AssistantDto {
  id: string;
  name: string;
  systemPrompt: string;
  builtinTools: string[];
  /** Resolved ACTIVE platform skill ids (server applies default semantics). */
  platformSkills: string[];
  createdAt: number;
  updatedAt: number;
}

export interface BuiltinToolDto {
  key: string;
  label: string;
}

export interface CreateAssistantRequest {
  name: string;
  systemPrompt: string;
  builtinTools: string[];
  /** Omit for platform defaults; set an explicit list to customize ([] disables all). */
  platformSkills?: string[];
}

export type UpdateAssistantRequest = Partial<CreateAssistantRequest>;
