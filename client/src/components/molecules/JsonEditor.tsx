import React, { useMemo, useState } from 'react';
import CustomTextarea from '@atoms/CustomTextarea';
import { formatJson } from '@utils/formatJson';
import * as styles from '@styles/jsonEditor.module.scss';

export interface JsonEditorProps {
  value: string;
  onChange?: (next: string) => void;
  readOnly?: boolean;
  placeholder?: string;
  minRows?: number;
  maxRows?: number;
  compact?: boolean;
  ariaLabel?: string;
  className?: string;
}

type Mode = 'edit' | 'preview';

const escapeHtml = (text: string): string =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const highlightJson = (raw: string): string => {
  const formatted = formatJson(raw);
  if (!formatted) return '';

  return formatted.replace(
    /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
    (match, quoted, colon) => {
      if (quoted && colon) {
        return `<span class="${styles.key}">${escapeHtml(quoted)}</span>${colon}`;
      }
      if (quoted) {
        return `<span class="${styles.string}">${escapeHtml(quoted)}</span>`;
      }
      if (match === 'true' || match === 'false') {
        return `<span class="${styles.boolean}">${match}</span>`;
      }
      if (match === 'null') {
        return `<span class="${styles.null}">${match}</span>`;
      }
      return `<span class="${styles.number}">${match}</span>`;
    }
  );
};

const JsonEditor: React.FC<JsonEditorProps> = ({
  value,
  onChange,
  readOnly = false,
  placeholder,
  minRows = 8,
  maxRows = 18,
  compact = false,
  ariaLabel,
  className,
}) => {
  const [mode, setMode] = useState<Mode>(readOnly ? 'preview' : 'edit');

  const previewHtml = useMemo(() => highlightJson(value), [value]);
  const parseError = useMemo(() => {
    if (!value.trim()) return null;
    try {
      JSON.parse(value);
      return null;
    } catch (e) {
      return (e as Error).message;
    }
  }, [value]);

  const handleFormat = () => {
    if (!onChange || !value.trim()) return;
    onChange(formatJson(value));
    setMode('edit');
  };

  const canFormat = !!onChange && !!value.trim() && !parseError;

  return (
    <div
      className={[
        styles.wrap,
        readOnly ? styles.wrapReadOnly : undefined,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.toolbar}>
        <div className={styles.toolbarLeft}>
          {!readOnly && (
            <div className={styles.tabs} role="tablist" aria-label="JSON mode">
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'edit'}
                className={`${styles.tab} ${mode === 'edit' ? styles.tabActive : ''}`}
                onClick={() => setMode('edit')}
              >
                Edit
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'preview'}
                className={`${styles.tab} ${mode === 'preview' ? styles.tabActive : ''}`}
                onClick={() => setMode('preview')}
              >
                Preview
              </button>
            </div>
          )}
          <span className={styles.hint}>JSON</span>
        </div>
        {!readOnly && (
          <button
            type="button"
            className={styles.formatBtn}
            onClick={handleFormat}
            disabled={!canFormat}
          >
            Format
          </button>
        )}
      </div>

      <div
        className={compact ? styles.bodyScrollCompact : styles.bodyScroll}
      >
        {!readOnly && mode === 'edit' ? (
          <CustomTextarea
            value={value}
            onChange={(e) => onChange?.(e.target.value)}
            placeholder={placeholder}
            autoSize={{ minRows, maxRows }}
            aria-label={ariaLabel}
            variant="borderless"
            fullWidth
            className={styles.textarea}
          />
        ) : value.trim() ? (
          parseError ? (
            <div className={styles.previewError}>
              Invalid JSON: {parseError}
            </div>
          ) : (
            <pre
              className={styles.preview}
              dangerouslySetInnerHTML={{ __html: previewHtml }}
            />
          )
        ) : (
          <div className={styles.previewEmpty}>
            {readOnly ? 'No output yet.' : 'Nothing to preview yet.'}
          </div>
        )}
      </div>
    </div>
  );
};

export default JsonEditor;
