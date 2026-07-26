import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { suggestionsApi } from '@apiCalls/suggestions';

interface PromptSuggestionsProps {
  assistantId: string;
  lang: string;
  /** Prefill + send the clicked prompt. */
  onSelect: (text: string) => void;
  disabled?: boolean;
  /** How many chips to show at once (the rest rotate in over time). */
  count?: number;
}

/** How often the visible subset rotates. */
const ROTATE_MS = 9000;

// Personalize at most once per assistant per browser session: the first time an assistant's empty
// screen is shown we kick off a background regeneration from the user's memories + recent chats, so
// later loads surface user-relevant chips without blocking (or repeating) on every mount.
const personalizedThisSession = new Set<string>();

const rotate = <T,>(arr: T[], by: number): T[] =>
  arr.length ? arr.slice(by % arr.length).concat(arr.slice(0, by % arr.length)) : arr;

const PromptSuggestions: React.FC<PromptSuggestionsProps> = ({
  assistantId,
  lang,
  onSelect,
  disabled,
  count = 4,
}) => {
  const [pool, setPool] = useState<string[]>([]);
  const [offset, setOffset] = useState(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!assistantId) {
      setPool([]);
      return;
    }
    let cancelled = false;
    setOffset(0);

    const load = async () => {
      try {
        const items = await suggestionsApi.list(assistantId, lang);
        if (!cancelled && mounted.current) setPool(items);
      } catch {
        if (!cancelled && mounted.current) setPool([]);
      }
    };

    void load();

    // Fire-and-forget personalization once per assistant/session, then refresh the pool.
    const key = `${assistantId}:${lang}`;
    if (!personalizedThisSession.has(key)) {
      personalizedThisSession.add(key);
      suggestionsApi
        .generate(assistantId, lang, true)
        .then((items) => {
          if (!cancelled && mounted.current && items.length) setPool(items);
        })
        .catch(() => personalizedThisSession.delete(key));
    }

    return () => {
      cancelled = true;
    };
  }, [assistantId, lang]);

  useEffect(() => {
    if (pool.length <= count) return undefined;
    const id = window.setInterval(() => setOffset((o) => o + count), ROTATE_MS);
    return () => window.clearInterval(id);
  }, [pool.length, count]);

  const shown = useMemo(
    () => rotate(pool, offset).slice(0, count),
    [pool, offset, count]
  );

  if (!shown.length) return null;

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        justifyContent: 'center',
        gap: '0.5rem',
        maxWidth: 640,
        margin: '1.25rem auto 0',
      }}
    >
      <AnimatePresence mode="popLayout">
        {shown.map((text) => (
          <motion.button
            key={text}
            type="button"
            disabled={disabled}
            onClick={() => onSelect(text)}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.25 }}
            className="prompt-suggestion-chip"
            style={{
              cursor: disabled ? 'default' : 'pointer',
              border: '1px solid var(--border, rgba(0,0,0,0.12))',
              background: 'var(--card, rgba(255,255,255,0.6))',
              color: 'var(--foreground, inherit)',
              borderRadius: 9999,
              padding: '0.5rem 0.9rem',
              fontSize: '0.85rem',
              lineHeight: 1.2,
              opacity: disabled ? 0.5 : 1,
              maxWidth: '100%',
            }}
          >
            {text}
          </motion.button>
        ))}
      </AnimatePresence>
    </div>
  );
};

export default PromptSuggestions;
