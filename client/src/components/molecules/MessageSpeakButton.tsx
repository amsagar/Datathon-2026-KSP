import React, { useEffect, useRef, useState } from 'react';
import { Volume2 } from 'lucide-react';
import CustomButton from '@atoms/CustomButton';
import CustomTooltip from '@atoms/CustomTooltip';
import { detectSpeechLang, speak, stopSpeaking } from '@utils/speech';
import { useT } from '@constants/translations';

/** Strips markdown/code noise so TTS reads prose, not syntax. */
const speechText = (markdown: string): string =>
  markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[*_#`>|]/g, '')
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();

export interface MessageSpeakButtonProps {
  content: string;
  disabled?: boolean;
  /** Called when speaking starts/stops so the parent can keep the actions row visible. */
  onSpeakingChange?: (speaking: boolean) => void;
}

const MessageSpeakButton: React.FC<MessageSpeakButtonProps> = ({
  content,
  disabled,
  onSpeakingChange,
}) => {
  const t = useT();
  const [speaking, setSpeaking] = useState(false);
  const speakingRef = useRef(false);
  const pollRef = useRef<number | null>(null);

  useEffect(() => () => {
    if (speakingRef.current) stopSpeaking();
    if (pollRef.current !== null) window.clearInterval(pollRef.current);
  }, []);

  useEffect(() => {
    onSpeakingChange?.(speaking);
  }, [speaking, onSpeakingChange]);

  if (!('speechSynthesis' in window)) return null;

  const toggleSpeak = () => {
    if (speaking) {
      stopSpeaking();
      setSpeaking(false);
      speakingRef.current = false;
      return;
    }
    const text = speechText(content || '');
    if (!text) return;
    speak(text, detectSpeechLang(text));
    setSpeaking(true);
    speakingRef.current = true;
    pollRef.current = window.setInterval(() => {
      if (!window.speechSynthesis.speaking) {
        if (pollRef.current !== null) window.clearInterval(pollRef.current);
        pollRef.current = null;
        setSpeaking(false);
        speakingRef.current = false;
      }
    }, 400);
  };

  return (
    <CustomTooltip title={speaking ? t('stopReadingAloud') : t('readAloud')}>
      <CustomButton
        variant="text"
        size="small"
        onClick={toggleSpeak}
        disabled={disabled || !content.trim()}
        aria-label={speaking ? t('stopReadingAloud') : t('readAloud')}
        aria-pressed={speaking}
      >
        <Volume2
          className="size-4"
          style={{
            color: speaking ? 'var(--primary, #b01722)' : undefined,
          }}
        />
      </CustomButton>
    </CustomTooltip>
  );
};

export default MessageSpeakButton;
