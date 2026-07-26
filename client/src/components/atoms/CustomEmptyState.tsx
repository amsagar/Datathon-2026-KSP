import React from 'react';
import { motion } from 'motion/react';
import { Inbox } from 'lucide-react';

export interface CustomEmptyStateProps {
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  icon?: React.ReactNode;
}

const CustomEmptyState: React.FC<CustomEmptyStateProps> = ({
  title = 'Nothing here yet',
  description,
  action,
  icon,
}) => (
  <motion.div
    initial={{ opacity: 0, y: 4 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.25, ease: 'easeOut' }}
    className="flex flex-col items-center justify-center gap-2 px-6 py-10 text-center"
  >
    <span className="text-muted-foreground/60">
      {icon ?? <Inbox size={40} strokeWidth={1.5} />}
    </span>
    {title && (
      <div className="text-sm font-medium text-foreground">{title}</div>
    )}
    {description && (
      <div className="max-w-sm text-sm text-muted-foreground">{description}</div>
    )}
    {action && <div className="mt-2">{action}</div>}
  </motion.div>
);

export default CustomEmptyState;
