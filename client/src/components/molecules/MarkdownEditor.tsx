import React, { useState } from 'react';
import CustomTextarea from '@atoms/CustomTextarea';
import MarkdownContent from '@molecules/MarkdownContent';
import { useT } from '@constants/translations';
import * as styles from '@styles/markdownEditor.module.scss';

export interface MarkdownEditorProps {
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  /** min textarea rows (ignored when fillHeight) */
  minRows?: number;
  /** max textarea rows before scrolling (ignored when fillHeight) */
  maxRows?: number;
  /** Fill parent height; only the editor body scrolls */
  fillHeight?: boolean;
  /** Optional className for the outer wrapper */
  className?: string;
  /** Optional aria-label for the textarea */
  ariaLabel?: string;
}

type Mode = 'edit' | 'preview';

const MarkdownEditor: React.FC<MarkdownEditorProps> = ({
  value,
  onChange,
  placeholder,
  minRows = 12,
  maxRows = 28,
  fillHeight = false,
  className,
  ariaLabel,
}) => {
  const t = useT();
  const [mode, setMode] = useState<Mode>('edit');

  return (
    <div
      className={[
        styles.wrap,
        fillHeight ? styles.wrapFill : undefined,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.toolbar}>
        <div className={styles.tabs} role="tablist" aria-label={t('editorMode')}>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'edit'}
            className={`${styles.tab} ${mode === 'edit' ? styles.tabActive : ''}`}
            onClick={() => setMode('edit')}
          >
            {t('editLabel')}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'preview'}
            className={`${styles.tab} ${mode === 'preview' ? styles.tabActive : ''}`}
            onClick={() => setMode('preview')}
          >
            {t('previewLabel')}
          </button>
        </div>
        <span className={styles.hint}>{t('markdownLabel')}</span>
      </div>

      <div className={fillHeight ? styles.bodyScroll : undefined}>
        {mode === 'edit' ? (
          <CustomTextarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            autoSize={fillHeight ? false : { minRows, maxRows }}
            aria-label={ariaLabel}
            variant="borderless"
            fullWidth
            className={
              fillHeight ? `${styles.textarea} ${styles.textareaFill}` : styles.textarea
            }
          />
        ) : value.trim() ? (
          <div
            className={
              fillHeight ? `${styles.preview} ${styles.previewFill}` : styles.preview
            }
          >
            <MarkdownContent source={value} />
          </div>
        ) : (
          <div
            className={
              fillHeight
                ? `${styles.previewEmpty} ${styles.previewFill}`
                : styles.previewEmpty
            }
          >
            {t('nothingToPreview')}
          </div>
        )}
      </div>
    </div>
  );
};

export default MarkdownEditor;
