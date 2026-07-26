export interface DocumentDto {
  id: string;
  assistantId: string;
  name: string;
  chunkCount: number;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface UpdateDocumentRequest {
  name?: string;
  enabled?: boolean;
}
