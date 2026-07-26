import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { NavLink, Navigate, useParams, useNavigate } from 'react-router-dom';
import CustomIcon, { CustomIconName } from '@atoms/CustomIcon';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import AccountMenu from '@organisms/AccountMenu';
import {
  SettingsScopeProvider,
  useSettingsScope,
} from '@providers/SettingsScopeProvider';
import { settingsPath } from '@constants/routePaths';
import { getAuthUser, isAdmin } from '@apiCalls/auth';
import { authApi, configAuditApi } from '@apiCalls/services';
import ProfilePage from './ProfilePage';
import UsersPage from './UsersPage';
import AuditLogPage from './AuditLogPage';
import UsagePage from './UsagePage';
import MemoriesPage from './MemoriesPage';
import AssistantsPage from './AssistantsPage';
import ToolsPage from './ToolsPage';
import SkillsPage from './SkillsPage';
import DocumentsPage from './DocumentsPage';
import ResponseStylesPage from './ResponseStylesPage';
import McpServersPage from './McpServersPage';
import * as styles from '@styles/settings.module.scss';

const initialsOf = (label: string): string => {
  const parts = label.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return (parts[0] || '?').slice(0, 2).toUpperCase();
};
type SectionKey =
  | 'profile'
  | 'users'
  | 'audit'
  | 'usage'
  | 'memory'
  | 'assistants'
  | 'tools'
  | 'skills'
  | 'documents'
  | 'response-styles'
  | 'mcp-servers';

type ScopeGroup = 'Account' | 'Platform' | 'Assistant';

interface SectionDef {
  key: SectionKey;
  label: string;
  icon: CustomIconName;
  group: ScopeGroup;
  Component: React.ComponentType;
}

const SECTIONS: SectionDef[] = [
  {
    key: 'profile',
    label: 'My profile',
    icon: 'profile',
    group: 'Account',
    Component: ProfilePage,
  },
  {
    key: 'usage',
    label: 'Usage',
    icon: 'usage',
    group: 'Account',
    Component: UsagePage,
  },
  {
    key: 'memory',
    label: 'Memory',
    icon: 'star',
    group: 'Account',
    Component: MemoriesPage,
  },
  {
    key: 'users',
    label: 'Users & roles',
    icon: 'users',
    group: 'Platform',
    Component: UsersPage,
  },
  {
    key: 'audit',
    label: 'Audit log',
    icon: 'audit',
    group: 'Platform',
    Component: AuditLogPage,
  },
  {
    key: 'assistants',
    label: 'General',
    icon: 'robot',
    group: 'Assistant',
    Component: AssistantsPage,
  },
  {
    key: 'tools',
    label: 'HTTP tools',
    icon: 'tool',
    group: 'Assistant',
    Component: ToolsPage,
  },
  {
    key: 'skills',
    label: 'Skills',
    icon: 'skill',
    group: 'Assistant',
    Component: SkillsPage,
  },
  {
    key: 'documents',
    label: 'Documents',
    icon: 'document',
    group: 'Assistant',
    Component: DocumentsPage,
  },
  {
    key: 'mcp-servers',
    label: 'MCP servers',
    icon: 'mcp',
    group: 'Assistant',
    Component: McpServersPage,
  },
  {
    key: 'response-styles',
    label: 'Response styles',
    icon: 'style',
    group: 'Assistant',
    Component: ResponseStylesPage,
  },
];

const GROUP_ORDER: ScopeGroup[] = ['Account', 'Platform', 'Assistant'];

const GROUP_LABELS: Record<ScopeGroup, string> = {
  Account: 'Account',
  Platform: 'Platform',
  Assistant: 'For this assistant',
};

