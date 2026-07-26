/**
 * Hide evidence-trail SQL the model may still emit after charts/answers.
 * Strips "SQL executed" headings and fenced ```sql blocks from chat markdown.
 */
export const stripSqlEvidenceSections = (input: string): string =>
  input
    .replace(
      /(?:^|\n)\s*(?:\*\*|__)?\s*SQL\s+executed\s*:?\s*(?:\*\*|__)?\s*/gi,
      '\n',
    )
    .replace(/```sql\s*[\s\S]*?```/gi, '')
    .replace(/\n{3,}/g, '\n\n')
    .trimEnd();

/**
 * Pre-process markdown so common authoring patterns render as expected:
 * - Unicode bullets (•, ·, ●, ‣) → standard list dashes
 * - CRLF → LF
 * - Strip SQL evidence sections from assistant replies
 */
export const normalizeMarkdown = (input: string): string =>
  stripSqlEvidenceSections(
    input
      .replace(/\r\n?/g, '\n')
      .replace(/^([ \t]*)[•·●‣]\s+/gm, '$1- '),
  );
