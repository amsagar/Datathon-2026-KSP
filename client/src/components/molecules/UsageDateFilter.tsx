import React from 'react';
import CustomRangePicker from '@atoms/CustomRangePicker';
import type { Dayjs } from 'dayjs';
import type { UsageDatePreset } from '@utils/usageDateRange';
import * as pageStyles from '@styles/usage.module.scss';
import * as modalStyles from '@styles/accountPreferencesModal.module.scss';

interface UsageDateFilterProps {
  preset: UsageDatePreset;
  onPresetChange: (preset: UsageDatePreset) => void;
  customRange: [Dayjs, Dayjs] | null;
  onCustomRangeChange: (range: [Dayjs, Dayjs] | null) => void;
  compact?: boolean;
}

const PRESETS: { key: UsageDatePreset; label: string }[] = [
  { key: 'today', label: 'Today' },
  { key: '7d', label: '7 days' },
  { key: '30d', label: '30 days' },
  { key: 'custom', label: 'Custom' },
];

const UsageDateFilter: React.FC<UsageDateFilterProps> = ({
  preset,
  onPresetChange,
  customRange,
  onCustomRangeChange,
  compact,
}) => {
  const styles = compact ? modalStyles : pageStyles;
  return (
  <div className={styles.filterBar}>
    <div className={styles.presetTabs} role="tablist" aria-label="Date range">
      {PRESETS.map((p) => (
        <button
          key={p.key}
          type="button"
          role="tab"
          aria-selected={preset === p.key}
          className={`${styles.presetTab} ${preset === p.key ? styles.presetTabActive : ''}`}
          onClick={() => onPresetChange(p.key)}
        >
          {p.label}
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
