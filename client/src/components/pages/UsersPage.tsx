import React, { useCallback, useEffect, useMemo, useState } from 'react';
import CustomTable, {
  type CustomTableColumnsType,
} from '@templates/CustomTable';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import CustomSelect from '@atoms/CustomSelect';
import CustomDropdown from '@atoms/CustomDropdown';
import CustomIcon from '@atoms/CustomIcon';
import CustomAvatar from '@atoms/CustomAvatar';
import CustomTag from '@atoms/CustomTag';
import CustomModal from '@atoms/CustomModal';
import CustomEmptyState from '@atoms/CustomEmptyState';
import { confirm } from '@atoms/CustomConfirm';
import UserFormDrawer from '@organisms/UserFormDrawer';
import { usersApi } from '@apiCalls/services';
import { getAuthUser } from '@apiCalls/auth';
import { useNotification } from '@providers/NotificationProviders';
import { APP_ROLES } from '@interfaces/user.interface';
import type { AppRole, UserDto } from '@interfaces/user.interface';
import { useT, type StringKey } from '@constants/translations';
import { Check, ChevronDown, ChevronRight, Copy } from 'lucide-react';
import * as styles from '@styles/usersPage.module.scss';

type StatusFilter = 'all' | 'active' | 'disabled';

const ROLE_DESC_KEYS: Record<AppRole, StringKey> = {
  ADMIN: 'roleDescAdmin',
  SUPERVISOR: 'roleDescSupervisor',
  INVESTIGATOR: 'roleDescInvestigator',
  ANALYST: 'roleDescAnalyst',
  POLICYMAKER: 'roleDescPolicymaker',
};

const ROLE_LABEL_KEYS: Record<AppRole, StringKey> = {
  ADMIN: 'roleAdmin',
  SUPERVISOR: 'roleSupervisor',
  INVESTIGATOR: 'roleInvestigator',
  ANALYST: 'roleAnalyst',
  POLICYMAKER: 'rolePolicymaker',
};

const roleTone = (role: string): 'error' | 'info' | 'neutral' =>
  role === 'ADMIN' ? 'error' : role === 'ANALYST' ? 'neutral' : 'info';

