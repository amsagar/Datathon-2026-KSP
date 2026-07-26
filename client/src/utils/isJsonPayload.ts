/** True when the string parses as JSON (object, array, or quoted primitive). */
export const isJsonPayload = (raw: string | null | undefined): boolean => {
  if (!raw?.trim()) return false;
  try {
    JSON.parse(raw);
    return true;
  } catch {
    return false;
  }
};
