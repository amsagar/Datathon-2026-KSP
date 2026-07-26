import React, { useMemo, useState } from 'react';
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { UsageDailyRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
import { useT } from '@constants/translations';
import * as styles from '@styles/usage.module.scss';

interface UsageTrendChartProps {
  rows: UsageDailyRowDto[];
  loading?: boolean;
}

type Metric = 'tokens' | 'cost';

/** Dual-axis daily trend: an area for tokens-or-spend (toggle) plus a request-count line. */
const UsageTrendChart: React.FC<UsageTrendChartProps> = ({ rows, loading }) => {
  const t = useT();
  const [metric, setMetric] = useState<Metric>('tokens');

  const chartData = useMemo(
    () =>
      rows.map((r) => ({
        day: r.day,
        metricValue: metric === 'tokens' ? r.totalTokens : r.estimatedCostUsd,
        requestCount: r.requestCount,
      })),
    [rows, metric]
  );

  const metricLabel =
    metric === 'tokens' ? t('usageTokens') : t('usageEstimatedCost');
  const metricFormat = metric === 'tokens' ? formatTokens : formatCurrency;

  return (
    <div className={styles.chartCard}>
      <div className={styles.chartHeader}>
        <span className={styles.chartTitle}>{t('usageTrend')}</span>
        <div
          className={styles.metricToggle}
          role="tablist"
          aria-label={t('usageTrendMetricAria')}
        >
          <button
            type="button"
            role="tab"
            aria-selected={metric === 'tokens'}
            className={`${styles.toggleBtn} ${metric === 'tokens' ? styles.toggleBtnActive : ''}`}
            onClick={() => setMetric('tokens')}
          >
            {t('usageTokens')}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={metric === 'cost'}
            className={`${styles.toggleBtn} ${metric === 'cost' ? styles.toggleBtnActive : ''}`}
            onClick={() => setMetric('cost')}
          >
            {t('usageCost')}
          </button>
        </div>
      </div>
      {!loading && chartData.length === 0 ? (
        <div className={styles.chartEmpty}>{t('usageNoUsageInPeriod')}</div>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={chartData} margin={{ top: 8, right: 24, bottom: 8, left: 4 }}>
            <defs>
              <linearGradient id="usageTrendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--chart-1)" stopOpacity={0.35} />
                <stop offset="95%" stopColor="var(--chart-1)" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis
              dataKey="day"
              tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
              stroke="var(--border)"
              minTickGap={24}
            />
            <YAxis
              yAxisId="metric"
              tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
              stroke="var(--border)"
              width={56}
              tickFormatter={metricFormat}
            />
            <YAxis
              yAxisId="requests"
              orientation="right"
              tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
              stroke="var(--border)"
              width={40}
              allowDecimals={false}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--card)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
                fontSize: 12,
                color: 'var(--card-foreground)',
              }}
              labelStyle={{ color: 'var(--muted-foreground)', marginBottom: 4, fontWeight: 600 }}
              formatter={(value: any, name: any) =>
                name === 'requestCount'
                  ? [value, t('usageRequests')]
                  : [metricFormat(Number(value)), metricLabel]
              }
            />
            <Area
              yAxisId="metric"
              type="monotone"
              dataKey="metricValue"
              name={metricLabel}
              stroke="var(--chart-1)"
              strokeWidth={2.5}
              fill="url(#usageTrendFill)"
            />
            <Line
              yAxisId="requests"
              type="monotone"
              dataKey="requestCount"
              name={t('usageRequests')}
              stroke="var(--chart-3)"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4 }}
            />
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </div>
  );
};

export default UsageTrendChart;
