import React, { useMemo } from 'react';
import CustomTable, { type CustomColumn } from '@atoms/CustomTable';
import type { UsageBreakdownRowDto, UsageDailyRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
import { useT } from '@constants/translations';
import * as pageStyles from '@styles/usage.module.scss';
import * as modalStyles from '@styles/accountPreferencesModal.module.scss';

type Row = UsageBreakdownRowDto | UsageDailyRowDto;

interface UsageBreakdownTableProps {
  rows: Row[];
  labelHeader: string;
  loading?: boolean;
  emptyText?: string;
  compact?: boolean;
}

const rowLabel = (r: Row): string => ('day' in r ? r.day : r.key);

const UsageBreakdownTable: React.FC<UsageBreakdownTableProps> = ({
  rows,
  labelHeader,
  loading,
  emptyText,
  compact,
}) => {
  const t = useT();
  const styles = compact ? modalStyles : pageStyles;
  const resolvedEmpty = emptyText ?? t('usageNoUsageInPeriod');
  const maxTokens = useMemo(
    () => Math.max(1, ...rows.map((r) => r.totalTokens)),
    [rows]
  );

  const columns: CustomColumn<Row>[] = [
    {
      title: labelHeader,
      key: 'label',
      render: (_, record) => (
        <span className={styles.rowLabel}>{rowLabel(record)}</span>
      ),
    },
    {
      title: t('usageRequests'),
      dataIndex: 'requestCount',
      key: 'requestCount',
      width: 100,
      align: 'right',
    },
    {
      title: t('usageTotalTokens'),
      dataIndex: 'totalTokens',
      key: 'totalTokens',
      width: 200,
      render: (tokens: number) => (
        <div className={styles.barCell}>
          <div
            className={styles.barFill}
            style={{ width: `${Math.round((tokens / maxTokens) * 100)}%` }}
          />
          <span className={styles.barValue}>{formatTokens(tokens)}</span>
        </div>
      ),
    },
    {
      title: t('usageColPrompt'),
      dataIndex: 'promptTokens',
      key: 'promptTokens',
      width: 90,
      align: 'right',
      render: (n: number) => formatTokens(n),
    },
    {
      title: t('usageColCompletion'),
      dataIndex: 'completionTokens',
      key: 'completionTokens',
      width: 100,
      align: 'right',
      render: (n: number) => formatTokens(n),
    },
    {
      title: t('usageEstCost'),
      dataIndex: 'estimatedCostUsd',
      key: 'estimatedCostUsd',
      width: 100,
      align: 'right',
      render: (n: number) => formatCurrency(n ?? 0),
    },
  ];

  return (
    <CustomTable<Row>
      className={styles.table}
      columns={columns}
      dataSource={rows.map((r, i) => ({
        ...r,
        key: rowLabel(r) || String(i),
      }))}
      loading={loading}
      pagination={rows.length > 12 ? { pageSize: 12, size: 'small' } : false}
      locale={{ emptyText: resolvedEmpty }}
      size="middle"
    />
  );
};

export default UsageBreakdownTable;
