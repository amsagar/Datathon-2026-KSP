import { create } from 'zustand';
import { sessionsApi, assistantsApi, stylesApi, chatApi } from '@apiCalls/services';
import { openChatStream } from '@apiCalls/chatStream';
import { useLangStore } from '@store/useLangStore';
import { STRINGS } from '@constants/translations';
import type {
  ChatSessionDto,
  UiChatMessage,
  UiToolCall,
  PendingClarification,
  PendingSkillUpdateProposal,
} from '@interfaces/chat.interface';
import {
  answersOutputJson,
  enrichAskUserTool,
  questionsInputJson,
} from '@utils/clarificationFromTool';
import type { AssistantDto } from '@interfaces/assistant.interface';
import type { ResponseStyleDto } from '@interfaces/style.interface';

interface ChatStoreState {
  // Session list state
  sessions: ChatSessionDto[];
  showArchived: boolean;
  currentId: string | null;

  // Assistant state
  assistants: AssistantDto[];
  selectedAssistantId: string;

  // Response styles
  styles: ResponseStyleDto[];
  /** Style to apply to the *next* new chat. Overridden per-session once a
   *  chat exists (sessions store their own styleId). */
  selectedStyleId: string;

  // Temporary Chat: persisted + viewable, but auto-deleted after the retention window and isolated
  // from long-term memory. Reflects whether the *current* conversation (draft or open) is temporary.
  temporary: boolean;

  // Active conversation
  messages: UiChatMessage[];
  streaming: boolean;
  /** Latest `status` SSE from the active turn (shown while thinking / running tools). */
  streamStatus: string | null;
  pendingClarification: PendingClarification | null;
  pendingSkillUpdateProposal: PendingSkillUpdateProposal | null;
  messagesLoading: boolean;
  sessionsLoading: boolean;

  // ---- actions ----
  refreshSessions: (archived?: boolean) => Promise<void>;
  refreshAssistants: () => Promise<void>;
  refreshStyles: (assistantId: string) => Promise<void>;
  setShowArchived: (archived: boolean) => void;
  setSelectedAssistantId: (id: string) => void;
  setSelectedStyleId: (id: string) => void;

  openSession: (id: string) => Promise<void>;
  newChat: () => Promise<void>;
  clearSelection: () => void;
  /** Start a new temporary-chat draft (persisted on first send, auto-deleted after retention). */
  setTemporary: (on: boolean) => void;

  renameSession: (id: string, title: string) => Promise<void>;
  toggleArchive: (id: string, archived: boolean) => Promise<void>;
  deleteSession: (id: string) => Promise<void>;
  setSessionStyle: (id: string, styleId: string) => Promise<void>;

  send: (text: string) => Promise<void>;
  resendFromUser: (userIndex: number, text: string) => Promise<void>;
  submitClarification: (answers: Record<string, string>) => Promise<void>;
  submitSkillUpdateDecision: (approved: boolean, rejectionReason?: string) => Promise<void>;
  stopStream: () => void;

  // Internal: shared by send / resendFromUser
  _runTurn: (sessionId: string, text: string) => void;
  _closeStream: () => void;
}

let activeStreamClose: (() => void) | null = null;
let sessionLoadGeneration = 0;

const toUiMessage = (m: {
  role: 'user' | 'assistant' | 'system';
  content: string;
  tools?: { id: string; name: string; input: string; output: string; error: boolean }[];
}): UiChatMessage => {
  const tools = (m.tools || []).map<UiToolCall>((t) =>
    enrichAskUserTool({
      id: t.id,
      name: t.name,
      input: t.input,
      output: t.output,
      error: t.error,
      running: false,
    })
  );
  return {
    role: m.role,
    content: m.content,
    tools,
    clarification: null,
  };
};

