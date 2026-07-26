import React, { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import ArtifactFrame from '@molecules/ArtifactFrame';
import { normalizeMarkdown } from '@utils/normalizeMarkdown';
import * as styles from '@styles/markdownContent.module.scss';

export interface MarkdownContentProps {
  source: string;
  /** Tighter typography for tool cards / side panels */
  compact?: boolean;
  /** True while this message is still receiving SSE chunks (gates the artifact placeholder). */
  streaming?: boolean;
  className?: string;
}

const ARTIFACT_FENCE = '```artifact';

/** Concatenated text of a hast node's descendants (a code block's raw content). */
const textOf = (node: unknown): string => {
  if (!node || typeof node !== 'object') return '';
  const n = node as { value?: string; children?: unknown[] };
  if (typeof n.value === 'string') return n.value;
  return (n.children ?? []).map(textOf).join('');
};

/** True when a hast <code> node carries the given `language-*` class. */
const isCodeLang = (node: unknown, lang: string): boolean => {
  const n = node as {
    tagName?: string;
    properties?: { className?: unknown };
  } | null;
  if (!n || n.tagName !== 'code') return false;
  const cls = n.properties?.className;
  const classes = Array.isArray(cls) ? cls.join(' ') : String(cls ?? '');
  return classes.includes(`language-${lang}`);
};

/**
 * While streaming, the text after the last unterminated fence of `marker` — i.e. the still-arriving
 * block — or null if every such fence is closed. Used to gate the placeholder for incomplete blocks.
 */
const computeOpenTail = (text: string, marker: string): string | null => {
  const lastOpen = text.lastIndexOf(marker);
  if (lastOpen < 0) return null;
  const closed = text.indexOf('\n```', lastOpen + marker.length) !== -1;
  return closed ? null : text.slice(lastOpen + marker.length);
};

const MarkdownContent: React.FC<MarkdownContentProps> = ({
  source,
  compact,
  streaming,
  className,
}) => {
  const prepared = useMemo(() => normalizeMarkdown(source), [source]);

  // An unterminated ```artifact fence is parsed as a code block running to the end of the
  // message; while streaming, that trailing block is "incomplete" and shows a placeholder.
  const artifactTail = useMemo(
    () => (streaming ? computeOpenTail(prepared, ARTIFACT_FENCE) : null),
    [prepared, streaming],
  );

  const components = useMemo(
    () => ({
      pre: (props: React.ComponentProps<'pre'> & { node?: unknown }) => {
        const { node, children, ...rest } = props;
        const codeNode = (node as { children?: unknown[] } | undefined)
          ?.children?.[0];
        // Only the trailing fence can be unterminated, so the incomplete block is the one
        // whose content matches the open tail of the source.
        if (isCodeLang(codeNode, 'artifact')) {
          const html = textOf(codeNode);
          const complete = artifactTail === null || artifactTail.trim() !== html.trim();
          return <ArtifactFrame html={html} complete={complete} />;
        }
        return <pre {...rest}>{children}</pre>;
      },
    }),
    [artifactTail],
  );

  return (
    <div
      className={[
        styles.prose,
        compact ? styles.proseCompact : undefined,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {prepared}
      </ReactMarkdown>
    </div>
  );
};

// Memoized: during SSE streaming only the active message's `source` changes;
// every other bubble's markdown parse is skipped entirely.
export default React.memo(MarkdownContent);
