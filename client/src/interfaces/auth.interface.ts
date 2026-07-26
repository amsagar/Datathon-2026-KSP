export interface ToolAuthProfileDto {
  id: string;
  name: string;
  description: string;
  authType: string;
  authConfig?: string | null;
  tokenUrl?: string | null;
  scopes?: string | null;
  hasClientSecret: boolean;
  hasAccessToken: boolean;
  tokenExpiresAt?: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface CreateAuthProfileRequest {
  name: string;
  description?: string;
  authType: string;
  authConfig?: string;
  clientSecret?: string;
  tokenUrl?: string;
  scopes?: string;
}

export type UpdateAuthProfileRequest = Partial<CreateAuthProfileRequest>;
