import React, { useEffect, useState } from 'react';
import { useLangStore } from '@store/useLangStore';
import { translateText } from '@apiCalls/translate';
import {
  offlineSessionTitle,
  titleNeedsTranslation,
} from '@utils/sessionTitleLocale';

export interface LocalizedSessionTitleProps {
  title: string;
  className?: string;
}

/**
 * Session titles in the active UI language.
 * Known titles resolve from a local dictionary; others use backend NLLB translation.
 */
const LocalizedSessionTitle: React.FC<LocalizedSessionTitleProps> = ({
  title,
  className,
}) => {
  const lang = useLangStore((s) => s.lang);
  const offline = offlineSessionTitle(title, lang);
  const [display, setDisplay] = useState(offline || title);

  useEffect(() => {
    const instant = offlineSessionTitle(title, lang);
    if (instant) {
      setDisplay(instant);
      return;
    }
    if (!titleNeedsTranslation(title, lang)) {
      setDisplay(title);
      return;
    }
    let cancelled = false;
    setDisplay(title);
    void translateText(title, lang)
      .then((out) => {
        if (!cancelled && out) setDisplay(out);
      })
      .catch(() => {
        /* keep original */
      });
    return () => {
      cancelled = true;
    };
  }, [title, lang]);

  return (
    <span className={className} title={title}>
      {display}
    </span>
  );
};

export default LocalizedSessionTitle;
