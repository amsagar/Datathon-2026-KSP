/**
 * Runtime API base for SSE (and related chat calls). In production, docker-defaults.sh
 * sets `window.__RUNTIME_CONFIG__.streamApiBase` from the same BASE_URL nginx uses,
 * so the browser hits the API gateway directly instead of UI nginx → proxy → gateway.
 * When empty, requests stay same-origin `/api/...` (local dev webpack proxy).
 */
declare global {
  interface Window {
    __RUNTIME_CONFIG__?: {
      streamApiBase?: string;
    };
  }
}

const trimTrailingSlash = (url: string) => url.replace(/\/$/, '');

/** API gateway origin (e.g. https://api.ksp.example.gov.in) or "" for same-origin /api. */
export const streamApiBase = (): string => {
  const fromWindow = window.__RUNTIME_CONFIG__?.streamApiBase;
  if (fromWindow && fromWindow.trim()) {
    return trimTrailingSlash(fromWindow.trim());
  }
  const fromBuild = process.env.STREAM_API_BASE;
  if (fromBuild && fromBuild.trim()) {
    return trimTrailingSlash(fromBuild.trim());
  }
  return '';
};

/** Full URL for a backend path under /api (path must start with /api). */
export const directApiUrl = (apiPath: string): string => {
  const path = apiPath.startsWith('/') ? apiPath : `/${apiPath}`;
  const base = streamApiBase();
  return base ? `${base}${path}` : path;
};
