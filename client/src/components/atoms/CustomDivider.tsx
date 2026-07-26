import React from 'react';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

export interface CustomDividerProps {
  children?: React.ReactNode;
  orientation?: 'left' | 'right' | 'center';
  dashed?: boolean;
  margin?: number;
}

const CustomDivider: React.FC<CustomDividerProps> = ({
  children,
  orientation = 'left',
  dashed,
  margin,
}) => {
  const style: React.CSSProperties | undefined =
    margin != null ? { marginTop: margin, marginBottom: margin } : undefined;

  const lineClass = dashed
    ? 'flex-1 border-t border-dashed border-border bg-transparent h-0'
    : undefined;

  const line = (grow?: string) =>
    dashed ? (
      <span className={cn(lineClass, grow)} />
    ) : (
      <Separator className={cn('flex-1', grow)} />
    );

  // Plain divider (no label).
  if (children == null) {
    return (
      <div style={style} role="separator">
        {line()}
      </div>
    );
  }

  // Labelled divider: line(s) around centered/left/right text.
  const leftGrow = orientation === 'left' ? 'max-w-[24px]' : undefined;
  const rightGrow = orientation === 'right' ? 'max-w-[24px]' : undefined;

  return (
    <div
      className="flex items-center gap-3 text-sm font-medium text-muted-foreground"
      style={style}
      role="separator"
    >
      {line(leftGrow)}
      <span className="whitespace-nowrap">{children}</span>
      {line(rightGrow)}
    </div>
  );
};

export default CustomDivider;
