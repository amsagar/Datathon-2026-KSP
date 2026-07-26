import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useT } from '@constants/translations';
import FormTemplate from '@templates/FormTemplate';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomTooltip from '@atoms/CustomTooltip';
import MarkdownEditor from '@molecules/MarkdownEditor';
import RevisionHistory from '@molecules/RevisionHistory';
import { confirm } from '@atoms/CustomConfirm';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import { stylesApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type {
  ResponseStyleDto,
  CreateStyleRequest,
} from '@interfaces/style.interface';
import * as styles from '@styles/responseStylesPage.module.scss';

const EMPTY: CreateStyleRequest = {
  name: '',
  description: '',
  instructions: '',
};

const formsEqual = (a: CreateStyleRequest, b: CreateStyleRequest): boolean =>
  a.name.trim() === b.name.trim() &&
  (a.description || '').trim() === (b.description || '').trim() &&
  a.instructions === b.instructions;

const ResponseStylesPage: React.FC = () => {
  const t = useT();
  const openNotification = useNotification();
  const { assistantId } = useSettingsScope();
  const [items, setItems] = useState<ResponseStyleDto[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CreateStyleRequest>(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const selectedStyle = useMemo(
    () => items.find((s) => s.id === editingId) || null,
    [items, editingId]
  );

  const savedForm = useMemo<CreateStyleRequest>(() => {
    if (editingId === 'new') return EMPTY;
    if (!selectedStyle) return EMPTY;
    return {
      name: selectedStyle.name,
      description: selectedStyle.description,
      instructions: selectedStyle.instructions,
    };
  }, [editingId, selectedStyle]);

  const isDirty = useMemo(
    () => editingId !== null && !formsEqual(form, savedForm),
    [form, savedForm, editingId]
  );

  const canSave =
    isDirty &&
    !!form.name.trim() &&
    !!form.instructions.trim() &&
    !saving;

  const refresh = useCallback(async () => {
    if (!assistantId) {
      setItems([]);
      return;
    }
    try {
      setItems(await stylesApi.list(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load styles',
        'Error'
      );
    }
  }, [assistantId, openNotification]);

  useEffect(() => {
    void refresh();
    setEditingId(null);
    setForm(EMPTY);
  }, [assistantId, refresh]);

  const discardIfDirty = (action: () => void) => {
    if (!isDirty) {
      action();
      return;
    }
    confirm({
      title: 'Discard unsaved changes?',
      body: 'You have unsaved edits for this style.',
      danger: true,
      okText: 'Discard',
      onOk: action,
    });
  };

  const startNew = () => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    discardIfDirty(() => {
      setEditingId('new');
      setForm(EMPTY);
      setError('');
    });
  };

  const startEdit = (s: ResponseStyleDto) => {
    if (editingId === s.id) return;
    discardIfDirty(() => {
      setEditingId(s.id);
      setForm({
        name: s.name,
        description: s.description,
        instructions: s.instructions,
      });
      setError('');
    });
  };

  const cancel = () => {
    setEditingId(null);
    setForm(EMPTY);
    setError('');
  };

  const save = async () => {
    if (!form.name.trim() || !form.instructions.trim()) {
      setError('Name and instructions are required.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const body: CreateStyleRequest = {
        name: form.name.trim(),
        description: form.description?.trim() || '',
        instructions: form.instructions,
      };
      if (editingId === 'new') {
        const created = await stylesApi.create(assistantId, body);
        openNotification(`Style "${created.name}" created`, 'Success');
        setEditingId(created.id);
        setForm({
          name: created.name,
          description: created.description,
          instructions: created.instructions,
        });
      } else if (editingId) {
        const updated = await stylesApi.update(editingId, body);
        openNotification(`Style "${updated.name}" updated`, 'Success');
        setForm({
          name: updated.name,
          description: updated.description,
          instructions: updated.instructions,
        });
      }
      await refresh();
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const setAsDefault = async (s: ResponseStyleDto) => {
    if (s.defaultStyle) return;
    try {
      await stylesApi.setDefault(s.id);
      await refresh();
      openNotification(`"${s.name}" is now the default style`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to set default style',
        'Error'
      );
    }
  };

  const remove = (s: ResponseStyleDto) => {
    confirm({
      title: `Delete "${s.name}"?`,
      body: 'This removes the response style.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await stylesApi.delete(s.id);
          if (editingId === s.id) cancel();
          await refresh();
          openNotification(`Style "${s.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete style',
            'Error'
          );
        }
      },
    });
  };

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.pageEyebrow}>Chat</div>
        <h1 className={styles.pageTitle}>Response styles</h1>
        <p className={styles.pageSubtitle}>
          Reusable instruction snippets for chat. Star one style per assistant
          to use it as the default in new chats.
        </p>
      </header>

      <div className={styles.workspace}>
        <aside className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <span className={styles.sidebarTitle}>Styles</span>
            <CustomButton
              variant="primary"
              size="small"
              disabled={!assistantId}
              onClick={startNew}
            >
              <CustomIcon name="plus" size={14} />
              New
            </CustomButton>
          </div>
          <div className={styles.styleList}>
            {!assistantId && (
              <div className={styles.emptyList}>
                Pick an assistant in the left menu
              </div>
            )}
            {assistantId && items.length === 0 && editingId !== 'new' && (
              <div className={styles.emptyList}>No styles yet</div>
            )}
            {editingId === 'new' && (
              <button
                type="button"
                className={`${styles.styleRow} ${styles.styleRowActive}`}
              >
                <span className={styles.styleRowName}>New style</span>
              </button>
            )}
            {items.map((s) => (
              <div
                key={s.id}
                className={`${styles.styleRow} ${
                  editingId === s.id ? styles.styleRowActive : ''
                }`}
              >
                <button
                  type="button"
                  className={styles.styleRowMain}
                  onClick={() => startEdit(s)}
                >
                  <span className={styles.styleRowName}>{s.name}</span>
                  {s.description && (
                    <span className={styles.styleRowMeta}>{s.description}</span>
                  )}
                </button>
                <CustomTooltip
                  title={
                    s.defaultStyle
                      ? 'Default for new chats'
                      : 'Set as default for new chats'
                  }
                >
                  <button
                    type="button"
                    className={`${styles.defaultStar} ${
                      s.defaultStyle ? styles.defaultStarActive : ''
                    }`}
                    aria-label={
                      s.defaultStyle
                        ? `${s.name} is the default style`
                        : `Set ${s.name} as default style`
                    }
                    aria-pressed={s.defaultStyle}
                    onClick={(e) => {
                      e.stopPropagation();
                      void setAsDefault(s);
                    }}
                  >
                    <CustomIcon
                      name={s.defaultStyle ? 'star-filled' : 'star'}
                      size={15}
                    />
                  </button>
                </CustomTooltip>
              </div>
            ))}
          </div>
        </aside>

        <section className={styles.editorPane}>
          {!editingId ? (
            <div className={styles.editorEmpty}>
              <CustomIcon name="style" size={28} />
              <p>Select or create a style</p>
              <span>
                Response styles shape tone and format when pinned in a chat.
              </span>
            </div>
          ) : (
            <>
              <div className={styles.editorHeader}>
                <h2 className={styles.editorTitle}>
                  {editingId === 'new'
                    ? 'New style'
                    : form.name.trim() || 'Untitled style'}
                </h2>
                {selectedStyle && (
                  <div className="flex items-center gap-2">
                    <RevisionHistory
                      key={selectedStyle.id}
                      resourceType="response_style"
                      resourceId={selectedStyle.id}
                      resourceLabel={selectedStyle.name}
                      onReverted={refresh}
                    />
                    <CustomTooltip title={t('deleteStyle')}>
                      <CustomButton
                        variant="text"
                        size="small"
                        onClick={() => remove(selectedStyle)}
                        aria-label="Delete style"
                      >
                        <CustomIcon name="delete" size={15} />
                      </CustomButton>
                    </CustomTooltip>
                  </div>
                )}
              </div>

              <FormTemplate
                sectionClassName={styles.formWrap}
                className={styles.form}
                onSubmit={(e) => {
                  e.preventDefault();
                  void save();
                }}
              >
                <div className={styles.formBody}>
                  <div className={styles.formMeta}>
                    <div className={styles.field}>
                      <label className={styles.fieldLabel}>Name</label>
                      <CustomInput
                        value={form.name}
                        onChange={(e) =>
                          setForm((f) => ({ ...f, name: e.target.value }))
                        }
                        placeholder="e.g. Investigator briefing"
                        fullWidth
                      />
                    </div>
                    <div className={styles.field}>
                      <label className={styles.fieldLabel}>Description</label>
                      <CustomInput
                        value={form.description || ''}
                        onChange={(e) =>
                          setForm((f) => ({
                            ...f,
                            description: e.target.value,
                          }))
                        }
                        placeholder="Short summary in the picker"
                        fullWidth
                      />
                    </div>
                  </div>

                  <div className={styles.instructionsSection}>
                    <div className={styles.instructionsHeader}>
                      <label className={styles.fieldLabel}>Instructions</label>
                      <span className={styles.charCount}>
                        {form.instructions.length.toLocaleString()} chars
                      </span>
                    </div>
                    <MarkdownEditor
                      fillHeight
                      value={form.instructions}
                      onChange={(next) =>
                        setForm((f) => ({ ...f, instructions: next }))
                      }
                      placeholder="How should responses read when this style is pinned? Markdown supported — use Preview to check rendering."
                      ariaLabel="Style instructions"
                    />
                  </div>

                  {error && <div className={styles.formError}>{error}</div>}
                </div>

                <div className={styles.formFooter}>
                  <CustomButton
                    variant="primary"
                    htmlType="submit"
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
              </FormTemplate>
            </>
          )}
        </section>
      </div>
    </div>
  );
};

export default ResponseStylesPage;
