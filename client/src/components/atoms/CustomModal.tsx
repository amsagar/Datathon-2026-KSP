import React from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { cn } from '@/lib/utils';

export type CustomModalWidth = 'sm' | 'md' | 'lg' | 'wide';

const widthMap: Record<CustomModalWidth, number> = {
  sm: 420,
  md: 560,
  lg: 720,
  wide: 960,
};

export interface CustomModalProps {
  open: boolean;
  title?: React.ReactNode;
  onClose: () => void;
  width?: CustomModalWidth | number;
  footer?: React.ReactNode;
  children: React.ReactNode;
  maskClosable?: boolean;
  destroyOnClose?: boolean;
}

const CustomModal: React.FC<CustomModalProps> = ({
  open,
  title,
  onClose,
  width = 'md',
  footer,
  children,
  maskClosable = true,
  // Radix already unmounts content when the dialog closes, matching the
  // AntD `destroyOnClose` default. Accepted for API compatibility.
  destroyOnClose = true,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
}) => {
  void destroyOnClose;
  const resolvedWidth = typeof width === 'number' ? width : widthMap[width];

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        className={cn('max-w-none gap-0 p-0')}
        style={{ width: '100%', maxWidth: resolvedWidth }}
        onInteractOutside={(e) => {
          if (!maskClosable) e.preventDefault();
        }}
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          {title ? (
            <DialogTitle className="text-base font-semibold text-foreground">
              {title}
            </DialogTitle>
          ) : (
            <DialogTitle className="sr-only">Dialog</DialogTitle>
          )}
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-2 border-t border-border px-6 py-4">
            {footer}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
};

export default CustomModal;
