import React from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export type CustomButtonVariant =
  | 'primary'
  | 'secondary'
  | 'ghost'
  | 'danger'
  | 'text';

/** antd-compatible size scale kept for consumers that still pass it. */
export type CustomButtonSize = 'small' | 'middle' | 'large';

/** antd-compatible shape kept for icon buttons. */
export type CustomButtonShape = 'default' | 'circle' | 'round';

export interface CustomButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  variant?: CustomButtonVariant;
  fullWidth?: boolean;
  /** Shows a spinner and disables the button while true. */
  loading?: boolean;
  /** Rendered before children. */
  icon?: React.ReactNode;
  /** antd's `htmlType` maps to the native button `type`. */
  htmlType?: 'button' | 'submit' | 'reset';
  size?: CustomButtonSize;
  /** antd's `shape`: 'circle' renders a round icon button. */
  shape?: CustomButtonShape;
}

const variantToShadcn: Record<
  CustomButtonVariant,
  React.ComponentProps<typeof Button>['variant']
> = {
  primary: 'default',
  secondary: 'secondary',
  ghost: 'ghost',
  danger: 'destructive',
  text: 'link',
};

const sizeToShadcn: Record<
  CustomButtonSize,
  React.ComponentProps<typeof Button>['size']
> = {
  small: 'sm',
  middle: 'default',
  large: 'lg',
};

const CustomButton = React.forwardRef(
  (
    {
      variant = 'secondary',
      fullWidth,
      loading = false,
      icon,
      htmlType = 'button',
      size = 'middle',
      shape = 'default',
      disabled,
      className,
      children,
      ...rest
    }: CustomButtonProps,
    ref: React.ForwardedRef<HTMLButtonElement>,
  ) => {
  const isCircle = shape === 'circle';
  return (
    <Button
      {...rest}
      ref={ref}
      type={htmlType}
      variant={variantToShadcn[variant] ?? 'secondary'}
      size={isCircle ? 'icon' : sizeToShadcn[size] ?? 'default'}
      disabled={disabled || loading}
      className={cn(
        fullWidth && 'w-full',
        isCircle && 'rounded-full',
        shape === 'round' && 'rounded-full',
        className,
      )}
    >
      {loading ? (
        <Loader2 className="animate-spin" aria-hidden />
      ) : (
        icon ?? null
      )}
      {children}
    </Button>
  );
  },
);

CustomButton.displayName = 'CustomButton';

export default CustomButton;
