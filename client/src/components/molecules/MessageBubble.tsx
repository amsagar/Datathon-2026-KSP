import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import MarkdownContent from '@molecules/MarkdownContent';
import type { UiChatMessage } from '@interfaces/chat.interface';
import { useT } from '@constants/translations';
import * as styles from '@styles/chatThread.module.scss';

export interface MessageBubbleProps {
  message: UiChatMessage;
  /** Show the typing dots when the assistant content is empty AND no tools yet */
  showTypingDots?: boolean;
  /** Overrides the default "Thinking" label (from SSE `status` events). */
  typingLabel?: string;
  /** True while this message is still receiving SSE chunks. */
  streaming?: boolean;
  /** When set, render this instead of message.content (e.g. on-the-fly translation). */
  displayContent?: string | null;
  /** Small caption under the bubble when showing a translation. */
  caption?: string | null;
}

/** Three dots with a staggered pulse, driven by motion (respects reduced-motion). */
const TypingDots: React.FC = () => {
  const reduce = useReducedMotion();
  return (
    <span className={styles.typingDots} aria-hidden>
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          animate={
            reduce ? { opacity: 0.6 } : { opacity: [0.3, 1, 0.3], y: [0, -2, 0] }
          }
          transition={
            reduce
              ? undefined
              : {
                  duration: 1.1,
                  repeat: Infinity,
                  ease: 'easeInOut',
                  delay: i * 0.16,
                }
          }
        />
      ))}
    </span>
  );
};

const MessageBubble: React.FC<MessageBubbleProps> = ({
  message,
  showTypingDots,
  typingLabel,
  streaming,
  displayContent,
  caption,
}) => {
  const isUser = message.role === 'user';
  const bubbleClass = isUser ? styles.bubbleUser : styles.bubbleAssistant;
  const t = useT();
  const body = displayContent != null ? displayContent : message.content;

  return (
    <div className={bubbleClass}>
      {isUser ? (
        body
      ) : body ? (
        <MarkdownContent source={body} streaming={streaming} />
      ) : showTypingDots ? (
        <span
          className={styles.typing}
          aria-live="polite"
          aria-label={typingLabel || t('thinking')}
        >
          <span className={styles.typingShimmer}>{typingLabel || t('thinking')}</span>
          <TypingDots />
        </span>
      ) : null}
      {caption ? <div className={styles.translateCaption}>{caption}</div> : null}
    </div>
  );
};

// Memoized: completed messages keep stable object identity across streaming
// updates (the store only replaces the last message), so they skip re-render.
export default React.memo(MessageBubble);
