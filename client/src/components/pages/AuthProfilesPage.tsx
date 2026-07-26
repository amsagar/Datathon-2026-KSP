import React, { useCallback, useEffect, useMemo, useState } from 'react';
import CustomTable, {
  type CustomTableColumnsType,
} from '@templates/CustomTable';
import CustomInput from '@atoms/CustomInput';
import CustomSelect from '@atoms/CustomSelect';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomTag from '@atoms/CustomTag';
import CustomModal from '@atoms/CustomModal';
import CustomDropdown from '@atoms/CustomDropdown';
import CustomEmptyState from '@atoms/CustomEmptyState';
import { confirm } from '@atoms/CustomConfirm';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import { authProfilesApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { TOOL_AUTH_TYPES } from '@constants/toolSourceKinds';
import { relativeTime } from '@utils/relativeTime';
import {
  EMPTY_AUTH_PROFILE_FORM,
  applyAuthTypeChange,
  buildAuthConfigForSave,
  formsEqual,
  isAuthConfigValid,
  normalizeAuthType,
  profileToForm,
  type AuthConfigState,
  type AuthProfileFormState,
} from '@utils/authProfileForm';
import type {
  ToolAuthProfileDto,
  CreateAuthProfileRequest,
} from '@interfaces/auth.interface';
import * as styles from '@styles/authProfilesPage.module.scss';

const API_KEY_IN_OPTIONS = [
  { value: 'header', label: 'Header' },
  { value: 'query', label: 'Query parameter' },
];

const authTypeLabel = (value: string): string =>
  TOOL_AUTH_TYPES.find((t) => t.value === normalizeAuthType(value))?.label ||
  value;

const credentialsSectionTitle = (authType: string): string => {
  switch (authType) {
    case 'api_key_header':
      return 'API key';
    case 'bearer_token':
      return 'Bearer token';
    case 'basic_auth':
      return 'Basic auth';
    case 'oauth_client_credentials':
      return 'OAuth credentials';
    default:
      return 'Credentials';
  }
};

interface AuthProfileFormFieldsProps {
  form: AuthProfileFormState;
  setForm: React.Dispatch<React.SetStateAction<AuthProfileFormState>>;
  selectedProfile: ToolAuthProfileDto | null;
}

const AuthProfileFormFields: React.FC<AuthProfileFormFieldsProps> = ({
  form,
  setForm,
  selectedProfile,
}) => {
  const setConfig = (key: keyof AuthConfigState, value: string) =>
    setForm((f) => ({ ...f, config: { ...f.config, [key]: value } }));

  const secretPlaceholder = selectedProfile?.hasClientSecret
    ? 'Leave blank to keep current'
    : 'Encrypted server-side';

  const renderSecretField = (
    label: string,
    placeholder?: string
  ) => (
    <div className={styles.field}>
      <label className={styles.fieldLabel}>
        {label}
        {selectedProfile?.hasClientSecret && (
          <CustomTag tone="info" className={styles.fieldLabelTag}>
            stored
          </CustomTag>
        )}
      </label>
      <CustomInput
        type="password"
        value={form.clientSecret}
        onChange={(e) =>
          setForm((f) => ({ ...f, clientSecret: e.target.value }))
        }
        placeholder={placeholder ?? secretPlaceholder}
        fullWidth
      />
    </div>
  );

  return (
  <div className={styles.modalForm}>
    {selectedProfile && (
      <div className={styles.statusRow}>
        <span
          className={`${styles.statusChip} ${
            selectedProfile.hasAccessToken
              ? styles.statusChipOk
              : styles.statusChipMuted
          }`}
        >
          Token {selectedProfile.hasAccessToken ? 'cached' : 'not cached'}
          {selectedProfile.tokenExpiresAt
            ? ` · ${new Date(
                selectedProfile.tokenExpiresAt * 1000
              ).toLocaleString()}`
            : ''}
        </span>
        {selectedProfile.hasClientSecret && (
          <span className={`${styles.statusChip} ${styles.statusChipOk}`}>
            Secret stored
          </span>
        )}
      </div>
    )}

    <section className={styles.section}>
      <h3 className={styles.sectionTitle}>General</h3>
      <div className={styles.sectionCard}>
        <div className={styles.row2}>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Name</label>
            <CustomInput
              value={form.name}
              onChange={(e) =>
                setForm((f) => ({ ...f, name: e.target.value }))
              }
              placeholder="e.g. CCTNS-API-Auth"
              fullWidth
            />
          </div>
          <div className={styles.fieldAuthType}>
            <label className={styles.fieldLabel}>Auth type</label>
            <CustomSelect
              options={TOOL_AUTH_TYPES.map((t) => ({
                value: t.value,
                label: t.label,
              }))}
              value={form.authType}
              onChange={(v) =>
                setForm((f) => applyAuthTypeChange(f, v as string))
              }
              fullWidth
            />
          </div>
        </div>
        <div className={styles.field}>
          <label className={styles.fieldLabel}>Description</label>
          <CustomInput
            value={form.description || ''}
            onChange={(e) =>
              setForm((f) => ({ ...f, description: e.target.value }))
            }
            placeholder="Optional summary"
            fullWidth
          />
        </div>
      </div>
    </section>

    {form.authType !== 'none' && (
      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>
          {credentialsSectionTitle(form.authType)}
        </h3>
        <div className={styles.sectionCard}>
          {form.authType === 'api_key_header' && (
            <>
              <div className={styles.row2Equal}>
                <div className={styles.field}>
                  <label className={styles.fieldLabel}>Send as</label>
                  <CustomSelect
                    options={API_KEY_IN_OPTIONS}
                    value={form.config.in || 'header'}
                    onChange={(v) => setConfig('in', v as string)}
                    fullWidth
                  />
                </div>
                <div className={styles.field}>
                  <label className={styles.fieldLabel}>Key name</label>
                  <CustomInput
                    value={form.config.name}
                    onChange={(e) => setConfig('name', e.target.value)}
                    placeholder="X-API-Key"
                    fullWidth
                  />
                </div>
              </div>
              {renderSecretField('API key value', 'Your API key')}
            </>
          )}

          {form.authType === 'bearer_token' && (
            <>
              <p className={styles.fieldHint}>
                Sent as{' '}
                <code className={styles.inlineCode}>Authorization: Bearer …</code>
              </p>
              {renderSecretField('Bearer token', 'Paste token')}
            </>
          )}

          {form.authType === 'basic_auth' && (
            <>
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Username</label>
                <CustomInput
                  value={form.config.username}
                  onChange={(e) => setConfig('username', e.target.value)}
                  placeholder="Username"
                  fullWidth
                />
              </div>
              {renderSecretField('Password')}
            </>
          )}

          {form.authType === 'oauth_client_credentials' && (
            <>
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Token URL</label>
                <CustomInput
                  value={form.tokenUrl}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, tokenUrl: e.target.value }))
                  }
                  placeholder="https://example.com/oauth/token"
                  fullWidth
                />
              </div>
              <div className={styles.row2Equal}>
                <div className={styles.field}>
                  <label className={styles.fieldLabel}>Client ID</label>
                  <CustomInput
                    value={form.config.clientId}
                    onChange={(e) => setConfig('clientId', e.target.value)}
                    placeholder="OAuth client ID"
                    fullWidth
                  />
                </div>
                {renderSecretField('Client secret')}
              </div>
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Scopes</label>
                <CustomInput
                  value={form.scopes}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, scopes: e.target.value }))
                  }
                  placeholder="read write (space-separated)"
                  fullWidth
                />
              </div>
            </>
          )}
        </div>
      </section>
    )}
  </div>
  );
};

