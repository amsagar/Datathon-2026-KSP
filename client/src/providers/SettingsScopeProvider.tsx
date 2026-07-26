import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { assistantsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type { AssistantDto } from '@interfaces/assistant.interface';

interface SettingsScopeValue {
  assistants: AssistantDto[];
  assistantId: string;
  assistant: AssistantDto | null;
  setAssistantId: (id: string) => void;
  refreshAssistants: () => Promise<void>;
  loading: boolean;
  /** True while composing a brand-new assistant (before first save). */
  creatingAssistant: boolean;
  startCreateAssistant: () => void;
  cancelCreateAssistant: () => void;
}

const SettingsScopeContext = createContext<SettingsScopeValue | null>(null);

const STORAGE_KEY = 'ca:settingsAssistantId';

export const SettingsScopeProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const openNotification = useNotification();
  const [assistants, setAssistants] = useState<AssistantDto[]>([]);
  const [assistantId, setAssistantIdState] = useState<string>(() => {
    try {
      return window.localStorage.getItem(STORAGE_KEY) || '';
    } catch {
      return '';
    }
  });
  const [loading, setLoading] = useState(true);
  const [creatingAssistant, setCreatingAssistant] = useState(false);

  const setAssistantId = useCallback((id: string) => {
    setAssistantIdState(id);
    setCreatingAssistant(false);
    try {
      window.localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // ignore
    }
  }, []);

  const startCreateAssistant = useCallback(() => {
    setCreatingAssistant(true);
    setAssistantIdState('');
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }, []);

  const cancelCreateAssistant = useCallback(() => {
    setCreatingAssistant(false);
    setAssistantIdState((curr) => {
      if (curr) return curr;
      return '';
    });
  }, []);

  const refreshAssistants = useCallback(async () => {
    try {
      const list = await assistantsApi.list();
      setAssistants(list);
      setAssistantIdState((curr) => {
        if (creatingAssistant) return curr;
        return curr && list.some((a) => a.id === curr)
          ? curr
          : list[0]?.id || '';
      });
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load assistants',
        'Error'
      );
    } finally {
      setLoading(false);
    }
  }, [openNotification, creatingAssistant]);

  useEffect(() => {
    if (!creatingAssistant && !assistantId && assistants.length > 0) {
      setAssistantId(assistants[0].id);
    }
  }, [creatingAssistant, assistantId, assistants, setAssistantId]);

  useEffect(() => {
    void refreshAssistants();
  }, [refreshAssistants]);

  const value = useMemo<SettingsScopeValue>(
    () => ({
      assistants,
      assistantId,
      assistant: assistants.find((a) => a.id === assistantId) || null,
      setAssistantId,
      refreshAssistants,
      loading,
      creatingAssistant,
      startCreateAssistant,
      cancelCreateAssistant,
    }),
    [
      assistants,
      assistantId,
      setAssistantId,
      refreshAssistants,
      loading,
      creatingAssistant,
      startCreateAssistant,
      cancelCreateAssistant,
    ]
  );

  return (
    <SettingsScopeContext.Provider value={value}>
      {children}
    </SettingsScopeContext.Provider>
  );
};

export const useSettingsScope = (): SettingsScopeValue => {
  const ctx = useContext(SettingsScopeContext);
  if (!ctx) {
    throw new Error(
      'useSettingsScope must be used inside <SettingsScopeProvider />'
    );
  }
  return ctx;
};
