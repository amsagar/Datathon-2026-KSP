import React, { useMemo, useState } from 'react';
import CustomButton from '@atoms/CustomButton';
import CustomInput from '@atoms/CustomInput';
import type { PendingSkillUpdateProposal } from '@interfaces/chat.interface';
import { computeLineDiff } from '@utils/skillUpdateDiff';
import * as styles from '@styles/skillUpdateProposal.module.scss';

export interface SkillUpdateProposalCardProps {
  pending: PendingSkillUpdateProposal;
  onSubmit: (approved: boolean, rejectionReason?: string) => void;
}

const SkillUpdateProposalCard: React.FC<SkillUpdateProposalCardProps> = ({
  pending,
  onSubmit,
}) => {
  const [rejectionReason, setRejectionReason] = useState('');
  const diffLines = useMemo(
    () =>
      computeLineDiff(
        pending.currentContent ?? '',
        pending.proposedContent ?? ''
      ),
    [pending.currentContent, pending.proposedContent]
  );

  const handleReject = () => {
    const reason = rejectionReason.trim();
    onSubmit(false, reason || undefined);
  };

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <p className={styles.title}>Proposed skill update</p>
        <p className={styles.meta}>
          {pending.skillName} · {pending.filePath}
        </p>
      </div>

      <p className={styles.summary}>{pending.summary}</p>

      {pending.feedbackQuote && (
        <p className={styles.feedbackQuote}>
          &ldquo;{pending.feedbackQuote}&rdquo;
        </p>
      )}

      <div className={styles.diff} aria-label="Skill file diff">
        {diffLines.map((line, index) => (
          <span
            key={`${line.type}-${index}`}
            className={`${styles.diffLine} ${
              line.type === 'add'
                ? styles.diffAdd
                : line.type === 'remove'
                  ? styles.diffRemove
                  : styles.diffSame
            }`}
          >
            {line.type === 'add' ? '+ ' : line.type === 'remove' ? '- ' : '  '}
            {line.text || ' '}
          </span>
        ))}
      </div>

      <CustomInput
        className={styles.rejectInput}
        placeholder="Optional reason if rejecting…"
        value={rejectionReason}
        onChange={(e) => setRejectionReason(e.target.value)}
        disabled={pending.submitting}
      />

      {pending.error && <p className={styles.error}>{pending.error}</p>}

      <div className={styles.actions}>
        <CustomButton
          variant="secondary"
          disabled={pending.submitting}
          onClick={handleReject}
        >
          Reject
        </CustomButton>
        <CustomButton
          variant="primary"
          loading={pending.submitting}
          disabled={pending.submitting}
          onClick={() => onSubmit(true)}
        >
          Approve
        </CustomButton>
      </div>
    </div>
  );
};

export default SkillUpdateProposalCard;
