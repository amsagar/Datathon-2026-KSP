import React, { useEffect, useRef, useState } from 'react';
import { Play, Pause, Loader2 } from 'lucide-react';
import CustomButton from '@atoms/CustomButton';
import CustomTooltip from '@atoms/CustomTooltip';
import {
  detectSpeechLang,
  isSpeakPaused,
  pauseSpeaking,
  resumeSpeaking,
  speakIndianStreaming,
  stopSpeaking,
} from '@utils/speech';
import { useNotification } from '@providers/NotificationProviders';
import { useT } from '@constants/translations';

/** Strips markdown/code noise so TTS reads prose, not syntax. */
const speechText = (markdown: string): string =>
  markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/~~(.*?)~~/g, '$1')
    .replace(/^ {0,3}([-*_])( *\1){2,} *$/gm, ' ')
    .replace(/^\s*\|?[\s:-]*-[\s:-]*\|[\s:|-]*$/gm, ' ')
    .replace(/^\s*([-*+]|\d+[.)])\s+/gm, '')
    .replace(/^\s*>+\s?/gm, '')
    .replace(/^ {0,3}#{1,6}\s*/gm, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[*_#`>|~]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

export interface MessageSpeakButtonProps {
  content: string;
  disabled?: boolean;
  onSpeakingChange?: (speaking: boolean) => void;
}

/**
 * Speak-aloud with Play / Pause icons (Indian neural TTS, sentence streaming).
 */
const MessageSpeakButton: React.FC<MessageSpeakButtonProps> = ({
  content,
  disabled,
  onSpeakingChange,
}) => {
  const t = useT();
  const openNotification = useNotification();
  const [active, setActive] = useState(false);
  const [paused, setPaused] = useState(false);
  const [loading, setLoading] = useState(false);
  const stopRef = useRef<(() => void) | null>(null);

  useEffect(() => () => {
    stopRef.current?.();
    stopSpeaking();
  }, []);

  useEffect(() => {
    onSpeakingChange?.(active && !paused);
  }, [active, paused, onSpeakingChange]);

  const hardStop = () => {
    stopRef.current?.();
    stopSpeaking();
    stopRef.current = null;
    setActive(false);
    setPaused(false);
    setLoading(false);
  };

  const toggleSpeak = () => {
    if (loading) {
      hardStop();
      return;
    }

    // Active + playing → pause
    if (active && !paused) {
      pauseSpeaking();
      setPaused(true);
      return;
    }

    // Active + paused → resume
    if (active && paused) {
      resumeSpeaking();
      setPaused(false);
      return;
    }

    const text = speechText(content || '');
    if (!text) return;
    const lang = detectSpeechLang(text);
    setLoading(true);
    setPaused(false);
    stopRef.current = speakIndianStreaming(text, lang, {
      onStart: () => {
        setLoading(false);
        setActive(true);
        setPaused(false);
      },
      onEnd: () => {
        setActive(false);
        setPaused(false);
        setLoading(false);
        stopRef.current = null;
      },
      onError: () => {
        setActive(false);
        setPaused(false);
        setLoading(false);
        stopRef.current = null;
        openNotification(t('ttsFailed'), 'Warning');
      },
    });
  };

  // Double-click / long stop: if paused, Play resumes; hold isn't needed —
  // clicking Play while paused resumes; users can stop via pausing then leaving.
  // Provide stop on second pause-path via tooltip "Pause" / "Play".
  useEffect(() => {
    if (!active) return;
    const id = window.setInterval(() => {
      setPaused(isSpeakPaused());
    }, 200);
    return () => window.clearInterval(id);
  }, [active]);

  const tip = loading
    ? t('stopReadingAloud')
    : active && !paused
      ? t('pauseReadingAloud')
      : active && paused
        ? t('resumeReadingAloud')
        : t('readAloud');

  const showPause = active && !paused && !loading;

  return (
    <CustomTooltip title={tip}>
      <CustomButton
        variant="text"
        size="small"
        onClick={toggleSpeak}
        disabled={disabled || !content.trim()}
        aria-label={tip}
        aria-pressed={active && !paused}
      >
        {loading ? (
          <Loader2 className="size-4 animate-spin" />
        ) : showPause ? (
          <Pause
            className="size-4"
            fill="currentColor"
            style={{ color: 'var(--primary, #b01722)' }}
          />
        ) : (
          <Play
            className="size-4"
            fill="currentColor"
            style={{
              color: active ? 'var(--primary, #b01722)' : undefined,
            }}
          />
        )}
      </CustomButton>
    </CustomTooltip>
  );
};

export default MessageSpeakButton;
