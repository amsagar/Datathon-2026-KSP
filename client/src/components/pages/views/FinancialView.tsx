import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Banknote, Search } from 'lucide-react';
import dayjs from 'dayjs';
import {
  analyticsApi,
  MoneyTrailRow,
  SuspiciousFinancialResult,
} from '@apiCalls/analytics';
import CustomTable from '@atoms/CustomTable';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { useT } from '@constants/translations';
import * as styles from '@styles/analyticsLayout.module.scss';

const money = (v: number) =>
  '₹' + Number(v).toLocaleString('en-IN', { maximumFractionDigits: 0 });

const fmtDate = (value: string | null | undefined) => {
  if (!value) return '—';
  const d = dayjs(value);
  return d.isValid() ? d.format('D MMM YYYY') : String(value);
};

/** Financial-crime & transaction link analysis: suspicious flow, mule accounts, per-offender trail. */
const FinancialView: React.FC = () => {
  const t = useT();
  const [data, setData] = useState<SuspiciousFinancialResult>({
    transactions: [],
    muleAccounts: [],
  });
  const [loading, setLoading] = useState(true);
  const [uid, setUid] = useState('');
  const [trail, setTrail] = useState<MoneyTrailRow[]>([]);
  const [trailLoading, setTrailLoading] = useState(false);

  useEffect(() => {
    analyticsApi
      .suspiciousFinancial()
      .then(setData)
      .catch(() => setData({ transactions: [], muleAccounts: [] }))
      .finally(() => setLoading(false));
  }, []);

  const traceTrail = (personUid: string) => {
    if (!personUid.trim()) return;
    setTrailLoading(true);
    analyticsApi
      .moneyTrail(personUid.trim())
      .then(setTrail)
      .catch(() => setTrail([]))
      .finally(() => setTrailLoading(false));
  };

  const txnColumns = useMemo(
    () => [
      { title: t('colFrom'), dataIndex: 'from_name' as const },
      { title: t('colTo'), dataIndex: 'to_name' as const },
      {
        title: t('colAmount'),
        dataIndex: 'amount' as const,
        render: (v: number) => <span className="tabular-nums">{money(v)}</span>,
      },
      { title: t('colType'), dataIndex: 'txn_type' as const },
      {
        title: t('colDate'),
        dataIndex: 'txn_date' as const,
        render: (v: string) => fmtDate(v),
      },
      {
        title: t('colFlag'),
        dataIndex: 'is_suspicious' as const,
        render: (v: boolean) =>
          v ? (
            <Badge className="border-transparent bg-primary text-primary-foreground">
              {t('suspiciousBadge')}
            </Badge>
          ) : (
            <span className="text-muted-foreground">—</span>
          ),
      },
    ],
    [t]
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', minHeight: 0 }}
    >
      <div className={styles.toolbar}>
        <div className={styles.toolbarLead}>
          <Banknote className="size-4 text-primary" aria-hidden />
          <span className={styles.toolbarMeta}>
            {loading
              ? 'Loading…'
              : `${data.transactions.length} flagged/high-value transactions · ${data.muleAccounts.length} mule accounts`}
          </span>
        </div>
        <form
          className="flex items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            traceTrail(uid);
          }}
        >
          <CustomInput
            placeholder={t('traceMoneyTrailPlaceholder')}
            value={uid}
            allowClear
            onChange={(e) => setUid(e.target.value)}
          />
          <CustomButton variant="primary" htmlType="submit">
            <Search className="size-4" aria-hidden /> {t('trace')}
          </CustomButton>
        </form>
      </div>

      {trail.length > 0 && (
        <Card className="gap-0 py-0">
          <div className="border-b border-border px-4 py-3 text-sm font-semibold text-foreground">
            {t('moneyTrailFor')} {uid} — {trail.length} {t('transactionsWord')}
          </div>
          <div className="p-3">
            <CustomTable<MoneyTrailRow>
              size="small"
              rowKey="txn_id"
              loading={trailLoading}
              dataSource={trail}
              pagination={{ pageSize: 8 }}
              columns={txnColumns}
            />
          </div>
        </Card>
      )}

      <Card className="gap-0 py-0">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold text-foreground">
          {t('suspiciousTxns')}
        </div>
        <div className="p-3">
          <CustomTable
            size="small"
            rowKey="txn_id"
            loading={loading}
            dataSource={data.transactions}
            pagination={{ pageSize: 10 }}
            columns={txnColumns}
          />
        </div>
      </Card>

      <Card className="gap-0 py-0">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold text-foreground">
          {t('muleAccountsTitle')}
        </div>
        <div className="p-3">
          <CustomTable
            size="small"
            rowKey="account_no"
            loading={loading}
            dataSource={data.muleAccounts}
            pagination={{ pageSize: 8 }}
            columns={[
              { title: t('colAccountHolder'), dataIndex: 'holder_name' },
              { title: t('colAccount'), dataIndex: 'account_no' },
              { title: t('colDistinctSources'), dataIndex: 'sources' },
              { title: t('colIncomingTxns'), dataIndex: 'incoming_count' },
              {
                title: t('colTotalReceived'),
                dataIndex: 'total_in',
                render: (v: number) => <span className="tabular-nums">{money(v)}</span>,
              },
            ]}
          />
        </div>
      </Card>
    </motion.div>
  );
};

export default FinancialView;
