import React from 'react';
import { Switch } from '@/components/ui/switch';
import { cn } from '@/lib/utils';

export interface CustomSwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  defaultChecked?: boolean;
  disabled?: boolean;
  /** antd-compatible size; 'small' renders a slightly more compact control. */
  size?: 'small' | 'default';
  ariaLabel?: string;
  id?: string;
  name?: string;
  className?: string;
}

const CustomSwitch: React.FC<CustomSwitchProps> = ({
  checked,
  onChange,
  defaultChecked,
  disabled,
  size = 'default',
  ariaLabel,
  id,
  name,
  className,
}) => (
  <Switch
    checked={checked}
    defaultChecked={defaultChecked}
    onCheckedChange={onChange}
    disabled={disabled}
    aria-label={ariaLabel}
    id={id}
    name={name}
    className={cn(size === 'small' && 'h-4 w-7', className)}
  />
);

export default CustomSwitch;
