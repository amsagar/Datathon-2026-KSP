import React, { useMemo, useState } from 'react';
import CustomButton from '@atoms/CustomButton';
import CustomInput from '@atoms/CustomInput';
import type {
  ClarificationQuestionDto,
  PendingClarification,
} from '@interfaces/chat.interface';
import { useT } from '@constants/translations';
import * as styles from '@styles/clarifyingQuestions.module.scss';

export interface ClarifyingQuestionsCardProps {
  pending: PendingClarification;
  onSubmit: (answers: Record<string, string>) => void;
}

type QuestionState = {
  selected: string[];
  other: string;
};

const buildAnswer = (q: ClarificationQuestionDto, state: QuestionState): string => {
  const other = state.other.trim();
  if (other) return other;
  if (state.selected.length === 0) return '';
  return q.multiSelect ? state.selected.join(', ') : state.selected[0];
};

const ClarifyingQuestionsCard: React.FC<ClarifyingQuestionsCardProps> = ({
  pending,
  onSubmit,
}) => {
  const initial = useMemo(() => {
    const map: Record<string, QuestionState> = {};
    for (const q of pending.questions) {
      map[q.question] = { selected: [], other: '' };
    }
    return map;
  }, [pending.requestId, pending.questions]);

  const [answers, setAnswers] = useState<Record<string, QuestionState>>(initial);

  const toggleOption = (questionKey: string, label: string, multi: boolean) => {
    setAnswers((prev) => {
      const cur = prev[questionKey] ?? { selected: [], other: '' };
      let selected: string[];
      if (multi) {
        selected = cur.selected.includes(label)
          ? cur.selected.filter((l) => l !== label)
          : [...cur.selected, label];
      } else {
        selected = [label];
      }
      return {
        ...prev,
        [questionKey]: { selected, other: multi ? cur.other : '' },
      };
    });
  };

  const setOther = (questionKey: string, value: string) => {
    setAnswers((prev) => {
      const cur = prev[questionKey] ?? { selected: [], other: '' };
      return { ...prev, [questionKey]: { ...cur, other: value } };
    });
  };

  const handleSubmit = () => {
    const payload: Record<string, string> = {};
    for (const q of pending.questions) {
      const state = answers[q.question] ?? { selected: [], other: '' };
      payload[q.question] = buildAnswer(q, state);
    }
    onSubmit(payload);
  };

  const allAnswered = pending.questions.every((q) => {
    const state = answers[q.question];
    if (!state) return false;
    return buildAnswer(q, state).trim().length > 0;
  });

  const disabled = pending.submitting || !allAnswered;
  const t = useT();

  return (
    <div className={styles.card} role="form" aria-label={t('clarifyingQuestionsLabel')}>
      <p className={styles.title}>{t('clarifyingQuestionsTitle')}</p>

      {pending.questions.map((q) => {
        const state = answers[q.question] ?? { selected: [], other: '' };
        return (
          <div key={q.question} className={styles.questionBlock}>
            <span className={styles.questionHeader}>{q.header}</span>
            <p className={styles.questionText}>{q.question}</p>
            <div className={styles.options}>
              {q.options.map((opt) => {
                const checked = state.selected.includes(opt.label);
                const inputType = q.multiSelect ? 'checkbox' : 'radio';
                return (
                  <label key={opt.label} className={styles.optionRow}>
                    <input
                      type={inputType}
                      name={q.question}
                      checked={checked}
                      disabled={pending.submitting}
                      onChange={() => toggleOption(q.question, opt.label, q.multiSelect)}
                    />
                    <span>
                      {opt.label}
                      {opt.description ? (
                        <span className={styles.optionDesc}>{opt.description}</span>
                      ) : null}
                    </span>
                  </label>
                );
              })}
            </div>
            <CustomInput
              className={styles.otherInput}
              placeholder={t('orTypeOwnAnswer')}
              value={state.other}
              disabled={pending.submitting}
              onChange={(e) => setOther(q.question, e.target.value)}
            />
          </div>
        );
      })}

      {pending.error ? <p className={styles.error}>{pending.error}</p> : null}

      {!pending.submitting && (
        <div className={styles.actions}>
          <CustomButton
            variant="primary"
            onClick={handleSubmit}
            disabled={disabled}
          >
            {t('continueLabel')}
          </CustomButton>
        </div>
      )}
    </div>
  );
};

export default ClarifyingQuestionsCard;
