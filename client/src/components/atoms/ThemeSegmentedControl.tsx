import React from 'react';
import { Monitor, Sun, Moon } from 'lucide-react';
import type { ThemePreference } from '@store/useThemeStore';
import * as styles from '@styles/themeSegmented.module.scss';

interface ThemeSegmentedControlProps {
  value: ThemePreference;
  onChange: (value: ThemePreference) => void;
}

const OPTIONS: { value: ThemePreference; icon: React.ReactNode; label: string }[] = [
  { value: 'system', icon: <Monitor className="size-4" />, label: 'System theme' },
  { value: 'light', icon: <Sun className="size-4" />, label: 'Light theme' },
  { value: 'dark', icon: <Moon className="size-4" />, label: 'Dark theme' },
];

const ThemeSegmentedControl: React.FC<ThemeSegmentedControlProps> = ({
  value,
  onChange,
}) => (
  <div className={styles.control} role="group" aria-label="Appearance">
    {OPTIONS.map((opt) => (
      <button
        key={opt.value}
        type="button"
        className={`${styles.segment} ${value === opt.value ? styles.segmentActive : ''}`}
        onClick={() => onChange(opt.value)}
        aria-label={opt.label}
        aria-pressed={value === opt.value}
      >
        {opt.icon}
      </button>
    ))}
  </div>
);

export default ThemeSegmentedControl;
