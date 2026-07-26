import { httpClient } from '@apiCalls/makeApiRequest';

export interface TrendRow {
  period: string;
  crime_head: string;
  count: number;
}
export interface HotspotRow {
  lat: number;
  lng: number;
  weight: number;
  crime_head: string;
}
export interface DistrictSummaryRow {
  district_id: number;
  district_name: string;
  district_name_kn?: string | null;
  total: number;
  heinous: number;
  stations: number;
}
export interface LookupRow {
  district_id?: number;
  district_name?: string;
  district_name_kn?: string | null;
  crime_head_id?: number;
  crime_group_name?: string;
}
export interface EarlyWarningRow {
  district_name: string;
  district_name_kn?: string | null;
  crime_head: string;
  recent_count: number;
  baseline_per_quarter: number;
  spike_ratio: number;
}
export interface HotspotForecastRow {
  districtId: number;
  districtName: string;
  baselineTotal: number;
  forecastTotal: number;
  ratio: number;
  method: string;
  backtestMape: number | null;
}
export interface RiskScoreRow {
  person_uid: string;
  accused_name: string;
  case_count: number;
  heinous_count: number;
  chargesheeted_count: number;
  last_case_date: string;
  risk_score: number;
}
export interface GraphNode {
  id: string;
  name: string;
  type: string;
  [key: string]: unknown;
}
export interface GraphLink {
  source: string;
  target: string;
  kind?: string;
  sharedCases?: number;
}
export interface NetworkGraph {
  nodes: GraphNode[];
  links: GraphLink[];
}

export interface AnalyticsFilters {
  from?: string;
  to?: string;
  districtId?: number;
  crimeHeadId?: number;
}

export interface ForecastPoint {
  period: string;
  value: number;
}
export interface Forecast {
  history: ForecastPoint[];
  forecast: ForecastPoint[];
  method: string;
}
export interface SeasonalityRow {
  month_num: number;
  avg_count: number;
  total: number;
}
export interface DemographicRow {
  bucket: string;
  count: number;
}
export interface OffenderGroupMember {
  personUid: string;
  name: string;
  connections: number;
}
export interface OffenderGroup {
  size: number;
  edges: number;
  sharedCases: number;
  cohesion: number;
  ringleaderUid: string;
  ringleaderName: string;
  members: OffenderGroupMember[];
}
export interface OffenderGroupsResult {
  groups: OffenderGroup[];
  pairCount: number;
}
export interface SimilarCaseRow {
  crime_no: string;
  crime_head: string;
  crime_sub_head: string;
  district_name: string;
  crime_registered_date: string;
  shared_sections: number;
  same_sub_head: boolean;
}
export interface MoneyTrailRow {
  txn_id: number;
  from_account: string;
  from_name: string;
  from_uid: string;
  to_account: string;
  to_name: string;
  to_uid: string;
  amount: number;
  txn_date: string;
  txn_type: string;
  is_suspicious: boolean;
}
export interface SuspiciousTxnRow {
  txn_id: number;
  from_name: string;
  from_uid: string;
  to_name: string;
  to_uid: string;
  amount: number;
  txn_date: string;
  txn_type: string;
  is_suspicious: boolean;
}
export interface MuleAccountRow {
  account_no: string;
  holder_name: string;
  holder_person_uid: string;
  incoming_count: number;
  sources: number;
  total_in: number;
}
export interface SuspiciousFinancialResult {
  transactions: SuspiciousTxnRow[];
  muleAccounts: MuleAccountRow[];
}

const get = async <T>(path: string, params?: object): Promise<T> => {
  const { data } = await httpClient.get<T>(`/api/analytics/${path}`, { params });
  return data;
};

export const analyticsApi = {
  trends: (f: AnalyticsFilters) => get<TrendRow[]>('trends', f),
  hotspots: (f: AnalyticsFilters) => get<HotspotRow[]>('hotspots', f),
  districtSummary: (f: AnalyticsFilters) => get<DistrictSummaryRow[]>('district-summary', f),
  crimeHeads: () => get<LookupRow[]>('crime-heads'),
  districts: () => get<LookupRow[]>('districts'),
  earlyWarnings: () => get<EarlyWarningRow[]>('early-warnings'),
  riskScores: (limit?: number) => get<RiskScoreRow[]>('risk-scores', { limit }),
  network: (personUid?: string, limit?: number) =>
    get<NetworkGraph>('network', { personUid, limit }),
  forecast: (f: AnalyticsFilters & { horizon?: number }) =>
    get<Forecast>('forecast', f),
  forecastHotspots: (f: { crimeHeadId?: number; horizon?: number; limit?: number } = {}) =>
    get<HotspotForecastRow[]>('forecast/hotspots', f),
  seasonality: (f: AnalyticsFilters) => get<SeasonalityRow[]>('seasonality', f),
  demographics: (dimension: 'age' | 'gender', f: AnalyticsFilters) =>
    get<DemographicRow[]>('demographics', { dimension, ...f }),
  offenderGroups: (minShared?: number, maxGroups?: number) =>
    get<OffenderGroupsResult>('network/groups', { minShared, maxGroups }),
  similarCases: (crimeNo: string, limit?: number) =>
    get<SimilarCaseRow[]>(`similar-cases/${encodeURIComponent(crimeNo)}`, { limit }),
  moneyTrail: (personUid: string) =>
    get<MoneyTrailRow[]>('financial/money-trail', { personUid }),
  suspiciousFinancial: () =>
    get<SuspiciousFinancialResult>('financial/suspicious'),
  offenderProfile: (personUid: string) =>
    get<{
      crimeHeads: { crime_head: string; count: number }[];
      sections: { section: string; count: number }[];
      districts: { district: string; count: number }[];
    }>('offender-profile', { personUid }),
};
