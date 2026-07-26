export type DiffLineType = 'same' | 'add' | 'remove';

export interface DiffLine {
  type: DiffLineType;
  text: string;
}

/**
 * Simple line-based diff for skill file previews (no external dependency).
 */
export function computeLineDiff(oldText: string, newText: string): DiffLine[] {
  const oldLines = oldText.split('\n');
  const newLines = newText.split('\n');
  const lcs = longestCommonSubsequence(oldLines, newLines);
  const result: DiffLine[] = [];
  let oi = 0;
  let ni = 0;
  for (const line of lcs) {
    while (oi < oldLines.length && oldLines[oi] !== line) {
      result.push({ type: 'remove', text: oldLines[oi] });
      oi += 1;
    }
    while (ni < newLines.length && newLines[ni] !== line) {
      result.push({ type: 'add', text: newLines[ni] });
      ni += 1;
    }
    result.push({ type: 'same', text: line });
    oi += 1;
    ni += 1;
  }
  while (oi < oldLines.length) {
    result.push({ type: 'remove', text: oldLines[oi] });
    oi += 1;
  }
  while (ni < newLines.length) {
    result.push({ type: 'add', text: newLines[ni] });
    ni += 1;
  }
  return result;
}

function longestCommonSubsequence(a: string[], b: string[]): string[] {
  const m = a.length;
  const n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () =>
    Array(n + 1).fill(0)
  );
  for (let i = 1; i <= m; i += 1) {
    for (let j = 1; j <= n; j += 1) {
      if (a[i - 1] === b[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }
  const out: string[] = [];
  let i = m;
  let j = n;
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      out.unshift(a[i - 1]);
      i -= 1;
      j -= 1;
    } else if (dp[i - 1][j] >= dp[i][j - 1]) {
      i -= 1;
    } else {
      j -= 1;
    }
  }
  return out;
}
