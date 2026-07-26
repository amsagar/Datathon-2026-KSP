import React, { useState } from 'react';
import CustomTooltip from '@atoms/CustomTooltip';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import TemporaryChatIcon from '@atoms/TemporaryChatIcon';
import { confirm } from '@atoms/CustomConfirm';
import { relativeTime } from '@utils/relativeTime';
import type { ChatSessionDto } from '@interfaces/chat.interface';
import * as styles from '@styles/chatSidebar.module.scss';

export interface SessionRowProps {
  session: ChatSessionDto;
  selected: boolean;
  loading?: boolean;
  onOpen: () => void;
  onRename: (title: string) => void | Promise<void>;
  onToggleArchive: () => void | Promise<void>;
  onDelete: () => void | Promise<void>;
}

const SessionRow: React.FC<SessionRowProps> = ({
  session,
  selected,
  loading,
  onOpen,
  onRename,
  onToggleArchive,
  onDelete,
}) => {
  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(session.title);

  const startRename = () => {
    setRenameValue(session.title);
    setRenaming(true);
  };

  const commitRename = async () => {
    setRenaming(false);
    const title = renameValue.trim();
    if (title && title !== session.title) {
      await onRename(title);
    }
  };

  const askDelete = () => {
    confirm({
      title: `Delete "${session.title}"?`,
      body: "This can't be undone.",
      danger: true,
      okText: 'Delete',
      onOk: onDelete,
    });
  };

  return (
    <div
      className={`${styles.sessionRow} ${selected ? styles.sessionSelected : ''} ${
        session.temporary ? styles.sessionTemporary : ''
      }`}
      onClick={onOpen}
    >
      {renaming ? (
        <input
          className={styles.renameInput}
          value={renameValue}
          autoFocus
          onClick={(e) => e.stopPropagation()}
          onChange={(e) => setRenameValue(e.target.value)}
          onBlur={commitRename}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              void commitRename();
            }
            if (e.key === 'Escape') setRenaming(false);
          }}
        />
      ) : (
        <div className={styles.sessionMain}>
          <div className={styles.sessionTitleRow}>
            <span className={styles.sessionTitle}>{session.title}</span>
            {session.temporary && (
              <CustomTooltip title="Temporary chat — auto-deletes 30 days after its last activity">
                <span className={styles.sessionTempBadge}>
                  <TemporaryChatIcon size={12} />
                  <span>Temp</span>
                </span>
              </CustomTooltip>
            )}
          </div>
          <div className={styles.sessionMeta}>
            {loading ? 'Loading…' : relativeTime(session.updatedAt)}
            {session.temporary &&
              ` · deletes in ${Math.max(
                0,
                30 - Math.floor((Date.now() / 1000 - session.updatedAt) / 86400)
              )}d`}
          </div>
        </div>
      )}
      <div
        className={styles.sessionActions}
        onClick={(e) => e.stopPropagation()}
      >
        <CustomTooltip title="Rename">
          <CustomButton
            variant="text"
            size="small"
            onClick={startRename}
            aria-label="Rename"
          >
            <CustomIcon name="edit" />
          </CustomButton>
        </CustomTooltip>
        <CustomTooltip title={session.archived ? 'Unarchive' : 'Archive'}>
          <CustomButton
            variant="text"
            size="small"
            onClick={onToggleArchive}
            aria-label={session.archived ? 'Unarchive' : 'Archive'}
          >
            <CustomIcon name={session.archived ? 'undo' : 'inbox'} />
          </CustomButton>
        </CustomTooltip>
        <CustomTooltip title="Delete">
          <CustomButton
            variant="text"
            size="small"
            onClick={askDelete}
            aria-label="Delete"
          >
            <CustomIcon name="delete" />
          </CustomButton>
        </CustomTooltip>
      </div>
    </div>
  );
};

export default SessionRow;
