// JWT auth helpers. The backend mints an HMAC-signed JWT after a successful
// username/password login (POST /api/v1/auth/login). We store it in localStorage
// and attach it as a Bearer token on every request (see the interceptors in
// makeApiRequest.ts).

const TOKEN_KEY = 'pods_auth_token';
const USER_KEY = 'pods_auth_user';
// Where to land after login completes — lets deep links (e.g. /share/:id) survive login.
const POST_LOGIN_REDIRECT_KEY = 'pods_post_login_redirect';

export interface DecodedJwt {
  sub?: string;
  name?: string;
  email?: string;
  iss?: string;
  exp?: number;
  iat?: number;
  roles?: string[];
  groups?: string[];
  mustChangePassword?: boolean;
  [key: string]: unknown;
}

export interface AuthUser {
  upn: string;
  name?: string;
  email?: string;
}

export interface UserProfileResponse {
  name: string | null;
  email: string | null;
  upn: string | null;
  jobTitle: string | null;
  photoUrl: string | null;
  roles: string[];
  admin: boolean;
  /** ISO `yyyy-MM-dd` or null. */
  dateOfBirth: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  enabled: boolean;
  /** True until the user changes the admin-issued first-time password. */
  mustChangePassword: boolean;
}

function base64UrlDecode(input: string): string {
  let s = input.replace(/-/g, '+').replace(/_/g, '/');
  const pad = s.length % 4;
  if (pad) s += '='.repeat(4 - pad);
  return atob(s);
}

export function decodeJwt(token: string | null | undefined): DecodedJwt | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const payload = base64UrlDecode(parts[1]);
    return JSON.parse(payload) as DecodedJwt;
  } catch {
    return null;
  }
}

export function isTokenExpired(token: string | null | undefined): boolean {
  const decoded = decodeJwt(token);
  if (!decoded || typeof decoded.exp !== 'number') return true;
  return decoded.exp * 1000 <= Date.now();
}

export function getAuthToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getAuthUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function setAuthUser(user: AuthUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuthToken(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isAuthenticated(): boolean {
  const token = getAuthToken();
  return !!token && !isTokenExpired(token);
}

// Roles come straight from the JWT `roles` claim (minted by the backend from the
// app_user table). Reading them synchronously here lets route guards and
// nav decide without an async /auth/me round-trip. The backend remains the real
// authorization boundary (@PreAuthorize) — this only drives UI visibility.
export function getRoles(): string[] {
  return decodeJwt(getAuthToken())?.roles ?? [];
}

export function isAdmin(): boolean {
  return getRoles().some((r) => r.toUpperCase() === 'ADMIN');
}

export function hasRole(role: string): boolean {
  return getRoles().some((r) => r.toUpperCase() === role.toUpperCase());
}

/**
 * True when the backend flagged the current token's user as needing a password
 * change (admin-issued first-time password). Read from the JWT `mustChangePassword`
 * claim so route guards can gate without an async round-trip.
 */
export function getMustChangePassword(): boolean {
  return decodeJwt(getAuthToken())?.mustChangePassword === true;
}

/** Sends the user to the local login page, remembering where they were headed. */
export function redirectToSso(): void {
  // Remember the page the user was trying to reach so we can return there after login.
  try {
    const path = window.location.pathname + window.location.search;
    if (!path.startsWith('/auth/') && !path.startsWith('/login')) {
      localStorage.setItem(POST_LOGIN_REDIRECT_KEY, path);
    }
  } catch {
    // localStorage may be unavailable — fall back to the default landing page.
  }
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

/** Returns and clears the saved post-login destination (a SPA path), or null if none. */
export function takePostLoginRedirect(): string | null {
  try {
    const value = localStorage.getItem(POST_LOGIN_REDIRECT_KEY);
    if (value) localStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
    return value && value.startsWith('/') ? value : null;
  } catch {
    return null;
  }
}
