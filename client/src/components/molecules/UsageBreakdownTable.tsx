import React, { useMemo } from 'react';
import CustomTable, { type CustomColumn } from '@atoms/CustomTable';
import type { UsageBreakdownRowDto, UsageDailyRowDto } from '@interfaces/usage.interface';
import { formatCurrency, formatTokens } from '@utils/usageDateRange';
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
  emptyText = 'No usage in this period',
  compact,
}) => {
  const styles = compact ? modalStyles : pageStyles;
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
      title: 'Requests',
      dataIndex: 'requestCount',
      key: 'requestCount',
      width: 100,
      align: 'right',
    },
    {
      title: 'Total tokens',
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
      title: 'Prompt',
      dataIndex: 'promptTokens',
      key: 'promptTokens',
      width: 90,
      align: 'right',
      render: (n: number) => formatTokens(n),
    },
    {
      title: 'Completion',
      dataIndex: 'completionTokens',
      key: 'completionTokens',
      width: 100,
      align: 'right',
      render: (n: number) => formatTokens(n),
    },
    {
      title: 'Est. cost',
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
      locale={{ emptyText }}
      size="middle"
    />
  );
};

export default UsageBreakdownTable;
