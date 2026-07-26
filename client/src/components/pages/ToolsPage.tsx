import React, { useCallback, useEffect, useMemo, useState } from 'react';
import FormTemplate from '@templates/FormTemplate';
import CustomInput from '@atoms/CustomInput';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import CustomDropdown from '@atoms/CustomDropdown';
import CustomIcon from '@atoms/CustomIcon';
import CustomModal from '@atoms/CustomModal';
import CustomSwitch from '@atoms/CustomSwitch';
import CustomTabs from '@atoms/CustomTabs';
import CustomTag from '@atoms/CustomTag';
import { confirm } from '@atoms/CustomConfirm';
import ImportToolsDialog from '@molecules/ImportToolsDialog';
import JsonEditor from '@molecules/JsonEditor';
import AuthProfilesPage from './AuthProfilesPage';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import { toolsApi, authProfilesApi, toolGroupsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { HTTP_METHODS, TOOL_AUTH_TYPES } from '@constants/toolSourceKinds';
import { formatJson } from '@utils/formatJson';
import type {
  AgentToolDto,
  CreateToolRequest,
  HttpToolMethod,
} from '@interfaces/tool.interface';
import type {
  ToolGroupDto,
  CreateToolGroupRequest,
  ImportToolsResult,
} from '@interfaces/toolGroup.interface';
import type { ToolAuthProfileDto } from '@interfaces/auth.interface';
import * as styles from '@styles/toolsPage.module.scss';

const EMPTY: CreateToolRequest = {
  name: '',
  description: '',
  method: 'GET',
  host: '',
  endpoint: '',
  requestSchema: '',
  authProfileId: '',
  authType: 'none',
  authConfig: '',
  groupId: '',
  enabled: true,
};

const EMPTY_GROUP: CreateToolGroupRequest = {
  name: '',
  description: '',
  enabled: true,
};

const methodBadgeClass = (method: HttpToolMethod): string => {
  switch (method) {
    case 'GET':
      return styles.methodGet;
    case 'POST':
      return styles.methodPost;
    case 'PUT':
    case 'PATCH':
      return styles.methodPut;
    case 'DELETE':
      return styles.methodDelete;
    default:
      return styles.methodGet;
  }
};

const formatToolJson = (raw: string | null | undefined): string =>
  raw?.trim() ? formatJson(raw) || raw : '';

const normalizeForm = (f: CreateToolRequest): CreateToolRequest => ({
  name: f.name.trim(),
  description: (f.description || '').trim(),
  method: f.method,
  host: f.host.trim(),
  endpoint: f.endpoint.trim(),
  requestSchema: (f.requestSchema || '').trim(),
  authProfileId: f.authProfileId || '',
  authType: f.authType || 'none',
  authConfig: (f.authConfig || '').trim(),
  groupId: f.groupId || '',
  enabled: f.enabled ?? true,
});

const formsEqual = (a: CreateToolRequest, b: CreateToolRequest): boolean => {
  const na = normalizeForm(a);
  const nb = normalizeForm(b);
  return (
    na.name === nb.name &&
    na.description === nb.description &&
    na.method === nb.method &&
    na.host === nb.host &&
    na.endpoint === nb.endpoint &&
    na.requestSchema === nb.requestSchema &&
    na.authProfileId === nb.authProfileId &&
    na.authType === nb.authType &&
    na.authConfig === nb.authConfig &&
    na.groupId === nb.groupId &&
    na.enabled === nb.enabled
  );
};

const normalizeGroupForm = (f: CreateToolGroupRequest): CreateToolGroupRequest => ({
  name: f.name.trim(),
  description: (f.description || '').trim(),
  enabled: f.enabled ?? true,
});

const groupFormsEqual = (
  a: CreateToolGroupRequest,
  b: CreateToolGroupRequest
): boolean => {
  const na = normalizeGroupForm(a);
  const nb = normalizeGroupForm(b);
  return (
    na.name === nb.name &&
    na.description === nb.description &&
    na.enabled === nb.enabled
  );
};

const ToolsPage: React.FC = () => {
  const openNotification = useNotification();
  const { assistant, assistantId } = useSettingsScope();
  const [items, setItems] = useState<AgentToolDto[]>([]);
  const [groups, setGroups] = useState<ToolGroupDto[]>([]);
  const [expandedGroupIds, setExpandedGroupIds] = useState<Set<string>>(
    () => new Set()
  );
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [creatingGroup, setCreatingGroup] = useState(false);
  const [groupForm, setGroupForm] = useState<CreateToolGroupRequest>(EMPTY_GROUP);
  const [groupSaving, setGroupSaving] = useState(false);
  const [profiles, setProfiles] = useState<ToolAuthProfileDto[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CreateToolRequest>(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [testInput, setTestInput] = useState('');
  const [testOutput, setTestOutput] = useState<string | null>(null);
  const [testError, setTestError] = useState('');
  const [testing, setTesting] = useState(false);
  const [testModalOpen, setTestModalOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [backfilling, setBackfilling] = useState(false);
  const [activeSectionTab, setActiveSectionTab] = useState<'tools' | 'auth-profiles'>('tools');

  const selectedTool = useMemo(
    () => items.find((t) => t.id === editingId) || null,
    [items, editingId]
  );

  const selectedGroup = useMemo(
    () => groups.find((g) => g.id === selectedGroupId) || null,
    [groups, selectedGroupId]
  );

  const toolsByGroup = useMemo(() => {
    const map = new Map<string, AgentToolDto[]>();
    for (const group of groups) {
      map.set(group.id, []);
    }
    const ungrouped: AgentToolDto[] = [];
    for (const tool of items) {
      if (tool.groupId && map.has(tool.groupId)) {
        map.get(tool.groupId)!.push(tool);
      } else {
        ungrouped.push(tool);
      }
    }
    return { map, ungrouped };
  }, [groups, items]);

  const legacyUngroupedCount = useMemo(
    () =>
      toolsByGroup.ungrouped.filter(
        (t) =>
          t.sourceType === 'openapi_import' ||
          t.sourceType === 'postman_import'
      ).length,
    [toolsByGroup.ungrouped]
  );

  const savedGroupForm = useMemo<CreateToolGroupRequest>(() => {
    if (creatingGroup) return EMPTY_GROUP;
    if (!selectedGroup) return EMPTY_GROUP;
    return {
      name: selectedGroup.name,
      description: selectedGroup.description || '',
      enabled: selectedGroup.enabled,
    };
  }, [creatingGroup, selectedGroup]);

  const isGroupDirty = useMemo(
    () =>
      (creatingGroup || !!selectedGroupId) &&
      !groupFormsEqual(groupForm, savedGroupForm),
    [groupForm, savedGroupForm, creatingGroup, selectedGroupId]
  );

  const canSaveGroup =
    isGroupDirty && !!groupForm.name.trim() && !groupSaving;

  const savedForm = useMemo<CreateToolRequest>(() => {
    if (editingId === 'new') return EMPTY;
    if (!selectedTool) return EMPTY;
    return {
      name: selectedTool.name,
      description: selectedTool.description,
      method: selectedTool.method,
      host: selectedTool.host,
      endpoint: selectedTool.endpoint,
      requestSchema: formatToolJson(selectedTool.requestSchema),
      authProfileId: selectedTool.authProfileId || '',
      authType: selectedTool.authType || 'none',
      authConfig: formatToolJson(selectedTool.authConfig),
      groupId: selectedTool.groupId || '',
      enabled: selectedTool.enabled,
    };
  }, [editingId, selectedTool]);

  const isDirty = useMemo(
    () => editingId !== null && !formsEqual(form, savedForm),
    [form, savedForm, editingId]
  );

  const canSave =
    isDirty &&
    !!form.name.trim() &&
    !!form.host.trim() &&
    !!form.endpoint.trim() &&
    !saving;

  const refresh = useCallback(async () => {
    if (!assistantId) {
      setItems([]);
      setGroups([]);
      return;
    }
    try {
      const [tools, nextGroups] = await Promise.all([
        toolsApi.list(assistantId),
        toolGroupsApi.list(assistantId),
      ]);
      setItems(tools);
      setGroups(nextGroups);
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load tools',
        'Error'
      );
    }
  }, [assistantId, openNotification]);

  useEffect(() => {
    void refresh();
    setEditingId(null);
    setForm(EMPTY);
    setExpandedGroupIds(new Set());
    setSelectedGroupId(null);
    setCreatingGroup(false);
    setGroupForm(EMPTY_GROUP);
  }, [assistantId, refresh]);

  useEffect(() => {
    if (!assistantId) {
      setProfiles([]);
      return;
    }
    authProfilesApi.list(assistantId).then(setProfiles).catch(() => setProfiles([]));
  }, [assistantId]);

  const discardIfDirty = (action: () => void) => {
    if (!isDirty && !isGroupDirty) {
      action();
      return;
    }
    confirm({
      title: 'Discard unsaved changes?',
      body: 'You have unsaved edits.',
      danger: true,
      okText: 'Discard',
      onOk: action,
    });
  };

  const clearGroupSelection = () => {
    setSelectedGroupId(null);
    setCreatingGroup(false);
    setGroupForm(EMPTY_GROUP);
  };

  const toggleGroupExpand = (group: ToolGroupDto) => {
    const isOpen = expandedGroupIds.has(group.id);
    if (isOpen) {
      setExpandedGroupIds((prev) => {
        const next = new Set(prev);
        next.delete(group.id);
        return next;
      });
      return;
    }

    discardIfDirty(() => {
      setExpandedGroupIds((prev) => new Set(prev).add(group.id));
      setSelectedGroupId(group.id);
      setCreatingGroup(false);
      setEditingId(null);
      setGroupForm({
        name: group.name,
        description: group.description || '',
        enabled: group.enabled,
      });
    });
  };

  const startNewGroup = () => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    discardIfDirty(() => {
      setCreatingGroup(true);
      setSelectedGroupId(null);
      setEditingId(null);
      setGroupForm({ ...EMPTY_GROUP, name: 'New group' });
      setError('');
    });
  };

  const startNew = (groupId?: string) => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    discardIfDirty(() => {
      setEditingId('new');
      setForm({ ...EMPTY, groupId: groupId || '' });
      clearGroupSelection();
      setError('');
      setTestOutput(null);
      setTestError('');
      setTestInput('');
      setTestModalOpen(false);
      setAuthModalOpen(false);
    });
  };

  const startEdit = (t: AgentToolDto) => {
    if (editingId === t.id) return;
    discardIfDirty(() => {
      setEditingId(t.id);
      setForm({
        name: t.name,
        description: t.description,
        method: t.method,
        host: t.host,
        endpoint: t.endpoint,
        requestSchema: formatToolJson(t.requestSchema),
        authProfileId: t.authProfileId || '',
        authType: t.authType || 'none',
        authConfig: formatToolJson(t.authConfig),
        groupId: t.groupId || '',
        enabled: t.enabled,
      });
      if (t.groupId) {
        setExpandedGroupIds((prev) => new Set(prev).add(t.groupId!));
        setSelectedGroupId(t.groupId);
        setCreatingGroup(false);
        const group = groups.find((g) => g.id === t.groupId);
        if (group) {
          setGroupForm({
            name: group.name,
            description: group.description || '',
            enabled: group.enabled,
          });
        }
      } else {
        clearGroupSelection();
      }
      setError('');
      setTestOutput(null);
      setTestError('');
      setTestInput('');
      setTestModalOpen(false);
      setAuthModalOpen(false);
    });
  };

  const cancel = () => {
    setEditingId(null);
    setForm(EMPTY);
    setError('');
    setTestOutput(null);
    setTestError('');
    setTestModalOpen(false);
    setAuthModalOpen(false);
  };

  const saveGroup = async () => {
    if (!groupForm.name.trim()) {
      setError('Group name is required.');
      return;
    }
    setGroupSaving(true);
    setError('');
    try {
      const body = normalizeGroupForm(groupForm);
      if (creatingGroup) {
        const created = await toolGroupsApi.create(assistantId, body);
        openNotification(`Group "${created.name}" created`, 'Success');
        setCreatingGroup(false);
        setSelectedGroupId(created.id);
        setExpandedGroupIds((prev) => new Set(prev).add(created.id));
        setGroupForm({
          name: created.name,
          description: created.description || '',
          enabled: created.enabled,
        });
      } else if (selectedGroupId) {
        const updated = await toolGroupsApi.update(selectedGroupId, body);
        openNotification(`Group "${updated.name}" updated`, 'Success');
        setGroupForm({
          name: updated.name,
          description: updated.description || '',
          enabled: updated.enabled,
        });
      }
      await refresh();
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save group');
    } finally {
      setGroupSaving(false);
    }
  };

  const removeGroup = async (group: ToolGroupDto) => {
    confirm({
      title: `Delete "${group.name}"?`,
      body: 'This removes the group and all tools inside it.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await toolGroupsApi.delete(group.id);
          if (selectedGroupId === group.id) clearGroupSelection();
          if (editingId && items.find((t) => t.id === editingId)?.groupId === group.id) {
            cancel();
          }
          setExpandedGroupIds((prev) => {
            const next = new Set(prev);
            next.delete(group.id);
            return next;
          });
          await refresh();
          openNotification(`Group "${group.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete group',
            'Error'
          );
        }
      },
    });
  };

  const toggleGroupEnabled = () => {
    setGroupForm((f) => ({ ...f, enabled: !f.enabled }));
  };

  const organizeLegacyImports = async () => {
    if (!assistantId) return;
    setBackfilling(true);
    try {
      const { groupsCreated } = await toolGroupsApi.backfill(assistantId);
      await refresh();
      openNotification(
        groupsCreated > 0
          ? `Organized ${groupsCreated} group(s) from existing imports`
          : 'No legacy imports needed grouping',
        'Success'
      );
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to organize imports',
        'Error'
      );
    } finally {
      setBackfilling(false);
    }
  };

  const handleImported = async (result: ImportToolsResult) => {
    await refresh();
    if (result.groupId) {
      setExpandedGroupIds((prev) => new Set(prev).add(result.groupId!));
      setSelectedGroupId(result.groupId);
      setCreatingGroup(false);
      setEditingId(null);
      if (result.groupName) {
        setGroupForm({
          name: result.groupName,
          description: '',
          enabled: true,
        });
      }
    }
  };

  const save = async (): Promise<boolean> => {
    if (!form.name.trim() || !form.host.trim() || !form.endpoint.trim()) {
      setError('Name, host, and endpoint are required.');
      return false;
    }
    setSaving(true);
    setError('');
    try {
      const body: CreateToolRequest = {
        ...normalizeForm(form),
        authProfileId: form.authProfileId || null,
        authType: form.authType || null,
        authConfig: form.authConfig?.trim() || null,
        groupId: form.groupId || null,
      };
      if (editingId === 'new') {
        const created = await toolsApi.create(assistantId, body);
        openNotification(`Tool "${created.name}" created`, 'Success');
        setEditingId(created.id);
        setForm({
          name: created.name,
          description: created.description,
          method: created.method,
          host: created.host,
          endpoint: created.endpoint,
          requestSchema: formatToolJson(created.requestSchema),
          authProfileId: created.authProfileId || '',
          authType: created.authType || 'none',
          authConfig: formatToolJson(created.authConfig),
          groupId: created.groupId || '',
          enabled: created.enabled,
        });
      } else if (editingId) {
        const updated = await toolsApi.update(editingId, body);
        openNotification(`Tool "${updated.name}" updated`, 'Success');
        setForm({
          name: updated.name,
          description: updated.description,
          method: updated.method,
          host: updated.host,
          endpoint: updated.endpoint,
          requestSchema: formatToolJson(updated.requestSchema),
          authProfileId: updated.authProfileId || '',
          authType: updated.authType || 'none',
          authConfig: formatToolJson(updated.authConfig),
          groupId: updated.groupId || '',
          enabled: updated.enabled,
        });
      }
      await refresh();
      return true;
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save');
      return false;
    } finally {
      setSaving(false);
    }
  };

  const remove = async (t: AgentToolDto) => {
    confirm({
      title: `Delete "${t.name}"?`,
      body: 'This removes the HTTP tool from this assistant.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await toolsApi.delete(t.id);
          if (editingId === t.id) cancel();
          await refresh();
          openNotification(`Tool "${t.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete tool',
            'Error'
          );
        }
      },
    });
  };

  const toggleEnabled = (checked: boolean) => {
    setForm((f) => ({ ...f, enabled: checked }));
  };

  const authSummary = useMemo(() => {
    if (form.authProfileId) {
      const profile = profiles.find((p) => p.id === form.authProfileId);
      return profile ? `Profile · ${profile.name}` : 'Auth profile';
    }
    if (form.authType && form.authType !== 'none') {
      const typeLabel =
        TOOL_AUTH_TYPES.find((t) => t.value === form.authType)?.label ||
        form.authType;
      return `Inline · ${typeLabel}`;
    }
    return 'None';
  }, [form.authProfileId, form.authType, profiles]);

  const runTest = async () => {
    if (!editingId || editingId === 'new') return;
    setTesting(true);
    setTestError('');
    setTestOutput(null);
    try {
      const out = await toolsApi.test(editingId, { input: testInput });
      setTestOutput(typeof out === 'string' ? out : JSON.stringify(out));
    } catch (e) {
      setTestError((e as Error)?.message || 'Test failed');
    } finally {
      setTesting(false);
    }
  };

  const profileOptions = [
    { value: '', label: 'None (inline auth)' },
    ...profiles.map((p) => ({ value: p.id, label: p.name })),
  ];

  const groupOptions = [
    { value: '', label: 'No group' },
    ...groups.map((g) => ({ value: g.id, label: g.name })),
  ];

  const headerTitle =
    editingId === 'new' ? 'New tool' : form.name.trim() || 'Untitled tool';

  const groupHeaderTitle = creatingGroup
    ? 'New group'
    : groupForm.name.trim() || 'Untitled group';

  const groupMenuItems = (group: ToolGroupDto) => [
    {
      key: 'add-tool',
      label: (
        <span className={styles.menuItem}>
          <CustomIcon name="plus" size={14} />
          Add tool
        </span>
      ),
      onClick: () => startNew(group.id),
    },
    {
      key: 'delete',
      label: (
        <span className={`${styles.menuItem} ${styles.menuItemDanger}`}>
          <CustomIcon name="delete" size={14} />
          Delete group
        </span>
      ),
      danger: true,
      onClick: () => void removeGroup(group),
    },
  ];

  const headerEndpoint =
    form.host.trim() || form.endpoint.trim()
      ? `${form.method} · ${form.host.trim()}${form.endpoint.trim()}`
      : `${form.method} · configure endpoint`;

  const actionMenuItems = [
    {
      key: 'auth',
      label: (
        <span className={styles.menuItem}>
          <CustomIcon name="key" size={14} />
          Authentication
        </span>
      ),
      onClick: () => setAuthModalOpen(true),
    },
    ...(selectedTool
      ? [
          {
            key: 'delete',
            label: (
              <span className={`${styles.menuItem} ${styles.menuItemDanger}`}>
                <CustomIcon name="delete" size={14} />
                Delete
              </span>
            ),
            danger: true,
            onClick: () => void remove(selectedTool),
          },
        ]
      : []),
  ];

  const catalogEmpty =
    !!assistantId &&
    groups.length === 0 &&
    items.length === 0 &&
    editingId !== 'new' &&
    !creatingGroup;

  return (
    <>
      <div className={styles.page}>
        <header className={styles.pageHeader}>
          <div className={styles.pageHeaderMain}>
            <h1 className={styles.pageTitle}>HTTP tools</h1>
            <p className={styles.pageSubtitle}>
              {activeSectionTab === 'tools'
                ? assistant
                  ? `Endpoints ${assistant.name} can call as tools.`
                  : 'Register HTTP endpoints the assistant can call as tools.'
                : 'Reusable HTTP auth configurations referenced by tools.'}
            </p>
          </div>
          {activeSectionTab === 'tools' && (
            <div className={styles.pageHeaderActions}>
              <CustomButton
                variant="secondary"
                size="small"
                onClick={() => setImportOpen(true)}
                disabled={!assistantId}
              >
                <CustomIcon name="upload" size={14} />
                Import
              </CustomButton>
              {!catalogEmpty && (
                <CustomButton
                  variant="primary"
                  size="small"
                  disabled={!assistantId}
                  onClick={() => startNew()}
                >
                  <CustomIcon name="plus" size={14} />
                  New tool
                </CustomButton>
              )}
            </div>
          )}
        </header>

        <div className={styles.sectionTabs}>
          <CustomTabs
            items={[
              { key: 'tools', label: 'Tools' },
              { key: 'auth-profiles', label: 'Auth profiles' },
            ]}
            activeKey={activeSectionTab}
            onChange={(key) => setActiveSectionTab(key as 'tools' | 'auth-profiles')}
          />
        </div>

        {activeSectionTab === 'auth-profiles' ? (
          <AuthProfilesPage hideHeader />
        ) : (
        <div className={styles.workspace}>
          <aside className={styles.sidebar}>
            <div className={styles.sidebarHeader}>
              <span className={styles.sidebarTitle}>Catalog</span>
              <div className={styles.sidebarHeaderActions}>
                <CustomButton
                  variant="ghost"
                  size="small"
                  disabled={!assistantId}
                  onClick={startNewGroup}
                >
                  New group
                </CustomButton>
              </div>
            </div>
            <div className={styles.toolList}>
              {assistantId && legacyUngroupedCount > 0 && (
                <div className={styles.legacyBanner}>
                  <p>
                    {legacyUngroupedCount} imported tool
                    {legacyUngroupedCount === 1 ? '' : 's'} from before groups
                    existed.
                  </p>
                  <CustomButton
                    variant="ghost"
                    size="small"
                    loading={backfilling}
                    onClick={() => void organizeLegacyImports()}
                  >
                    Organize into groups
                  </CustomButton>
                </div>
              )}
              {!assistantId && (
                <div className={styles.emptyList}>
                  Pick an assistant in the left menu to manage tools.
                </div>
              )}
              {catalogEmpty && (
                <div className={styles.emptyList}>
                  <p className={styles.emptyListTitle}>No tools yet</p>
                  <p>Import an OpenAPI/Postman collection or add an endpoint.</p>
                </div>
              )}

              {groups.map((group) => {
                const open = expandedGroupIds.has(group.id);
                const groupTools = toolsByGroup.map.get(group.id) || [];
                const selected =
                  selectedGroupId === group.id && !editingId && !creatingGroup;

                return (
                  <div key={group.id} className={styles.toolGroup}>
                    <button
                      type="button"
                      className={`${styles.toolGroupToggle} ${
                        open ? styles.toolGroupToggleOpen : ''
                      } ${selected ? styles.toolGroupToggleSelected : ''} ${
                        !group.enabled ? styles.toolRowDisabled : ''
                      }`}
                      onClick={() => toggleGroupExpand(group)}
                    >
                      <span className={styles.toolGroupName}>{group.name}</span>
                      <span className={styles.toolGroupMeta}>
                        {groupTools.length}
                      </span>
                      <span className={styles.toolGroupCaret}>
                        <CustomIcon
                          name={open ? 'caret-down' : 'caret-right'}
                          size={11}
                        />
                      </span>
                    </button>

                    {open && (
                      <div className={styles.toolGroupBody}>
                        {groupTools.map((t) => (
                          <button
                            key={t.id}
                            type="button"
                            className={`${styles.toolRow} ${
                              editingId === t.id ? styles.toolRowActive : ''
                            } ${!t.enabled ? styles.toolRowDisabled : ''}`}
                            onClick={() => startEdit(t)}
                          >
                            <div className={styles.toolRowTop}>
                              <span
                                className={`${styles.methodBadge} ${methodBadgeClass(t.method)}`}
                              >
                                {t.method}
                              </span>
                              <span className={styles.toolRowName}>
                                {t.name}
                              </span>
                            </div>
                            <span className={styles.toolRowMeta}>
                              {t.endpoint}
                            </span>
                          </button>
                        ))}
                        {groupTools.length === 0 && (
                          <div className={styles.emptyList}>
                            No tools in this group
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}

              {toolsByGroup.ungrouped.length > 0 && (
                <>
                  <div className={styles.sidebarSectionLabel}>Ungrouped</div>
                  {toolsByGroup.ungrouped.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      className={`${styles.toolRow} ${
                        editingId === t.id ? styles.toolRowActive : ''
                      } ${!t.enabled ? styles.toolRowDisabled : ''}`}
                      onClick={() => startEdit(t)}
                    >
                      <div className={styles.toolRowTop}>
                        <span
                          className={`${styles.methodBadge} ${methodBadgeClass(t.method)}`}
                        >
                          {t.method}
                        </span>
                        <span className={styles.toolRowName}>{t.name}</span>
                      </div>
                      <span className={styles.toolRowMeta}>{t.endpoint}</span>
                    </button>
                  ))}
                </>
              )}

              {editingId === 'new' && (
                <button
                  type="button"
                  className={`${styles.toolRow} ${styles.toolRowActive}`}
                >
                  <div className={styles.toolRowTop}>
                    <span
                      className={`${styles.methodBadge} ${methodBadgeClass('GET')}`}
                    >
                      NEW
                    </span>
                    <span className={styles.toolRowName}>New tool</span>
                  </div>
                </button>
              )}
            </div>
          </aside>

          <section className={styles.editorPane}>
            {!editingId && !creatingGroup && !selectedGroupId ? (
              <div className={styles.editorEmpty}>
                <CustomIcon name="tool" size={36} />
                {!assistantId ? (
                  <>
                    <p>Pick an assistant</p>
                    <span>
                      Choose an assistant in the settings sidebar to manage its
                      HTTP tools.
                    </span>
                  </>
                ) : catalogEmpty ? (
                  <>
                    <p>No HTTP tools yet</p>
                    <span>
                      Register an endpoint manually, or import an OpenAPI /
                      Postman collection so {assistant?.name || 'the assistant'}{' '}
                      can call it.
                    </span>
                    <div className={styles.editorEmptyActions}>
                      <CustomButton
                        variant="primary"
                        onClick={() => startNew()}
                      >
                        <CustomIcon name="plus" size={14} />
                        New tool
                      </CustomButton>
                      <CustomButton
                        variant="secondary"
                        onClick={() => setImportOpen(true)}
                      >
                        <CustomIcon name="upload" size={14} />
                        Import
                      </CustomButton>
                    </div>
                  </>
                ) : (
                  <>
                    <p>Select a group or tool</p>
                    <span>
                      Expand a group in the catalog to browse tools, or create a
                      new endpoint.
                    </span>
                  </>
                )}
              </div>
            ) : editingId ? (
              <>
                <div className={styles.editorHeader}>
                  <div className={styles.editorHeaderMain}>
                    <h2 className={styles.editorTitle}>{headerTitle}</h2>
                    <p className={styles.editorEndpoint}>{headerEndpoint}</p>
                  </div>
                  <div className={styles.editorHeaderActions}>
                    {editingId !== 'new' && (
                      <CustomButton
                        variant="ghost"
                        size="small"
                        onClick={() => setTestModalOpen(true)}
                      >
                        <CustomIcon name="play" size={14} />
                        Test
                      </CustomButton>
                    )}
                    <CustomSwitch
                      checked={!!form.enabled}
                      onChange={toggleEnabled}
                      ariaLabel={
                        form.enabled ? 'Disable tool' : 'Enable tool'
                      }
                    />
                    <CustomDropdown
                      items={actionMenuItems}
                      placement="bottomRight"
                    >
                      <CustomButton
                        variant="text"
                        size="small"
                        aria-label="Tool actions"
                      >
                        <CustomIcon name="more" size={16} />
                      </CustomButton>
                    </CustomDropdown>
                  </div>
                </div>

                <div className={styles.formScroll}>
                  <FormTemplate
                    onSubmit={(e) => {
                      e.preventDefault();
                      void save();
                    }}
                  >
                  <div className={styles.formGrid}>
                    <div>
                      <label className={styles.fieldLabel}>Name</label>
                      <CustomInput
                        value={form.name}
                        onChange={(e) =>
                          setForm((f) => ({ ...f, name: e.target.value }))
                        }
                        placeholder="e.g. lookupFir"
                        fullWidth
                      />
                    </div>
                    <div>
                      <label className={styles.fieldLabel}>Method</label>
                      <CustomSelect
                        options={HTTP_METHODS.map((m) => ({
                          value: m,
                          label: m,
                        }))}
                        value={form.method}
                        onChange={(v) =>
                          setForm((f) => ({
                            ...f,
                            method: v as HttpToolMethod,
                          }))
                        }
                        fullWidth
                      />
                    </div>
                    <div className={styles.formGridFull}>
                      <label className={styles.fieldLabel}>Description</label>
                      <CustomInput
                        value={form.description || ''}
                        onChange={(e) =>
                          setForm((f) => ({
                            ...f,
                            description: e.target.value,
                          }))
                        }
                        placeholder="What does this tool do?"
                        fullWidth
                      />
                    </div>
                    <div className={styles.formGridFull}>
                      <label className={styles.fieldLabel}>Group</label>
                      <CustomSelect
                        options={groupOptions}
                        value={form.groupId || ''}
                        onChange={(v) =>
                          setForm((f) => ({ ...f, groupId: v as string }))
                        }
                        fullWidth
                      />
                    </div>
                    <div>
                      <label className={styles.fieldLabel}>Host</label>
                      <CustomInput
                        value={form.host}
                        onChange={(e) =>
                          setForm((f) => ({ ...f, host: e.target.value }))
                        }
                        placeholder="https://api.ksp.example.gov.in"
                        fullWidth
                      />
                    </div>
                    <div>
                      <label className={styles.fieldLabel}>Endpoint</label>
                      <CustomInput
                        value={form.endpoint}
                        onChange={(e) =>
                          setForm((f) => ({
                            ...f,
                            endpoint: e.target.value,
                          }))
                        }
                        placeholder="/api/fir/{firNumber}"
                        fullWidth
                      />
                    </div>
                    <div className={styles.formGridFull}>
                      <label className={styles.fieldLabel}>
                        Request schema
                      </label>
                      <JsonEditor
                        value={form.requestSchema || ''}
                        onChange={(next) =>
                          setForm((f) => ({ ...f, requestSchema: next }))
                        }
                        placeholder='{"type":"object","properties":{...}}'
                        minRows={10}
                        maxRows={22}
                        ariaLabel="Request schema JSON"
                      />
                    </div>
                  </div>

                  {error && <div className={styles.formError}>{error}</div>}
                  </FormTemplate>
                </div>

                <div className={styles.formFooter}>
                  <CustomButton
                    variant="primary"
                    onClick={() => void save()}
                    loading={saving}
                    disabled={!canSave}
                  >
                    Save
                  </CustomButton>
                  {editingId === 'new' && (
                    <CustomButton
                      variant="secondary"
                      onClick={cancel}
                      disabled={saving}
                    >
                      Cancel
                    </CustomButton>
                  )}
                  {isDirty && (
                    <span className={styles.dirtyHint}>Unsaved changes</span>
                  )}
                </div>
              </>
            ) : (
              <>
                <div className={styles.editorHeader}>
                  <div className={styles.editorHeaderMain}>
                    <h2 className={styles.editorTitle}>{groupHeaderTitle}</h2>
                    {selectedGroup?.sourceType &&
                      selectedGroup.sourceType !== 'manual' && (
                        <p className={styles.editorEndpoint}>
                          Imported · {selectedGroup.sourceType.replace('_', ' ')}
                        </p>
                      )}
                    {groupForm.description && (
                      <p className={styles.editorDescription}>
                        {groupForm.description}
                      </p>
                    )}
                  </div>
                  <div className={styles.editorHeaderActions}>
                    {!creatingGroup && selectedGroup && (
                      <CustomButton
                        variant="ghost"
                        size="small"
                        onClick={() => startNew(selectedGroup.id)}
                      >
                        <CustomIcon name="plus" size={14} />
                        Add tool
                      </CustomButton>
                    )}
                    <button
                      type="button"
                      className={`${styles.enableToggle} ${
                        groupForm.enabled ? styles.enableToggleOn : ''
                      }`}
                      onClick={toggleGroupEnabled}
                      title={
                        groupForm.enabled ? 'Disable group' : 'Enable group'
                      }
                    >
                      <span className={styles.enableDot} />
                      {groupForm.enabled ? 'Enabled' : 'Disabled'}
                    </button>
                    {!creatingGroup && selectedGroup && (
                      <CustomDropdown
                        items={groupMenuItems(selectedGroup)}
                        placement="bottomRight"
                      >
                        <CustomButton
                          variant="text"
                          size="small"
                          aria-label="Group actions"
                        >
                          <CustomIcon name="more" size={16} />
                        </CustomButton>
                      </CustomDropdown>
                    )}
                  </div>
                </div>

                <div className={styles.formScroll}>
                  <FormTemplate
                    onSubmit={(e) => {
                      e.preventDefault();
                      void saveGroup();
                    }}
                  >
                    <div className={styles.formGrid}>
                      <div className={styles.formGridFull}>
                        <label className={styles.fieldLabel}>Name</label>
                        <CustomInput
                          value={groupForm.name}
                          onChange={(e) =>
                            setGroupForm((f) => ({
                              ...f,
                              name: e.target.value,
                            }))
                          }
                          placeholder="e.g. CCTNS FIR API"
                          fullWidth
                        />
                      </div>
                      <div className={styles.formGridFull}>
                        <label className={styles.fieldLabel}>
                          Description
                        </label>
                        <CustomInput
                          value={groupForm.description || ''}
                          onChange={(e) =>
                            setGroupForm((f) => ({
                              ...f,
                              description: e.target.value,
                            }))
                          }
                          placeholder="What endpoints does this group contain?"
                          fullWidth
                        />
                      </div>
                    </div>
                    {error && <div className={styles.formError}>{error}</div>}
                  </FormTemplate>
                </div>

                <div className={styles.formFooter}>
                  <CustomButton
                    variant="primary"
                    onClick={() => void saveGroup()}
                    loading={groupSaving}
                    disabled={!canSaveGroup}
                  >
                    Save
                  </CustomButton>
                  {creatingGroup && (
                    <CustomButton
                      variant="secondary"
                      onClick={clearGroupSelection}
                      disabled={groupSaving}
                    >
                      Cancel
                    </CustomButton>
                  )}
                  {isGroupDirty && (
                    <span className={styles.dirtyHint}>Unsaved changes</span>
                  )}
                </div>
              </>
            )}
          </section>
        </div>
        )}
      </div>

      <ImportToolsDialog
        open={importOpen}
        assistantId={assistantId}
        onClose={() => setImportOpen(false)}
        onImported={(result) => void handleImported(result)}
      />

      <CustomModal
        open={testModalOpen}
        title={`Test · ${form.name.trim() || 'Tool'}`}
        onClose={() => setTestModalOpen(false)}
        width="lg"
        footer={
          <>
            <CustomButton
              variant="secondary"
              onClick={() => setTestModalOpen(false)}
              disabled={testing}
            >
              Close
            </CustomButton>
            <CustomButton
              variant="primary"
              onClick={() => void runTest()}
              loading={testing}
            >
              <CustomIcon name="play" size={14} />
              Run test
            </CustomButton>
          </>
        }
      >
        <div className={styles.testModalBody}>
          {isDirty && (
            <p className={styles.testModalHint}>
              Save the tool first to test your latest changes.
            </p>
          )}
          <label className={styles.fieldLabel}>Input</label>
          <JsonEditor
            value={testInput}
            onChange={setTestInput}
            placeholder='{"firNumber":"0123/2024"}'
            minRows={6}
            maxRows={14}
            ariaLabel="Test input JSON"
          />
          {testError && (
            <div className={styles.formError}>
              <CustomTag tone="error">Error</CustomTag> {testError}
            </div>
          )}
          {testOutput != null && (
            <>
              <label className={styles.fieldLabel}>Output</label>
              <JsonEditor
                value={formatJson(testOutput)}
                readOnly
                minRows={6}
                maxRows={16}
                ariaLabel="Test output JSON"
              />
            </>
          )}
        </div>
      </CustomModal>

      <CustomModal
        open={authModalOpen}
        title={`Authentication · ${form.name.trim() || 'Tool'}`}
        onClose={() => setAuthModalOpen(false)}
        width="md"
        footer={
          <CustomButton
            variant="primary"
            disabled={saving}
            onClick={async () => {
              if (await save()) setAuthModalOpen(false);
            }}
          >
            Done
          </CustomButton>
        }
      >
        <div className={styles.authModalBody}>
          <p className={styles.authModalSummary}>
            Current: <strong>{authSummary}</strong>
          </p>
          <label className={styles.fieldLabel}>Auth profile</label>
          <CustomSelect
            options={profileOptions}
            value={form.authProfileId || ''}
            onChange={(v) =>
              setForm((f) => ({
                ...f,
                authProfileId: (v as string) || '',
              }))
            }
            fullWidth
          />
          {!form.authProfileId && (
            <>
              <label className={styles.fieldLabel}>Inline auth type</label>
              <CustomSelect
                options={TOOL_AUTH_TYPES.map((t) => ({
                  value: t.value,
                  label: t.label,
                }))}
                value={form.authType || 'none'}
                onChange={(v) =>
                  setForm((f) => ({ ...f, authType: v as string }))
                }
                fullWidth
              />
              <label className={styles.fieldLabel}>Inline auth config</label>
              <JsonEditor
                value={form.authConfig || ''}
                onChange={(next) =>
                  setForm((f) => ({ ...f, authConfig: next }))
                }
                placeholder='{"name":"X-Api-Key","value":"..."}'
                minRows={5}
                maxRows={12}
                compact
                ariaLabel="Inline auth config JSON"
              />
            </>
          )}
        </div>
      </CustomModal>
    </>
  );
};

export default ToolsPage;
