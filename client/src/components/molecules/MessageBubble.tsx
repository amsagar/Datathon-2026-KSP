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
}) => {
  const isUser = message.role === 'user';
  const bubbleClass = isUser ? styles.bubbleUser : styles.bubbleAssistant;
  const t = useT();

  return (
    <div className={bubbleClass}>
      {isUser ? (
        message.content
      ) : message.content ? (
        <MarkdownContent source={message.content} streaming={streaming} />
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
    </div>
  );
};

// Memoized: completed messages keep stable object identity across streaming
// updates (the store only replaces the last message), so they skip re-render.
export default React.memo(MessageBubble);
