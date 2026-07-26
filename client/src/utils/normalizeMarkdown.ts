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
 * Repair tables that translators flattened into one line with doubled pipes
 * (e.g. `| a | b ||| c | d |` or `----|| |---|`).
 */
export const repairFlattenedTables = (input: string): string => {
  const lines = input.replace(/\r\n?/g, '\n').split('\n');
  const out: string[] = [];

  for (const line of lines) {
    const pipeCount = (line.match(/\|/g) || []).length;
    // A single visual "line" that clearly contains multiple table rows.
    if (pipeCount >= 8 && (line.includes('||') || /\|[\t ]*\|/.test(line))) {
      const chunks = line
        .split(/\|\|/)
        .map((c) => c.trim())
        .filter(Boolean)
        .map((c) => {
          let row = c;
          if (!row.startsWith('|')) row = `| ${row}`;
          if (!row.endsWith('|')) row = `${row} |`;
          // Normalize separator fragments like "----|" / "|---|"
          if (/^[\s|:-]+$/.test(row.replace(/\|/g, ''))) {
            const cols = Math.max(1, (row.match(/\|/g) || []).length - 1);
            return `| ${Array.from({ length: cols }, () => '---').join(' | ')} |`;
          }
          return row.replace(/\s*\|\s*/g, ' | ').replace(/^\s*\|\s*/, '| ').replace(/\s*\|\s*$/, ' |');
        });
      if (chunks.length >= 2) {
        out.push(...chunks);
        continue;
      }
    }
    out.push(line);
  }
  return out.join('\n');
};

/**
 * Pre-process markdown so common authoring patterns render as expected:
 * - Unicode bullets (•, ·, ●, ‣) → standard list dashes
 * - CRLF → LF
 * - Repair translator-flattened GFM tables
 * - Strip SQL evidence sections from assistant replies
 */
export const normalizeMarkdown = (input: string): string =>
  stripSqlEvidenceSections(
    repairFlattenedTables(
      input
        .replace(/\r\n?/g, '\n')
        .replace(/^([ \t]*)[•·●‣]\s+/gm, '$1- '),
    ),
  );