const formatLastLogin = (millis: number | null): string => {
  if (!millis) return '—';
  const diff = Date.now() - millis;
  const min = 60_000;
  const hour = 60 * min;
  const day = 24 * hour;
  if (diff < min) return 'just now';
  if (diff < hour) return `${Math.floor(diff / min)}m ago`;
  if (diff < day) return `${Math.floor(diff / hour)}h ago`;
  if (diff < 7 * day) return `${Math.floor(diff / day)}d ago`;
  return new Date(millis).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

const initials = (user: UserDto): string => {
  const base = user.displayName?.trim() || user.username;
  const parts = base.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return base.slice(0, 2).toUpperCase();
};

/**
 * Lazily resolves the object-url for a user's photo, if they have one.
 * URLs come from the usersApi module-level cache (one fetch per user, shared
 * across rows and remounts) — the cache owns them, so never revoke here.
 */
const UserAvatar: React.FC<{ user: UserDto }> = ({ user }) => {
  const [src, setSrc] = useState<string | undefined>();

  useEffect(() => {
    let cancelled = false;
    if (user.hasPhoto) {
      void usersApi.photoObjectUrl(user.id).then((url) => {
        if (!cancelled) setSrc(url || undefined);
      });
    } else {
      setSrc(undefined);
    }
    return () => {
      cancelled = true;
    };
  }, [user.id, user.hasPhoto]);

  return (
    <CustomAvatar src={src} size={32} alt={user.displayName || user.username}>
      {initials(user)}
    </CustomAvatar>
  );
};

const UsersPage: React.FC = () => {
  const t = useT();
  const notify = useNotification();
  const currentUsername = getAuthUser()?.upn?.toLowerCase() ?? null;

  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [rolesOpen, setRolesOpen] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingUser, setEditingUser] = useState<UserDto | undefined>();

  const [resetResult, setResetResult] = useState<{
    username: string;
    password: string;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await usersApi.list());
    } catch (err) {
      notify(
        (err as Error)?.message || 'Failed to load users',
        'Error',
      );
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users.filter((u) => {
      if (q) {
        const hay = `${u.displayName || ''} ${u.username} ${
          u.email || ''
        }`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      if (roleFilter !== 'all' && roleFilter && !u.roles.includes(roleFilter))
        return false;
      if (statusFilter === 'active' && !u.enabled) return false;
      if (statusFilter === 'disabled' && u.enabled) return false;
      return true;
    });
  }, [users, search, roleFilter, statusFilter]);

  const isSelf = (u: UserDto) =>
    currentUsername != null && u.username.toLowerCase() === currentUsername;

  const openCreate = () => {
    setDrawerMode('create');
    setEditingUser(undefined);
    setDrawerOpen(true);
  };

  const openEdit = (u: UserDto) => {
    setDrawerMode('edit');
    setEditingUser(u);
    setDrawerOpen(true);
  };

  const serverError = (err: unknown): string | null =>
    (err as { response?: { data?: { error?: string } } })?.response?.data
      ?.error || null;

  const reportError = (err: unknown, fallback: string) =>
    notify(serverError(err) || (err as Error)?.message || fallback, 'Error');

  const doReset = (u: UserDto) => {
    confirm({
      title: `Reset password for ${u.displayName || u.username}?`,
      body: 'A new temporary password will be generated. The user must change it on next login.',
      okText: t('resetPassword'),
      onOk: async () => {
        try {
          const { temporaryPassword } = await usersApi.resetPassword(u.id);
          setResetResult({ username: u.username, password: temporaryPassword });
          setCopied(false);
          notify('Password reset', 'Success');
        } catch (err) {
          reportError(err, 'Failed to reset password');
        }
      },
    });
  };

  const toggleEnabled = async (u: UserDto) => {
    try {
      await usersApi.update(u.id, { enabled: !u.enabled });
      notify(
        `${u.displayName || u.username} ${u.enabled ? 'deactivated' : 'activated'}`,
        'Success',
      );
      await refresh();
    } catch (err) {
      reportError(err, 'Failed to update user');
    }
  };

  const doDelete = (u: UserDto) => {
    confirm({
      title: `Delete ${u.displayName || u.username}?`,
      body: 'This permanently removes the user account. This cannot be undone.',
      danger: true,
      okText: t('deleteAction'),
      onOk: async () => {
        try {
          await usersApi.delete(u.id);
          notify(`User "${u.username}" deleted`, 'Success');
          await refresh();
        } catch (err) {
          reportError(err, 'Failed to delete user');
        }
      },
    });
  };

  const copyResetPassword = async () => {
    if (!resetResult) return;
    try {
      await navigator.clipboard.writeText(resetResult.password);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      notify('Could not copy to clipboard.', 'Warning');
    }
  };

  const roleLabel = (role: string): string => {
    const key = ROLE_LABEL_KEYS[role as AppRole];
    return key ? t(key) : role;
  };

  const rowMenu = (u: UserDto) => {
    const self = isSelf(u);
    return [
      {
        key: 'edit',
        label: (
          <span className={styles.menuItem}>
            <CustomIcon name="edit" size={14} />
            {t('edit')}
          </span>
        ),
        onClick: () => openEdit(u),
      },
      {
        key: 'reset',
        label: (
          <span className={styles.menuItem}>
            <CustomIcon name="key" size={14} />
            {t('resetPassword')}
          </span>
        ),
        onClick: () => doReset(u),
      },
      {
        key: 'toggle',
        label: (
          <span className={styles.menuItem}>
            <CustomIcon name={u.enabled ? 'close' : 'check'} size={14} />
            {u.enabled ? t('deactivate') : t('activate')}
          </span>
        ),
        disabled: self,
        onClick: () => void toggleEnabled(u),
      },
      {
        key: 'delete',
        label: (
          <span className={`${styles.menuItem} ${styles.menuItemDanger}`}>
            <CustomIcon name="delete" size={14} />
            {t('deleteAction')}
          </span>
        ),
        danger: true,
        disabled: self,
        onClick: () => doDelete(u),
      },
    ];
  };

  const columns: CustomTableColumnsType<UserDto> = [
    {
      title: t('roleUser'),
      key: 'user',
      render: (_: unknown, record) => (
        <div className={styles.userCell}>
          <UserAvatar user={record} />
          <div className={styles.userCellText}>
            <span className={styles.userCellName}>
              {record.displayName || record.username}
              {isSelf(record) && (
                <span className={styles.selfBadge}> · {t('youBadge')}</span>
              )}
            </span>
            <span className={styles.userCellUsername}>@{record.username}</span>
          </div>
        </div>
      ),
    },
    {
      title: t('colEmail'),
      key: 'email',
      render: (_: unknown, record) =>
        record.email ? (
          <span className={styles.cellEmail}>{record.email}</span>
        ) : (
          <span className={styles.cellMuted}>—</span>
        ),
    },
    {
      title: t('colRoles'),
      key: 'roles',
      render: (_: unknown, record) => (
        <span className={styles.tagRow}>
          {record.roles.length === 0 ? (
            <span className={styles.cellMuted}>—</span>
          ) : (
            record.roles.map((r) => (
              <CustomTag key={r} tone={roleTone(r)}>
                {roleLabel(r)}
              </CustomTag>
            ))
          )}
        </span>
      ),
    },
    {
      title: t('colStatus'),
      key: 'status',
      width: 110,
      render: (_: unknown, record) =>
        record.enabled ? (
          <CustomTag tone="success">{t('statusActive')}</CustomTag>
        ) : (
          <CustomTag tone="neutral">{t('statusDisabled')}</CustomTag>
        ),
    },
    {
      title: t('colLastLogin'),
      key: 'lastLogin',
      width: 130,
      render: (_: unknown, record) => (
        <span className={styles.cellMuted}>
          {formatLastLogin(record.lastLoginAt)}
        </span>
      ),
    },
    {
      title: t('colActions'),
      key: 'actions',
      width: 52,
      align: 'center',
      render: (_: unknown, record) => (
        <CustomDropdown items={rowMenu(record)} placement="bottomRight">
          <CustomButton
            variant="text"
            size="small"
            aria-label={`${t('colActions')} ${record.username}`}
            onClick={(e) => e.stopPropagation()}
          >
            <CustomIcon name="more" size={16} />
          </CustomButton>
        </CustomDropdown>
      ),
    },
  ];

  const hasFilters =
    !!search.trim() || roleFilter !== 'all' || statusFilter !== 'all';

  return (
    <>
      <div className={styles.page}>
        <header className={styles.pageHeader}>
          <div className={styles.pageHeaderMain}>
            <div className={styles.pageEyebrow}>{t('settingsGroupPlatform')}</div>
            <h1 className={styles.pageTitle}>{t('usersRolesTitle')}</h1>
            <p className={styles.pageSubtitle}>{t('usersSubtitle')}</p>
          </div>
          <CustomButton variant="primary" size="small" onClick={openCreate}>
            <CustomIcon name="plus" size={14} />
            {t('newUser')}
          </CustomButton>
        </header>

        <div className={styles.body}>
          <div className={styles.toolbar}>
            <div className={styles.toolbarSearch}>
              <CustomInput
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('searchUsersPlaceholder')}
                allowClear
                prefix={<CustomIcon name="search" size={14} />}
              />
            </div>
            <div className={styles.toolbarFilter}>
              <CustomSelect
                value={roleFilter}
                onChange={(v) => setRoleFilter(v || 'all')}
                placeholder={t('allRoles')}
                options={[
                  { value: 'all', label: t('allRoles') },
                  ...APP_ROLES.map((r) => ({
                    value: r,
                    label: t(ROLE_LABEL_KEYS[r]),
                  })),
                ]}
              />
            </div>
            <div className={styles.toolbarFilter}>
              <CustomSelect<StatusFilter>
                value={statusFilter}
                onChange={(v) => setStatusFilter(v || 'all')}
                placeholder={t('allStatuses')}
                options={[
                  { value: 'all', label: t('allStatuses') },
                  { value: 'active', label: t('statusActive') },
                  { value: 'disabled', label: t('statusDisabled') },
                ]}
              />
            </div>
          </div>

          <div className={styles.tableWrap}>
            {!loading && filtered.length === 0 ? (
              <CustomEmptyState
                icon={<CustomIcon name="users" size={40} />}
                title={hasFilters ? t('noMatchingUsers') : t('noUsersYet')}
                description={
                  hasFilters
                    ? t('tryAdjustingFilters')
                    : t('createFirstUser')
                }
                action={
                  hasFilters ? undefined : (
                    <CustomButton variant="primary" onClick={openCreate}>
                      <CustomIcon name="plus" size={14} />
                      {t('newUser')}
                    </CustomButton>
                  )
                }
              />
            ) : (
              <CustomTable<UserDto>
                rowKey="id"
                dataSource={filtered}
                columns={columns}
                loading={loading}
              />
            )}
          </div>

          <div className={styles.rolesCard}>
            <button
              type="button"
              className={styles.rolesHeader}
              onClick={() => setRolesOpen((o) => !o)}
              aria-expanded={rolesOpen}
            >
              <span className={styles.rolesHeaderMain}>
                {rolesOpen ? (
                  <ChevronDown className="size-4" aria-hidden />
                ) : (
                  <ChevronRight className="size-4" aria-hidden />
                )}
                <CustomIcon name="shield" size={16} />
                <span className={styles.rolesHeaderTitle}>
                  {t('rolesReference')}
                </span>
              </span>
              <span className={styles.rolesHeaderHint}>
                {t('rolesReferenceHint')}
              </span>
            </button>
            {rolesOpen && (
              <div className={styles.rolesGrid}>
                {APP_ROLES.map((role) => (
                  <div key={role} className={styles.roleTile}>
                    <div className={styles.roleTileHead}>
                      <CustomTag tone={roleTone(role)}>
                        {t(ROLE_LABEL_KEYS[role])}
                      </CustomTag>
                    </div>
                    <p className={styles.roleTileDesc}>
                      {t(ROLE_DESC_KEYS[role])}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <UserFormDrawer
        open={drawerOpen}
        mode={drawerMode}
        user={editingUser}
        onClose={() => setDrawerOpen(false)}
        onSaved={() => void refresh()}
      />

      <CustomModal
        open={!!resetResult}
        title={t('temporaryPassword')}
        onClose={() => setResetResult(null)}
        width="sm"
        footer={
          <CustomButton variant="primary" onClick={() => setResetResult(null)}>
            {t('done')}
          </CustomButton>
        }
      >
        <div className="flex flex-col gap-3">
          <p className="m-0 text-sm text-muted-foreground">
            {t('tempPasswordFor')}{' '}
            <span className="font-mono font-medium text-foreground">
              @{resetResult?.username}
            </span>
            . {t('tempPasswordShareHint')}
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 rounded-md border border-border bg-muted px-3 py-2 font-mono text-sm text-foreground">
              {resetResult?.password}
            </code>
            <CustomButton
              variant="ghost"
              size="small"
              onClick={() => void copyResetPassword()}
              icon={
                copied ? (
                  <Check className="size-4" />
                ) : (
                  <Copy className="size-4" />
                )
              }
            >
              {copied ? t('copied') : t('copy')}
            </CustomButton>
          </div>
        </div>
      </CustomModal>
    </>
  );
};

export default UsersPage;
