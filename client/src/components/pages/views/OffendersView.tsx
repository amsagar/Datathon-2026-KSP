import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import dayjs from 'dayjs';
import { analyticsApi, RiskScoreRow } from '@apiCalls/analytics';
import { analyticsChatPath } from '@constants/routePaths';
import CustomTable from '@src/components/atoms/CustomTable';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { StringKey, useT } from '@constants/translations';
import * as styles from '@styles/analyticsLayout.module.scss';

type RiskTier = { label: StringKey; className: string };

const riskTier = (score: number): RiskTier => {
  if (score >= 200) return { label: 'tierHigh', className: 'bg-primary text-primary-foreground border-transparent' };
  if (score >= 100)
    return { label: 'tierElevated', className: 'bg-accent text-accent-foreground border-transparent' };
  return { label: 'tierWatch', className: 'bg-muted text-muted-foreground border-transparent' };
};

const formatDate = (value: string | null | undefined) => {
  if (!value) return '—';
  const d = dayjs(value);
  return d.isValid() ? d.format('D MMM YYYY') : String(value);
};

const OffendersView: React.FC = () => {
  const t = useT();
  const [rows, setRows] = useState<RiskScoreRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    analyticsApi
      .riskScores(100)
      .then(setRows)
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, []);

  // Preserve the previous default view (highest risk first).
  const sortedRows = useMemo(
    () => [...rows].sort((a, b) => b.risk_score - a.risk_score),
    [rows]
  );

  return (
    <>
      <div className={styles.toolbar}>
        <div className={styles.toolbarLead}>
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <Badge className="bg-primary text-primary-foreground border-transparent">≥ 200</Badge>
              {t('tierHigh')}
            </span>
            <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <Badge className="bg-accent text-accent-foreground border-transparent">100–199</Badge>
              {t('tierElevated')}
            </span>
            <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <Badge className="bg-muted text-muted-foreground border-transparent">&lt; 100</Badge>
              {t('tierWatch')}
            </span>
          </div>
        </div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        className={styles.grow}
        style={{ marginBottom: 0 }}
      >
        <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden py-0">
          <div className="flex flex-shrink-0 items-center gap-2 border-b border-border px-4 py-3">
            <ShieldAlert className="size-4 text-primary" aria-hidden />
            <span className="text-sm font-semibold text-foreground">{t('offenderRiskRanking')}</span>
            {!loading && (
              <span className="ml-auto text-xs font-medium text-muted-foreground tabular-nums">
                {sortedRows.length.toLocaleString()} {t('peopleSuffix')}
              </span>
            )}
          </div>

          <div className="min-h-0 flex-1 overflow-auto px-2">
            <CustomTable
              size="middle"
              rowKey="person_uid"
              loading={loading}
              dataSource={sortedRows}
              pagination={{ pageSize: 25 }}
              locale={{ emptyText: t('noOffenders') }}
              columns={[
                {
                  title: t('colRiskScore'),
                  dataIndex: 'risk_score',
                  width: 160,
                  render: (v: number) => {
                    const tier = riskTier(v);
                    return (
                      <span className="flex items-center gap-2">
                        <Badge className={cn('tabular-nums', tier.className)}>
                          {Number(v).toFixed(1)}
                        </Badge>
                        <span className="text-xs text-muted-foreground">{t(tier.label)}</span>
                      </span>
                    );
                  },
                },
                { title: t('colName'), dataIndex: 'accused_name' },
                {
                  title: t('colOffenderId'),
                  dataIndex: 'person_uid',
                  width: 140,
                  render: (uid: string) => (
                    <Link
                      className={styles.offenderLink}
                      to={analyticsChatPath('network', { personUid: uid })}
                      title="Open this person’s criminal network"
                    >
                      {uid}
                    </Link>
                  ),
                },
                { title: t('colCases'), dataIndex: 'case_count', width: 90 },
                { title: t('heinous'), dataIndex: 'heinous_count', width: 100 },
                { title: t('colChargesheeted'), dataIndex: 'chargesheeted_count', width: 130 },
                {
                  title: t('colLastCase'),
                  dataIndex: 'last_case_date',
                  width: 130,
                  render: (v: string) => formatDate(v),
                },
              ]}
            />
          </div>

          <div className="flex-shrink-0 border-t border-border bg-muted/30 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
            Score = 10/case + 15/heinous + 20 if active last year, scaled by chargesheet rate. Click an
            ID to open their network in chat.
          </div>
        </Card>
      </motion.div>
    </>
  );
};

export default OffendersView;
