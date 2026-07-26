import React, { useState } from 'react';
import CustomIcon from '@atoms/CustomIcon';
import MarkdownContent from '@molecules/MarkdownContent';
import { formatJson } from '@utils/formatJson';
import { isJsonPayload } from '@utils/isJsonPayload';
import type { UiToolCall } from '@interfaces/chat.interface';
import { useExportModeStore } from '@store/useExportModeStore';
import { useT } from '@constants/translations';
import * as styles from '@styles/toolEventCard.module.scss';

export interface ToolEventCardProps {
  tool: UiToolCall;
  /** Render inside a {@link ToolEventsGroup} (indented, compact). */
  nested?: boolean;
}

const PayloadBlock: React.FC<{
  label: string;
  raw: string | null;
  running?: boolean;
  runningLabel: string;
}> = ({ label, raw, running, runningLabel }) => {
  if (running) {
    return (
      <>
        <div className={styles.label}>{label}</div>
        <div className={styles.running}>{runningLabel}</div>
      </>
    );
  }

  if (!raw?.trim()) {
    return (
      <>
        <div className={styles.label}>{label}</div>
        <div className={styles.empty}>—</div>
      </>
    );
  }

  if (isJsonPayload(raw)) {
    return (
      <>
        <div className={styles.label}>{label}</div>
        <pre className={styles.pre}>{formatJson(raw)}</pre>
      </>
    );
  }

  return (
    <>
      <div className={styles.label}>{label}</div>
      <div className={styles.md}>
        <MarkdownContent source={raw} compact />
      </div>
    </>
  );
};

const ToolEventCard: React.FC<ToolEventCardProps> = ({ tool, nested }) => {
  const [open, setOpen] = useState(false);
  const exporting = useExportModeStore((s) => s.exporting);
  const t = useT();
  // ORs the user's own toggle with export mode — a PDF export must always capture the full
  // Input/Output audit evidence regardless of what's manually left collapsed (see
  // useExportModeStore's comment: the collapsed body isn't just hidden, it isn't in the DOM).
  const effectiveOpen = open || exporting;

  const statusIcon = tool.running ? (
    <CustomIcon name="loading" size={13} color="#0060c0" />
  ) : tool.error ? (
    <CustomIcon name="warning" size={13} color="#d4380d" />
  ) : (
    <CustomIcon name="check-circle" size={13} color="#16a34a" />
  );

  return (
    <div
      className={`${styles.card} ${nested ? styles.cardNested : ''} ${
        tool.error ? styles.error : ''
      }`}
    >
      <button
        type="button"
        className={styles.head}
        onClick={() => setOpen((o) => !o)}
        aria-expanded={effectiveOpen}
      >
        <span className={styles.status}>{statusIcon}</span>
        <span className={styles.name}>{tool.name}</span>
        <span className={styles.caret}>
          <CustomIcon name={effectiveOpen ? 'caret-down' : 'caret-right'} size={11} />
        </span>
      </button>
      {effectiveOpen && (
        <div className={styles.body}>
          <PayloadBlock label={t('toolInput')} raw={tool.input} runningLabel={t('toolRunning')} />
          <PayloadBlock
            label={t('toolOutput')}
            raw={tool.output}
            running={tool.running}
            runningLabel={t('toolRunning')}
          />
        </div>
      )}
    </div>
  );
};

export default ToolEventCard;
