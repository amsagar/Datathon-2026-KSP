import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import CustomTable, {
  type CustomTableColumnsType,
} from '@templates/CustomTable';
import CustomInput from '@atoms/CustomInput';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import CustomSwitch from '@atoms/CustomSwitch';
import CustomTag from '@atoms/CustomTag';
import CustomIcon from '@atoms/CustomIcon';
import CustomEmptyState from '@atoms/CustomEmptyState';
import { auditApi, configAuditApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { isAdmin } from '@apiCalls/auth';
import type { AuditEntryDto } from '@interfaces/user.interface';
import type { ConfigAuditEventDto } from '@interfaces/configAudit.interface';
import { RESOURCE_TYPES } from '@interfaces/configAudit.interface';
import * as styles from '@styles/usersPage.module.scss';

const PAGE_SIZE = 50;

/** Known audit actions surfaced by the backend AuditService (legacy user_audit_log feed). */
const KNOWN_ACTIONS = [
  'USER_CREATED',
  'USER_UPDATED',
  'USER_DELETED',
  'USER_ROLE_CHANGED',
  'USER_ACTIVATED',
  'USER_DEACTIVATED',
  'USER_PASSWORD_RESET',
  'PASSWORD_CHANGED',
  'PROFILE_UPDATED',
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
] as const;

const ACTION_OPTIONS = [
  { value: 'all', label: 'All actions' },
  ...KNOWN_ACTIONS.map((a) => ({ value: a, label: a.replace(/_/g, ' ') })),
];

const RESOURCE_TYPE_OPTIONS = [
  { value: 'all', label: 'All resources' },
  ...RESOURCE_TYPES.map((t) => ({ value: t, label: t.replace(/_/g, ' ') })),
];

/** A single unified row the table renders, tagging its origin feed so the new "Resource"
 * column can stay blank for legacy rows (which have no resource-type concept). */
interface MergedRow {
  id: string;
  origin: 'legacy' | 'config';
  timeMillis: number;
  actor: string | null;
  action: string;
  resourceType: string | null;
  resourceLabel: string | null;
  details: string | null;
}

const actionTone = (
  action: string
): 'success' | 'error' | 'warning' | 'info' | 'neutral' => {
  switch (action) {
    case 'LOGIN_FAILED':
    case 'USER_DELETED':
    case 'USER_DEACTIVATED':
    case 'delete':
    case 'disable':
      return 'error';
    case 'USER_CREATED':
    case 'USER_ACTIVATED':
    case 'LOGIN_SUCCESS':
    case 'create':
    case 'enable':
    case 'set_default':
      return 'success';
    case 'revert':
      return 'warning';
    default:
      return 'info';
  }
};

const formatTime = (millis: number): string =>
  new Date(millis).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

const serverError = (err: unknown): string | null =>
  (err as { response?: { data?: { error?: string } } })?.response?.data?.error ||
  null;

const AuditLogPage: React.FC = () => {
  const notify = useNotification();
  const admin = isAdmin();

  const [legacyItems, setLegacyItems] = useState<AuditEntryDto[]>([]);
  const [legacyTotal, setLegacyTotal] = useState(0);
  const [configItems, setConfigItems] = useState<ConfigAuditEventDto[]>([]);
  const [configTotal, setConfigTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [action, setAction] = useState('all');
  const [resourceType, setResourceType] = useState('all');
  const [offset, setOffset] = useState(0);

  // Admin-configurable read-access toggle for the config-audit feed/revision history
  // (non-admin roles: Supervisor/Investigator/Analyst/Policymaker). Admin-only to view/change;
  // the backend endpoint itself has no role gate so any authenticated user could read it, but
  // there's no reason to show the control to non-admins since they can't change it (403).
  const [nonAdminReadEnabled, setNonAdminReadEnabled] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);

  useEffect(() => {
    if (!admin) return;
    configAuditApi
      .getSettings()
      .then((s) => setNonAdminReadEnabled(s.nonAdminReadEnabled))
      .catch(() => {});
  }, [admin]);

  const toggleNonAdminRead = async (checked: boolean) => {
    setSavingSettings(true);
    try {
      const updated = await configAuditApi.updateSettings(checked);
      setNonAdminReadEnabled(updated.nonAdminReadEnabled);
      notify(
        updated.nonAdminReadEnabled
          ? 'Supervisor, Investigator, Analyst, and Policymaker roles can now view audit history and revisions (read-only)'
          : 'Audit history and revisions are admin-only again',
        'Success'
      );
    } catch (e) {
      notify(
        serverError(e) || (e as Error)?.message || 'Failed to update audit access setting',
        'Error'
      );
    } finally {
      setSavingSettings(false);
    }
  };

  // Debounce the search box (~300ms).
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  // Reset to the first page whenever filters change.
  useEffect(() => {
    setOffset(0);
  }, [debouncedSearch, action, resourceType]);

  const reqId = useRef(0);

  const load = useCallback(async () => {
    const id = ++reqId.current;
    setLoading(true);
    // Selecting a specific resource type only makes sense for the config-audit feed —
    // legacy user_audit_log rows have no resource-type concept, so they're hidden while
    // a resource-type filter is active rather than shown unfiltered alongside it.
    const filteringByResourceType = resourceType !== 'all';
    const page = Math.floor(offset / PAGE_SIZE);
    try {
      const [legacyResult, configResult] = await Promise.allSettled([
        filteringByResourceType
          ? Promise.resolve({ items: [] as AuditEntryDto[], total: 0 })
          : auditApi.list({
              search: debouncedSearch || undefined,
              action: action === 'all' ? undefined : action || undefined,
              limit: PAGE_SIZE,
              offset,
            }),
        configAuditApi.listFeed({
          resourceType: filteringByResourceType ? resourceType : undefined,
          page,
          size: PAGE_SIZE,
        }),
      ]);
      if (id !== reqId.current) return;

      if (legacyResult.status === 'fulfilled') {
        setLegacyItems(legacyResult.value.items);
        setLegacyTotal(legacyResult.value.total);
      } else {
        setLegacyItems([]);
        setLegacyTotal(0);
      }

      if (configResult.status === 'fulfilled') {
        let items = configResult.value.items;
        // The config-audit feed has no free-text search server-side (only
        // resourceType/actor/resourceId/from/to). Apply the same search string as a
        // client-side filter over this page's raw batch — a known limitation: a match
        // that falls outside the current raw batch won't surface until you page to it.
        if (debouncedSearch) {
          const needle = debouncedSearch.toLowerCase();
          items = items.filter(
            (e) =>
              e.actor.toLowerCase().includes(needle) ||
              (e.resourceName || '').toLowerCase().includes(needle) ||
              (e.summary || '').toLowerCase().includes(needle) ||
              e.resourceId.toLowerCase().includes(needle)
          );
        }
        setConfigItems(items);
        setConfigTotal(configResult.value.total);
      } else {
        setConfigItems([]);
        setConfigTotal(0);
      }

      if (legacyResult.status === 'rejected' && configResult.status === 'rejected') {
        notify(
          serverError(legacyResult.reason) || 'Failed to load audit log',
          'Error'
        );
      }
    } finally {
      if (id === reqId.current) setLoading(false);
    }
  }, [debouncedSearch, action, resourceType, offset, notify]);

  useEffect(() => {
    void load();
  }, [load]);

  // Merge-sort by real wall-clock time. IMPORTANT unit note: the legacy feed's
  // createdAt is epoch millis (System.currentTimeMillis() in AuditService.record),
  // while the new config-audit feed's createdAt is epoch SECONDS
  // (Instant.now().getEpochSecond() in ConfigAuditService) — verified in the actual
  // backend code, not assumed. Normalize both to millis before comparing.
  const merged: MergedRow[] = useMemo(() => {
    const legacyRows: MergedRow[] = legacyItems.map((e) => ({
      id: `legacy-${e.id}`,
      origin: 'legacy',
      timeMillis: e.createdAt,
      actor: e.actor,
      action: e.action,
      resourceType: null,
      resourceLabel: e.target,
      details: e.details,
    }));
    const configRows: MergedRow[] = configItems.map((e) => ({
      id: `config-${e.id}`,
      origin: 'config',
      timeMillis: e.createdAt * 1000,
      actor: e.actor,
      action: e.action,
      resourceType: e.resourceType,
      resourceLabel: e.resourceName,
      details: e.summary,
    }));
    return [...legacyRows, ...configRows]
      .sort((a, b) => b.timeMillis - a.timeMillis)
      .slice(0, PAGE_SIZE);
  }, [legacyItems, configItems]);

  const total = legacyTotal + configTotal;

  const columns: CustomTableColumnsType<MergedRow> = [
    {
      title: 'Time',
      key: 'time',
      width: 180,
      render: (_: unknown, record) => (
        <span className={styles.cellMuted}>{formatTime(record.timeMillis)}</span>
      ),
    },
    {
      title: 'Actor',
      key: 'actor',
      width: 160,
      render: (_: unknown, record) =>
        record.actor ? (
          <span className="font-mono text-[13px] text-foreground">
            {record.actor}
          </span>
        ) : (
          <span className={styles.cellMuted}>system</span>
        ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 160,
      render: (_: unknown, record) => (
        <CustomTag tone={actionTone(record.action)}>
          {record.action.replace(/_/g, ' ')}
        </CustomTag>
      ),
    },
    {
      title: 'Resource',
      key: 'resource',
      width: 200,
      render: (_: unknown, record) =>
        record.origin === 'config' ? (
          <span className="text-[13px] text-foreground">
            <span className="text-muted-foreground">
              {record.resourceType ? `${record.resourceType.replace(/_/g, ' ')}: ` : ''}
            </span>
            {record.resourceLabel || '—'}
          </span>
        ) : (
          <span className={styles.cellMuted}>—</span>
        ),
    },
    {
      title: 'Details',
      key: 'details',
      render: (_: unknown, record) =>
        record.details ? (
          <span className="text-[13px] text-muted-foreground">
            {record.details}
          </span>
        ) : (
          <span className={styles.cellMuted}>—</span>
        ),
    },
  ];

  const pageStart = total === 0 ? 0 : offset + 1;
  const pageEnd = Math.min(offset + merged.length, total);
  const canPrev = offset > 0;
  const canNext = offset + PAGE_SIZE < total;
  const hasFilters = !!debouncedSearch || action !== 'all' || resourceType !== 'all';

  const rangeLabel = useMemo(() => {
    if (total === 0) return 'No entries';
    return `${pageStart}–${pageEnd} of ${total}`;
  }, [total, pageStart, pageEnd]);

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.pageHeaderMain}>
          <div className={styles.pageEyebrow}>Platform</div>
          <h1 className={styles.pageTitle}>Audit log</h1>
          <p className={styles.pageSubtitle}>
            A record of user, access, and administrative events across the
            platform, including every change to assistants, skills, response
            styles, and UI templates.
          </p>
        </div>
      </header>

      <div className={styles.body}>
        {admin && (
          <div className="flex items-center justify-between rounded-md border border-border bg-muted/30 px-4 py-3">
            <div className="pr-4">
              <div className="text-sm font-medium text-foreground">
                Allow read-only access for non-admin roles
              </div>
              <p className="text-xs text-muted-foreground">
                Lets Supervisor, Investigator, Analyst, and Policymaker roles view the
                audit history and revisions (read-only) below and on assistant/skill/
                response-style/template pages. Revert always stays admin-only.
              </p>
            </div>
            <CustomSwitch
              checked={nonAdminReadEnabled}
              onChange={(checked) => void toggleNonAdminRead(checked)}
              disabled={savingSettings}
              ariaLabel="Allow non-admin read access to audit history"
            />
          </div>
        )}

        <div className={styles.toolbar}>
          <div className={styles.toolbarSearch}>
            <CustomInput
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search actor, target, or details"
              allowClear
              prefix={<CustomIcon name="search" size={14} />}
            />
          </div>
          <div className={styles.toolbarFilter}>
            <CustomSelect
              value={action}
              onChange={(v) => setAction(v || 'all')}
              options={ACTION_OPTIONS}
              placeholder="All actions"
            />
          </div>
          <div className={styles.toolbarFilter}>
            <CustomSelect
              value={resourceType}
              onChange={(v) => setResourceType(v || 'all')}
              options={RESOURCE_TYPE_OPTIONS}
              placeholder="All resources"
            />
          </div>
        </div>

        <div className={styles.tableWrap}>
          {!loading && merged.length === 0 ? (
            <CustomEmptyState
              icon={<CustomIcon name="audit" size={40} />}
              title={hasFilters ? 'No matching events' : 'No audit events yet'}
              description={
                hasFilters
                  ? 'Try adjusting your search, action, or resource filter.'
                  : 'Platform activity will appear here as it happens.'
              }
            />
          ) : (
            <CustomTable<MergedRow>
              rowKey="id"
              dataSource={merged}
              columns={columns}
              loading={loading}
            />
          )}
        </div>

        {(total > 0 || canPrev) && (
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">{rangeLabel}</span>
            <div className="flex items-center gap-2">
              <CustomButton
                variant="ghost"
                size="small"
                disabled={!canPrev || loading}
                onClick={() => setOffset((o) => Math.max(0, o - PAGE_SIZE))}
                icon={<ChevronLeft className="size-4" />}
              >
                Previous
              </CustomButton>
              <CustomButton
                variant="ghost"
                size="small"
                disabled={!canNext || loading}
                onClick={() => setOffset((o) => o + PAGE_SIZE)}
              >
                Next
                <ChevronRight className="size-4" />
              </CustomButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AuditLogPage;
