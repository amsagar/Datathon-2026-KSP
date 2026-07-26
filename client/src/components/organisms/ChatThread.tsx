import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import CustomButton from '@atoms/CustomButton';
import CustomSpinner from '@atoms/CustomSpinner';
import CustomTooltip from '@atoms/CustomTooltip';
import CustomIcon from '@atoms/CustomIcon';
import MessageBubble from '@molecules/MessageBubble';
import MessageEditor from '@molecules/MessageEditor';
import MessageSpeakButton from '@molecules/MessageSpeakButton';
import ClarifyingQuestionsCard from '@molecules/ClarifyingQuestionsCard';
import PromptSuggestions from '@molecules/PromptSuggestions';
import type {
  PendingClarification,
  PendingSkillUpdateProposal,
  UiChatMessage,
} from '@interfaces/chat.interface';
import SkillUpdateProposalCard from '@molecules/SkillUpdateProposalCard';
import * as styles from '@styles/chatThread.module.scss';
import { useT } from '@constants/translations';

export interface ChatThreadProps {
  messages: UiChatMessage[];
  streaming: boolean;
  streamStatus?: string | null;
  pendingClarification?: PendingClarification | null;
  pendingSkillUpdateProposal?: PendingSkillUpdateProposal | null;
  messagesLoading?: boolean;
  hasSession: boolean;
  assistantName?: string;
  /** Active assistant + UI language, used to fetch empty-screen prompt suggestions. */
  assistantId?: string;
  lang?: string;
  /** Send a starter prompt clicked on the empty screen. */
  onSelectPrompt?: (text: string) => void;
  /** Read-only share view: hide all message actions (copy/edit/resend/regenerate). */
  readOnly?: boolean;
  onResend?: (userIndex: number, text: string) => void;
  onSubmitClarification?: (answers: Record<string, string>) => void;
  onSubmitSkillUpdateDecision?: (approved: boolean, rejectionReason?: string) => void;
}

/** Entrance spring for newly appended rows (hoisted: stable identity across renders). */
const ROW_SPRING = { type: 'spring', stiffness: 380, damping: 30 } as const;

