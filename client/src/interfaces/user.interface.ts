// User management + RBAC DTOs. Mirrors the backend user/ and audit/ packages
// (UserController @ /api/v1/users, AuditController @ /api/v1/audit).

/** The five fixed application roles enforced by the backend @PreAuthorize guards. */
export type AppRole =
  | 'ADMIN'
  | 'SUPERVISOR'
  | 'INVESTIGATOR'
  | 'ANALYST'
  | 'POLICYMAKER';

export const APP_ROLES: AppRole[] = [
  'ADMIN',
  'SUPERVISOR',
  'INVESTIGATOR',
  'ANALYST',
  'POLICYMAKER',
];

export interface UserDto {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  roles: string[];
  enabled: boolean;
  mustChangePassword: boolean;
  /** ISO date `yyyy-MM-dd` or null. */
  dateOfBirth: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  hasPhoto: boolean;
  lastLoginAt: number | null;
  createdAt: number | null;
  updatedAt: number | null;
}

export interface CreateUserRequest {
  username: string;
  displayName?: string;
  email?: string;
  roles: string[];
  /** Required — `yyyy-MM-dd`. Drives the auto-generated first password. */
  dateOfBirth: string;
  phone?: string;
  designation?: string;
  department?: string;
  enabled?: boolean;
}

export interface UpdateUserRequest {
  displayName?: string;
  email?: string;
  roles?: string[];
  enabled?: boolean;
  dateOfBirth?: string;
  phone?: string;
  designation?: string;
  department?: string;
}

export interface CreateUserResponse {
  user: UserDto;
  /** Shown to the admin once; the user must change it on first login. */
  temporaryPassword: string;
}

export interface ResetPasswordResponse {
  temporaryPassword: string;
}

export interface ProfileUpdateRequest {
  displayName?: string;
  email?: string;
  phone?: string;
  dateOfBirth?: string;
  designation?: string;
  department?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuditEntryDto {
  id: string;
  actor: string | null;
  action: string;
  target: string | null;
  details: string | null;
  createdAt: number;
}

export interface AuditPage {
  items: AuditEntryDto[];
  total: number;
}

/** Filters for the admin user list. */
export interface UserListParams {
  search?: string;
  role?: string;
  status?: 'active' | 'disabled';
}
