import React from 'react';
import CustomRangePicker from '@atoms/CustomRangePicker';
import type { Dayjs } from 'dayjs';
import type { UsageDatePreset } from '@utils/usageDateRange';
import { useT, type StringKey } from '@constants/translations';
import * as pageStyles from '@styles/usage.module.scss';
import * as modalStyles from '@styles/accountPreferencesModal.module.scss';

interface UsageDateFilterProps {
  preset: UsageDatePreset;
  onPresetChange: (preset: UsageDatePreset) => void;
  customRange: [Dayjs, Dayjs] | null;
  onCustomRangeChange: (range: [Dayjs, Dayjs] | null) => void;
  compact?: boolean;
}

const PRESETS: { key: UsageDatePreset; labelKey: StringKey }[] = [
  { key: 'today', labelKey: 'Today' },
  { key: '7d', labelKey: 'usage7Days' },
  { key: '30d', labelKey: 'usage30Days' },
  { key: 'custom', labelKey: 'usageCustom' },
];

const UsageDateFilter: React.FC<UsageDateFilterProps> = ({
  preset,
  onPresetChange,
  customRange,
  onCustomRangeChange,
  compact,
}) => {
  const t = useT();
  const styles = compact ? modalStyles : pageStyles;
  return (
  <div className={styles.filterBar}>
    <div className={styles.presetTabs} role="tablist" aria-label={t('usageDateRangeAria')}>
      {PRESETS.map((p) => (
        <button
          key={p.key}
          type="button"
          role="tab"
          aria-selected={preset === p.key}
          className={`${styles.presetTab} ${preset === p.key ? styles.presetTabActive : ''}`}
          onClick={() => onPresetChange(p.key)}
        >
          {t(p.labelKey)}
        </button>
      ))}
    </div>
    {preset === 'custom' && (
      <CustomRangePicker
        value={customRange}
        onChange={(dates) => {
          if (dates && dates[0] && dates[1]) {
            onCustomRangeChange([dates[0], dates[1]]);
          } else {
            onCustomRangeChange(null);
          }
        }}
        allowClear={false}
        className={styles.rangePicker}
      />
    )}
  </div>
  );
};

export default UsageDateFilter;
