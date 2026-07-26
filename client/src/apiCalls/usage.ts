import makeApiRequest, { qs } from './makeApiRequest';
import { API_ENDPOINTS } from '@constants/apiEndpoints';
import type {
  UsageBreakdownRowDto,
  UsageSummaryResponse,
} from '@interfaces/usage.interface';

export const fetchUsageSummary = (
  from: string,
  to: string
): Promise<UsageSummaryResponse> =>
  makeApiRequest<UsageSummaryResponse>(
    {},
    API_ENDPOINTS.USAGE_SUMMARY,
    qs({ from, to })
  );

export const fetchUsageByModel = (
  from: string,
  to: string
): Promise<UsageBreakdownRowDto[]> =>
  makeApiRequest<UsageBreakdownRowDto[]>(
    {},
    API_ENDPOINTS.USAGE_BY_MODEL,
    qs({ from, to })
  );

export const fetchUsageByUser = (
  from: string,
  to: string
): Promise<UsageBreakdownRowDto[]> =>
  makeApiRequest<UsageBreakdownRowDto[]>(
    {},
    API_ENDPOINTS.USAGE_BY_USER,
    qs({ from, to })
  );

export const fetchUsageByAssistant = (
  from: string,
  to: string
): Promise<UsageBreakdownRowDto[]> =>
  makeApiRequest<UsageBreakdownRowDto[]>(
    {},
    API_ENDPOINTS.USAGE_BY_ASSISTANT,
    qs({ from, to })
  );

export const fetchUsageBySource = (
  from: string,
  to: string
): Promise<UsageBreakdownRowDto[]> =>
  makeApiRequest<UsageBreakdownRowDto[]>(
    {},
    API_ENDPOINTS.USAGE_BY_SOURCE,
    qs({ from, to })
  );

export const fetchUsageHourly = (
  from: string,
  to: string
): Promise<UsageBreakdownRowDto[]> =>
  makeApiRequest<UsageBreakdownRowDto[]>(
    {},
    API_ENDPOINTS.USAGE_HOURLY,
    qs({ from, to })
  );
