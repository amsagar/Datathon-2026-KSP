import React, { Suspense, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import CustomLayout from '@templates/CustomLayout';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import CustomSpinner from '@atoms/CustomSpinner';
import CustomTooltip from '@atoms/CustomTooltip';
import TemporaryChatIcon from '@atoms/TemporaryChatIcon';
import ChatSidebar from '@organisms/ChatSidebar';
import ChatThread from '@organisms/ChatThread';
import Composer from '@organisms/Composer';
import ShareChatDialog from '@organisms/ShareChatDialog';
import FieldLabel from '@molecules/FieldLabel';
import { useChatStore } from '@store/useChatStore';
import { useNotification } from '@providers/NotificationProviders';
import { useSidebarCollapse } from '@utils/useSidebarCollapse';
import { useSidebarWidth } from '@utils/useSidebarWidth';
import { useLangStore } from '@store/useLangStore';
import { useExportModeStore } from '@store/useExportModeStore';
import { useT } from '@constants/translations';
import type { AnalyticsChatTab } from '@constants/routePaths';
import * as styles from '@styles/chatWorkspace.module.scss';

// Loaded on demand: the analytics views pull in recharts/leaflet/force-graph.
// Users who never open the analytics tabs never download them.
const AnalyticsPanel = React.lazy(() => import('@organisms/AnalyticsPanel'));

const ChatWorkspace: React.FC = () => {
  const openNotification = useNotification();
  const { collapsed, toggle: toggleSidebar } = useSidebarCollapse();
  const { width: sidebarWidth, resizing, beginResize } = useSidebarWidth();
  const [shareOpen, setShareOpen] = useState(false);
  const [params, setParams] = useSearchParams();
  const analyticsTab = (params.get('analytics') as AnalyticsChatTab | null) || null;
  const analyticsOpen = !!analyticsTab;
  const uiLang = useLangStore((s) => s.lang);
  const toggleLang = useLangStore((s) => s.toggle);
  const t = useT();
  const exporting = useExportModeStore((s) => s.exporting);
  const setExporting = useExportModeStore((s) => s.setExporting);

  // Per-field selectors: subscribing to the whole store would re-render this
  // page on every SSE token. Zustand action references are stable.
  const sessions = useChatStore((s) => s.sessions);
  const showArchived = useChatStore((s) => s.showArchived);
  const currentId = useChatStore((s) => s.currentId);
  const assistants = useChatStore((s) => s.assistants);
  const selectedAssistantId = useChatStore((s) => s.selectedAssistantId);
  const responseStyles = useChatStore((s) => s.styles);
  const selectedStyleId = useChatStore((s) => s.selectedStyleId);
  const messages = useChatStore((s) => s.messages);
  const streaming = useChatStore((s) => s.streaming);
  const streamStatus = useChatStore((s) => s.streamStatus);
  const messagesLoading = useChatStore((s) => s.messagesLoading);
  const sessionsLoading = useChatStore((s) => s.sessionsLoading);
  const refreshSessions = useChatStore((s) => s.refreshSessions);
  const refreshAssistants = useChatStore((s) => s.refreshAssistants);
  const refreshStyles = useChatStore((s) => s.refreshStyles);
  const setShowArchived = useChatStore((s) => s.setShowArchived);
  const setSelectedAssistantId = useChatStore((s) => s.setSelectedAssistantId);
  const setSelectedStyleId = useChatStore((s) => s.setSelectedStyleId);
  const openSession = useChatStore((s) => s.openSession);
  const newChat = useChatStore((s) => s.newChat);
  const temporary = useChatStore((s) => s.temporary);
  const setTemporary = useChatStore((s) => s.setTemporary);
  const renameSession = useChatStore((s) => s.renameSession);
  const toggleArchive = useChatStore((s) => s.toggleArchive);
  const deleteSession = useChatStore((s) => s.deleteSession);
  const setSessionStyle = useChatStore((s) => s.setSessionStyle);
  const send = useChatStore((s) => s.send);
  const resendFromUser = useChatStore((s) => s.resendFromUser);
  const submitClarification = useChatStore((s) => s.submitClarification);
  const submitSkillUpdateDecision = useChatStore(
    (s) => s.submitSkillUpdateDecision
  );
  const pendingClarification = useChatStore((s) => s.pendingClarification);
  const pendingSkillUpdateProposal = useChatStore(
    (s) => s.pendingSkillUpdateProposal
  );
  const stopStream = useChatStore((s) => s.stopStream);

  useEffect(() => {
    void refreshSessions();
  }, [refreshSessions]);

  useEffect(() => {
    void refreshAssistants().catch((e) =>
      openNotification(e?.message || 'Failed to load assistants', 'Error')
    );
  }, [refreshAssistants, openNotification]);

  const wrap = <T extends unknown[]>(
    fn: (...args: T) => Promise<unknown> | unknown,
    label: string
  ) =>
    async (...args: T) => {
      try {
        await fn(...args);
      } catch (e) {
        const msg = (e as Error)?.message || `Failed to ${label}`;
        openNotification(msg, 'Error');
      }
    };

  const currentSession = sessions.find((s) => s.id === currentId) || null;
  const headerAssistantId =
    currentSession?.assistantId || selectedAssistantId || '';

  useEffect(() => {
    void refreshStyles(headerAssistantId).catch((e) =>
      openNotification(e?.message || 'Failed to load styles', 'Error')
    );
  }, [headerAssistantId, refreshStyles, openNotification]);

  const activeAssistant =
    assistants.find((a) => a.id === headerAssistantId) || null;

  const composerPlaceholder = activeAssistant
    ? uiLang === 'kn'
      ? `${activeAssistant.name} ಗೆ ಸಂದೇಶ…`
      : `Message ${activeAssistant.name}…`
    : assistants.length === 0
      ? uiLang === 'kn'
        ? 'ಚಾಟ್ ಪ್ರಾರಂಭಿಸಲು ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಸಹಾಯಕರನ್ನು ರಚಿಸಿ'
        : 'Create an assistant in Settings to start chatting'
      : uiLang === 'kn'
        ? 'ಚಾಟ್ ಪ್ರಾರಂಭಿಸಲು ಸಹಾಯಕರನ್ನು ಆಯ್ಕೆಮಾಡಿ'
        : 'Pick an assistant to start chatting';

  const assistantOptions =
    assistants.length === 0
      ? [{ value: '', label: t('noAssistantsYet') }]
      : assistants.map((a) => ({ value: a.id, label: a.name }));

  const defaultStyleId = useMemo(
    () => responseStyles.find((s) => s.defaultStyle)?.id || '',
    [responseStyles]
  );

  const styleOptions = useMemo(() => {
    const named = responseStyles.map((s) => ({
      value: s.id,
      label: s.name,
    }));
    if (named.length === 0) {
      return [{ value: '', label: t('defaultStyle') }];
    }
    if (defaultStyleId) {
      return named;
    }
    return [{ value: '', label: t('defaultStyle') }, ...named];
  }, [responseStyles, defaultStyleId, t]);

  const effectiveStyleId = currentSession
    ? currentSession.styleId || defaultStyleId || ''
    : selectedStyleId || defaultStyleId || '';

  const stylePlaceholder =
    responseStyles.length === 0
      ? t('defaultStyle')
      : defaultStyleId
        ? responseStyles.find((s) => s.id === defaultStyleId)?.name ||
          t('style')
        : t('defaultStyle');

  return (
    <CustomLayout className={styles.workspace}>
      <ChatSidebar
        collapsed={collapsed}
        onCollapse={toggleSidebar}
        width={sidebarWidth}
        resizing={resizing}
        onBeginResize={beginResize}
        showArchived={showArchived}
        onToggleArchived={setShowArchived}
        sessions={sessions}
        currentId={currentId}
        onOpenSession={(id) => {
          const next = new URLSearchParams(params);
          next.delete('analytics');
          next.delete('personUid');
          setParams(next, { replace: true });
          void openSession(id).catch((e) =>
            openNotification(
              (e as Error)?.message || 'Failed to open session',
              'Error'
            )
          );
        }}
        sessionsLoading={sessionsLoading}
        messagesLoading={messagesLoading}
        onRenameSession={wrap(renameSession, 'rename')}
        onToggleArchive={wrap(toggleArchive, 'archive')}
        onDeleteSession={wrap(deleteSession, 'delete')}
        onNewChat={async () => {
          const next = new URLSearchParams(params);
          next.delete('analytics');
          next.delete('personUid');
          setParams(next, { replace: true });
          await wrap(newChat, 'create chat')();
        }}
        analyticsTab={analyticsTab}
        onOpenAnalytics={(tab) => {
          setParams(new URLSearchParams({ analytics: tab }), { replace: true });
        }}
      />

      <main className={styles.chat}>
        {!analyticsOpen && (
        <header className={styles.header}>
          <div className={styles.headerRight}>
            <FieldLabel
              label={t('style')}
              info={
                currentSession
                  ? t('styleInfoSession')
                  : t('styleInfoNewChat')
              }
            >
              <CustomSelect
                className={styles.stylePicker}
                fullWidth={false}
                options={styleOptions}
                value={effectiveStyleId}
                onChange={(v) => {
                  const next = (v as string) || '';
                  if (currentSession) {
                    void wrap(setSessionStyle, 'apply style')(
                      currentSession.id,
                      next
                    );
                  } else {
                    setSelectedStyleId(next);
                  }
                }}
                placeholder={stylePlaceholder}
                allowClear={!defaultStyleId}
              />
            </FieldLabel>
            <FieldLabel
              label={t('assistant')}
              info={
                currentSession
                  ? t('assistantInfoSession')
                  : t('assistantInfoNewChat')
              }
            >
              <CustomSelect
                className={styles.assistantPicker}
                fullWidth={false}
                options={assistantOptions}
                value={headerAssistantId}
                onChange={(v) => setSelectedAssistantId(v as string)}
                placeholder={t('selectAssistant')}
                disabled={!!currentSession}
              />
            </FieldLabel>
            <CustomTooltip
              title={
                temporary
                  ? t('temporaryChatTooltipOn')
                  : t('temporaryChatTooltipOff')
              }
            >
              <CustomButton
                variant="text"
                size="small"
                shape="circle"
                onClick={() => setTemporary(!temporary)}
                aria-label={t('toggleTemporaryChat')}
                aria-pressed={temporary}
                disabled={!!currentSession}
                style={temporary ? { color: 'var(--red, #c8102e)' } : undefined}
              >
                <TemporaryChatIcon size={20} />
              </CustomButton>
            </CustomTooltip>
            <CustomTooltip
              title={
                uiLang === 'en' ? t('switchToKannada') : t('switchToEnglish')
              }
            >
              <CustomButton
                variant="text"
                size="small"
                onClick={toggleLang}
                aria-label={t('toggleLanguage')}
                style={{ fontWeight: 600 }}
              >
                {uiLang === 'en' ? 'ಕ' : 'EN'}
              </CustomButton>
            </CustomTooltip>
            {currentSession && messages.length > 0 && (
              <CustomTooltip title={t('saveConversationAsPdf')}>
                <CustomButton
                  variant="text"
                  size="small"
                  shape="circle"
                  disabled={exporting}
                  onClick={async () => {
                    // Wait a frame so any pending UI settles before the print clone.
                    // Tool names/payloads are intentionally not shown in chat or PDF.
                    setExporting(true);
                    await new Promise((resolve) => requestAnimationFrame(resolve));
                    try {
                      // Dynamic import: this module (and its print-window pipeline) is only
                      // fetched the first time someone actually exports a PDF — no html2canvas or
                      // jspdf involved, both were dropped in favor of the browser's own
                      // print-to-PDF pipeline (see exportChatPdf.ts's file header).
                      const { exportChatToPdf } = await import('@utils/exportChatPdf');
                      await exportChatToPdf('chat-thread-export', currentSession.title || 'conversation');
                    } catch (e) {
                      openNotification((e as Error)?.message || 'Failed to export PDF', 'Error');
                    } finally {
                      // window.print() does not expose a cross-browser "dialog closed" event, so
                      // this can only mean "the print dialog has been handed off to the browser",
                      // not "the user finished printing" — the spinner is a preparing/rendering
                      // indicator, not a wait-for-dialog indicator.
                      setExporting(false);
                    }
                  }}
                  aria-label={t('exportChatToPdf')}
                >
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden>
                    <path
                      d="M12 3v12m0 0 4-4m-4 4-4-4M5 21h14"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </CustomButton>
              </CustomTooltip>
            )}
            {currentSession && (
              <CustomTooltip title={t('shareChatViewOnly')}>
                <CustomButton
                  variant="text"
                  size="small"
                  shape="circle"
                  onClick={() => setShareOpen(true)}
                  aria-label={t('shareChat')}
                >
                  <svg
                    width="17"
                    height="17"
                    viewBox="0 0 24 24"
                    fill="none"
                    aria-hidden
                  >
                    <path
                      d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </CustomButton>
              </CustomTooltip>
            )}
          </div>
        </header>
        )}

        {analyticsOpen ? (
          <div className={styles.analyticsHost}>
            <Suspense
              fallback={
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flex: '1 1 auto',
                    minHeight: 240,
                  }}
                >
                  <CustomSpinner size="large" />
                </div>
              }
            >
              <AnalyticsPanel />
            </Suspense>
          </div>
        ) : (
          <>
        {temporary && (
          <div className={styles.temporaryBanner}>
            <strong className={styles.temporaryBannerTitle}>Temporary Chat</strong>
            This chat is saved for 30 days and then automatically deleted. It isn&apos;t used for
            long-term memory or model training.
          </div>
        )}

        {/* Export wrapper: a real box (exportChatPdf.ts walks scrollHeight to find the tallest
            scrollable container to clone — a display:contents element reports no box of its own)
            that preserves the column flex layout ChatThread expects. */}
        <div id="chat-thread-export" className={styles.threadExport}>
        <ChatThread
          messages={messages}
          streaming={streaming}
          streamStatus={streamStatus}
          pendingClarification={pendingClarification}
          pendingSkillUpdateProposal={pendingSkillUpdateProposal}
          messagesLoading={messagesLoading}
          hasSession={!!currentId}
          assistantName={activeAssistant?.name}
          assistantId={headerAssistantId || undefined}
          lang={uiLang}
          onSelectPrompt={(text) => void wrap(send, 'send')(text)}
          onResend={(idx, text) =>
            void wrap(resendFromUser, 'resend')(idx, text)
          }
          onSubmitClarification={(answers) =>
            void wrap(submitClarification, 'clarify')(answers)
          }
          onSubmitSkillUpdateDecision={(approved, rejectionReason) =>
            void wrap(submitSkillUpdateDecision, 'skill-update')(
              approved,
              rejectionReason
            )
          }
        />
        </div>

        <Composer
          streaming={streaming}
          disabled={
            messagesLoading ||
            !!pendingClarification ||
            !!pendingSkillUpdateProposal
          }
          onSend={(text) => void wrap(send, 'send')(text)}
          onStop={stopStream}
          placeholder={composerPlaceholder}
        />
          </>
        )}
      </main>

      {currentSession && (
        <ShareChatDialog
          open={shareOpen}
          sessionId={currentSession.id}
          onClose={() => setShareOpen(false)}
        />
      )}
    </CustomLayout>
  );
};

export default ChatWorkspace;
