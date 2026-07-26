import React, { useEffect, useState } from 'react';
import CustomButton from './CustomButton';
import { Checkbox } from '@/components/ui/checkbox';
import { cn } from '@/lib/utils';

export interface CustomFilterOption {
  label: string;
  value: string;
}

export interface CustomFilterDropdownProps {
  options: CustomFilterOption[];
  selectedKeys?: string[];
  setSelectedKeys?: (keys: string[]) => void;
  confirm?: () => void;
  clearFilters?: () => void;
}

/**
 * Custom replacement for the default column filterDropdown UI. Used by
 * CustomTable when a column has filters — a multi-select checkbox list plus
 * Apply / Reset actions. Rendered inside an already-open filter popup, so it
 * intentionally has no trigger of its own. Built on the shadcn Checkbox +
 * design tokens (no antd).
 */
const CustomFilter: React.FC<CustomFilterDropdownProps> = ({
  options,
  selectedKeys = [],
  setSelectedKeys,
  confirm,
  clearFilters,
}) => {
  const [local, setLocal] = useState<string[]>(selectedKeys);

  // Keep local state in sync when the popup is re-opened with new keys.
  useEffect(() => {
    setLocal(selectedKeys);
  }, [selectedKeys.join('|')]);

  const toggle = (value: string) => {
    setLocal((prev) =>
      prev.includes(value)
        ? prev.filter((v) => v !== value)
        : [...prev, value],
    );
  };

  const apply = () => {
    setSelectedKeys?.(local);
    confirm?.();
  };

  const reset = () => {
    setLocal([]);
    setSelectedKeys?.([]);
    clearFilters?.();
    confirm?.();
  };

  return (
    <div className="bg-popover text-popover-foreground min-w-[11rem] rounded-md border p-1 shadow-md">
      <div className="max-h-64 overflow-y-auto p-1">
        {options.length === 0 && (
          <div className="text-muted-foreground px-2 py-1.5 text-sm">
            No filter values
          </div>
        )}
        {options.map((opt) => (
          <label
            key={opt.value}
            className={cn(
              'hover:bg-accent hover:text-accent-foreground flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm select-none',
            )}
          >
            <Checkbox
              checked={local.includes(opt.value)}
              onCheckedChange={() => toggle(opt.value)}
            />
            <span>{opt.label}</span>
          </label>
        ))}
      </div>
      <div className="flex items-center justify-between gap-2 border-t px-2 py-1.5">
        <CustomButton variant="text" size="small" onClick={reset}>
          Reset
        </CustomButton>
        <CustomButton variant="primary" size="small" onClick={apply}>
          Apply
        </CustomButton>
      </div>
    </div>
  );
};

export default CustomFilter;
