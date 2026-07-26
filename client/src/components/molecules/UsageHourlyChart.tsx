import React, { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { UsageBreakdownRowDto } from '@interfaces/usage.interface';
import { formatTokens } from '@utils/usageDateRange';
import { useT } from '@constants/translations';
import * as styles from '@styles/usage.module.scss';

interface UsageHourlyChartProps {
  rows: UsageBreakdownRowDto[];
  loading?: boolean;
}

const hourLabel = (hourKey: string): string => `${hourKey.padStart(2, '0')}:00`;

/** 24-bar chart of usage by hour-of-day (UTC) — the service backfills all 24 hours with zeros. */
const UsageHourlyChart: React.FC<UsageHourlyChartProps> = ({ rows, loading }) => {
  const t = useT();
  const data = useMemo(
    () =>
      rows.map((r) => ({
        hour: hourLabel(r.key),
        totalTokens: r.totalTokens,
        requestCount: r.requestCount,
      })),
    [rows]
  );

  return (
    <div className={styles.chartCard}>
      <span className={styles.chartTitle}>{t('usageByHour')}</span>
      {!loading && data.length === 0 ? (
        <div className={styles.chartEmpty}>{t('usageNoUsageInPeriod')}</div>
      ) : (
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis
              dataKey="hour"
              tick={{ fontSize: 10, fill: 'var(--muted-foreground)' }}
              interval={1}
              stroke="var(--border)"
            />
            <YAxis
              tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
              allowDecimals={false}
              tickFormatter={formatTokens}
              stroke="var(--border)"
              width={44}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--popover)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                fontSize: 12,
                color: 'var(--popover-foreground)',
              }}
              formatter={(value: any, name: any) => [
                name === 'totalTokens' ? formatTokens(Number(value)) : value,
                name === 'totalTokens' ? t('usageTokens') : t('usageRequests'),
              ]}
            />
            <Bar dataKey="totalTokens" fill="var(--chart-2)" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
};

export default UsageHourlyChart;
