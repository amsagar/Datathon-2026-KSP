import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface CustomSpinnerProps {
  size?: 'small' | 'default' | 'large';
  tip?: string;
  children?: React.ReactNode;
  spinning?: boolean;
}

const sizePx: Record<NonNullable<CustomSpinnerProps['size']>, number> = {
  small: 14,
  default: 20,
  large: 28,
};

const CustomSpinner: React.FC<CustomSpinnerProps> = ({
  size = 'default',
  tip,
  children,
  spinning,
}) => {
  const indicator = (
    <span className="inline-flex flex-col items-center justify-center gap-2 text-muted-foreground">
      <Loader2 className="animate-spin text-primary" size={sizePx[size]} />
      {tip && <span className="text-sm">{tip}</span>}
    </span>
  );

  // Wrapper form: overlay the spinner on top of children while spinning.
  if (children) {
    const isSpinning = spinning ?? true;
    return (
      <div className="relative">
        <div
          className={cn(
            'transition-opacity',
            isSpinning && 'pointer-events-none select-none opacity-50 blur-[0.5px]',
          )}
        >
          {children}
        </div>
        {isSpinning && (
          <div className="absolute inset-0 flex items-center justify-center">
            {indicator}
          </div>
        )}
      </div>
    );
  }

  // Standalone spinner. Respect an explicit spinning={false}.
  if (spinning === false) return null;

  return (
    <div className="flex items-center justify-center p-2">{indicator}</div>
  );
};

export default CustomSpinner;
