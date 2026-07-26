import React, { useMemo, useState } from 'react';
import { Languages, Loader2 } from 'lucide-react';
import CustomButton from '@atoms/CustomButton';
import CustomTooltip from '@atoms/CustomTooltip';
import { translateText } from '@apiCalls/translate';
import { useNotification } from '@providers/NotificationProviders';
import { useT } from '@constants/translations';
import { useLangStore } from '@store/useLangStore';
import type { UiLang } from '@utils/speech';

export interface MessageTranslateButtonProps {
  content: string;
  showingTranslation: boolean;
  translatedText: string | null;
  onShowOriginal: () => void;
  onShowTranslation: (text: string) => void;
  disabled?: boolean;
}

/** Plain text for translation — keeps paragraphs, drops code fences / heavy markdown. */
const plainForTranslate = (markdown: string): string =>
  markdown
    .replace(/```[\s\S]*?```/g, '\n')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')
    .replace(/[*_>#]+/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

const scriptCounts = (content: string) => {
  const kn = (content.match(/[\u0C80-\u0CFF]/g) || []).length;
  const en = (content.match(/[A-Za-z]/g) || []).length;
  return { kn, en };
};

/**
 * Prefer the active UI language as the translation target when the message is
 * written in the other script (so KN UI → translate chat into Kannada).
 */
export const resolveTranslateTarget = (
  content: string,
  uiLang: UiLang
): UiLang => {
  const { kn, en } = scriptCounts(content);
  const mostlyKn = kn > 0 && kn >= en;
  if (uiLang === 'kn' && !mostlyKn) return 'kn';
  if (uiLang === 'en' && mostlyKn) return 'en';
  return mostlyKn ? 'en' : 'kn';
};

const MessageTranslateButton: React.FC<MessageTranslateButtonProps> = ({
  content,
  showingTranslation,
  translatedText,
  onShowOriginal,
  onShowTranslation,
  disabled,
}) => {
  const t = useT();
  const uiLang = useLangStore((s) => s.lang);
  const openNotification = useNotification();
  const [busy, setBusy] = useState(false);
  const target = useMemo(
    () => resolveTranslateTarget(content, uiLang),
    [content, uiLang]
  );

  const toggle = async () => {
    if (showingTranslation) {
      onShowOriginal();
      return;
    }
    if (translatedText) {
      onShowTranslation(translatedText);
      return;
    }
    const source = plainForTranslate(content || '');
    if (!source) return;
    setBusy(true);
    try {
      const out = await translateText(source, target);
      if (!out || out === source) {
        // Still show result so the user sees the toggle worked; BE may no-op
        // when scripts already match.
        onShowTranslation(out || source);
      } else {
        onShowTranslation(out);
      }
    } catch {
      openNotification(t('translateFailed'), 'Warning');
    } finally {
      setBusy(false);
    }
  };

  const tip = showingTranslation
    ? t('showOriginal')
    : target === 'kn'
      ? t('translateToKannada')
      : t('translateToEnglish');

  return (
    <CustomTooltip title={tip}>
      <CustomButton
        variant="text"
        size="small"
        onClick={() => void toggle()}
        disabled={disabled || busy || !content.trim()}
        aria-label={tip}
        aria-pressed={showingTranslation}
      >
        {busy ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <Languages
            className="size-4"
            style={{
              color: showingTranslation ? 'var(--primary, #b01722)' : undefined,
            }}
          />
        )}
      </CustomButton>
    </CustomTooltip>
  );
};

export default MessageTranslateButton;
