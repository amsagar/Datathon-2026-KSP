import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { AlertTriangle, Building2, FileText, ShieldAlert, TrendingUp } from 'lucide-react';
import dayjs, { Dayjs } from 'dayjs';
import {
  analyticsApi,
  DemographicRow,
  DistrictSummaryRow,
  EarlyWarningRow,
  Forecast,
  HotspotForecastRow,
  LookupRow,
  TrendRow,
} from '@apiCalls/analytics';
import CrimeTrendLine from '@src/uiTemplates/CrimeTrendLine';
import CrimeBarChart from '@src/uiTemplates/CrimeBarChart';
import CustomRangePicker from '@atoms/CustomRangePicker';
import CustomSelect from '@atoms/CustomSelect';
import CustomTable from '@atoms/CustomTable';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@constants/translations';
import { useLangStore } from '@store/useLangStore';
import { districtLabel } from '@utils/districtLabel';
import { cn } from '@/lib/utils';
import * as styles from '@styles/analyticsLayout.module.scss';

const DEFAULT_RANGE: [Dayjs, Dayjs] = [dayjs('2023-01-01'), dayjs('2026-06-30')];

const CrimeDashboardView: React.FC = () => {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(DEFAULT_RANGE);
  const [districtId, setDistrictId] = useState<number | undefined>();
  const [crimeHeadId, setCrimeHeadId] = useState<number | undefined>();
  const [districts, setDistricts] = useState<LookupRow[]>([]);
  const [crimeHeads, setCrimeHeads] = useState<LookupRow[]>([]);
  const [trends, setTrends] = useState<TrendRow[]>([]);
  const [summary, setSummary] = useState<DistrictSummaryRow[]>([]);
  const [warnings, setWarnings] = useState<EarlyWarningRow[]>([]);
  const [forecast, setForecast] = useState<Forecast | null>(null);
  const [hotspotForecasts, setHotspotForecasts] = useState<HotspotForecastRow[]>([]);
  const [demographics, setDemographics] = useState<DemographicRow[]>([]);
  const [loading, setLoading] = useState(false);
  const t = useT();
  const lang = useLangStore((s) => s.lang);

  useEffect(() => {
    analyticsApi.districts().then(setDistricts).catch(() => undefined);
    analyticsApi.crimeHeads().then(setCrimeHeads).catch(() => undefined);
    analyticsApi.earlyWarnings().then(setWarnings).catch(() => undefined);
  }, []);

  useEffect(() => {
    const filters = {
      from: range[0].format('YYYY-MM-DD'),
      to: range[1].format('YYYY-MM-DD'),
      districtId,
      crimeHeadId,
    };
    setLoading(true);
    Promise.all([analyticsApi.trends(filters), analyticsApi.districtSummary(filters)])
      .then(([trendRows, summaryRows]) => {
        setTrends(trendRows);
        setSummary(summaryRows);
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
    analyticsApi
      .forecast({ ...filters, horizon: 6 })
      .then(setForecast)
      .catch(() => setForecast(null));
    analyticsApi
      .demographics('age', filters)
      .then(setDemographics)
      .catch(() => setDemographics([]));
    analyticsApi
      .forecastHotspots({ crimeHeadId, horizon: 3, limit: 8 })
      .then(setHotspotForecasts)
      .catch(() => setHotspotForecasts([]));
  }, [range, districtId, crimeHeadId]);

  const demographicsData = useMemo(() => {
    // Present in a natural age-band order rather than by count.
    const order = ['<18', '18-24', '25-34', '35-44', '45-59', '60+', 'Unknown'];
    const sorted = [...demographics].sort(
      (a, b) => order.indexOf(a.bucket) - order.indexOf(b.bucket)
    );
    return {
      categories: sorted.map((r) => r.bucket),
      series: [{ name: 'Accused', values: sorted.map((r) => Number(r.count)) }],
    };
  }, [demographics]);

  const forecastData = useMemo(() => {
    if (!forecast || !forecast.history.length) return { series: [] as unknown[] };
    const actual = forecast.history.map((p) => ({ x: p.period, y: p.value }));
    // Bridge the forecast line to the last actual point so the two series meet visually.
    const bridge = actual.length ? [actual[actual.length - 1]] : [];
    const projected = [
      ...bridge,
      ...forecast.forecast.map((p) => ({ x: p.period, y: p.value })),
    ];
    return {
      series: [
        { name: 'Actual', points: actual },
        { name: `Forecast (${forecast.method})`, points: projected },
      ],
    };
  }, [forecast]);

  const trendData = useMemo(() => {
    const byHead = new Map<string, { x: string; y: number }[]>();
    trends.forEach((r) => {
      if (!byHead.has(r.crime_head)) byHead.set(r.crime_head, []);
      byHead.get(r.crime_head)!.push({ x: r.period, y: Number(r.count) });
    });
    const series = Array.from(byHead.entries())
      .map(([name, points]) => ({ name, points, total: points.reduce((s, p) => s + p.y, 0) }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 6);
    return { series };
  }, [trends]);

  const totals = useMemo(
    () => ({
      cases: summary.reduce((s, r) => s + Number(r.total), 0),
      heinous: summary.reduce((s, r) => s + Number(r.heinous), 0),
      districts: summary.length,
    }),
    [summary]
  );

  const kpis = [
    {
      label: t('totalCases'),
      value: totals.cases.toLocaleString(),
      icon: FileText,
      border: 'border-l-primary',
      iconColor: 'text-primary',
    },
    {
      label: t('heinousCases'),
      value: totals.heinous.toLocaleString(),
      icon: ShieldAlert,
      border: 'border-l-accent',
      iconColor: 'text-accent',
    },
    {
      label: t('districtsReporting'),
      value: String(totals.districts),
      icon: Building2,
      border: 'border-l-muted-foreground',
      iconColor: 'text-muted-foreground',
    },
  ];

  return (
    <>
      <div className={styles.toolbar}>
        <div className={styles.toolbarFilters}>
          <CustomRangePicker
            className="w-auto min-w-[240px]"
            value={range}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
            allowClear={false}
          />
          <CustomSelect<number>
            className="min-w-[180px]"
            fullWidth={false}
            allowClear
            placeholder={t('allDistricts')}
            value={districtId}
            onChange={setDistrictId}
            options={districts
              .filter((d) => d.district_id != null)
              .map((d) => ({
                value: d.district_id as number,
                label: districtLabel(d, lang),
              }))}
          />
          <CustomSelect<number>
            className="min-w-[180px]"
            fullWidth={false}
            allowClear
            placeholder={t('allCrimeHeads')}
            value={crimeHeadId}
            onChange={setCrimeHeadId}
            options={crimeHeads
              .filter((c) => c.crime_head_id != null)
              .map((c) => ({ value: c.crime_head_id as number, label: c.crime_group_name }))}
          />
        </div>
      </div>

      <div className={styles.scrollBody}>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {kpis.map((kpi, i) => (
            <motion.div
              key={kpi.label}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.06, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            >
              <Card className={cn('gap-0 border-l-4 py-0', kpi.border)}>
                <div className="flex items-center justify-between px-5 pt-4">
                  <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    {kpi.label}
                  </span>
                  <kpi.icon className={cn('size-4', kpi.iconColor)} aria-hidden />
                </div>
                <div className="px-5 pb-4 pt-1.5">
                  {loading ? (
                    <Skeleton className="h-8 w-24" />
                  ) : (
                    <span className="text-3xl font-bold tracking-tight tabular-nums text-foreground">
                      {kpi.value}
                    </span>
                  )}
                </div>
              </Card>
            </motion.div>
          ))}
        </div>

        {warnings.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.18, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            className="mt-4 rounded-xl border border-accent/40 bg-accent/10 p-4 text-foreground"
          >
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <AlertTriangle className="size-4 shrink-0 text-accent" aria-hidden />
              {t('earlyWarnings')} — {t('lookHereFirst')}
            </div>
            <ul className="mt-3 grid gap-2">
              {warnings.slice(0, 5).map((w, i) => (
                <li
                  key={i}
                  className="grid grid-cols-1 gap-x-3 gap-y-1 rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground sm:grid-cols-[minmax(120px,180px)_1fr]"
                >
                  <span className="font-semibold text-foreground">
                    {districtLabel(w, lang)}
                  </span>
                  <span className="text-foreground">
                    <strong className="text-foreground">{w.crime_head}</strong>
                    <span className="text-muted-foreground">
                      {' '}
                      — {w.recent_count} {t('inLast90Days')} ({w.spike_ratio}
                      {t('timesUsual')}
                      {w.baseline_per_quarter})
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          </motion.div>
        )}

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.24, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
          className="mt-4"
        >
          <Card>
            <CardHeader className="pb-0">
              <CardTitle className="flex items-center gap-2 text-base">
                <TrendingUp className="size-4 text-primary" aria-hidden />
                {t('monthlyCaseVolume')}
              </CardTitle>
              <CardDescription>{t('topCrimeHeadsRange')}</CardDescription>
            </CardHeader>
            <CardContent>
              {trendData.series.length ? (
                <CrimeTrendLine data={{ ...trendData }} />
              ) : (
                <div className="flex h-40 items-center justify-center text-sm text-muted-foreground">
                  {loading ? t('loadingTrends') : t('noTrendData')}
                </div>
              )}
            </CardContent>
          </Card>
        </motion.div>

        {forecastData.series.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.27, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            className="mt-4"
          >
            <Card>
              <CardHeader className="pb-0">
                <CardTitle className="flex items-center gap-2 text-base">
                  <TrendingUp className="size-4 text-primary" aria-hidden />
                  {t('forecastTitle')}
                </CardTitle>
                <CardDescription>
                  {t('forecastDesc')} ({forecast?.method})
                </CardDescription>
              </CardHeader>
              <CardContent>
                <CrimeTrendLine data={{ ...forecastData }} />
              </CardContent>
            </Card>
          </motion.div>
        )}

        {hotspotForecasts.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.28, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            className="mt-4"
          >
            <Card>
              <CardHeader className="pb-0">
                <CardTitle className="flex items-center gap-2 text-base">
                  <TrendingUp className="size-4 text-primary" aria-hidden />
                  {t('predictedHotspots')}
                </CardTitle>
                <CardDescription>{t('predictedHotspotsDesc')}</CardDescription>
              </CardHeader>
              <CardContent>
                <CustomTable<HotspotForecastRow>
                  size="middle"
                  rowKey="districtId"
                  dataSource={hotspotForecasts}
                  pagination={false}
                  columns={[
                    {
                      title: t('district'),
                      dataIndex: 'districtName',
                      key: 'districtName',
                      render: (name: string, row: HotspotForecastRow) => {
                        const match = districts.find((d) => d.district_id === row.districtId);
                        return match ? districtLabel(match, lang) : name;
                      },
                    },
                    {
                      title: t('recentTotal'),
                      dataIndex: 'baselineTotal',
                      key: 'baselineTotal',
                      render: (v: number) => Number(v).toLocaleString(),
                    },
                    {
                      title: t('predictedTotal'),
                      dataIndex: 'forecastTotal',
                      key: 'forecastTotal',
                      render: (v: number) => Number(v).toLocaleString(),
                    },
                    {
                      title: t('changeRatio'),
                      dataIndex: 'ratio',
                      key: 'ratio',
                      render: (v: number) => `${v >= 1 ? '+' : ''}${Math.round((v - 1) * 100)}%`,
                    },
                    {
                      title: t('forecastMape'),
                      dataIndex: 'backtestMape',
                      key: 'backtestMape',
                      render: (v: number | null) => (v == null ? '—' : `${v}%`),
                    },
                  ]}
                />
              </CardContent>
            </Card>
          </motion.div>
        )}

        {demographicsData.categories.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.29, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            className="mt-4"
          >
            <Card>
              <CardHeader className="pb-0">
                <CardTitle className="text-base">{t('accusedByAge')}</CardTitle>
                <CardDescription>{t('demographicsDesc')}</CardDescription>
              </CardHeader>
              <CardContent>
                <CrimeBarChart data={{ ...demographicsData }} />
              </CardContent>
            </Card>
          </motion.div>
        )}

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
          className="mt-4"
        >
          <Card>
            <CardHeader className="pb-0">
              <CardTitle className="text-base">{t('districtSummary')}</CardTitle>
            </CardHeader>
            <CardContent>
              <CustomTable<DistrictSummaryRow>
                size="middle"
                rowKey="district_id"
                loading={loading}
                dataSource={summary}
                pagination={{ pageSize: 12 }}
                columns={[
                  {
                    title: t('district'),
                    dataIndex: 'district_name',
                    key: 'district_name',
                    render: (_: string, row: DistrictSummaryRow) => districtLabel(row, lang),
                  },
                  {
                    title: t('totalCases'),
                    dataIndex: 'total',
                    key: 'total',
                    render: (v: number) => Number(v).toLocaleString(),
                  },
                  {
                    title: t('heinous'),
                    dataIndex: 'heinous',
                    key: 'heinous',
                    render: (v: number) => Number(v).toLocaleString(),
                  },
                  { title: t('stations'), dataIndex: 'stations', key: 'stations' },
                ]}
              />
            </CardContent>
          </Card>
        </motion.div>

        <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
          Source: FIR database — live aggregates from case_master.
        </p>
      </div>
    </>
  );
};

export default CrimeDashboardView;
