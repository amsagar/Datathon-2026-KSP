import React, { useEffect, useState } from 'react';
import CustomIcon, { type CustomIconName } from '@atoms/CustomIcon';
import AccountMenu from '@organisms/AccountMenu';
import CustomTooltip from '@atoms/CustomTooltip';
import SessionList from './SessionList';
import { getAuthUser, hasRole, isAdmin } from '@apiCalls/auth';
import { authApi } from '@apiCalls/services';
import type { ChatSessionDto } from '@interfaces/chat.interface';
import { SIDEBAR_COLLAPSED_WIDTH } from '@utils/useSidebarWidth';
import { StringKey, useT } from '@constants/translations';
import type { AnalyticsChatTab } from '@constants/routePaths';
import * as styles from '@styles/chatSidebar.module.scss';
import KspLogo from '@atoms/KspLogo';

const initialsOf = (label: string): string => {
  const parts = label.trim().split(/[\s@._-]+/).filter(Boolean);
  if (!parts.length) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
};

const ANALYTICS_ITEMS: {
  id: AnalyticsChatTab;
  labelKey: StringKey;
  icon: CustomIconName;
  show: (r: { investigative: boolean; analyst: boolean }) => boolean;
}[] = [
  { id: 'dashboard', labelKey: 'dashboard', icon: 'usage', show: () => true },
  { id: 'map', labelKey: 'hotspotMap', icon: 'search', show: () => true },
  {
    id: 'network',
    labelKey: 'criminalNetwork',
    icon: 'mcp',
    show: ({ analyst }) => analyst,
  },
  {
    id: 'offenders',
    labelKey: 'offenderRisk',
    icon: 'warning',
    show: ({ investigative }) => investigative,
  },
];

export interface ChatSidebarProps {
  collapsed: boolean;
  onCollapse: () => void;

  width: number;
  resizing?: boolean;
  onBeginResize: (e: React.MouseEvent) => void;

  showArchived: boolean;
  onToggleArchived: (archived: boolean) => void;

  sessions: ChatSessionDto[];
  currentId: string | null;
  sessionsLoading?: boolean;
  messagesLoading?: boolean;
  onNewChat: () => void;
  onOpenSession: (id: string) => void;
  onRenameSession: (id: string, title: string) => void | Promise<void>;
  onToggleArchive: (id: string, archived: boolean) => void | Promise<void>;
  onDeleteSession: (id: string) => void | Promise<void>;

  /** Active analytics tab when the chat pane is showing analytics; null = chat mode. */
  analyticsTab?: AnalyticsChatTab | null;
  onOpenAnalytics?: (tab: AnalyticsChatTab) => void;
}