export const useChatStore = create<ChatStoreState>((set, get) => ({
  sessions: [],
  showArchived: false,
  currentId: null,
  assistants: [],
  selectedAssistantId: '',
  styles: [],
  selectedStyleId: '',
  temporary: false,
  messages: [],
  streaming: false,
  streamStatus: null,
  pendingClarification: null,
  pendingSkillUpdateProposal: null,
  messagesLoading: false,
  sessionsLoading: false,

  refreshSessions: async (archived) => {
    const isArchived = archived ?? get().showArchived;
    set({ sessionsLoading: true });
    try {
      const sessions = await sessionsApi.list(isArchived);
      set({ sessions });
    } finally {
      set({ sessionsLoading: false });
    }
  },

  refreshAssistants: async () => {
    const assistants = await assistantsApi.list();
    set((state) => ({
      assistants,
      selectedAssistantId:
        state.selectedAssistantId &&
        assistants.some((a) => a.id === state.selectedAssistantId)
          ? state.selectedAssistantId
          : assistants[0]?.id || '',
    }));
  },

  refreshStyles: async (assistantId) => {
    if (!assistantId) {
      set({ styles: [], selectedStyleId: '' });
      return;
    }
    const styles = await stylesApi.list(assistantId);
    const defaultId =
      styles.find((s) => s.defaultStyle)?.id || '';
    set((state) => ({
      styles,
      selectedStyleId: state.currentId ? state.selectedStyleId : defaultId,
    }));
  },

  setShowArchived: (archived) => {
    set({ showArchived: archived });
    void get().refreshSessions(archived);
  },

  setSelectedAssistantId: (id) => set({ selectedAssistantId: id }),

  setSelectedStyleId: (id) => set({ selectedStyleId: id }),

  openSession: async (id) => {
    get()._closeStream();
    const gen = ++sessionLoadGeneration;
    set({
      currentId: id,
      temporary: get().sessions.find((s) => s.id === id)?.temporary ?? false,
      messages: [],
      messagesLoading: true,
      streaming: false,
      streamStatus: null,
    });
    try {
      const msgs = await sessionsApi.messages(id);
      if (gen !== sessionLoadGeneration) return;
      set({ messages: msgs.map(toUiMessage), messagesLoading: false });
    } catch {
      if (gen !== sessionLoadGeneration) return;
      set({ messages: [], messagesLoading: false });
    }
  },

  // "+ New chat" is purely a local "draft" reset — it deliberately does NOT
  // hit the backend. A real ChatSession is created lazily by `send()` when
  // the user actually sends their first message. This avoids the bug where
  // every click on the button spawned an empty "New chat" row.
  newChat: async () => {
    get()._closeStream();
    set({
      currentId: null,
      temporary: false,
      messages: [],
      streaming: false,
      streamStatus: null,
      pendingClarification: null,
      pendingSkillUpdateProposal: null,
      messagesLoading: false,
      showArchived: false,
    });
  },

  clearSelection: () =>
    set({
      currentId: null,
      temporary: false,
      messages: [],
      pendingClarification: null,
      pendingSkillUpdateProposal: null,
      messagesLoading: false,
    }),

  // A temporary-chat draft: like New chat, but the session created on first send is flagged
  // temporary (persisted + viewable, auto-deleted after retention, isolated from long-term memory).
  setTemporary: (on) => {
    get()._closeStream();
    set({
      temporary: on,
      currentId: null,
      messages: [],
      streaming: false,
      streamStatus: null,
      pendingClarification: null,
      pendingSkillUpdateProposal: null,
      messagesLoading: false,
      showArchived: false,
    });
  },

  renameSession: async (id, title) => {
    if (!title.trim()) return;
    await sessionsApi.update(id, { title: title.trim() });
    await get().refreshSessions();
  },

  toggleArchive: async (id, archived) => {
    await sessionsApi.update(id, { archived });
    if (get().currentId === id) {
      set({
        currentId: null,
        messages: [],
        pendingClarification: null,
        pendingSkillUpdateProposal: null,
        messagesLoading: false,
      });
    }
    await get().refreshSessions();
  },

  deleteSession: async (id) => {
    await sessionsApi.delete(id);
    if (get().currentId === id) {
      set({
        currentId: null,
        messages: [],
        pendingClarification: null,
        pendingSkillUpdateProposal: null,
        messagesLoading: false,
      });
    }
    await get().refreshSessions();
  },

  setSessionStyle: async (id, styleId) => {
    const updated = await sessionsApi.setStyle(id, styleId);
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === id ? { ...s, styleId: updated.styleId } : s
      ),
    }));
  },

  send: async (text) => {
    const trimmed = text.trim();
    if (!trimmed || get().streaming) return;
    // Claim the turn synchronously. The `get().streaming` check above is not
    // enough on the first message of a new chat: `streaming` is only set later
    // in `_runTurn`, so a double-submit (double-Enter / Enter+click) during the
    // awaited session-create below would slip through and dispatch the turn
    // twice — which the server collapses into a duplicate assistant reply
    // (the duplicate user message is deduped, but both answers persist).
    set({ streaming: true });

    let sid = get().currentId;
    if (!sid) {
      try {
        let s = await sessionsApi.create(
          get().selectedAssistantId || undefined,
          get().temporary
        );
        const preStyleId = get().selectedStyleId;
        if (preStyleId) {
          try {
            s = await sessionsApi.setStyle(s.id, preStyleId);
          } catch (err) {
            console.error('Failed to apply preselected style', err);
          }
        }
        sid = s.id;
        set((state) => ({ sessions: [s, ...state.sessions], currentId: sid }));
      } catch (err) {
        console.error('Failed to create session', err);
        set({ streaming: false });
        return;
      }
    }
    get()._runTurn(sid, trimmed);
  },

  resendFromUser: async (userIndex, text) => {
    const trimmed = text.trim();
    const sid = get().currentId;
    if (get().streaming || !sid || userIndex < 0 || !trimmed) return;
    get()._closeStream();
    try {
      await sessionsApi.truncate(sid, userIndex);
    } catch (err) {
      console.error(err);
      return;
    }
    set((state) => ({ messages: state.messages.slice(0, userIndex) }));
    get()._runTurn(sid, trimmed);
  },

  submitClarification: async (answers) => {
    const pending = get().pendingClarification;
    if (!pending || pending.submitting) return;
    set({
      pendingClarification: { ...pending, submitting: true, error: undefined },
    });
    try {
      await chatApi.submitClarifications({
        requestId: pending.requestId,
        answers,
      });
      set((state) => {
        const next = [...state.messages];
        const last = next[next.length - 1];
        if (last?.role === 'assistant') {
          const askTool = enrichAskUserTool({
            id: pending.callId,
            name: 'AskUserQuestionTool',
            input: questionsInputJson(pending.questions),
            output: answersOutputJson(answers),
            running: false,
          });
          const hasAsk = last.tools.some((t) => t.id === pending.callId);
          const tools = hasAsk
            ? last.tools.map((t) =>
                t.id === pending.callId ? askTool : t
              )
            : [...last.tools, askTool];
          next[next.length - 1] = {
            ...last,
            tools,
            clarification: null,
          };
        }
        return {
          messages: next,
          pendingClarification: null,
          streamStatus: STRINGS.thinking[useLangStore.getState().lang],
          streaming: true,
        };
      });
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      const message =
        status === 404
          ? 'This question expired — send your message again.'
          : 'Failed to submit answers. Please try again.';
      set({
        pendingClarification: { ...pending, submitting: false, error: message },
      });
    }
  },

  submitSkillUpdateDecision: async (approved, rejectionReason) => {
    const pending = get().pendingSkillUpdateProposal;
    if (!pending || pending.submitting) return;
    set({
      pendingSkillUpdateProposal: { ...pending, submitting: true, error: undefined },
    });
    try {
      await chatApi.submitSkillUpdateDecision({
        requestId: pending.requestId,
        approved,
        rejectionReason,
      });
      set((state) => {
        const next = [...state.messages];
        const last = next[next.length - 1];
        if (last?.role === 'assistant') {
          const output = approved
            ? `Approved skill update for ${pending.skillName} (${pending.filePath}).`
            : `Rejected skill update${rejectionReason ? `: ${rejectionReason}` : ''}.`;
          const skillTool = {
            id: pending.callId,
            name: 'propose_skill_update',
            input: JSON.stringify({
              skillId: pending.skillId,
              filePath: pending.filePath,
              summary: pending.summary,
            }),
            output,
            error: !approved,
            running: false,
          };
          const hasTool = last.tools.some((t) => t.id === pending.callId);
          const tools = hasTool
            ? last.tools.map((t) =>
                t.id === pending.callId ? skillTool : t
              )
            : [...last.tools, skillTool];
          next[next.length - 1] = { ...last, tools };
        }
        return {
          messages: next,
          pendingSkillUpdateProposal: null,
          streamStatus: STRINGS.thinking[useLangStore.getState().lang],
          streaming: true,
        };
      });
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      const message =
        status === 404
          ? 'This proposal expired — send your message again.'
          : 'Failed to submit decision. Please try again.';
      set({
        pendingSkillUpdateProposal: { ...pending, submitting: false, error: message },
      });
    }
  },

  stopStream: () => {
    if (!get().streaming) return;
    get()._closeStream();
    set((state) => {
      const next = [...state.messages];
      const last = next[next.length - 1];
      if (last && last.role === 'assistant') {
        next[next.length - 1] = {
          ...last,
          content: last.content + '\n\n_(stopped)_',
          tools: last.tools.map((t) =>
            t.running ? { ...t, running: false } : t
          ),
        };
      }
      return {
        messages: next,
        streaming: false,
        streamStatus: null,
        pendingClarification: null,
        pendingSkillUpdateProposal: null,
      };
    });
    // No full session refetch on stop — patch the current session locally.
    // First turn still refreshes once so the server-generated title shows up.
    const sid = get().currentId;
    if (get().messages.length <= 2) {
      void get().refreshSessions();
    } else if (sid) {
      set((state) => ({
        sessions: state.sessions.map((s) =>
          s.id === sid ? { ...s, updatedAt: Date.now() } : s
        ),
      }));
    }
  },

  _closeStream: () => {
    activeStreamClose?.();
    activeStreamClose = null;
  },

  _runTurn: (sessionId, text) => {
    get()._closeStream();
    set((state) => ({
      messages: [
        ...state.messages,
        { role: 'user', content: text, tools: [] },
        { role: 'assistant', content: '', tools: [] },
      ],
      streaming: true,
      streamStatus: STRINGS.thinking[useLangStore.getState().lang],
      pendingClarification: null,
      pendingSkillUpdateProposal: null,
    }));

    const session = get().sessions.find((s) => s.id === sessionId);
    const styleId = session?.styleId || undefined;

    const updateLast = (
      fn: (msg: UiChatMessage) => UiChatMessage
    ) =>
      set((state) => {
        const next = [...state.messages];
        next[next.length - 1] = fn(next[next.length - 1]);
        return { messages: next };
      });

    // Token micro-batcher: SSE chunks can arrive far faster than 60fps; setting
    // store state per chunk re-renders the thread per token. Buffer incoming
    // text and flush at most every ~30ms. Flushed synchronously before any
    // event that reads/reorders the message (tools, done, error, close) so no
    // text is lost or misordered.
    let pendingText = '';
    let flushTimer: number | null = null;
    const flushPending = () => {
      if (flushTimer !== null) {
        window.clearTimeout(flushTimer);
        flushTimer = null;
      }
      if (!pendingText) return;
      const chunk = pendingText;
      pendingText = '';
      updateLast((last) => ({ ...last, content: last.content + chunk }));
    };

    // End-of-turn bookkeeping. A full session-list refetch per turn is wasteful;
    // patch the current session locally instead. Exception: the first turn, where
    // the server generates the session title asynchronously — one refresh picks
    // it up (user message + assistant reply => length <= 2).
    const finishTurn = () => {
      const { messages, sessions } = get();
      if (messages.length <= 2) {
        void get().refreshSessions();
        return;
      }
      set({
        sessions: sessions.map((s) =>
          s.id === sessionId ? { ...s, updatedAt: Date.now() } : s
        ),
      });
    };

    const handle = openChatStream(
      { sessionId, message: text, styleId, lang: useLangStore.getState().lang },
      {
        onMessage: (e) => {
          pendingText += e.text;
          if (flushTimer === null) {
            flushTimer = window.setTimeout(flushPending, 30);
          }
        },
        onTool: (t) => {
          flushPending();
          updateLast((last) => ({
            ...last,
            tools: [
              ...last.tools,
              enrichAskUserTool({
                id: t.id,
                name: t.name,
                input: t.input,
                output: null,
                running: true,
              }),
            ],
          }));
        },
        onToolResult: (r) => {
          flushPending();
          updateLast((last) => ({
            ...last,
            tools: last.tools.map((t) =>
              t.id === r.id
                ? enrichAskUserTool({
                    ...t,
                    output: r.output,
                    error: r.error,
                    running: false,
                  })
                : t
            ),
          }));
        },
        onClarification: (e) => {
          flushPending();
          const callId = e.callId || e.requestId;
          set({
            pendingClarification: {
              requestId: e.requestId,
              callId,
              questions: e.questions,
              submitting: false,
            },
            pendingSkillUpdateProposal: null,
          });
          updateLast((last) => {
            const input = questionsInputJson(e.questions);
            const existing = last.tools.find((t) => t.id === callId);
            const tools = existing
              ? last.tools.map((t) =>
                  t.id === callId
                    ? enrichAskUserTool({
                        ...t,
                        input,
                        running: true,
                      })
                    : t
                )
              : [
                  ...last.tools,
                  enrichAskUserTool({
                    id: callId,
                    name: 'AskUserQuestionTool',
                    input,
                    output: null,
                    running: true,
                  }),
                ];
            return { ...last, tools, clarification: null };
          });
        },
        onSkillUpdateProposal: (e) => {
          flushPending();
          const callId = e.callId || e.requestId;
          set({
            pendingSkillUpdateProposal: {
              requestId: e.requestId,
              callId,
              skillId: e.skillId,
              skillName: e.skillName,
              filePath: e.filePath,
              summary: e.summary,
              feedbackQuote: e.feedbackQuote,
              currentContent: e.currentContent,
              proposedContent: e.proposedContent,
              submitting: false,
            },
            pendingClarification: null,
          });
          updateLast((last) => {
            const input = JSON.stringify({
              skillId: e.skillId,
              filePath: e.filePath,
              summary: e.summary,
            });
            const existing = last.tools.find((t) => t.id === callId);
            const tools = existing
              ? last.tools.map((t) =>
                  t.id === callId
                    ? { ...t, name: 'propose_skill_update', input, running: true }
                    : t
                )
              : [
                  ...last.tools,
                  {
                    id: callId,
                    name: 'propose_skill_update',
                    input,
                    output: null,
                    running: true,
                  },
                ];
            return { ...last, tools };
          });
        },
        onStatus: (e) => {
          if (e?.text) set({ streamStatus: e.text });
        },
        onError: (err) => {
          flushPending();
          if (err?.text) {
            updateLast((last) => ({
              ...last,
              content: last.content + `\n\n_Error: ${err.text}_`,
            }));
          }
          set({
            streaming: false,
            streamStatus: null,
            pendingClarification: null,
            pendingSkillUpdateProposal: null,
          });
          activeStreamClose = null;
          finishTurn();
        },
        onDone: () => {
          flushPending();
          set({
            streaming: false,
            streamStatus: null,
            pendingClarification: null,
            pendingSkillUpdateProposal: null,
          });
          activeStreamClose = null;
          finishTurn();
        },
      }
    );

    // Flush buffered text before closing so a user "stop" keeps every token
    // received so far (and no stale timer fires into a replaced session).
    activeStreamClose = () => {
      flushPending();
      handle.close();
    };
  },
}));
