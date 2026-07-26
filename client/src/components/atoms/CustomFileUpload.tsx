import React, { useRef, useState } from 'react';
import { UploadCloud, X } from 'lucide-react';
import CustomButton from './CustomButton';
import { cn } from '@/lib/utils';

export interface CustomFileUploadProps {
  accept?: string;
  buttonLabel?: string;
  dropLabel?: React.ReactNode;
  value?: File | null;
  onChange?: (file: File | null) => void;
  disabled?: boolean;
  /** Tighter padding for side panels / profile headers. */
  compact?: boolean;
}

const CustomFileUpload: React.FC<CustomFileUploadProps> = ({
  accept,
  buttonLabel = 'Choose file',
  dropLabel = 'Click or drop a file here',
  value,
  onChange,
  disabled,
  compact,
}) => {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragging, setDragging] = useState(false);

  const pick = () => {
    if (disabled) return;
    inputRef.current?.click();
  };

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    onChange?.(files[0]);
  };

  return (
    <div className="flex flex-col gap-3">
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        disabled={disabled}
        onChange={(e) => {
          handleFiles(e.target.files);
          // allow re-selecting the same file
          e.target.value = '';
        }}
      />
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-disabled={disabled}
        onClick={pick}
        onKeyDown={(e) => {
          if (disabled) return;
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            pick();
          }
        }}
        onDragOver={(e) => {
          if (disabled) return;
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (disabled) return;
          handleFiles(e.dataTransfer.files);
        }}
        className={cn(
          'flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed text-center text-sm transition-colors',
          compact ? 'px-3 py-4' : 'px-4 py-8',
          disabled
            ? 'cursor-not-allowed text-muted-foreground/60 opacity-60'
            : 'cursor-pointer text-muted-foreground hover:border-primary/40 hover:bg-primary/5 hover:text-primary',
          dragging && !disabled && 'border-primary bg-primary/5 text-primary',
        )}
      >
        <UploadCloud className={cn(compact ? 'size-5' : 'size-6')} aria-hidden />
        <span className={cn(compact && 'text-xs leading-snug')}>{dropLabel}</span>
      </div>

      {value && (
        <div className="bg-card flex items-center justify-between gap-2 rounded-md border px-3 py-2 text-sm">
          <span className="truncate" title={value.name}>
            {value.name}
          </span>
          <CustomButton
            variant="text"
            size="small"
            onClick={() => onChange?.(null)}
            disabled={disabled}
          >
            <X className="size-4" aria-hidden />
            Remove
          </CustomButton>
        </div>
      )}

      <div>
        <CustomButton variant="ghost" onClick={pick} disabled={disabled}>
          {buttonLabel}
        </CustomButton>
      </div>
    </div>
  );
};

export default CustomFileUpload;
