import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Download } from 'lucide-react';
import CustomTabs from '@atoms/CustomTabs';
import CustomButton from '@atoms/CustomButton';
import dayjs, { type Dayjs } from 'dayjs';
import UsageDateFilter from '@molecules/UsageDateFilter';
import UsageSummaryCards from '@molecules/UsageSummaryCards';
import UsageBreakdownTable from '@molecules/UsageBreakdownTable';
import UsageTrendChart from '@molecules/UsageTrendChart';
import UsageModelShare from '@molecules/UsageModelShare';
import UsageSourceBreakdown from '@molecules/UsageSourceBreakdown';
import UsageHourlyChart from '@molecules/UsageHourlyChart';
import {
  fetchUsageByAssistant,
  fetchUsageByModel,
  fetchUsageBySource,
  fetchUsageByUser,
  fetchUsageHourly,
  fetchUsageSummary,
} from '@apiCalls/usage';
import { authApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type {
  UsageBreakdownRowDto,
  UsageSummaryResponse,
} from '@interfaces/usage.interface';
import {
  rangeForCustom,
  rangeForPreset,
  type UsageDatePreset,
} from '@utils/usageDateRange';
import { exportUsageCsv } from '@utils/usageCsvExport';
import { useT } from '@constants/translations';
import * as styles from '@styles/usage.module.scss';

const UsagePage: React.FC = () => {
  const t = useT();
  const openNotification = useNotification();
  const [preset, setPreset] = useState<UsageDatePreset>('7d');
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>([
    dayjs().subtract(6, 'day'),
    dayjs(),
  ]);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [byModel, setByModel] = useState<UsageBreakdownRowDto[]>([]);
  const [byUser, setByUser] = useState<UsageBreakdownRowDto[]>([]);
  const [byAssistant, setByAssistant] = useState<UsageBreakdownRowDto[]>([]);
  const [bySource, setBySource] = useState<UsageBreakdownRowDto[]>([]);
  const [hourly, setHourly] = useState<UsageBreakdownRowDto[]>([]);
  const [activeTab, setActiveTab] = useState('overview');

  const dateRange = useMemo(() => {
    if (preset === 'custom' && customRange) {
      return rangeForCustom(customRange[0], customRange[1]);
    }
    return rangeForPreset(preset);
  }, [preset, customRange]);

  const loadData = useCallback(async () => {
    if (preset === 'custom' && !customRange) return;
    setLoading(true);
    try {
      const { from, to } = dateRange;
      const [summaryRes, modelRes, userRes, assistantRes, sourceRes, hourlyRes] =
        await Promise.all([
          fetchUsageSummary(from, to),
          fetchUsageByModel(from, to),
          fetchUsageByUser(from, to),
          fetchUsageByAssistant(from, to),
          fetchUsageBySource(from, to),
          fetchUsageHourly(from, to),
        ]);
      setSummary(summaryRes);
      setByModel(modelRes);
      setByUser(userRes);
      setByAssistant(assistantRes);
      setBySource(sourceRes);
      setHourly(hourlyRes);
    } catch {
      openNotification('Failed to load usage', 'Error');
    } finally {
      setLoading(false);
    }
  }, [dateRange, preset, customRange, openNotification]);

  useEffect(() => {
    authApi.me().then((p) => setIsAdmin(p.admin)).catch(() => {});
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const userTabLabel = isAdmin ? t('usageByUser') : t('usageMyUsage');

  const handleExport = () => {
    exportUsageCsv({
      from: dateRange.from,
      to: dateRange.to,
      totals: summary?.totals ?? null,
      daily: summary?.daily ?? [],
      byModel,
      byUser,
      byAssistant,
      bySource,
      hourly,
    });
  };

  const tabItems = [
    {
      key: 'overview',
      label: t('usageOverview'),
      children: (
        <div className={styles.overviewStack}>
          <UsageSummaryCards
            totals={summary?.totals ?? null}
            previousTotals={summary?.previousTotals}
            loading={loading}
          />
          <UsageTrendChart rows={summary?.daily ?? []} loading={loading} />
          <div className={styles.chartsRow}>
            <UsageModelShare rows={byModel} loading={loading} />
            <UsageSourceBreakdown rows={bySource} loading={loading} />
          </div>
          <UsageHourlyChart rows={hourly} loading={loading} />
          <UsageBreakdownTable
            rows={summary?.daily ?? []}
            labelHeader={t('colDate')}
            loading={loading}
          />
        </div>
      ),
    },
    {
      key: 'model',
      label: t('usageByModel'),
      children: (
        <UsageBreakdownTable
          rows={byModel}
          labelHeader={t('model')}
          loading={loading}
        />
      ),
    },
    {
      key: 'assistant',
      label: t('usageByAssistant'),
      children: (
        <UsageBreakdownTable
          rows={byAssistant}
          labelHeader={t('assistant')}
          loading={loading}
        />
      ),
    },
    {
      key: 'source',
      label: t('usageBySource'),
      children: (
        <UsageBreakdownTable
          rows={bySource}
          labelHeader={t('usageColSource')}
          loading={loading}
        />
      ),
    },
    {
      key: 'user',
      label: userTabLabel,
      children: (
        <UsageBreakdownTable
          rows={byUser}
          labelHeader={isAdmin ? t('roleUser') : t('accountLabel')}
          loading={loading}
        />
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h1 className={styles.title}>{t('usageTitle')}</h1>
        <UsageDateFilter
          preset={preset}
          onPresetChange={setPreset}
          customRange={customRange}
          onCustomRangeChange={setCustomRange}
        />
        <CustomButton
          variant="secondary"
          size="small"
          className={styles.exportButton}
          onClick={handleExport}
          disabled={loading}
        >
          <Download className="size-4" aria-hidden />
          {t('usageExportCsv')}
        </CustomButton>
      </div>
      <CustomTabs
        className={styles.contentTabs}
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
      />
    </div>
  );
};

export default UsagePage;
