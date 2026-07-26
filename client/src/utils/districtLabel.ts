/** Pick English or Kannada district display name from analytics payloads. */
export function districtLabel(
  row: { district_name?: string | null; district_name_kn?: string | null },
  lang: string,
): string {
  if (lang === 'kn') {
    const kn = row.district_name_kn?.trim();
    if (kn) return kn;
  }
  return row.district_name?.trim() || '';
}
