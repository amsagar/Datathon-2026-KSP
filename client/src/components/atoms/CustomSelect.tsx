import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Search, X } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { cn } from '@/lib/utils';
import { useT } from '@constants/translations';

export interface CustomSelectOption<V = string> {
  label: React.ReactNode;
  value: V;
  disabled?: boolean;
}

/** antd-compatible size scale kept for consumers that still pass it. */
export type CustomSelectSize = 'small' | 'middle' | 'large';

export interface CustomSelectProps<V = string> {
  options: CustomSelectOption<V>[];
  value?: V;
  defaultValue?: V;
  onChange?: (value: V) => void;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  size?: CustomSelectSize;
  fullWidth?: boolean;
  className?: string;
  id?: string;
  name?: string;
  /** Show a search field in the menu (default true). */
  showSearch?: boolean;
  searchPlaceholder?: string;
}

const optionSearchText = (label: React.ReactNode, value: string): string => {
  if (typeof label === 'string' || typeof label === 'number') {
    return `${label} ${value}`.toLowerCase();
  }
  if (React.isValidElement(label)) {
    const kids = (label.props as { children?: React.ReactNode }).children;
    if (typeof kids === 'string' || typeof kids === 'number') {
      return `${kids} ${value}`.toLowerCase();
    }
  }
  return value.toLowerCase();
};

function CustomSelect<V extends string | number = string>({
  options,
  value,
  defaultValue,
  onChange,
  placeholder,
  disabled,
  allowClear,
  size = 'middle',
  fullWidth = true,
  className,
  id,
  name,
  showSearch = true,
  searchPlaceholder,
}: CustomSelectProps<V>) {
  const t = useT();
  const resolvedSearchPlaceholder = searchPlaceholder ?? t('search');
  const listId = useId();
  const searchRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [uncontrolled, setUncontrolled] = useState<V | undefined>(defaultValue);

  const isControlled = value !== undefined;
  const selected = isControlled ? value : uncontrolled;

  const selectedOption = useMemo(
    () => options.find((o) => String(o.value) === String(selected)),
    [options, selected],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!showSearch || !q) return options;
    return options.filter((opt) =>
      optionSearchText(opt.label, String(opt.value)).includes(q),
    );
  }, [options, query, showSearch]);

  const showClear =
    !!allowClear &&
    !disabled &&
    selected != null &&
    String(selected) !== '' &&
    String(selected) !== 'all';

  useEffect(() => {
    if (!open) {
      setQuery('');
      return;
    }
    if (!showSearch) return;
    const t = window.setTimeout(() => searchRef.current?.focus(), 0);
    return () => window.clearTimeout(t);
  }, [open, showSearch]);

  const commit = (next: V) => {
    if (!isControlled) setUncontrolled(next);
    onChange?.(next);
    setOpen(false);
  };

  return (
    <div
      className={cn(
        'relative',
        fullWidth ? 'w-full' : 'inline-flex min-w-[10rem]',
        className,
      )}
    >
      {name != null && (
        <input type="hidden" name={name} value={selected == null ? '' : String(selected)} />
      )}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            id={id}
            disabled={disabled}
            aria-haspopup="listbox"
            aria-expanded={open}
            aria-controls={listId}
            data-slot="select-trigger"
            data-size={size === 'small' ? 'sm' : 'default'}
            className={cn(
              'border-input bg-card text-foreground flex w-full items-center justify-between gap-1.5 rounded-lg border px-3 py-2 text-sm shadow-sm outline-none transition-[color,box-shadow,border-color]',
              'hover:border-primary/35 focus-visible:border-primary focus-visible:ring-[3px] focus-visible:ring-primary/20',
              'disabled:cursor-not-allowed disabled:opacity-50',
              size === 'small' ? 'h-8' : 'h-9',
              !selectedOption && 'text-muted-foreground',
            )}
          >
            <span className="line-clamp-1 min-w-0 flex-1 text-left">
              {selectedOption ? selectedOption.label : (placeholder ?? 'Select…')}
            </span>
            <span className="flex shrink-0 items-center gap-0.5">
              {showClear && (
                <span
                  role="button"
                  tabIndex={-1}
                  aria-label="Clear"
                  className="inline-flex size-5 items-center justify-center rounded-sm text-muted-foreground hover:bg-muted hover:text-foreground"
                  onPointerDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                  }}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    commit(undefined as unknown as V);
                  }}
                >
                  <X className="size-3.5" />
                </span>
              )}
              <ChevronDown className="size-4 opacity-50" aria-hidden />
            </span>
          </button>
        </PopoverTrigger>
        <PopoverContent
          align="start"
          className="w-[var(--radix-popover-trigger-width)] min-w-[12rem] p-0"
          onOpenAutoFocus={(e) => {
            if (showSearch) {
              e.preventDefault();
              requestAnimationFrame(() => searchRef.current?.focus());
            }
          }}
        >
          {showSearch && (
            <div className="border-b border-border p-2">
              <div className="relative">
                <Search
                  className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground"
                  aria-hidden
                />
                <input
                  ref={searchRef}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={resolvedSearchPlaceholder}
                  aria-label={resolvedSearchPlaceholder}
                  autoComplete="off"
                  className="border-input bg-background text-foreground placeholder:text-muted-foreground h-8 w-full rounded-md border py-1 pr-2 pl-8 text-sm outline-none focus-visible:border-primary focus-visible:ring-[3px] focus-visible:ring-primary/20"
                  onKeyDown={(e) => {
                    if (e.key === 'Escape') {
                      e.preventDefault();
                      setOpen(false);
                    }
                    if (e.key === 'Enter' && filtered.length === 1 && !filtered[0].disabled) {
                      e.preventDefault();
                      commit(filtered[0].value);
                    }
                  }}
                />
              </div>
            </div>
          )}
          <ul
            id={listId}
            role="listbox"
            className="max-h-64 overflow-y-auto p-1"
          >
            {filtered.length === 0 ? (
              <li className="text-muted-foreground px-2 py-3 text-center text-sm">
                No matches
              </li>
            ) : (
              filtered.map((opt) => {
                const isSelected = String(opt.value) === String(selected);
                return (
                  <li key={String(opt.value)} role="option" aria-selected={isSelected}>
                    <button
                      type="button"
                      disabled={opt.disabled}
                      className={cn(
                        'relative flex w-full cursor-default items-center gap-2 rounded-md py-2 pr-8 pl-2.5 text-left text-sm outline-none select-none',
                        'hover:bg-accent hover:text-accent-foreground focus-visible:bg-accent focus-visible:text-accent-foreground',
                        'disabled:pointer-events-none disabled:opacity-50',
                        isSelected && 'bg-primary/10 text-foreground',
                      )}
                      onClick={() => commit(opt.value)}
                    >
                      <span className="flex-1 truncate">{opt.label}</span>
                      {isSelected && (
                        <Check className="absolute right-2 size-4 text-primary" aria-hidden />
                      )}
                    </button>
                  </li>
                );
              })
            )}
          </ul>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default CustomSelect;