const SettingsInner: React.FC<{ section: SectionKey }> = ({ section }) => {
  const navigate = useNavigate();
  const {
    assistants,
    assistantId,
    setAssistantId,
    loading,
    creatingAssistant,
    startCreateAssistant,
  } = useSettingsScope();
  const active = SECTIONS.find((s) => s.key === section)!;
  const ActiveComponent = active.Component;
  // The assistant scope picker is only relevant to the per-assistant sections.
  const isAccountSection = active.group !== 'Assistant';
  // Non-admins only ever see the Account group (Usage) by default; the assistant-management
  // sections stay admin-only. The backend enforces this regardless via @PreAuthorize. The one
  // exception is "Audit log": when an admin has flipped the read-access toggle on, non-admin
  // roles (Supervisor/Investigator/Analyst/Policymaker) can view it read-only, so we fetch that
  // flag here (default hidden until resolved, to avoid a flash of a nav item they can't use).
  const admin = isAdmin();
  const [nonAdminAuditAccess, setNonAdminAuditAccess] = useState(false);

  useEffect(() => {
    if (admin) return;
    configAuditApi
      .getSettings()
      .then((s) => setNonAdminAuditAccess(s.nonAdminReadEnabled))
      .catch(() => {});
  }, [admin]);

  const visibleSections = admin
    ? SECTIONS
    : SECTIONS.filter(
        (s) => s.group === 'Account' || (s.key === 'audit' && nonAdminAuditAccess)
      );

  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);

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
        if (revoked) return;
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

  const assistantOptions =
    assistants.length === 0
      ? [{ value: '', label: 'No assistants yet' }]
      : assistants.map((a) => ({ value: a.id, label: a.name }));

  const handleNewAssistant = () => {
    startCreateAssistant();
    navigate(settingsPath('assistants'));
  };

  return (
    <div className={styles.page}>
      <aside className={styles.nav}>
        <div className={styles.navScroll}>
          <div className={styles.navHeader} onClick={() => navigate('/')}>
            <CustomIcon name="arrowLeft" size={13} />
            Back to chat
          </div>

          {!isAccountSection && (
            <div className={styles.scopePicker}>
              <label className={styles.scopeLabel}>Assistant</label>
              <CustomButton
                variant="primary"
                fullWidth
                className={styles.newAssistantBtn}
                onClick={handleNewAssistant}
              >
                <CustomIcon name="plus" size={14} />
                New assistant
              </CustomButton>
              <CustomSelect
                options={assistantOptions}
                value={creatingAssistant ? '' : assistantId}
                onChange={(v) => setAssistantId(v as string)}
                placeholder={
                  creatingAssistant
                    ? 'Creating new assistant…'
                    : loading
                      ? 'Loading…'
                      : 'Switch assistant'
                }
                fullWidth
                disabled={
                  loading || assistants.length === 0 || creatingAssistant
                }
              />
            </div>
          )}

          {GROUP_ORDER.map((group) => {
            const items = visibleSections.filter((s) => s.group === group);
            if (items.length === 0) return null;
            return (
              <React.Fragment key={group}>
                <div className={styles.navTitle}>{GROUP_LABELS[group]}</div>
                {items.map((it) => {
                  const isGeneral = it.key === 'assistants';
                  const scopedDisabled =
                    group === 'Assistant' &&
                    !isGeneral &&
                    !creatingAssistant &&
                    !assistantId &&
                    !loading;
                  return (
                    <NavLink
                      key={it.key}
                      to={settingsPath(it.key)}
                      className={({ isActive }) =>
                        [
                          styles.navItem,
                          isActive ? styles.navItemActive : '',
                          scopedDisabled ? styles.navItemDisabled : '',
                        ]
                          .filter(Boolean)
                          .join(' ')
                      }
                      onClick={(e) => {
                        if (scopedDisabled) e.preventDefault();
                      }}
                    >
                      <span className={styles.navIcon}>
                        <CustomIcon name={it.icon} size={15} />
                      </span>
                      {it.label}
                    </NavLink>
                  );
                })}
              </React.Fragment>
            );
          })}
        </div>

        {displayName && (
          <div className={styles.navFooter}>
            <AccountMenu
              displayName={displayName}
              email={email}
              photoUrl={photoUrl}
              initials={initialsOf(displayName)}
            />
          </div>
        )}
      </aside>

      <main className={styles.content}>
        <div className={styles.contentInner}>
          <AnimatePresence mode="wait">
            <motion.div
              key={section}
              className={styles.sectionMotion}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            >
              <ActiveComponent />
            </motion.div>
          </AnimatePresence>
        </div>
      </main>
    </div>
  );
};

const SettingsPage: React.FC = () => {
  const { section } = useParams<{ section?: string }>();
  // Admins land on assistant management; non-admins only have the Usage page.
  const admin = isAdmin();
  const defaultSection: SectionKey = admin ? 'assistants' : 'usage';
  if (!section) return <Navigate to={settingsPath(defaultSection)} replace />;
  if (section === 'appearance') {
    return <Navigate to={settingsPath(defaultSection)} replace />;
  }
  const active = SECTIONS.find((s) => s.key === section);
  if (!active) return <Navigate to={settingsPath(defaultSection)} replace />;
  // Block non-admins from admin-only (assistant) sections, even via direct URL — except
  // "Audit log", which is conditionally readable by non-admins when an admin has enabled
  // the read-access toggle. We can't synchronously know the toggle's value here (it's an
  // async fetch), so we let the route through and rely on the backend's own
  // @PreAuthorize("hasRole('ADMIN') or @auditAccessSettingsService.isNonAdminReadEnabled()")
  // check on every underlying request: if the toggle is actually off, the page's data
  // fetches simply come back empty/403 rather than exposing anything.
  if (!admin && active.group !== 'Account' && active.key !== 'audit') {
    return <Navigate to={settingsPath('usage')} replace />;
  }
  return (
    <SettingsScopeProvider>
      <SettingsInner section={active.key} />
    </SettingsScopeProvider>
  );
};

export default SettingsPage;
