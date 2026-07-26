import { useEffect } from 'react';
import { useThemeStore } from '@store/useThemeStore';

/** Hydrates theme from localStorage before first paint on subsequent visits. */
const ThemeInitializer: React.FC = () => {
  const init = useThemeStore((s) => s.init);

  useEffect(() => {
    init();
  }, [init]);

  return null;
};

export default ThemeInitializer;
