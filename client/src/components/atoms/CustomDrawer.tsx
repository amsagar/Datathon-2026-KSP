import React, { useCallback, useEffect, useState } from 'react';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import { cn } from '@/lib/utils';

export interface CustomDrawerProps {
  open: boolean;
  title?: React.ReactNode;
  onClose: () => void;
  width?: number;
  minWidth?: number;
  maxWidth?: number;
  resizable?: boolean;
  placement?: 'left' | 'right';
  destroyOnClose?: boolean;
  footer?: React.ReactNode;
  children: React.ReactNode;
}

const CustomDrawer: React.FC<CustomDrawerProps> = ({
  open,
  title,
  onClose,
  width: initialWidth = 640,
  minWidth = 400,
  maxWidth = 1200,
  resizable = true,
  placement = 'right',
  destroyOnClose = true,
  footer,
  children,
}) => {
  void destroyOnClose;
  const [width, setWidth] = useState(initialWidth);
  const [isResizing, setIsResizing] = useState(false);

  const handleMouseDown = (e: React.MouseEvent) => {
    if (!resizable) return;
    setIsResizing(true);
    e.preventDefault();
  };

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isResizing) return;
      const newWidth =
        placement === 'right' ? window.innerWidth - e.clientX : e.clientX;
      if (newWidth >= minWidth && newWidth <= maxWidth) {
        setWidth(newWidth);
      }
    },
    [isResizing, minWidth, maxWidth, placement]
  );

  const handleMouseUp = useCallback(() => {
    setIsResizing(false);
  }, []);

  useEffect(() => {
    if (isResizing) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    }
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing, handleMouseMove, handleMouseUp]);

  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <SheetContent
        side={placement}
        className="w-full gap-0 p-0 sm:max-w-none"
        style={{ width, maxWidth: '100vw' }}
      >
        {resizable && (
          <div
            onMouseDown={handleMouseDown}
            className={cn(
              'absolute inset-y-0 z-10 w-1 cursor-col-resize bg-transparent transition-colors hover:bg-primary/40',
              placement === 'right' ? 'left-0' : 'right-0',
              isResizing && 'bg-primary/60'
            )}
          />
        )}
        <div className="flex h-full flex-col">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            {title ? (
              <SheetTitle className="text-base font-semibold text-foreground">
                {title}
              </SheetTitle>
            ) : (
              <SheetTitle className="sr-only">Drawer</SheetTitle>
            )}
          </div>
          <div className="flex-1 overflow-auto">{children}</div>
          {footer && (
            <div className="flex items-center justify-end gap-2 border-t border-border px-5 py-4">
              {footer}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
};

export default CustomDrawer;
