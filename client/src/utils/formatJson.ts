/**
 * Pretty-print a JSON-shaped string. If parsing fails (or the input is empty)
 * returns the raw input unchanged. Ported from legacy/src/App.jsx.
 */
export const formatJson = (raw: string | null | undefined): string => {
  if (raw == null || raw === '') return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};
