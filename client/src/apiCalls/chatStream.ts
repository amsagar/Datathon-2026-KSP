import { qs } from './makeApiRequest';
import { directApiUrl } from '@config/runtimeConfig';
import { getAuthToken, clearAuthToken, redirectToSso } from '@apiCalls/auth';
import type {
  SseMessageEvent,
  SseToolEvent,
  SseToolResultEvent,
  SseClarificationEvent,
  SseSkillUpdateProposalEvent,
  SseStatusEvent,
  SseErrorEvent,
} from '@interfaces/chat.interface';

export interface ChatStreamHandlers {
  onMessage?: (e: SseMessageEvent) => void;
  onTool?: (e: SseToolEvent) => void;
  onToolResult?: (e: SseToolResultEvent) => void;
  onClarification?: (e: SseClarificationEvent) => void;
  onSkillUpdateProposal?: (e: SseSkillUpdateProposalEvent) => void;
  onStatus?: (e: SseStatusEvent) => void;
  onError?: (e: SseErrorEvent | null) => void;
  onDone?: () => void;
}

export interface ChatStreamHandle {
  close: () => void;
}

export interface ChatStreamParams {
  sessionId: string;
  message: string;
  styleId?: string;
  /** UI language ('en' | 'kn') — threaded to the backend so the assistant actually answers in it,
   * not just the chrome around it. */
  lang?: string;
}

/**
 * Open a streaming connection to /api/chat/stream (direct to API host when
 * runtime-config sets streamApiBase; same-origin /api in local dev). Named SSE
 * events: `message` (token chunks), `status` (prep/tool progress), `tool`,
 * `tool_result`, `clarification`, `done`, `error`. Comment lines (`: keep-alive`)
 * are ignored.
 *
 * We use `fetch` + a ReadableStream reader (rather than the browser's
 * `EventSource`) because EventSource cannot send the `Authorization` header the
 * SSO JWT requires. The SSE wire format is parsed manually below.
 */
export const openChatStream = (
  params: ChatStreamParams,
  handlers: ChatStreamHandlers
): ChatStreamHandle => {
  const query = qs({
    sessionId: params.sessionId,
    message: params.message,
    styleId: params.styleId || undefined,
    lang: params.lang || undefined,
  });

  const abort = new AbortController();
  const token = getAuthToken();

  const dispatch = (event: string, data: string) => {
    switch (event) {
      case 'message':
        try {
          handlers.onMessage?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onMessage parse', err);
        }
        break;
      case 'tool':
        try {
          handlers.onTool?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onTool parse', err);
        }
        break;
      case 'tool_result':
        try {
          handlers.onToolResult?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onToolResult parse', err);
        }
        break;
      case 'clarification':
        try {
          handlers.onClarification?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onClarification parse', err);
        }
        break;
      case 'skill_update_proposal':
        try {
          handlers.onSkillUpdateProposal?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onSkillUpdateProposal parse', err);
        }
        break;
      case 'status':
        try {
          handlers.onStatus?.(JSON.parse(data));
        } catch (err) {
          console.error('chatStream onStatus parse', err);
        }
        break;
      case 'done':
        handlers.onDone?.();
        abort.abort();
        break;
      case 'error': {
        let payload: SseErrorEvent | null = null;
        if (data) {
          try {
            payload = JSON.parse(data);
          } catch {
            payload = { text: data } as SseErrorEvent;
          }
        }
        handlers.onError?.(payload);
        abort.abort();
        break;
      }
      default:
        break;
    }
  };

  // Parse one raw SSE frame (lines separated by \n, frames by a blank line).
  const handleFrame = (frame: string) => {
    let event = 'message';
    const dataLines: string[] = [];
    for (const rawLine of frame.split('\n')) {
      const line = rawLine.replace(/\r$/, '');
      if (!line || line.startsWith(':')) continue;
      const idx = line.indexOf(':');
      const field = idx === -1 ? line : line.slice(0, idx);
      let value = idx === -1 ? '' : line.slice(idx + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') event = value;
      else if (field === 'data') dataLines.push(value);
    }
    if (dataLines.length || event !== 'message') {
      dispatch(event, dataLines.join('\n'));
    }
  };

  (async () => {
    try {
      const response = await fetch(directApiUrl(`/api/chat/stream${query}`), {
        method: 'GET',
        signal: abort.signal,
        headers: {
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });

      if (response.status === 401 || response.status === 403) {
        clearAuthToken();
        redirectToSso();
        return;
      }
      if (!response.ok || !response.body) {
        handlers.onError?.({ text: `HTTP ${response.status}` } as SseErrorEvent);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) !== -1) {
          const frame = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          if (frame.trim()) handleFrame(frame);
        }
      }
      if (buffer.trim()) handleFrame(buffer);
    } catch (err) {
      if ((err as Error)?.name === 'AbortError') return;
      console.error('chatStream fetch error', err);
      handlers.onError?.(null);
    }
  })();

  return { close: () => abort.abort() };
};