const ChatThread: React.FC<ChatThreadProps> = ({
  messages,
  streaming,
  streamStatus,
  pendingClarification,
  pendingSkillUpdateProposal,
  messagesLoading,
  hasSession,
  assistantName,
  assistantId,
  lang,
  onSelectPrompt,
  readOnly,
  onResend,
  onSubmitClarification,
  onSubmitSkillUpdateDecision,
}) => {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);
  const [speakingIndex, setSpeakingIndex] = useState<number | null>(null);
  const t = useT();
  const reduceMotion = useReducedMotion();
  // Rows already mounted before this render skip the entrance animation
  // (initial={false}); only genuinely new rows animate in. During streaming
  // this render runs per flush — re-running springs on every row is wasted work.
  const mountedCountRef = useRef(0);
  const prevMountedCount = mountedCountRef.current;
  useEffect(() => {
    mountedCountRef.current = messages.length;
  }, [messages.length]);
  const displayName = assistantName?.trim() || '';
  const avatarInitial = (displayName[0] || 'P').toUpperCase();

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo(0, el.scrollHeight);
  }, [messages]);

  const handleCopy = async (index: number, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedIndex(index);
      window.setTimeout(() => {
        setCopiedIndex((curr) => (curr === index ? null : curr));
      }, 1400);
    } catch {
      // ignore — clipboard may be unavailable in certain contexts
    }
  };

  if (messagesLoading && hasSession) {
    return (
      <div className={styles.thread} ref={scrollRef}>
        <div className={styles.threadLoading} aria-busy="true" aria-live="polite">
          <CustomSpinner size="large" tip={t('loadingConversation')} />
        </div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className={styles.thread} ref={scrollRef}>
        <motion.div
          className={styles.emptyHero}
          initial={reduceMotion ? false : { opacity: 0, y: 16, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
        >
          {displayName && (
            <div className={styles.emptyHeroBadge}>
              <span className={styles.emptyHeroBadgeMark}>{avatarInitial}</span>
              <span className={styles.emptyHeroBadgeLabel}>{displayName}</span>
            </div>
          )}
          <div className={styles.emptyHeroTitle}>{t('howCanIHelp')}</div>
          <div className={styles.emptyHeroSub}>
            {hasSession ? t('sendToContinue') : t('pickAssistant')}
          </div>
          {!readOnly && assistantId && onSelectPrompt && (
            <PromptSuggestions
              assistantId={assistantId}
              lang={lang || 'en'}
              onSelect={onSelectPrompt}
            />
          )}
        </motion.div>
      </div>
    );
  }

  return (
    <div className={styles.thread} ref={scrollRef}>
      <div className={styles.threadInner}>
        <AnimatePresence initial={false}>
        {messages.map((m, i) => {
          const isUser = m.role === 'user';
          const activeTurn =
            !isUser && streaming && i === messages.length - 1;
          const showClarification =
            activeTurn && !!pendingClarification && !!onSubmitClarification;
          const showSkillUpdateProposal =
            activeTurn &&
            !!pendingSkillUpdateProposal &&
            !!onSubmitSkillUpdateDecision;
          const showTypingDots =
            !isUser &&
            activeTurn &&
            !showClarification &&
            !showSkillUpdateProposal &&
            !m.content;
          // Inline ChatGPT-style cue only — never render tool names / accordion cards.
          const typingLabel =
            activeTurn && streamStatus
              ? streamStatus
              : activeTurn && showTypingDots
                ? t('thinking')
                : undefined;
          const isEditing = editingIndex === i;
          const canRegenerate =
            !isUser && i > 0 && messages[i - 1]?.role === 'user' && !streaming;
          const isCopied = copiedIndex === i;

          return (
            <motion.div
              key={i}
              initial={
                reduceMotion || i < prevMountedCount
                  ? false
                  : { opacity: 0, y: 8 }
              }
              animate={{ opacity: 1, y: 0 }}
              transition={ROW_SPRING}
              className={`${styles.message} ${
                isUser ? styles.messageUser : styles.messageAssistant
              }`}
            >
              {!isUser && displayName && (
                <div className={styles.messageHeader}>
                  <span className={styles.avatar}>{avatarInitial}</span>
                  <span className={styles.senderName}>{displayName}</span>
                </div>
              )}

              {isEditing && isUser ? (
                <MessageEditor
                  initialValue={m.content}
                  disabled={streaming}
                  onCancel={() => setEditingIndex(null)}
                  onSubmit={(text) => {
                    setEditingIndex(null);
                    onResend?.(i, text);
                  }}
                />
              ) : (
                (isUser || !!m.content || showTypingDots || !!typingLabel) && (
                  <MessageBubble
                    message={m}
                    showTypingDots={showTypingDots || (!!typingLabel && !m.content)}
                    typingLabel={typingLabel}
                    streaming={activeTurn}
                  />
                )
              )}

              {showClarification && pendingClarification && (
                <ClarifyingQuestionsCard
                  key={pendingClarification.requestId}
                  pending={pendingClarification}
                  onSubmit={(answers) => onSubmitClarification(answers)}
                />
              )}

              {showSkillUpdateProposal && pendingSkillUpdateProposal && (
                <SkillUpdateProposalCard
                  key={pendingSkillUpdateProposal.requestId}
                  pending={pendingSkillUpdateProposal}
                  onSubmit={(approved, rejectionReason) =>
                    onSubmitSkillUpdateDecision(approved, rejectionReason)
                  }
                />
              )}

              {!isEditing && !streaming && !readOnly && (
                <div
                  className={`${styles.actions} ${
                    speakingIndex === i ? styles.actionsPinned : ''
                  }`}
                >
                  {isUser ? (
                    <>
                      <CustomTooltip title={isCopied ? t('copied') : t('copy')}>
                        <CustomButton
                          variant="text"
                          size="small"
                          onClick={() => void handleCopy(i, m.content)}
                          aria-label={t('copy')}
                        >
                          <CustomIcon name={isCopied ? 'check' : 'copy'} />
                        </CustomButton>
                      </CustomTooltip>
                      <CustomTooltip title={t('editAndResend')}>
                        <CustomButton
                          variant="text"
                          size="small"
                          onClick={() => setEditingIndex(i)}
                          aria-label={t('edit')}
                        >
                          <CustomIcon name="edit" />
                        </CustomButton>
                      </CustomTooltip>
                      <CustomTooltip title={t('resend')}>
                        <CustomButton
                          variant="text"
                          size="small"
                          onClick={() => onResend?.(i, m.content)}
                          aria-label={t('resend')}
                        >
                          <CustomIcon name="reload" />
                        </CustomButton>
                      </CustomTooltip>
                    </>
                  ) : (
                    <>
                      <MessageSpeakButton
                        content={m.content}
                        disabled={!m.content}
                        onSpeakingChange={(speaking) =>
                          setSpeakingIndex((curr) => {
                            if (speaking) return i;
                            return curr === i ? null : curr;
                          })
                        }
                      />
                      <CustomTooltip title={isCopied ? t('copied') : t('copyReply')}>
                        <CustomButton
                          variant="text"
                          size="small"
                          onClick={() => void handleCopy(i, m.content)}
                          aria-label={t('copyReply')}
                          disabled={!m.content}
                        >
                          <CustomIcon name={isCopied ? 'check' : 'copy'} />
                        </CustomButton>
                      </CustomTooltip>
                      {canRegenerate && (
                        <CustomTooltip title={t('regenerate')}>
                          <CustomButton
                            variant="text"
                            size="small"
                            onClick={() =>
                              onResend?.(i - 1, messages[i - 1].content)
                            }
                            aria-label={t('regenerate')}
                          >
                            <CustomIcon name="reload" />
                          </CustomButton>
                        </CustomTooltip>
                      )}
                    </>
                  )}
                </div>
              )}
            </motion.div>
          );
        })}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default ChatThread;
