import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ArrowUp, Mic, Square } from 'lucide-react';
import { motion } from 'motion/react';
import { isSpeechRecognitionSupported, startDictation } from '@utils/speech';
import { useLangStore } from '@store/useLangStore';
import { useNotification } from '@providers/NotificationProviders';
import { useT } from '@constants/translations';
import * as styles from '@styles/composer.module.scss';

export interface ComposerProps {
  streaming: boolean;
  disabled?: boolean;
  onSend: (text: string) => void;
  onStop?: () => void;
  placeholder?: string;
}

/** Single-line box height (px). Keep in sync with composer.module.scss */
const MIN_H = 24;
/** Cap before inner scroll — ChatGPT-style */
const MAX_H = 200;

const Composer: React.FC<ComposerProps> = ({
  streaming,
  disabled,
  onSend,
  onStop,
  placeholder,
}) => {
  const [value, setValue] = useState('');
  const [listening, setListening] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const stopDictationRef = useRef<(() => void) | null>(null);
  const baseValueRef = useRef('');
  const taRef = useRef<HTMLTextAreaElement | null>(null);
  const blocked = streaming || disabled;
  const voiceLang = useLangStore((s) => s.lang);
  const t = useT();
  const openNotification = useNotification();
  const resolvedPlaceholder = placeholder ?? t('messagePlaceholder');
  const canSend = streaming || (!blocked && !!value.trim());

  const fitHeight = () => {
    const el = taRef.current;
    if (!el) return;
    // Shrink → measure content → grow (cap at MAX_H, then scroll)
    el.style.height = 'auto';
    const content = el.scrollHeight;
    const next = Math.min(Math.max(content, MIN_H), MAX_H);
    el.style.height = `${next}px`;
    el.style.overflowY = content > MAX_H ? 'auto' : 'hidden';
    setExpanded(next > MIN_H + 4);
  };

  useLayoutEffect(() => {
    fitHeight();
  }, [value]);

  useEffect(() => () => stopDictationRef.current?.(), []);

  const submit = () => {
    if (streaming) {
      onStop?.();
      return;
    }
    const text = value.trim();
    if (!text || blocked) return;
    stopDictationRef.current?.();
    setValue('');
    onSend(text);
  };

  const toggleMic = () => {
    if (listening) {
      stopDictationRef.current?.();
      return;
    }
    baseValueRef.current = value ? `${value.trimEnd()} ` : '';
    const stop = startDictation(
      voiceLang,
      (transcript) => setValue(baseValueRef.current + transcript),
      (reason, errorCode) => {
        setListening(false);
        if (reason === 'error') {
          const message =
            errorCode === 'not-allowed'
              ? t('micPermissionDenied')
              : errorCode === 'no-speech'
                ? t('noSpeechDetected')
                : errorCode === 'network'
                  ? t('voiceNetworkError')
                  : t('voiceInputFailed');
          openNotification(message, 'Warning');
        }
      },
    );
    if (stop) {
      stopDictationRef.current = stop;
      setListening(true);
    }
  };

  return (
    <div className={styles.composerOuter}>
      <div
        className={`${styles.composer} ${expanded ? styles.composerExpanded : ''}`}
      >
        <textarea
          ref={taRef}
          className={styles.input}
          value={value}
          rows={1}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          placeholder={listening ? t('listening') : resolvedPlaceholder}
          disabled={!!blocked}
          aria-label={resolvedPlaceholder}
        />
        <div className={styles.actions}>
          {isSpeechRecognitionSupported() && (
            <button
              type="button"
              className={`${styles.iconBtn} ${listening ? styles.iconBtnActive : ''}`}
              onClick={toggleMic}
              disabled={!!blocked}
              aria-label={listening ? t('stopVoiceInput') : t('startVoiceInput')}
              aria-pressed={listening}
            >
              <motion.span
                className={styles.iconBtnInner}
                animate={listening ? { scale: [1, 1.08, 1] } : { scale: 1 }}
                transition={
                  listening
                    ? { duration: 1.1, repeat: Infinity, ease: 'easeInOut' }
                    : { duration: 0.15 }
                }
              >
                <Mic size={16} strokeWidth={2} />
              </motion.span>
            </button>
          )}
          <motion.button
            type="button"
            className={styles.sendBtn}
            onClick={submit}
            disabled={!canSend}
            aria-label={streaming ? 'Stop' : 'Send'}
            whileHover={canSend ? { scale: 1.06 } : undefined}
            whileTap={canSend ? { scale: 0.92 } : undefined}
            transition={{ type: 'spring', stiffness: 500, damping: 28 }}
          >
            {streaming ? (
              <Square size={12} strokeWidth={2.5} fill="currentColor" />
            ) : (
              <ArrowUp size={16} strokeWidth={2.5} />
            )}
          </motion.button>
        </div>
      </div>
      <span className={styles.hint}>{t('composerHint')}</span>
    </div>
  );
};

export default Composer;
