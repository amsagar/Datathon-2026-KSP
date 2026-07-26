import React, { useMemo } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { UsageBreakdownRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
import { useT } from '@constants/translations';
import * as styles from '@styles/usage.module.scss';

interface UsageModelShareProps {
  rows: UsageBreakdownRowDto[];
  loading?: boolean;
}

const COLORS = [
  'var(--chart-1)',
  'var(--chart-2)',
  'var(--chart-3)',
  'var(--chart-4)',
  'var(--chart-5)',
];

/** Donut chart of token share per model, with an "Est. cost" figure in the tooltip. */
const UsageModelShare: React.FC<UsageModelShareProps> = ({ rows, loading }) => {
  const t = useT();
  const data = useMemo(() => rows.filter((r) => r.totalTokens > 0), [rows]);

  return (
    <div className={styles.chartCard}>
      <span className={styles.chartTitle}>{t('usageModelShare')}</span>
      {!loading && data.length === 0 ? (
        <div className={styles.chartEmpty}>{t('usageNoUsageInPeriod')}</div>
      ) : (
        <>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie
                data={data}
                dataKey="totalTokens"
                nameKey="key"
                innerRadius={56}
                outerRadius={84}
                paddingAngle={2}
                strokeWidth={0}
              >
                {data.map((entry, i) => (
                  <Cell key={entry.key} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: 'var(--popover)',
                  border: '1px solid var(--border)',
                  borderRadius: 8,
                  fontSize: 12,
                  color: 'var(--popover-foreground)',
                }}
                formatter={(value: any, _name: any, entry: any) => [
                  `${formatTokens(Number(value))} ${t('usageTokens')} · ${formatCurrency(
                    (entry?.payload as UsageBreakdownRowDto | undefined)?.estimatedCostUsd ?? 0
                  )}`,
                  (entry?.payload as UsageBreakdownRowDto | undefined)?.key ?? '',
                ]}
              />
            </PieChart>
          </ResponsiveContainer>
          <div className={styles.pieLegend}>
            {data.map((row, i) => (
              <div key={row.key} className={styles.pieLegendRow}>
                <span
                  className={styles.pieLegendDot}
                  style={{ background: COLORS[i % COLORS.length] }}
                />
                <span className={styles.pieLegendLabel}>{row.key}</span>
                <span className={styles.pieLegendValue}>{formatTokens(row.totalTokens)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
};

export default UsageModelShare;
