import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import * as styles from '@styles/markdownContent.module.scss';

export interface ArtifactFrameProps {
  html: string;
  /** False while the fenced block is still streaming in — shows a placeholder instead. */
  complete: boolean;
}

const MIN_HEIGHT = 160;
const DEFAULT_HEIGHT = 420;

/**
 * Posts the document height to the parent so the inline iframe can size to its content. Works
 * without allow-same-origin: postMessage is permitted across opaque origins.
 */
const RESIZE_SCRIPT = `<script>(function () {
  function post() {
    parent.postMessage(
      { type: 'ksp-artifact-height', height: document.documentElement.scrollHeight },
      '*'
    );
  }
  window.addEventListener('load', post);
  if (window.ResizeObserver) {
    new ResizeObserver(post).observe(document.documentElement);
  }
})();</script>`;

const ExpandIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M9 3H5a2 2 0 0 0-2 2v4m18 0V5a2 2 0 0 0-2-2h-4M3 15v4a2 2 0 0 0 2 2h4m6 0h4a2 2 0 0 0 2-2v-4"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const DownloadIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M12 3v12m0 0 4-4m-4 4-4-4M5 21h14"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const CloseIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M18 6 6 18M6 6l12 12"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

/**
 * Renders model-generated artifact HTML in a sandboxed iframe, with an expand-to-fullscreen
 * control. The sandbox MUST stay "allow-scripts" only — never add allow-same-origin, or
 * artifact scripts could reach the app's DOM, storage, and auth tokens.
 */
const ArtifactFrame: React.FC<ArtifactFrameProps> = ({ html, complete }) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState(DEFAULT_HEIGHT);
  const [expanded, setExpanded] = useState(false);

  const onMessage = useCallback((event: MessageEvent) => {
    if (
      !iframeRef.current ||
      event.source !== iframeRef.current.contentWindow ||
      event.data?.type !== 'ksp-artifact-height'
    ) {
      return;
    }
    const reported = Number(event.data.height);
    if (Number.isFinite(reported) && reported > 0) {
      const max = Math.round(window.innerHeight * 0.7);
      setHeight(Math.min(Math.max(Math.ceil(reported), MIN_HEIGHT), max));
    }
  }, []);

  useEffect(() => {
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [onMessage]);

  // Escape to exit fullscreen + lock background scroll while expanded.
  useEffect(() => {
    if (!expanded) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setExpanded(false);
    };
    window.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [expanded]);

  // Downloads the raw artifact HTML (without the injected resize script) as a self-contained file.
  const download = useCallback(() => {
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'artifact.html';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }, [html]);

  if (!complete) {
    return (
      <div className={styles.artifactPlaceholder} aria-live="polite">
        <span className={styles.artifactPlaceholderLabel}>Generating artifact…</span>
      </div>
    );
  }

  const srcDoc = html + RESIZE_SCRIPT;

  return (
    <div className={styles.artifactWrap}>
      <div className={styles.artifactToolbar}>
        <button
          type="button"
          className={styles.artifactToolbarBtn}
          onClick={download}
          title="Download as HTML"
          aria-label="Download artifact as HTML"
        >
          <DownloadIcon />
        </button>
        <button
          type="button"
          className={styles.artifactToolbarBtn}
          onClick={() => setExpanded(true)}
          title="View full screen"
          aria-label="View artifact full screen"
        >
          <ExpandIcon />
        </button>
      </div>
      <iframe
        ref={iframeRef}
        className={styles.artifactFrame}
        style={{ height }}
        sandbox="allow-scripts"
        referrerPolicy="no-referrer"
        loading="lazy"
        srcDoc={srcDoc}
        title="Generated artifact"
      />

      {expanded &&
        createPortal(
          <div
            className={styles.artifactOverlay}
            role="dialog"
            aria-modal="true"
            aria-label="Artifact full screen"
            onClick={() => setExpanded(false)}
          >
            <div
              className={styles.artifactOverlayInner}
              onClick={(e) => e.stopPropagation()}
            >
              <div className={styles.artifactOverlayBar}>
                <span className={styles.artifactOverlayHint}>
                  Press Esc to close
                </span>
                <button
                  type="button"
                  className={styles.artifactOverlayClose}
                  onClick={download}
                  title="Download as HTML"
                  aria-label="Download artifact as HTML"
                >
                  <DownloadIcon />
                </button>
                <button
                  type="button"
                  className={styles.artifactOverlayClose}
                  onClick={() => setExpanded(false)}
                  aria-label="Close full screen"
                >
                  <CloseIcon />
                </button>
              </div>
              <iframe
                className={styles.artifactOverlayFrame}
                sandbox="allow-scripts"
                referrerPolicy="no-referrer"
                srcDoc={srcDoc}
                title="Generated artifact (full screen)"
              />
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
};

export default React.memo(ArtifactFrame);
