import type { ToolAuthProfileDto } from '@interfaces/auth.interface';

export interface AuthConfigState {
  in: string;
  name: string;
  username: string;
  clientId: string;
}

export interface AuthProfileFormState {
  name: string;
  description: string;
  authType: string;
  config: AuthConfigState;
  clientSecret: string;
  tokenUrl: string;
  scopes: string;
}

export const EMPTY_CONFIG: AuthConfigState = {
  in: 'header',
  name: '',
  username: '',
  clientId: '',
};

export const EMPTY_AUTH_PROFILE_FORM: AuthProfileFormState = {
  name: '',
  description: '',
  authType: 'none',
  config: { ...EMPTY_CONFIG },
  clientSecret: '',
  tokenUrl: '',
  scopes: '',
};

/** Map legacy DB values to current UI auth type ids. */
export const normalizeAuthType = (authType: string): string => {
  const legacy: Record<string, string> = {
    api_key: 'api_key_header',
    bearer: 'bearer_token',
    basic: 'basic_auth',
  };
  return legacy[authType] ?? authType;
};

export const defaultConfigForType = (authType: string): AuthConfigState => {
  switch (authType) {
    case 'api_key_header':
      return { in: 'header', name: 'X-API-Key', username: '', clientId: '' };
    case 'basic_auth':
      return { in: 'header', name: '', username: '', clientId: '' };
    case 'oauth_client_credentials':
      return { in: 'header', name: '', username: '', clientId: '' };
    default:
      return { ...EMPTY_CONFIG };
  }
};

const parseAuthConfig = (raw?: string | null): Record<string, string> => {
  if (!raw?.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(parsed).map(([k, v]) => [k, String(v ?? '')])
    );
  } catch {
    return {};
  }
};

export const profileToForm = (p: ToolAuthProfileDto): AuthProfileFormState => {
  const cfg = parseAuthConfig(p.authConfig);
  const authType = normalizeAuthType(p.authType);
  return {
    name: p.name,
    description: p.description,
    authType,
    config: {
      in: cfg.in || 'header',
      name: cfg.name || '',
      username: cfg.username || '',
      clientId: cfg.clientId || '',
    },
    clientSecret: '',
    tokenUrl: p.tokenUrl || '',
    scopes: p.scopes || '',
  };
};

export const buildAuthConfigForSave = (
  authType: string,
  config: AuthConfigState
): string | undefined => {
  switch (authType) {
    case 'oauth_client_credentials': {
      const next: Record<string, string> = {};
      if (config.clientId.trim()) next.clientId = config.clientId.trim();
      return Object.keys(next).length > 0 ? JSON.stringify(next) : undefined;
    }
    case 'api_key_header': {
      const next: Record<string, string> = {};
      if (config.name.trim()) next.name = config.name.trim();
      if (config.in.trim()) next.in = config.in.trim();
      return Object.keys(next).length > 0 ? JSON.stringify(next) : undefined;
    }
    case 'basic_auth': {
      if (!config.username.trim()) return undefined;
      return JSON.stringify({ username: config.username.trim() });
    }
    case 'bearer_token':
    case 'none':
      return undefined;
    default:
      return undefined;
  }
};

const configEqual = (a: AuthConfigState, b: AuthConfigState): boolean =>
  a.in === b.in &&
  a.name === b.name &&
  a.username === b.username &&
  a.clientId === b.clientId;

export const formsEqual = (
  a: AuthProfileFormState,
  b: AuthProfileFormState
): boolean =>
  a.name.trim() === b.name.trim() &&
  (a.description || '').trim() === (b.description || '').trim() &&
  a.authType === b.authType &&
  configEqual(a.config, b.config) &&
  a.tokenUrl === b.tokenUrl &&
  a.scopes === b.scopes &&
  !a.clientSecret.trim();

export const authTypeNeedsSecret = (authType: string): boolean =>
  authType !== 'none';

export const isAuthConfigValid = (
  form: AuthProfileFormState,
  isNew: boolean,
  hasStoredSecret: boolean
): boolean => {
  switch (form.authType) {
    case 'none':
      return true;
    case 'api_key_header':
      return (
        !!form.config.name.trim() &&
        (!isNew || !!form.clientSecret.trim() || hasStoredSecret)
      );
    case 'bearer_token':
      return !isNew || !!form.clientSecret.trim() || hasStoredSecret;
    case 'basic_auth':
      return (
        !!form.config.username.trim() &&
        (!isNew || !!form.clientSecret.trim() || hasStoredSecret)
      );
    case 'oauth_client_credentials':
      return !!form.config.clientId.trim();
    default:
      return true;
  }
};

export const applyAuthTypeChange = (
  prev: AuthProfileFormState,
  nextType: string
): AuthProfileFormState => {
  if (prev.authType === nextType) return prev;
  return {
    ...prev,
    authType: nextType,
    config: defaultConfigForType(nextType),
    clientSecret: '',
    tokenUrl: '',
    scopes: '',
  };
};
