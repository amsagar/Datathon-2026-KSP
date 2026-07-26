import React from 'react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

export interface CustomDropdownItem {
  key: string;
  label: React.ReactNode;
  danger?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  icon?: React.ReactNode;
  /** Render a separator instead of a normal item. */
  type?: 'divider';
}

export interface CustomDropdownProps {
  items?: CustomDropdownItem[];
  overlay?: React.ReactNode;
  trigger?: ('click' | 'hover')[];
  placement?: 'bottomLeft' | 'bottomRight' | 'topLeft' | 'topRight';
  children: React.ReactElement;
  disabled?: boolean;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

type Side = 'top' | 'bottom';
type Align = 'start' | 'end';

const placementMap: Record<
  NonNullable<CustomDropdownProps['placement']>,
  { side: Side; align: Align }
> = {
  bottomLeft: { side: 'bottom', align: 'start' },
  bottomRight: { side: 'bottom', align: 'end' },
  topLeft: { side: 'top', align: 'start' },
  topRight: { side: 'top', align: 'end' },
};

const CustomDropdown: React.FC<CustomDropdownProps> = ({
  items,
  overlay,
  // `trigger` is accepted for API compatibility. Radix dropdown-menu is
  // click-activated; hover triggering is not supported by the primitive.
  trigger,
  placement = 'bottomRight',
  children,
  disabled,
  open,
  onOpenChange,
}) => {
  void trigger;
  const { side, align } = placementMap[placement];

  return (
    <DropdownMenu open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger asChild disabled={disabled}>
        {children}
      </DropdownMenuTrigger>
      <DropdownMenuContent
        side={side}
        align={align}
        // Custom overlays (e.g. AccountMenu) bring their own chrome — avoid a
        // second border/padding shell from the Radix content wrapper.
        className={
          overlay
            ? 'border-0 bg-transparent p-0 shadow-none'
            : undefined
        }
      >
        {overlay
          ? overlay
          : (items ?? []).map((it) =>
              it.type === 'divider' ? (
                <DropdownMenuSeparator key={it.key} />
              ) : (
                <DropdownMenuItem
                  key={it.key}
                  disabled={it.disabled}
                  variant={it.danger ? 'destructive' : 'default'}
                  onClick={it.onClick}
                >
                  {it.icon}
                  {it.label}
                </DropdownMenuItem>
              )
            )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default CustomDropdown;
