export interface UsageTotalsDto {
  requestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export interface UsageDailyRowDto {
  day: string;
  requestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export interface UsageBreakdownRowDto {
  key: string;
  requestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export interface UsageSummaryResponse {
  totals: UsageTotalsDto;
  daily: UsageDailyRowDto[];
  previousTotals?: UsageTotalsDto;
}
