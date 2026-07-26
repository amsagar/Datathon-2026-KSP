import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'ca:sidebarCollapsed';

const readInitial = (): boolean => {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
};

/**
 * Tracks whether the chat sidebar is collapsed, persisting the choice to
 * localStorage so it survives reloads.
 */
export const useSidebarCollapse = () => {
  const [collapsed, setCollapsed] = useState<boolean>(readInitial);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
    } catch {
      // ignore quota / privacy-mode failures
    }
  }, [collapsed]);

  const toggle = useCallback(() => setCollapsed((c) => !c), []);

  return { collapsed, setCollapsed, toggle };
};
