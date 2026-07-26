import React, { useEffect, useState } from 'react';
import dayjs, { Dayjs } from 'dayjs';
import type { DateRange } from 'react-day-picker';
import { Calendar as CalendarIcon, X } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Calendar } from '@/components/ui/calendar';
import { cn } from '@/lib/utils';

export type CustomRangeValue = [Dayjs, Dayjs] | null;

export interface CustomRangePickerProps {
  value?: CustomRangeValue;
  onChange?: (dates: CustomRangeValue) => void;
  /** dayjs display format for the selected dates. */
  format?: string;
  /** Return true to disable a given day. */
  disabledDate?: (date: Dayjs) => boolean;
  placeholder?: [string, string] | string;
  allowClear?: boolean;
  disabled?: boolean;
  className?: string;
}

const toRange = (value?: CustomRangeValue): DateRange | undefined => {
  if (!value || !value[0] || !value[1]) return undefined;
  return { from: value[0].toDate(), to: value[1].toDate() };
};

const CustomRangePicker: React.FC<CustomRangePickerProps> = ({
  value,
  onChange,
  format = 'YYYY-MM-DD',
  disabledDate,
  placeholder = ['Start date', 'End date'],
  allowClear = true,
  disabled,
  className,
}) => {
  const [open, setOpen] = useState(false);
  const [range, setRange] = useState<DateRange | undefined>(toRange(value));

  useEffect(() => {
    setRange(toRange(value));
  }, [value]);

  const [startPlaceholder, endPlaceholder] = Array.isArray(placeholder)
    ? placeholder
    : [placeholder, placeholder];

  const label = range?.from ? (
    <span className="text-foreground">
      {dayjs(range.from).format(format)}
      {' – '}
      {range.to ? dayjs(range.to).format(format) : endPlaceholder}
    </span>
  ) : (
    <span className="text-muted-foreground">
      {startPlaceholder} – {endPlaceholder}
    </span>
  );

  const handleSelect = (next: DateRange | undefined) => {
    setRange(next);
    if (next?.from && next?.to) {
      onChange?.([dayjs(next.from), dayjs(next.to)]);
      setOpen(false);
    }
  };

  const clear = (e: React.MouseEvent) => {
    e.stopPropagation();
    setRange(undefined);
    onChange?.(null);
  };

  return (
    <Popover open={open} onOpenChange={disabled ? undefined : setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          disabled={disabled}
          className={cn(
            'flex h-9 w-full items-center gap-2 rounded-lg border border-input bg-card px-3 py-1 text-sm shadow-sm transition-[color,box-shadow,border-color] outline-none',
            'hover:border-primary/35 focus-visible:border-primary focus-visible:ring-[3px] focus-visible:ring-primary/20',
            'disabled:cursor-not-allowed disabled:opacity-50',
            className,
          )}
        >
          <CalendarIcon
            className="size-4 shrink-0 text-primary/80"
            aria-hidden
          />
          <span className="flex-1 truncate text-left">{label}</span>
          {allowClear && range?.from && !disabled && (
            <span
              role="button"
              tabIndex={-1}
              aria-label="Clear"
              onClick={clear}
              className="inline-flex text-muted-foreground hover:text-foreground"
            >
              <X className="size-4" aria-hidden />
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-auto overflow-hidden p-3"
        align="start"
        sideOffset={8}
      >
        <Calendar
          mode="range"
          numberOfMonths={2}
          defaultMonth={range?.from}
          selected={range}
          onSelect={handleSelect}
          disabled={
            disabledDate ? (date: Date) => disabledDate(dayjs(date)) : undefined
          }
          autoFocus
        />
      </PopoverContent>
    </Popover>
  );
};

export default CustomRangePicker;
