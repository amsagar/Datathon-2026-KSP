/**
 * Render an epoch-seconds timestamp as a short relative-time string
 * ("just now", "5m ago", "3h ago", "2d ago"). Ported from the previous
 * client's `relativeTime()` in legacy/src/api.js.
 */
export const relativeTime = (epochSeconds?: number | null): string => {
  if (!epochSeconds) return '';
  const diff = Date.now() / 1000 - epochSeconds;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
};
