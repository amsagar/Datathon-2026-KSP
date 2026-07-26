import React from 'react';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

export interface CustomBadgeProps {
  count?: number;
  dot?: boolean;
  status?: 'success' | 'processing' | 'default' | 'error' | 'warning';
  children?: React.ReactNode;
}

const statusColor: Record<
  NonNullable<CustomBadgeProps['status']>,
  string
> = {
  success: 'bg-green-500',
  processing: 'bg-primary',
  default: 'bg-muted-foreground',
  error: 'bg-destructive',
  warning: 'bg-accent',
};

const CustomBadge: React.FC<CustomBadgeProps> = ({
  count,
  dot,
  status,
  children,
}) => {
  // Standalone status badge (antd: <Badge status="..." />)
  if (status && children == null && !dot && count == null) {
    return (
      <span className="inline-flex items-center gap-1.5">
        <span className={cn('inline-block size-2 rounded-full', statusColor[status])} />
      </span>
    );
  }

  // Wrapper form: overlay a corner dot or count on top of children.
  if (children != null) {
    const showDot = dot || (status != null && count == null);
    const showCount = !showDot && count != null && count > 0;

    return (
      <span className="relative inline-flex">
        {children}
        {showDot && (
          <span
            className={cn(
              'absolute -top-0.5 -right-0.5 size-2 rounded-full ring-2 ring-background',
              status ? statusColor[status] : 'bg-destructive',
            )}
          />
        )}
        {showCount && (
          <Badge
            variant="destructive"
            className="absolute -top-2 -right-2 h-5 min-w-5 rounded-full px-1 text-[10px] tabular-nums"
          >
            {count}
          </Badge>
        )}
      </span>
    );
  }

  // Standalone dot / count.
  if (dot) {
    return <span className="inline-block size-2 rounded-full bg-destructive" />;
  }
  if (count != null) {
    return (
      <Badge
        variant="destructive"
        className="h-5 min-w-5 rounded-full px-1 text-[10px] tabular-nums"
      >
        {count}
      </Badge>
    );
  }
  return null;
};

export default CustomBadge;
