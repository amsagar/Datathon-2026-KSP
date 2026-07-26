import React from 'react';
import type { UsageTotalsDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens, percentDelta } from '@utils/usageDateRange';
import { Skeleton } from '@/components/ui/skeleton';
import { useT, type StringKey } from '@constants/translations';
import * as pageStyles from '@styles/usage.module.scss';
import * as modalStyles from '@styles/accountPreferencesModal.module.scss';

interface UsageSummaryCardsProps {
  totals: UsageTotalsDto | null;
  /** Prior period's totals (same length window immediately before the current one), for %Δ badges. */
  previousTotals?: UsageTotalsDto | null;
  loading?: boolean;
  compact?: boolean;
}

const CARDS: {
  key: keyof UsageTotalsDto;
  labelKey: StringKey;
  format?: 'tokens' | 'currency';
}[] = [
  { key: 'requestCount', labelKey: 'usageRequests' },
  { key: 'promptTokens', labelKey: 'usagePromptTokens', format: 'tokens' },
  { key: 'completionTokens', labelKey: 'usageCompletionTokens', format: 'tokens' },
  { key: 'totalTokens', labelKey: 'usageTotalTokens', format: 'tokens' },
  { key: 'estimatedCostUsd', labelKey: 'usageEstCost', format: 'currency' },
];

const DeltaBadge: React.FC<{
  delta: number | null;
  styles: typeof pageStyles;
  vsPrior: string;
}> = ({ delta, styles, vsPrior }) => {
  if (delta === null) return null;
  const rounded = Math.round(delta * 10) / 10;
  const direction = rounded > 0 ? 'up' : rounded < 0 ? 'down' : 'flat';
  const sign = rounded > 0 ? '+' : '';
  const cls =
    direction === 'up'
      ? styles.deltaUp
      : direction === 'down'
        ? styles.deltaDown
        : styles.deltaFlat;
  return (
    <span className={`${styles.deltaBadge} ${cls}`}>
      {sign}
      {rounded}
      {vsPrior}
    </span>
  );
};

const UsageSummaryCards: React.FC<UsageSummaryCardsProps> = ({
  totals,
  previousTotals,
  loading,
  compact,
}) => {
  const t = useT();
  const styles = compact ? modalStyles : pageStyles;
  return (
    <div className={styles.summaryGrid} aria-busy={loading || undefined}>
      {CARDS.map((card) => {
        const raw = totals?.[card.key] ?? 0;
        const value =
          card.format === 'tokens'
            ? formatTokens(raw)
            : card.format === 'currency'
              ? formatCurrency(raw)
              : String(raw);
        const delta = previousTotals
          ? percentDelta(raw, previousTotals[card.key] ?? 0)
          : null;
        return (
          <div key={card.key} className={styles.summaryCard}>
            <span className={styles.summaryLabel}>{t(card.labelKey)}</span>
            {loading ? (
              <Skeleton className="mt-1 h-7 w-20" />
            ) : (
              <>
                <span className={styles.summaryValue}>{value}</span>
                <DeltaBadge
                  delta={delta}
                  styles={styles}
                  vsPrior={t('usageVsPrior')}
                />
              </>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default UsageSummaryCards;
