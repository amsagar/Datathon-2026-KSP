import { translateText } from '@apiCalls/translate';
import type { UiLang } from '@utils/speech';

const FENCE_RE = /```[\s\S]*?```/g;
const TABLE_SEP_RE = /^\s*\|?[\t :|-]+\|[\t :|-]*\|?\s*$/;
const LIST_PREFIX_RE = /^(\s*(?:[-*+]|\d+\.)\s+)/;
const QUOTE_PREFIX_RE = /^(\s*>+\s*)/;
const HEADING_PREFIX_RE = /^(#{1,6}\s+)/;

const isTableLine = (line: string): boolean => {
  const t = line.trim();
  if (!t.includes('|')) return false;
  // At least two pipes, or leading pipe with a cell
  return (t.match(/\|/g) || []).length >= 2 || /^\|/.test(t);
};

const isTableBlock = (block: string): boolean => {
  const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
  if (lines.length < 2) return false;
  const tableLines = lines.filter(isTableLine);
  return tableLines.length >= 2 && tableLines.length >= lines.length - 1;
};

const splitTableCells = (line: string): string[] => {
  let t = line.trim();
  if (t.startsWith('|')) t = t.slice(1);
  if (t.endsWith('|')) t = t.slice(0, -1);
  return t.split('|').map((c) => c.trim());
};

const formatTableRow = (cells: string[]): string =>
  `| ${cells.map((c) => c.replace(/\s*\n\s*/g, ' ').trim()).join(' | ')} |`;

const formatSeparator = (colCount: number): string =>
  `| ${Array.from({ length: Math.max(colCount, 1) }, () => '---').join(' | ')} |`;

/**
 * Translate cell texts while preserving count. Batches into one request with a
 * private delimiter; falls back to per-cell if the engine collapses lines.
 */
const translatePieces = async (
  pieces: string[],
  targetLang: UiLang
): Promise<string[]> => {
  if (!pieces.length) return pieces;
  const needs = pieces.map((p) => p.trim());
  const todoIdx = needs
    .map((p, i) => (p && /[A-Za-z\u0C80-\u0CFF]/.test(p) ? i : -1))
    .filter((i) => i >= 0);
  if (!todoIdx.length) return needs;

  const payload = todoIdx.map((i) => needs[i]).join('\n');
  const translated = await translateText(payload, targetLang);
  const parts = translated.split(/\n/).map((p) => p.trim());

  const out = [...needs];
  if (parts.length === todoIdx.length) {
    todoIdx.forEach((i, j) => {
      out[i] = parts[j] || needs[i];
    });
    return out;
  }

  // Fallback: one request per piece (still cached by translateText).
  await Promise.all(
    todoIdx.map(async (i) => {
      out[i] = (await translateText(needs[i], targetLang)) || needs[i];
    })
  );
  return out;
};

const translateTable = async (
  block: string,
  targetLang: UiLang
): Promise<string> => {
  const lines = block.split('\n').filter((l) => l.trim().length > 0);
  const parsed = lines.map((line) => {
    if (TABLE_SEP_RE.test(line)) {
      return { type: 'sep' as const, cells: splitTableCells(line) };
    }
    return { type: 'row' as const, cells: splitTableCells(line) };
  });

  const colCount = Math.max(
    1,
    ...parsed.map((r) => r.cells.length).filter((n) => n > 0)
  );

  const cellJobs: { row: number; col: number; text: string }[] = [];
  parsed.forEach((row, ri) => {
    if (row.type !== 'row') return;
    // Pad/truncate to colCount for stable GFM
    while (row.cells.length < colCount) row.cells.push('');
    if (row.cells.length > colCount) row.cells.length = colCount;
    row.cells.forEach((text, ci) => {
      cellJobs.push({ row: ri, col: ci, text });
    });
  });

  const translatedCells = await translatePieces(
    cellJobs.map((j) => j.text),
    targetLang
  );
  cellJobs.forEach((job, i) => {
    parsed[job.row].cells[job.col] = translatedCells[i];
  });

  const outLines: string[] = [];
  let sawSep = false;
  parsed.forEach((row, idx) => {
    if (row.type === 'sep') {
      outLines.push(formatSeparator(colCount));
      sawSep = true;
      return;
    }
    outLines.push(formatTableRow(row.cells));
    // Ensure a separator after the header row when the source omitted/broke it
    if (idx === 0 && !sawSep && parsed.length > 1) {
      const next = parsed[1];
      if (next?.type !== 'sep') {
        outLines.push(formatSeparator(colCount));
        sawSep = true;
      }
    }
  });
  return outLines.join('\n');
};

const translateProseLine = async (
  line: string,
  targetLang: UiLang
): Promise<string> => {
  if (!line.trim()) return line;

  let prefix = '';
  let body = line;
  const heading = body.match(HEADING_PREFIX_RE);
  if (heading) {
    prefix = heading[1];
    body = body.slice(prefix.length);
  } else {
    const quote = body.match(QUOTE_PREFIX_RE);
    if (quote) {
      prefix = quote[1];
      body = body.slice(prefix.length);
    } else {
      const list = body.match(LIST_PREFIX_RE);
      if (list) {
        prefix = list[1];
        body = body.slice(prefix.length);
      }
    }
  }

  // Keep bare markdown image/link targets stable: translate visible label only.
  const linky = body.match(/^\[([^\]]+)]\(([^)]+)\)$/);
  if (linky) {
    const label = await translateText(linky[1], targetLang);
    return `${prefix}[${label}](${linky[2]})`;
  }

  if (!/[A-Za-z\u0C80-\u0CFF]/.test(body)) {
    return line;
  }
  const translated = await translateText(body, targetLang);
  return `${prefix}${translated}`;
};

