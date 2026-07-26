import React from 'react';
import { X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

/** antd-compatible size scale kept for consumers that still pass it. */
export type CustomInputSize = 'small' | 'middle' | 'large';

export interface CustomInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'prefix' | 'size'> {
  fullWidth?: boolean;
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
  /** Shows a clear (X) affordance when there is a value and onChange is set. */
  allowClear?: boolean;
  size?: CustomInputSize;
}

const sizeClass: Record<CustomInputSize, string> = {
  small: 'h-8',
  middle: 'h-9',
  large: 'h-10',
};

const CustomInput = React.forwardRef<HTMLInputElement, CustomInputProps>(
  (
    {
      fullWidth = true,
      prefix,
      suffix,
      allowClear,
      size = 'middle',
      value,
      onChange,
      disabled,
      className,
      ...rest
    },
    forwardedRef,
  ) => {
    const innerRef = React.useRef<HTMLInputElement | null>(null);
    const setRefs = (node: HTMLInputElement | null) => {
      innerRef.current = node;
      if (typeof forwardedRef === 'function') forwardedRef(node);
      else if (forwardedRef)
        (forwardedRef as React.MutableRefObject<HTMLInputElement | null>).current =
          node;
    };

    const showClear =
      !!allowClear && !disabled && !!onChange && !!value && String(value).length > 0;

    const handleClear = () => {
      const node = innerRef.current;
      if (node && onChange) {
        // Use the native setter so React's synthetic onChange fires correctly.
        const setter = Object.getOwnPropertyDescriptor(
          window.HTMLInputElement.prototype,
          'value',
        )?.set;
        setter?.call(node, '');
        node.dispatchEvent(new Event('input', { bubbles: true }));
        node.focus();
      }
    };

    const input = (
      <Input
        {...rest}
        ref={setRefs}
        value={value}
        onChange={onChange}
        disabled={disabled}
        className={cn(
          sizeClass[size],
          fullWidth ? 'w-full' : 'w-auto',
          prefix && 'pl-9',
          (suffix || showClear) && 'pr-9',
          className,
        )}
      />
    );

    if (!prefix && !suffix && !showClear) {
      return input;
    }

    return (
      <div className={cn('relative inline-flex items-center', fullWidth && 'w-full')}>
        {prefix && (
          <span className="text-muted-foreground pointer-events-none absolute left-3 flex items-center">
            {prefix}
          </span>
        )}
        {input}
        {showClear ? (
          <button
            type="button"
            tabIndex={-1}
            aria-label="Clear"
            onClick={handleClear}
            className="text-muted-foreground hover:text-foreground absolute right-3 flex items-center"
          >
            <X className="size-4" />
          </button>
        ) : (
          suffix && (
            <span className="text-muted-foreground absolute right-3 flex items-center">
              {suffix}
            </span>
          )
        )}
      </div>
    );
  },
);

CustomInput.displayName = 'CustomInput';

export default CustomInput;
