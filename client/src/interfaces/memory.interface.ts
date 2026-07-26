/** A durable long-term-memory fact the agent has learned about the user. */
export interface SemanticFactDto {
  id: string;
  assistantId: string | null;
  sessionId: string | null;
  subject: string;
  predicate: string;
  object: string;
  confidence: number;
  importance: number;
  createdAt: number | null;
  lastAccessedAt: number | null;
}
