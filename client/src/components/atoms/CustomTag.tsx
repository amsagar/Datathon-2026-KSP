import React from 'react';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

export type CustomTagTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

const toneToClass: Record<CustomTagTone, string> = {
  success:
    'border-transparent bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-400',
  warning:
    'border-transparent bg-accent/20 text-amber-900 dark:bg-accent/20 dark:text-amber-200',
  error: 'border-transparent bg-destructive text-white',
  info: 'border-transparent bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  neutral: 'border-transparent bg-muted text-foreground',
};

export interface CustomTagProps {
  tone?: CustomTagTone;
  children: React.ReactNode;
  className?: string;
}

const CustomTag: React.FC<CustomTagProps> = ({
  tone = 'neutral',
  children,
  className,
}) => (
  <Badge className={cn('font-medium', toneToClass[tone], className)}>
    {children}
  </Badge>
);

export default CustomTag;
