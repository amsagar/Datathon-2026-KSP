import React, { useEffect, useMemo, useState } from 'react';
import ResourcePanelTemplate from '@templates/ResourcePanelTemplate';
import FormTemplate from '@templates/FormTemplate';
import CustomInput from '@atoms/CustomInput';
import CustomTextarea from '@atoms/CustomTextarea';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import CustomDivider from '@atoms/CustomDivider';
import CustomTag from '@atoms/CustomTag';
import CustomEmptyState from '@atoms/CustomEmptyState';
import CustomSpinner from '@atoms/CustomSpinner';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import { mcpApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { MCP_TRANSPORTS, MCP_AUTH_TYPES } from '@constants/toolSourceKinds';
import type {
  McpServerDto,
  McpServerToolDto,
  CreateMcpServerRequest,
  McpAuthType,
  McpTransport,
} from '@interfaces/mcp.interface';
import * as styles from '@styles/resourcePanel.module.scss';

const EMPTY: CreateMcpServerRequest = {
  name: '',
  description: '',
  transport: 'streamable_http',
  url: '',
  sseEndpoint: '',
  authType: 'none',
  authConfig: '',
  secret: '',
  enabled: true,
};

const McpServersPage: React.FC = () => {
  const openNotification = useNotification();
  const { assistant, assistantId } = useSettingsScope();
  const [items, setItems] = useState<McpServerDto[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CreateMcpServerRequest>(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [tools, setTools] = useState<McpServerToolDto[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [discovering, setDiscovering] = useState(false);

  const refresh = async () => {
    if (!assistantId) {
      setItems([]);
      return;
    }
    try {
      setItems(await mcpApi.list(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load MCP servers',
        'Error'
      );
    }
  };

  useEffect(() => {
    void refresh();
    setEditingId(null);
    setTools([]);
  }, [assistantId]);

  const loadTools = async (serverId: string) => {
    setToolsLoading(true);
    try {
      setTools(await mcpApi.tools(serverId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load tools',
        'Error'
      );
    } finally {
      setToolsLoading(false);
    }
  };

  const startNew = () => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    setEditingId('new');
    setForm(EMPTY);
    setTools([]);
    setError('');
  };

  const startEdit = (s: McpServerDto) => {
    setEditingId(s.id);
    setForm({
      name: s.name,
      description: s.description,
      transport: s.transport,
      url: s.url,
      sseEndpoint: s.sseEndpoint || '',
      authType: s.authType,
      authConfig: s.authConfig || '',
      secret: '',
      enabled: s.enabled,
    });
    setError('');
    void loadTools(s.id);
  };

  const cancel = () => {
    setEditingId(null);
    setForm(EMPTY);
    setTools([]);
    setError('');
  };

  const save = async () => {
    if (!form.name.trim() || !form.url.trim()) {
      setError('Name and URL are required.');
      return;
    }
    setSaving(true);
    try {
      const body: CreateMcpServerRequest = {
        ...form,
        name: form.name.trim(),
        description: form.description?.trim() || '',
        url: form.url.trim(),
        sseEndpoint: form.sseEndpoint?.trim() || undefined,
        authConfig: form.authConfig?.trim() || undefined,
        secret: form.secret?.trim() || undefined,
      };
      if (editingId === 'new') {
        const created = await mcpApi.create(assistantId, body);
        openNotification(`Server "${created.name}" created`, 'Success');
        await refresh();
        startEdit(created);
      } else if (editingId) {
        const updated = await mcpApi.update(editingId, body);
        openNotification(`Server "${updated.name}" updated`, 'Success');
        await refresh();
      }
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (s: McpServerDto) => {
    try {
      await mcpApi.delete(s.id);
      if (editingId === s.id) cancel();
      await refresh();
      openNotification(`Server "${s.name}" deleted`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to delete server',
        'Error'
      );
    }
  };

  const discover = async () => {
    if (!editingId || editingId === 'new') return;
    setDiscovering(true);
    try {
      await mcpApi.discover(editingId);
      await loadTools(editingId);
      openNotification('Discovery complete', 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Discovery failed',
        'Error'
      );
    } finally {
      setDiscovering(false);
    }
  };

  const toggleTool = async (t: McpServerToolDto, enabled: boolean) => {
    if (!editingId || editingId === 'new') return;
    try {
      const updated = await mcpApi.setToolEnabled(editingId, t.id, {
        enabled,
      });
      setTools((curr) =>
        curr.map((x) => (x.id === updated.id ? updated : x))
      );
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to toggle tool',
        'Error'
      );
    }
  };

  const listItems = useMemo(
    () =>
      items.map((s) => ({
        id: s.id,
        name: s.name,
        meta: `${s.transport} · ${s.enabled ? 'enabled' : 'disabled'}`,
      })),
    [items]
  );

  const current =
    editingId && editingId !== 'new'
      ? items.find((i) => i.id === editingId)
      : null;
  const showSecret = form.authType !== 'none';

  return (
    <ResourcePanelTemplate
      title="MCP Servers"
      subtitle={
        assistant
          ? `External MCP servers exposing tools to ${assistant.name}.`
          : 'Connect external Model Context Protocol servers for additional tools.'
      }
      items={listItems}
      selectedId={editingId}
      onSelect={(it) => {
        const found = items.find((x) => x.id === it.id);
        if (found) startEdit(found);
      }}
      onNew={startNew}
      onDelete={(it) => {
        const found = items.find((x) => x.id === it.id);
        if (found) return remove(found);
      }}
      newLabel="+ New server"
      emptyListLabel={
        assistantId ? 'No MCP servers yet' : 'Pick an assistant in the left rail'
      }
    >
      <FormTemplate
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <label className={styles.fieldLabel}>Name</label>
        <CustomInput
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          placeholder="e.g. fir-knowledge-base"
          fullWidth
        />

        <label className={styles.fieldLabel}>Description</label>
        <CustomInput
          value={form.description || ''}
          onChange={(e) =>
            setForm((f) => ({ ...f, description: e.target.value }))
          }
          placeholder="Optional"
          fullWidth
        />

        <div className={styles.row2}>
          <div>
            <label className={styles.fieldLabel}>Transport</label>
            <CustomSelect
              options={MCP_TRANSPORTS.map((t) => ({
                value: t.value,
                label: t.label,
              }))}
              value={form.transport}
              onChange={(v) =>
                setForm((f) => ({ ...f, transport: v as McpTransport }))
              }
              fullWidth
            />
          </div>
          <div>
            <label className={styles.fieldLabel}>Enabled</label>
            <CustomSelect
              options={[
                { value: 'true', label: 'Enabled' },
                { value: 'false', label: 'Disabled' },
              ]}
              value={String(form.enabled ?? true)}
              onChange={(v) =>
                setForm((f) => ({ ...f, enabled: v === 'true' }))
              }
              fullWidth
            />
          </div>
        </div>

        <label className={styles.fieldLabel}>URL</label>
        <CustomInput
          value={form.url}
          onChange={(e) => setForm((f) => ({ ...f, url: e.target.value }))}
          placeholder="https://mcp.example.com/v1"
          fullWidth
        />

        {form.transport === 'sse' && (
          <>
            <label className={styles.fieldLabel}>SSE endpoint (optional)</label>
            <CustomInput
              value={form.sseEndpoint || ''}
              onChange={(e) =>
                setForm((f) => ({ ...f, sseEndpoint: e.target.value }))
              }
              placeholder="/sse"
              fullWidth
            />
          </>
        )}

        <CustomDivider margin={4}>Authentication</CustomDivider>

        <label className={styles.fieldLabel}>Auth type</label>
        <CustomSelect
          options={MCP_AUTH_TYPES.map((t) => ({
            value: t.value,
            label: t.label,
          }))}
          value={form.authType}
          onChange={(v) =>
            setForm((f) => ({ ...f, authType: v as McpAuthType }))
          }
          fullWidth
        />

        <label className={styles.fieldLabel}>Auth config (JSON)</label>
        <CustomTextarea
          value={form.authConfig || ''}
          onChange={(e) =>
            setForm((f) => ({ ...f, authConfig: e.target.value }))
          }
          placeholder='{"headerName":"X-Api-Key"} or {"clientId":"..."}'
          autoSize={{ minRows: 3, maxRows: 8 }}
          fullWidth
        />

        {showSecret && (
          <>
            <label className={styles.fieldLabel}>
              Secret{' '}
              {current?.hasSecret && <CustomTag tone="info">stored</CustomTag>}
            </label>
            <CustomInput
              type="password"
              value={form.secret || ''}
              onChange={(e) =>
                setForm((f) => ({ ...f, secret: e.target.value }))
              }
              placeholder={
                current?.hasSecret
                  ? 'Leave blank to keep current secret'
                  : 'Plaintext secret (encrypted server-side)'
              }
              fullWidth
            />
          </>
        )}

        {current?.status && (
          <div className={styles.fieldHelp}>
            Status: <CustomTag tone="info">{current.status}</CustomTag>
            {current.statusDetail ? ` · ${current.statusDetail}` : ''}
          </div>
        )}

        {error && <div className={styles.formError}>{error}</div>}

        <div className={styles.formActions}>
          <CustomButton variant="primary" htmlType="submit" loading={saving}>
            Save
          </CustomButton>
          <CustomButton variant="secondary" onClick={cancel} disabled={saving}>
            Cancel
          </CustomButton>
        </div>

        {editingId && editingId !== 'new' && (
          <div className={styles.testBox}>
            <CustomDivider margin={4}>Discovered tools</CustomDivider>
            <div className={styles.formActions}>
              <CustomButton
                variant="ghost"
                onClick={discover}
                loading={discovering}
              >
                Discover now
              </CustomButton>
            </div>
            {toolsLoading ? (
              <CustomSpinner size="small" />
            ) : tools.length === 0 ? (
              <CustomEmptyState
                title="No tools yet"
                description="Run discovery to load tools from the server."
              />
            ) : (
              <div className={styles.mcpToolList}>
                {tools.map((t) => (
                  <div key={t.id} className={styles.mcpToolRow}>
                    <div className={styles.mcpToolMeta}>
                      <strong>{t.name}</strong>
                      {t.description && <span>{t.description}</span>}
                      {!t.enabled && (
                        <CustomTag tone="neutral">disabled</CustomTag>
                      )}
                    </div>
                    <CustomButton
                      variant="ghost"
                      size="small"
                      onClick={() => toggleTool(t, !t.enabled)}
                    >
                      {t.enabled ? 'Disable' : 'Enable'}
                    </CustomButton>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </FormTemplate>
    </ResourcePanelTemplate>
  );
};

export default McpServersPage;