const ChatSidebar: React.FC<ChatSidebarProps> = ({
  collapsed,
  onCollapse,
  width,
  resizing,
  onBeginResize,
  showArchived,
  onToggleArchived,
  sessions,
  currentId,
  sessionsLoading,
  messagesLoading,
  onNewChat,
  onOpenSession,
  onRenameSession,
  onToggleArchive,
  onDeleteSession,
  analyticsTab = null,
  onOpenAnalytics,
}) => {
  const [displayName, setDisplayName] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const t = useT();

  const investigative = isAdmin() || hasRole('SUPERVISOR') || hasRole('INVESTIGATOR');
  const analyst = investigative || hasRole('ANALYST');
  const analyticsItems = ANALYTICS_ITEMS.filter((item) =>
    item.show({ investigative, analyst })
  );

  useEffect(() => {
    const stored = getAuthUser();
    if (stored) {
      setDisplayName(stored.name || stored.upn);
      setEmail(stored.email || stored.upn);
    }

    let revoked = false;
    let objectUrl: string | null = null;
    authApi
      .me()
      .then((profile) => {
        if (profile.name) setDisplayName(profile.name);
        if (profile.email) setEmail(profile.email);
      })
      .catch(() => {});
    authApi.photoObjectUrl().then((url) => {
      if (revoked) {
        if (url) URL.revokeObjectURL(url);
        return;
      }
      objectUrl = url;
      setPhotoUrl(url);
    });
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, []);

  return (
    <aside
      className={[
        styles.sidebar,
        collapsed ? styles.sidebarCollapsed : '',
        resizing ? styles.sidebarResizing : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ width: collapsed ? SIDEBAR_COLLAPSED_WIDTH : width }}
    >
      {collapsed ? (
        <nav className={styles.sidebarRail} aria-label="Sidebar shortcuts">
          <CustomTooltip title="Open sidebar" placement="right">
            <button
              type="button"
              className={styles.railBrandBtn}
              onClick={onCollapse}
              aria-label="Open sidebar"
            >
              <KspLogo className={styles.railBrandMark} />
              <span className={styles.railBrandHoverIcon} aria-hidden>
                <CustomIcon name="sidebarUnfold" size={16} />
              </span>
            </button>
          </CustomTooltip>

          <div className={styles.railDivider} role="presentation" />

          <CustomTooltip title={t('newChat')} placement="right">
            <button
              type="button"
              className={styles.railBtn}
              onClick={onNewChat}
              aria-label={t('newChat')}
            >
              <CustomIcon name="plus" size={15} />
            </button>
          </CustomTooltip>

          {onOpenAnalytics &&
            analyticsItems.map((item) => (
              <CustomTooltip key={item.id} title={t(item.labelKey)} placement="right">
                <button
                  type="button"
                  className={`${styles.railBtn} ${
                    analyticsTab === item.id ? styles.railBtnActive : ''
                  }`}
                  onClick={() => onOpenAnalytics(item.id)}
                  aria-label={t(item.labelKey)}
                  aria-current={analyticsTab === item.id ? 'page' : undefined}
                >
                  <CustomIcon name={item.icon} size={15} />
                </button>
              </CustomTooltip>
            ))}

          <div className={styles.railSpacer} />

          <div className={styles.railFooter}>
            {displayName && (
              <AccountMenu
                collapsed
                displayName={displayName}
                email={email}
                photoUrl={photoUrl}
                initials={initialsOf(displayName)}
              />
            )}
          </div>
        </nav>
      ) : (
        <>
          <div className={styles.sidebarInner} style={{ width }}>
            <div className={styles.sidebarHeader}>
              <div className={styles.sidebarBrand}>
                <KspLogo className={styles.sidebarBrandMark} />
                <span>{t('brand')}</span>
              </div>
              <CustomTooltip title="Collapse sidebar" placement="right">
                <button
                  type="button"
                  className={styles.collapseBtn}
                  onClick={onCollapse}
                  aria-label="Collapse sidebar"
                >
                  <CustomIcon name="sidebarFold" size={15} />
                </button>
              </CustomTooltip>
            </div>

            {onOpenAnalytics && analyticsItems.length > 0 && (
              <div className={styles.analyticsNav}>
                <div className={styles.navSectionLabel}>{t('crimeAnalytics')}</div>
                {analyticsItems.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`${styles.navItem} ${
                      analyticsTab === item.id ? styles.navItemActive : ''
                    }`}
                    onClick={() => onOpenAnalytics(item.id)}
                    aria-current={analyticsTab === item.id ? 'page' : undefined}
                  >
                    <CustomIcon name={item.icon} size={14} />
                    {t(item.labelKey)}
                  </button>
                ))}
              </div>
            )}

            <div className={styles.navSectionLabel}>{t('chat')}</div>

            <button type="button" className={styles.newChatBtn} onClick={onNewChat}>
              <CustomIcon name="plus" size={14} />
              {t('newChat')}
            </button>

            <div className={styles.toggle}>
              <button
                type="button"
                className={`${styles.toggleButton} ${
                  !showArchived ? styles.toggleActive : ''
                }`}
                onClick={() => onToggleArchived(false)}
              >
                {t('recent')}
              </button>
              <button
                type="button"
                className={`${styles.toggleButton} ${
                  showArchived ? styles.toggleActive : ''
                }`}
                onClick={() => onToggleArchived(true)}
              >
                {t('archived')}
              </button>
            </div>

            <SessionList
              sessions={sessions}
              currentId={analyticsTab ? null : currentId}
              showArchived={showArchived}
              sessionsLoading={sessionsLoading}
              messagesLoading={messagesLoading}
              onOpen={onOpenSession}
              onRename={onRenameSession}
              onToggleArchive={onToggleArchive}
              onDelete={onDeleteSession}
            />

            {displayName && (
              <AccountMenu
                displayName={displayName}
                email={email}
                photoUrl={photoUrl}
                initials={initialsOf(displayName)}
              />
            )}
          </div>
          <div
            className={`${styles.resizer} ${resizing ? styles.resizerActive : ''}`}
            onMouseDown={onBeginResize}
            role="separator"
            aria-orientation="vertical"
            aria-label="Resize sidebar"
          />
        </>
      )}
    </aside>
  );
};

export default ChatSidebar;
