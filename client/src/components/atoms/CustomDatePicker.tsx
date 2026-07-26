import React, { useEffect, useState } from 'react';
import dayjs from 'dayjs';
import { Calendar as CalendarIcon, X } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Calendar } from '@/components/ui/calendar';
import { cn } from '@/lib/utils';

export interface CustomDatePickerProps {
  /** ISO date string `YYYY-MM-DD`, or empty. */
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  allowClear?: boolean;
  disabled?: boolean;
  className?: string;
  id?: string;
  /** Maximum selectable date (inclusive), ISO `YYYY-MM-DD`. */
  max?: string;
  /** Minimum selectable date (inclusive), ISO `YYYY-MM-DD`. */
  min?: string;
}

/**
 * Single-date picker built on the shared DayPicker calendar — use this
 * instead of native `type="date"` so light/dark theming stays consistent.
 */
const CustomDatePicker: React.FC<CustomDatePickerProps> = ({
  value,
  onChange,
  placeholder = 'Select date',
  allowClear = true,
  disabled,
  className,
  id,
  max,
  min,
}) => {
  const [open, setOpen] = useState(false);
  const selected = value ? dayjs(value) : null;
  const selectedDate =
    selected && selected.isValid() ? selected.toDate() : undefined;
  const [month, setMonth] = useState<Date | undefined>(selectedDate);

  useEffect(() => {
    if (selectedDate) setMonth(selectedDate);
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps -- sync month when value string changes

  const label = selectedDate ? (
    <span className="text-foreground">
      {dayjs(selectedDate).format('DD/MM/YYYY')}
    </span>
  ) : (
    <span className="text-muted-foreground">{placeholder}</span>
  );

  const clear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange?.('');
  };

  return (
    <Popover open={open} onOpenChange={disabled ? undefined : setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          id={id}
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
          {allowClear && selectedDate && !disabled && (
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
          mode="single"
          month={month}
          onMonthChange={setMonth}
          selected={selectedDate}
          onSelect={(date) => {
            onChange?.(date ? dayjs(date).format('YYYY-MM-DD') : '');
            if (date) setOpen(false);
          }}
          disabled={(date) => {
            if (max && dayjs(date).isAfter(dayjs(max), 'day')) return true;
            if (min && dayjs(date).isBefore(dayjs(min), 'day')) return true;
            return false;
          }}
          autoFocus
        />
      </PopoverContent>
    </Popover>
  );
};

export default CustomDatePicker;
