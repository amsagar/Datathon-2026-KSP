import dayjs, { type Dayjs } from 'dayjs';

export type UsageDatePreset = 'today' | '7d' | '30d' | 'custom';

export interface UsageDateRange {
  from: string;
  to: string;
}

export const rangeForPreset = (preset: UsageDatePreset): UsageDateRange => {
  const end = dayjs().endOf('day');
  if (preset === 'today') {
    return {
      from: dayjs().startOf('day').toISOString(),
      to: end.toISOString(),
    };
  }
  if (preset === '7d') {
    return {
      from: dayjs().subtract(6, 'day').startOf('day').toISOString(),
      to: end.toISOString(),
    };
  }
  if (preset === '30d') {
    return {
      from: dayjs().subtract(29, 'day').startOf('day').toISOString(),
      to: end.toISOString(),
    };
  }
  return {
    from: dayjs().subtract(6, 'day').startOf('day').toISOString(),
    to: end.toISOString(),
  };
};

export const rangeForCustom = (start: Dayjs, end: Dayjs): UsageDateRange => ({
  from: start.startOf('day').toISOString(),
  to: end.endOf('day').toISOString(),
});

export const formatTokens = (n: number): string => {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
};

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const smallCurrencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

/** Formats an estimated-cost figure as USD, with extra precision below $1 (e.g. sub-cent rates). */
export const formatCurrency = (n: number): string => {
  const amount = n ?? 0;
  return amount > 0 && amount < 1
    ? smallCurrencyFormatter.format(amount)
    : currencyFormatter.format(amount);
};

/** %Δ vs a prior-period value, e.g. `+12.4%` / `-3.0%`. Returns null when there's no baseline. */
export const percentDelta = (current: number, previous: number): number | null => {
  if (!previous) return current > 0 ? null : 0;
  return ((current - previous) / previous) * 100;
};