const translateProse = async (
  block: string,
  targetLang: UiLang
): Promise<string> => {
  const lines = block.split('\n');
  // Batch non-empty lines that are plain prose (no leading md markers) for speed.
  const out = [...lines];
  const plainIdx: number[] = [];
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (
      l.trim() &&
      !HEADING_PREFIX_RE.test(l) &&
      !QUOTE_PREFIX_RE.test(l) &&
      !LIST_PREFIX_RE.test(l) &&
      !/^\s*\|/.test(l)
    ) {
      plainIdx.push(i);
    } else if (l.trim()) {
      out[i] = await translateProseLine(l, targetLang);
    }
  }
  if (plainIdx.length) {
    const pieces = await translatePieces(
      plainIdx.map((i) => lines[i]),
      targetLang
    );
    plainIdx.forEach((i, j) => {
      out[i] = pieces[j];
    });
  }
  return out.join('\n');
};

/**
 * Translate markdown while preserving GFM tables, code fences, lists, and headings.
 * Google Translate collapses pipes/newlines when fed a raw table — so we translate
 * cell/prose text only and rebuild structure.
 */
export async function translateMarkdown(
  markdown: string,
  targetLang: UiLang
): Promise<string> {
  const source = (markdown || '').replace(/\r\n?/g, '\n');
  if (!source.trim()) return '';

  const fences: string[] = [];
  const withPlaceholders = source.replace(FENCE_RE, (m) => {
    const id = fences.length;
    fences.push(m);
    return `\n%%FENCE_${id}%%\n`;
  });

  // Split into blocks on blank lines, but keep consecutive table lines in one block.
  const rawLines = withPlaceholders.split('\n');
  const blocks: string[] = [];
  let buf: string[] = [];
  let inTable = false;

  const flush = () => {
    if (buf.length) {
      blocks.push(buf.join('\n'));
      buf = [];
    }
    inTable = false;
  };

  for (const line of rawLines) {
    const tableLine = isTableLine(line);
    if (tableLine) {
      if (!inTable && buf.length && buf.every((l) => !l.trim())) {
        flush();
      }
      if (!inTable && buf.length && !buf.every((l) => !isTableLine(l) && !l.trim())) {
        // Close prior prose block before starting a table
        if (!buf.every((l) => isTableLine(l) || !l.trim())) {
          flush();
        }
      }
      inTable = true;
      buf.push(line);
      continue;
    }
    if (inTable) {
      flush();
    }
    if (!line.trim()) {
      if (buf.length) flush();
      blocks.push('');
      continue;
    }
    buf.push(line);
  }
  flush();

  const translatedBlocks: string[] = [];
  for (const block of blocks) {
    if (!block) {
      translatedBlocks.push('');
      continue;
    }
    const trimmed = block.trim();
    const fence = trimmed.match(/^%%FENCE_(\d+)%%$/);
    if (fence) {
      translatedBlocks.push(trimmed);
      continue;
    }
    if (isTableBlock(block)) {
      translatedBlocks.push(await translateTable(block, targetLang));
    } else {
      translatedBlocks.push(await translateProse(block, targetLang));
    }
  }

  let result = translatedBlocks
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

  result = result.replace(/%%FENCE_(\d+)%%/g, (_, n) => fences[Number(n)] ?? '');
  return result;
}
