export const ROUTE_PATHS = {
  CHAT: '/',
  SHARE_CHAT: '/share/:shareId',
  SETTINGS: '/settings',
  SETTINGS_SECTION: '/settings/:section',
  LOGIN: '/login',
  /** @deprecated Use analyticsChatPath — analytics lives inside the chat window. */
  DASHBOARD: '/analytics/dashboard',
  CRIME_MAP: '/analytics/map',
  CRIME_NETWORK: '/analytics/network',
  OFFENDERS: '/analytics/offenders',
  ERROR: '/error',
  NOT_FOUND: '*',
} as const;

export type AnalyticsChatTab = 'dashboard' | 'map' | 'network' | 'offenders';

/** Open an analytics view inside the chat workspace. */
export const analyticsChatPath = (
  tab: AnalyticsChatTab = 'dashboard',
  extra?: Record<string, string>
) => {
  const params = new URLSearchParams({ analytics: tab, ...(extra || {}) });
  return `/?${params.toString()}`;
};

export const SETTINGS_SECTIONS = {
  APPEARANCE: 'appearance',
  PROFILE: 'profile',
  USAGE: 'usage',
  MEMORY: 'memory',
  USERS: 'users',
  AUDIT: 'audit',
  ASSISTANTS: 'assistants',
  TOOLS: 'tools',
  SKILLS: 'skills',
  DOCUMENTS: 'documents',
  RESPONSE_STYLES: 'response-styles',
  MCP_SERVERS: 'mcp-servers',
} as const;

export type SettingsSection =
  (typeof SETTINGS_SECTIONS)[keyof typeof SETTINGS_SECTIONS];

export const settingsPath = (section: SettingsSection) =>
  `/settings/${section}`;

export type RoutePath = (typeof ROUTE_PATHS)[keyof typeof ROUTE_PATHS];
