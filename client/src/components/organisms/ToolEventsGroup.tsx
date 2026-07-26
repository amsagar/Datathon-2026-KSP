import React, { useMemo, useState } from 'react';
import CustomIcon from '@atoms/CustomIcon';
import ToolEventCard from './ToolEventCard';
import type { UiToolCall } from '@interfaces/chat.interface';
import { useExportModeStore } from '@store/useExportModeStore';
import { useT } from '@constants/translations';
import * as styles from '@styles/toolEventCard.module.scss';

export interface ToolEventsGroupProps {
  tools: UiToolCall[];
  /** Expand the group while the assistant is still running tools on this turn. */
  activeTurn?: boolean;
}

const ToolEventsGroup: React.FC<ToolEventsGroupProps> = ({ tools }) => {
  const anyRunning = tools.some((t) => t.running);
  const anyError = tools.some((t) => t.error);
  const t = useT();
  const exporting = useExportModeStore((s) => s.exporting);

  const groupSummary = (n: number): string => {
    if (n === 0) return '';
    return n === 1 ? t('toolGroupOneStep') : `${n} ${t('toolGroupSteps')}`;
  };

  // Always collapsed by default — including while the turn is running. The user
  // expands the group manually; it never auto-opens. Export mode is the one exception (see
  // ToolEventCard's matching effectiveOpen — the PDF export needs the full audit trail visible).
  const [groupOpen, setGroupOpen] = useState(false);
  const effectiveGroupOpen = groupOpen || exporting;

  const groupLabel = useMemo(() => {
    if (anyRunning) return t('toolGroupWorking');
    if (anyError) return t('toolGroupErrors');
    return t('toolGroupCompleted');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anyRunning, anyError, t]);

  const groupIcon = anyRunning ? (
    <CustomIcon name="loading" size={14} color="#0060c0" />
  ) : anyError ? (
    <CustomIcon name="warning" size={14} color="#d4380d" />
  ) : (
    <CustomIcon name="check-circle" size={14} color="#16a34a" />
  );

  if (tools.length === 0) {
    return null;
  }

  return (
    <div className={styles.group}>
      <button
        type="button"
        className={styles.groupHead}
        onClick={() => setGroupOpen((o) => !o)}
        aria-expanded={effectiveGroupOpen}
      >
        <span className={styles.groupStatus}>{groupIcon}</span>
        <span className={styles.groupTitle}>
          <span className={styles.groupLabel}>{groupLabel}</span>
          <span className={styles.groupMeta}>{groupSummary(tools.length)}</span>
        </span>
        <span className={styles.caret}>
          <CustomIcon
            name={effectiveGroupOpen ? 'caret-down' : 'caret-right'}
            size={11}
          />
        </span>
      </button>

      {effectiveGroupOpen && (
        <div className={styles.groupBody}>
          <div className={styles.groupSteps}>
            {tools.map((toolCall) => (
              <ToolEventCard key={toolCall.id} tool={toolCall} nested />
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ToolEventsGroup;
