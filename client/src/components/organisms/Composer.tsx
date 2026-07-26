import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ArrowUp, Check, Mic, Square, X } from 'lucide-react';
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
const WAVE_BARS = 28;

const Composer: React.FC<ComposerProps> = ({
  streaming,
  disabled,
  onSend,
  onStop,
  placeholder,
}) => {
  const [value, setValue] = useState('');
  const [listening, setListening] = useState(false);
  const [draftTranscript, setDraftTranscript] = useState('');
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
    el.style.height = 'auto';
    const content = el.scrollHeight;
    const next = Math.min(Math.max(content, MIN_H), MAX_H);
    el.style.height = `${next}px`;
    el.style.overflowY = content > MAX_H ? 'auto' : 'hidden';
    setExpanded(next > MIN_H + 4);
  };

  useLayoutEffect(() => {
    if (!listening) fitHeight();
  }, [value, listening]);

  useEffect(() => () => stopDictationRef.current?.(), []);

  const stopListening = () => {
    stopDictationRef.current?.();
    stopDictationRef.current = null;
    setListening(false);
  };

  const submit = () => {
    if (streaming) {
      onStop?.();
      return;
    }
    const text = value.trim();
    if (!text || blocked) return;
    stopListening();
    setValue('');
    onSend(text);
  };

  const rejectVoice = () => {
    stopListening();
    setDraftTranscript('');
    setValue(baseValueRef.current.trimEnd());
  };

  const acceptVoice = () => {
    const next = `${baseValueRef.current}${draftTranscript}`.trim();
    stopListening();
    setDraftTranscript('');
    setValue(next);
    requestAnimationFrame(() => taRef.current?.focus());
  };

  const startVoice = () => {
    if (listening || blocked) return;
    baseValueRef.current = value ? `${value.trimEnd()} ` : '';
    setDraftTranscript('');
    const stop = startDictation(
      voiceLang,
      (transcript) => setDraftTranscript(transcript),
      (reason, errorCode) => {
        if (reason === 'error') {
          setListening(false);
          stopDictationRef.current = null;
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
        // continuous mode: 'done' only after accept/reject abort
      },
      { continuous: true }
    );
    if (stop) {
      stopDictationRef.current = stop;
      setListening(true);
    }
  };

  return (
    <div className={styles.composerOuter}>
      <div
        className={`${styles.composer} ${expanded && !listening ? styles.composerExpanded : ''} ${
          listening ? styles.composerListening : ''
        }`}
      >
        {listening ? (
          <div className={styles.voiceStage} aria-live="polite">
            <div className={styles.wave} aria-hidden>
              {Array.from({ length: WAVE_BARS }, (_, i) => (
                <motion.span
                  key={i}
                  className={styles.waveBar}
                  animate={{ scaleY: [0.25, 1, 0.35, 0.85, 0.25] }}
                  transition={{
                    duration: 0.9 + (i % 5) * 0.08,
                    repeat: Infinity,
                    ease: 'easeInOut',
                    delay: (i % 7) * 0.05,
                  }}
                />
              ))}
            </div>
            <span className={styles.voiceDraft}>
              {draftTranscript || t('listening')}
            </span>
          </div>
        ) : (
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
            placeholder={resolvedPlaceholder}
            disabled={!!blocked}
            aria-label={resolvedPlaceholder}
          />
        )}

        <div className={styles.actions}>
          {listening ? (
            <>
              <button
                type="button"
                className={styles.iconBtn}
                onClick={rejectVoice}
                aria-label={t('rejectVoiceInput')}
                title={t('rejectVoiceInput')}
              >
                <X size={18} strokeWidth={2.25} />
              </button>
              <button
                type="button"
                className={styles.acceptBtn}
                onClick={acceptVoice}
                aria-label={t('acceptVoiceInput')}
                title={t('acceptVoiceInput')}
              >
                <Check size={18} strokeWidth={2.5} />
              </button>
            </>
          ) : (
            <>
              {isSpeechRecognitionSupported() && (
                <button
                  type="button"
                  className={styles.iconBtn}
                  onClick={startVoice}
                  disabled={!!blocked}
                  aria-label={t('startVoiceInput')}
                >
                  <Mic size={16} strokeWidth={2} />
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
            </>
          )}
        </div>
      </div>
      <span className={styles.hint}>
        {listening ? t('voiceReviewHint') : t('composerHint')}
      </span>
    </div>
  );
};

export default Composer;
