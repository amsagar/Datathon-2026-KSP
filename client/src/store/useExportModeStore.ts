import { create } from 'zustand';

/**
 * True while a PDF export is in flight. Used to show a preparing state on the
 * export button; chat no longer expands tool cards for PDF (tool names stay hidden).
 */
interface ExportModeState {
  exporting: boolean;
  setExporting: (exporting: boolean) => void;
}

export const useExportModeStore = create<ExportModeState>((set) => ({
  exporting: false,
  setExporting: (exporting) => set({ exporting }),
}));
