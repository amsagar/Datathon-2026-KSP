import React from 'react';
import type { PersistedClarification } from '@utils/clarificationFromTool';
import * as styles from '@styles/clarifyingQuestions.module.scss';

export interface ClarificationSummaryProps {
  clarification: PersistedClarification;
}

const ClarificationSummary: React.FC<ClarificationSummaryProps> = ({
  clarification,
}) => (
  <div className={styles.card} aria-label="Your answers">
    <p className={styles.title}>Your choices</p>
    {clarification.questions.map((q) => (
      <div key={q.question} className={styles.questionBlock}>
        {q.header ? (
          <span className={styles.questionHeader}>{q.header}</span>
        ) : null}
        <p className={styles.questionText}>{q.question}</p>
        <p className={styles.answerLine}>
          {clarification.answers[q.question] || '—'}
        </p>
      </div>
    ))}
  </div>
);

export default ClarificationSummary;
