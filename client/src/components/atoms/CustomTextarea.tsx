import React from 'react';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

/** antd's autoSize: boolean or { minRows, maxRows }. */
export type CustomTextareaAutoSize =
  | boolean
  | { minRows?: number; maxRows?: number };

/** antd's input variant. 'borderless' strips the border/background (used by the composer). */
export type CustomTextareaVariant = 'outlined' | 'borderless' | 'filled';

export interface CustomTextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  fullWidth?: boolean;
  autoSize?: CustomTextareaAutoSize;
  variant?: CustomTextareaVariant;
  /** Hard max height in px (ChatGPT-style). When set, overrides maxRows math. */
  maxHeightPx?: number;
}

const LINE_PX = 24;
const MIN_PX = 24;

const CustomTextarea = React.forwardRef<
  HTMLTextAreaElement,
  CustomTextareaProps
>(
  (
    {
      fullWidth = true,
      autoSize,
      variant = 'outlined',
      value,
      onChange,
      className,
      style,
      maxHeightPx,
      ...rest
    },
    forwardedRef,
  ) => {
    const innerRef = React.useRef<HTMLTextAreaElement | null>(null);
    const setRefs = (node: HTMLTextAreaElement | null) => {
      innerRef.current = node;
      if (typeof forwardedRef === 'function') forwardedRef(node);
      else if (forwardedRef)
        (
          forwardedRef as React.MutableRefObject<HTMLTextAreaElement | null>
        ).current = node;
    };

    const enabled = autoSize === true || typeof autoSize === 'object';
    const minRows =
      typeof autoSize === 'object' ? autoSize.minRows : undefined;
    const maxRows =
      typeof autoSize === 'object' ? autoSize.maxRows : undefined;

    const resize = React.useCallback(() => {
      const node = innerRef.current;
      if (!node || !enabled) return;

      const maxPx =
        maxHeightPx ?? (maxRows ? maxRows * LINE_PX : Number.POSITIVE_INFINITY);

      // Collapse to measure natural content height
      node.style.height = 'auto';
      const content = node.scrollHeight;
      const next = Math.min(Math.max(content, MIN_PX), maxPx);
      node.style.height = `${next}px`;
      // Past the cap → scroll inside (like ChatGPT)
      node.style.overflowY = content > maxPx ? 'auto' : 'hidden';
    }, [enabled, maxRows, maxHeightPx]);

    React.useLayoutEffect(() => {
      resize();
    }, [resize, value]);

    // Recalc on font/layout changes
    React.useEffect(() => {
      if (!enabled) return;
      const node = innerRef.current;
      if (!node || typeof ResizeObserver === 'undefined') return;
      const ro = new ResizeObserver(() => resize());
      ro.observe(node);
      return () => ro.disconnect();
    }, [enabled, resize]);

    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange?.(e);
      // Resize after React commits the new value on next frame
      requestAnimationFrame(() => resize());
    };

    const autoStyle: React.CSSProperties = enabled
      ? {
          minHeight: MIN_PX,
          maxHeight: maxHeightPx ?? (maxRows ? maxRows * LINE_PX : undefined),
          resize: 'none',
          overflowY: 'hidden',
          ...style,
        }
      : style ?? {};

    return (
      <Textarea
        {...rest}
        ref={setRefs}
        value={value}
        onChange={handleChange}
        rows={minRows ?? 1}
        style={autoStyle}
        className={cn(
          fullWidth ? 'w-full' : 'w-auto',
          '!min-h-0 !py-0 !px-0',
          variant === 'borderless' &&
            'border-0 bg-transparent shadow-none focus-visible:ring-0 dark:bg-transparent',
          variant === 'filled' && 'bg-muted',
          className,
        )}
      />
    );
  },
);

CustomTextarea.displayName = 'CustomTextarea';

export default CustomTextarea;
