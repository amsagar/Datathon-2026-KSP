import type {
  UsageBreakdownRowDto,
  UsageDailyRowDto,
  UsageTotalsDto,
} from '@interfaces/usage.interface';

export interface CsvSection {
  title: string;
  headers: string[];
  rows: (string | number)[][];
}

const escapeCsvValue = (value: string | number): string => {
  const s = String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

/**
 * Triggers a browser download of one or more labelled CSV sections concatenated into a single
 * file (blank line + title between sections). No server round trip — everything already loaded
 * client-side for the Usage page is serialized directly.
 */
export const downloadCsv = (filename: string, sections: CsvSection[]): void => {
  const lines: string[] = [];
  sections.forEach((section, index) => {
    if (index > 0) lines.push('');
    lines.push(section.title);
    lines.push(section.headers.map(escapeCsvValue).join(','));
    section.rows.forEach((row) => lines.push(row.map(escapeCsvValue).join(',')));
  });

  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

export interface UsageExportData {
  from: string;
  to: string;
  totals: UsageTotalsDto | null;
  daily: UsageDailyRowDto[];
  byModel: UsageBreakdownRowDto[];
  byUser: UsageBreakdownRowDto[];
  byAssistant: UsageBreakdownRowDto[];
  bySource: UsageBreakdownRowDto[];
  hourly: UsageBreakdownRowDto[];
}

const BREAKDOWN_HEADERS = [
  'Requests',
  'Prompt tokens',
  'Completion tokens',
  'Total tokens',
  'Estimated cost (USD)',
];

const breakdownSection = (
  title: string,
  keyHeader: string,
  rows: UsageBreakdownRowDto[]
): CsvSection => ({
  title,
  headers: [keyHeader, ...BREAKDOWN_HEADERS],
  rows: rows.map((r) => [
    r.key,
    r.requestCount,
    r.promptTokens,
    r.completionTokens,
    r.totalTokens,
    r.estimatedCostUsd.toFixed(4),
  ]),
});

/** Exports every dataset currently loaded on the Usage page as one multi-section CSV file. */
export const exportUsageCsv = (data: UsageExportData): void => {
  const sections: CsvSection[] = [];

  if (data.totals) {
    sections.push({
      title: `Totals (${data.from} to ${data.to})`,
      headers: BREAKDOWN_HEADERS,
      rows: [
        [
          data.totals.requestCount,
          data.totals.promptTokens,
          data.totals.completionTokens,
          data.totals.totalTokens,
          data.totals.estimatedCostUsd.toFixed(4),
        ],
      ],
    });
  }
  if (data.daily.length) {
    sections.push({
      title: 'Daily',
      headers: ['Day', ...BREAKDOWN_HEADERS],
      rows: data.daily.map((r) => [
        r.day,
        r.requestCount,
        r.promptTokens,
        r.completionTokens,
        r.totalTokens,
        r.estimatedCostUsd.toFixed(4),
      ]),
    });
  }
  if (data.byModel.length) sections.push(breakdownSection('By model', 'Model', data.byModel));
  if (data.byAssistant.length) {
    sections.push(breakdownSection('By assistant', 'Assistant', data.byAssistant));
  }
  if (data.bySource.length) sections.push(breakdownSection('By source', 'Source', data.bySource));
  if (data.byUser.length) sections.push(breakdownSection('By user', 'User', data.byUser));
  if (data.hourly.length) sections.push(breakdownSection('By hour (UTC)', 'Hour', data.hourly));

  downloadCsv(`usage-${data.from.slice(0, 10)}-to-${data.to.slice(0, 10)}.csv`, sections);
};
