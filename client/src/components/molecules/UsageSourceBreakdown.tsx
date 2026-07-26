import React, { useMemo } from 'react';
import type { UsageBreakdownRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
import { useT, type StringKey } from '@constants/translations';
import * as styles from '@styles/usage.module.scss';

interface UsageSourceBreakdownProps {
  rows: UsageBreakdownRowDto[];
  loading?: boolean;
}

// Keyed off `LlmUsageSource` — localize display labels only.
const SOURCE_LABEL_KEYS: Record<string, StringKey> = {
  main: 'chat',
  title: 'sourceTitleGeneration',
  summary: 'sourceSummarization',
  scope_guard: 'sourceScopeGuard',
  consolidation: 'sourceMemoryConsolidation',
};

/** Horizontal bar list of usage by source, reusing UsageBreakdownTable's inline-bar styling. */
const UsageSourceBreakdown: React.FC<UsageSourceBreakdownProps> = ({ rows, loading }) => {
  const t = useT();
  const maxTokens = useMemo(() => Math.max(1, ...rows.map((r) => r.totalTokens)), [rows]);

  return (
    <div className={styles.chartCard}>
      <span className={styles.chartTitle}>{t('usageBySource')}</span>
      {!loading && rows.length === 0 ? (
        <div className={styles.chartEmpty}>{t('usageNoUsageInPeriod')}</div>
      ) : (
        <div className={styles.sourceList}>
          {rows.map((row) => (
            <div key={row.key} className={styles.sourceRow}>
              <span className={styles.sourceLabel}>
                {SOURCE_LABEL_KEYS[row.key]
                  ? t(SOURCE_LABEL_KEYS[row.key])
                  : row.key}
              </span>
              <div className={styles.barCell}>
                <div
                  className={styles.barFill}
                  style={{ width: `${Math.round((row.totalTokens / maxTokens) * 100)}%` }}
                />
                <span className={styles.barValue}>
                  {formatTokens(row.totalTokens)} · {formatCurrency(row.estimatedCostUsd)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default UsageSourceBreakdown;
