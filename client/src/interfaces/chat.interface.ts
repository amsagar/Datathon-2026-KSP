export interface ChatSessionDto {
  id: string;
  title: string;
  archived: boolean;
  assistantId?: string | null;
  styleId?: string | null;
  createdAt: number;
  updatedAt: number;
  /** Temporary chat: persisted + viewable, auto-deleted after the retention window, memory-isolated. */
  temporary?: boolean;
}

export interface ToolCallDto {
  id: string;
  name: string;
  input: string;
  output: string;
  error: boolean;
}

export interface ChatMessageDto {
  role: 'user' | 'assistant' | 'system';
  content: string;
  tools?: ToolCallDto[];
}

export interface UpdateSessionRequest {
  title?: string;
  archived?: boolean;
  styleId?: string;
}

/** Owner-facing view of a session's share link. */
export interface ShareLinkDto {
  shareId: string;
  messageCount: number;
  createdAt: number;
  updatedAt: number;
}

/** Viewer-facing frozen snapshot of a shared conversation. */
export interface SharedChatDto {
  title: string;
  assistantName?: string | null;
  messages: ChatMessageDto[];
  createdAt: number;
}

export interface UiToolCall {
  id: string;
  name: string;
  input: string;
  output: string | null;
  error?: boolean;
  running: boolean;
}

export interface AnsweredClarification {
  requestId: string;
  questions: ClarificationQuestionDto[];
  answers: Record<string, string>;
}

export interface UiChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  tools: UiToolCall[];
  /** Set when user answered AskUserQuestion (live turn or reloaded from DB). */
  clarification?: AnsweredClarification | null;
}

export interface ClarificationOptionDto {
  label: string;
  description: string;
}

export interface ClarificationQuestionDto {
  question: string;
  header: string;
  multiSelect: boolean;
  options: ClarificationOptionDto[];
}

export interface PendingClarification {
  requestId: string;
  callId: string;
  questions: ClarificationQuestionDto[];
  submitting?: boolean;
  error?: string;
}

export interface PendingSkillUpdateProposal {
  requestId: string;
  callId: string;
  skillId: string;
  skillName: string;
  filePath: string;
  summary: string;
  feedbackQuote?: string | null;
  currentContent: string;
  proposedContent: string;
  submitting?: boolean;
  error?: string;
}

export interface SubmitClarificationRequest {
  requestId: string;
  answers: Record<string, string>;
}

export interface SubmitSkillUpdateDecisionRequest {
  requestId: string;
  approved: boolean;
  rejectionReason?: string;
}

export type SseEventName =
  | 'message'
  | 'tool'
  | 'tool_result'
  | 'clarification'
  | 'skill_update_proposal'
  | 'status'
  | 'done'
  | 'error';

export interface SseStatusEvent {
  text: string;
}

export interface SseMessageEvent {
  text: string;
}

export interface SseToolEvent {
  id: string;
  name: string;
  input: string;
}

export interface SseToolResultEvent {
  id: string;
  output: string | null;
  error?: boolean;
}

export interface SseErrorEvent {
  text: string;
}

export interface SseClarificationEvent {
  requestId: string;
  callId: string;
  questions: ClarificationQuestionDto[];
}

export interface SseSkillUpdateProposalEvent {
  requestId: string;
  callId: string;
  skillId: string;
  skillName: string;
  filePath: string;
  summary: string;
  feedbackQuote?: string | null;
  currentContent: string;
  proposedContent: string;
}