export interface AuthProfilesPageProps {
  /** Skip the page header — used when this page is composed as a tab inside another
   * section (e.g. HTTP tools), which already renders its own header. */
  hideHeader?: boolean;
}

const AuthProfilesPage: React.FC<AuthProfilesPageProps> = ({ hideHeader = false }) => {
  const openNotification = useNotification();
  const { assistantId } = useSettingsScope();
  const [items, setItems] = useState<ToolAuthProfileDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<AuthProfileFormState>(EMPTY_AUTH_PROFILE_FORM);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const selectedProfile = useMemo(
    () =>
      editingId && editingId !== 'new'
        ? items.find((p) => p.id === editingId) || null
        : null,
    [items, editingId]
  );

  const savedForm = useMemo<AuthProfileFormState>(() => {
    if (editingId === 'new' || !editingId) return EMPTY_AUTH_PROFILE_FORM;
    if (!selectedProfile) return EMPTY_AUTH_PROFILE_FORM;
    return profileToForm(selectedProfile);
  }, [editingId, selectedProfile]);

  const isDirty = useMemo(() => {
    if (!modalOpen) return false;
    if (form.clientSecret.trim()) return true;
    return !formsEqual(form, savedForm);
  }, [form, savedForm, modalOpen]);

  const canSave =
    isDirty &&
    !!form.name.trim() &&
    !!form.authType &&
    !saving &&
    isAuthConfigValid(
      form,
      editingId === 'new',
      !!selectedProfile?.hasClientSecret
    );

  const refresh = useCallback(async () => {
    if (!assistantId) {
      setItems([]);
      return;
    }
    setLoading(true);
    try {
      setItems(await authProfilesApi.list(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load auth profiles',
        'Error'
      );
    } finally {
      setLoading(false);
    }
  }, [assistantId, openNotification]);

  useEffect(() => {
    void refresh();
    setModalOpen(false);
    setEditingId(null);
    setForm(EMPTY_AUTH_PROFILE_FORM);
  }, [assistantId, refresh]);

  const discardIfDirty = (action: () => void) => {
    if (!isDirty) {
      action();
      return;
    }
    confirm({
      title: 'Discard unsaved changes?',
      body: 'You have unsaved edits for this profile.',
      danger: true,
      okText: 'Discard',
      onOk: action,
    });
  };

  const closeModal = () => {
    if (saving) return;
    discardIfDirty(() => {
      setModalOpen(false);
      setEditingId(null);
      setForm(EMPTY_AUTH_PROFILE_FORM);
      setError('');
    });
  };

  const openNew = () => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    discardIfDirty(() => {
      setEditingId('new');
      setForm(EMPTY_AUTH_PROFILE_FORM);
      setError('');
      setModalOpen(true);
    });
  };

  const openEdit = (p: ToolAuthProfileDto) => {
    discardIfDirty(() => {
      setEditingId(p.id);
      setForm(profileToForm(p));
      setError('');
      setModalOpen(true);
    });
  };

  const save = async () => {
    if (!form.name.trim() || !form.authType) {
      setError('Name and auth type are required.');
      return;
    }
    if (
      !isAuthConfigValid(
        form,
        editingId === 'new',
        !!selectedProfile?.hasClientSecret
      )
    ) {
      setError('Complete the required credential fields for this auth type.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const body: CreateAuthProfileRequest = {
        name: form.name.trim(),
        description: form.description?.trim() || '',
        authType: form.authType,
        authConfig: buildAuthConfigForSave(form.authType, form.config),
        clientSecret: form.clientSecret?.trim() || undefined,
        tokenUrl: form.tokenUrl?.trim() || undefined,
        scopes: form.scopes?.trim() || undefined,
      };
      if (editingId === 'new') {
        const created = await authProfilesApi.create(assistantId, body);
        openNotification(`Profile "${created.name}" created`, 'Success');
      } else if (editingId) {
        const updated = await authProfilesApi.update(editingId, body);
        openNotification(`Profile "${updated.name}" updated`, 'Success');
      }
      setModalOpen(false);
      setEditingId(null);
      setForm(EMPTY_AUTH_PROFILE_FORM);
      await refresh();
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const remove = (p: ToolAuthProfileDto) => {
    confirm({
      title: `Delete "${p.name}"?`,
      body: 'Tools using this profile will need another auth configuration.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await authProfilesApi.delete(p.id);
          if (editingId === p.id) {
            setModalOpen(false);
            setEditingId(null);
            setForm(EMPTY_AUTH_PROFILE_FORM);
          }
          await refresh();
          openNotification(`Profile "${p.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete profile',
            'Error'
          );
        }
      },
    });
  };

  const rowMenuItems = (p: ToolAuthProfileDto) => [
    {
      key: 'delete',
      label: (
        <span className={`${styles.menuItem} ${styles.menuItemDanger}`}>
          <CustomIcon name="delete" size={14} />
          Delete
        </span>
      ),
      danger: true,
      onClick: () => remove(p),
    },
  ];

  const columns: CustomTableColumnsType<ToolAuthProfileDto> = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (name: string, record) => (
        <div className={styles.nameCell}>
          <span className={styles.cellName}>{name}</span>
          {record.description ? (
            <span className={styles.cellDesc}>{record.description}</span>
          ) : null}
        </div>
      ),
    },
    {
      title: 'Auth type',
      key: 'authType',
      width: 200,
      ellipsis: true,
      render: (_: unknown, record) => (
        <span className={styles.typeBadge}>{authTypeLabel(record.authType)}</span>
      ),
    },
    {
      title: 'Token',
      key: 'token',
      width: 120,
      render: (_: unknown, record) =>
        record.hasAccessToken ? (
          <CustomTag tone="success">Cached</CustomTag>
        ) : (
          <span className={styles.cellMuted}>—</span>
        ),
    },
    {
      title: 'Secret',
      key: 'secret',
      width: 100,
      align: 'center',
      render: (_: unknown, record) =>
        record.hasClientSecret ? (
          <CustomTag tone="info">Stored</CustomTag>
        ) : (
          <span className={styles.cellMuted}>—</span>
        ),
    },
    {
      title: 'Updated',
      key: 'updated',
      width: 110,
      render: (_: unknown, record) =>
        relativeTime(record.updatedAt) || '—',
    },
    {
      title: '',
      key: 'actions',
      width: 52,
      align: 'center',
      render: (_: unknown, record) => (
        <CustomDropdown items={rowMenuItems(record)} placement="bottomRight">
          <CustomButton
            variant="text"
            size="small"
            aria-label={`Actions for ${record.name}`}
            onClick={(e) => e.stopPropagation()}
          >
            <CustomIcon name="more" size={16} />
          </CustomButton>
        </CustomDropdown>
      ),
    },
  ];

  const modalTitle =
    editingId === 'new'
      ? 'New auth profile'
      : form.name.trim() || 'Edit auth profile';

  return (
    <>
      <div className={styles.page}>
        {!hideHeader && (
          <header className={styles.pageHeader}>
            <div className={styles.pageHeaderMain}>
              <h1 className={styles.pageTitle}>Auth profiles</h1>
              <p className={styles.pageSubtitle}>
                Reusable HTTP auth configurations referenced by tools.
              </p>
            </div>
            {(items.length > 0 || loading) && (
              <CustomButton
                variant="primary"
                size="small"
                disabled={!assistantId}
                onClick={openNew}
              >
                <CustomIcon name="plus" size={14} />
                New profile
              </CustomButton>
            )}
          </header>
        )}
        {hideHeader && (items.length > 0 || loading) && (
          <div className={styles.embeddedActions}>
            <CustomButton
              variant="primary"
              size="small"
              disabled={!assistantId}
              onClick={openNew}
            >
              <CustomIcon name="plus" size={14} />
              New profile
            </CustomButton>
          </div>
        )}

        <div
          className={`${styles.tableWrap} ${
            items.length === 0 && !loading ? styles.tableWrapEmpty : ''
          }`}
        >
          {!assistantId ? (
            <CustomEmptyState
              title="No assistant selected"
              description="Pick an assistant in the left menu to manage auth profiles."
            />
          ) : items.length === 0 && !loading ? (
            <CustomEmptyState
              title="No auth profiles yet"
              description="Store API keys, bearer tokens, basic auth, or OAuth credentials once and reuse them across HTTP tools."
              action={
                <CustomButton variant="primary" onClick={openNew}>
                  <CustomIcon name="plus" size={14} />
                  New profile
                </CustomButton>
              }
            />
          ) : (
            <CustomTable<ToolAuthProfileDto>
              rowKey="id"
              dataSource={items}
              columns={columns}
              loading={loading}
              onRow={(record) => ({
                onClick: () => openEdit(record),
              })}
            />
          )}
        </div>
      </div>

      <CustomModal
        open={modalOpen}
        title={modalTitle}
        onClose={closeModal}
        width="wide"
        maskClosable={!saving}
        footer={
          <>
            <CustomButton
              variant="secondary"
              onClick={closeModal}
              disabled={saving}
            >
              Cancel
            </CustomButton>
            <CustomButton
              variant="primary"
              onClick={() => void save()}
              loading={saving}
              disabled={!canSave}
            >
              Save
            </CustomButton>
          </>
        }
      >
        <AuthProfileFormFields
          form={form}
          setForm={setForm}
          selectedProfile={selectedProfile}
        />
        {error && <div className={styles.formError}>{error}</div>}
      </CustomModal>
    </>
  );
};

export default AuthProfilesPage;
