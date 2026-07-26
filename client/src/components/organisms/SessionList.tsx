import React, { useMemo } from 'react';
import CustomSpinner from '@atoms/CustomSpinner';
import SessionRow from '@molecules/SessionRow';
import { groupSessionsByDate } from '@utils/groupSessionsByDate';
import type { ChatSessionDto } from '@interfaces/chat.interface';
import * as styles from '@styles/chatSidebar.module.scss';
import { STRINGS, useT } from '@constants/translations';
import { useLangStore } from '@store/useLangStore';

export interface SessionListProps {
  sessions: ChatSessionDto[];
  currentId: string | null;
  showArchived: boolean;
  sessionsLoading?: boolean;
  messagesLoading?: boolean;
  onOpen: (id: string) => void;
  onRename: (id: string, title: string) => void | Promise<void>;
  onToggleArchive: (
    id: string,
    archived: boolean
  ) => void | Promise<void>;
  onDelete: (id: string) => void | Promise<void>;
}

const SessionList: React.FC<SessionListProps> = ({
  sessions,
  currentId,
  showArchived,
  sessionsLoading,
  messagesLoading,
  onOpen,
  onRename,
  onToggleArchive,
  onDelete,
}) => {
  const groups = useMemo(() => groupSessionsByDate(sessions), [sessions]);
  const t = useT();
  const lang = useLangStore((s) => s.lang);

  if (sessionsLoading && sessions.length === 0) {
    return (
      <div className={styles.sessionList}>
        <div className={styles.sessionListLoading}>
          <CustomSpinner size="small" tip={t('loadingChats')} />
        </div>
      </div>
    );
  }

  if (sessions.length === 0) {
    return (
      <div className={styles.sessionList}>
        <div className={styles.emptyList}>
          {showArchived ? t('noArchivedChats') : t('noRecentChats')}
          <br />
          {t('startOneToGetGoing')}
        </div>
      </div>
    );
  }

  return (
    <div className={styles.sessionList}>
      {sessionsLoading && (
        <div className={styles.sessionListOverlay} aria-busy="true">
          <CustomSpinner size="small" />
        </div>
      )}
      {groups.map((group) => (
        <div key={group.label}>
          <div className={styles.groupLabel}>
            {STRINGS[group.label as keyof typeof STRINGS]?.[lang] ?? group.label}
          </div>
          <div className={styles.groupSessions}>
            {group.sessions.map((s) => (
              <SessionRow
                key={s.id}
                session={s}
                selected={s.id === currentId}
                loading={s.id === currentId && !!messagesLoading}
                onOpen={() => onOpen(s.id)}
                onRename={(title) => onRename(s.id, title)}
                onToggleArchive={() =>
                  onToggleArchive(s.id, !s.archived)
                }
                onDelete={() => onDelete(s.id)}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
};

export default SessionList;
