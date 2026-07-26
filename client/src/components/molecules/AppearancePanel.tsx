import React, { useEffect } from 'react';
import { motion } from 'motion/react';
import ThemeSegmentedControl from '@atoms/ThemeSegmentedControl';
import { Card } from '@/components/ui/card';
import { useThemeStore } from '@store/useThemeStore';

const AppearancePanel: React.FC = () => {
  const { preference, setPreference, init } = useThemeStore();

  useEffect(() => {
    init();
  }, [init]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.22, ease: 'easeOut' }}
      className="flex flex-col gap-3 py-1"
    >
      <Card className="gap-0 rounded-xl border py-0 shadow-sm">
        <div className="flex items-center justify-between gap-4 px-4 py-4">
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-medium text-foreground">Theme</span>
            <span className="max-w-[16rem] text-xs leading-relaxed text-muted-foreground">
              System follows your device. Changes apply immediately.
            </span>
          </div>
          <ThemeSegmentedControl value={preference} onChange={setPreference} />
        </div>
      </Card>
    </motion.div>
  );
};

export default AppearancePanel;
