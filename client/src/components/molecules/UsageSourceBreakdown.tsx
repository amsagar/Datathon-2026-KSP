import React, { useMemo } from 'react';
import type { UsageBreakdownRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
import * as styles from '@styles/usage.module.scss';

interface UsageSourceBreakdownProps {
  rows: UsageBreakdownRowDto[];
  loading?: boolean;
}

// Keyed off `LlmUsageSource` (service/src/main/java/com/ksp/agent/chat/usage/LlmUsageSource.java)
// — every LLM call the backend records is tagged with exactly one of these five values.
const SOURCE_LABELS: Record<string, string> = {
  main: 'Chat',
  title: 'Title generation',
  summary: 'Summarization',
  scope_guard: 'Scope guard',
  consolidation: 'Memory consolidation',
};

/** Horizontal bar list of usage by source, reusing UsageBreakdownTable's inline-bar styling. */
const UsageSourceBreakdown: React.FC<UsageSourceBreakdownProps> = ({ rows, loading }) => {
  const maxTokens = useMemo(() => Math.max(1, ...rows.map((r) => r.totalTokens)), [rows]);

  return (
    <div className={styles.chartCard}>
      <span className={styles.chartTitle}>By source</span>
      {!loading && rows.length === 0 ? (
        <div className={styles.chartEmpty}>No usage in this period</div>
      ) : (
        <div className={styles.sourceList}>
          {rows.map((row) => (
            <div key={row.key} className={styles.sourceRow}>
              <span className={styles.sourceLabel}>{SOURCE_LABELS[row.key] ?? row.key}</span>
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
