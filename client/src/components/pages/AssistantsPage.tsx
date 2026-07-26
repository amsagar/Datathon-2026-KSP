import React, { useEffect, useMemo, useState } from 'react';
import { useT } from '@constants/translations';
import FormTemplate from '@templates/FormTemplate';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import CustomIcon, { CustomIconName } from '@atoms/CustomIcon';
import CustomTooltip from '@atoms/CustomTooltip';
import { confirm } from '@atoms/CustomConfirm';
import MarkdownEditor from '@molecules/MarkdownEditor';
import RevisionHistory from '@molecules/RevisionHistory';
import { assistantsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import type {
  AssistantDto,
  BuiltinToolDto,
  CreateAssistantRequest,
} from '@interfaces/assistant.interface';
import * as styles from '@styles/resourcePanel.module.scss';
import * as pageStyles from '@styles/assistantForm.module.scss';

const EMPTY_FORM: CreateAssistantRequest = {
  name: '',
  systemPrompt: '',
  builtinTools: [],
};

const TOOL_ICONS: Record<string, CustomIconName> = {
  file_system: 'document',
  grep: 'search',
  glob: 'inbox',
  shell: 'tool',
  ask_user_question: 'message',
};

const parseToolLabel = (label: string): { title: string; desc: string } => {
  const match = label.match(/^(.+?)\s*\((.+)\)\s*$/);
  if (match) {
    return { title: match[1].trim(), desc: match[2].trim() };
  }
  return { title: label, desc: '' };
};

const toolKey = (tools: string[]) => [...tools].sort().join('\0');

const formsEqual = (
  a: CreateAssistantRequest,
  b: CreateAssistantRequest
): boolean =>
  a.name.trim() === b.name.trim() &&
  a.systemPrompt === b.systemPrompt &&
  toolKey(a.builtinTools) === toolKey(b.builtinTools);

const AssistantsPage: React.FC = () => {
  const t = useT();
  const openNotification = useNotification();
  const {
    assistantId,
    assistant,
    setAssistantId,
    refreshAssistants,
    creatingAssistant,
    cancelCreateAssistant,
  } = useSettingsScope();
  const [builtins, setBuiltins] = useState<BuiltinToolDto[]>([]);
  const [form, setForm] = useState<CreateAssistantRequest>(EMPTY_FORM);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    assistantsApi
      .builtinTools()
      .then(setBuiltins)
      .catch(() => setBuiltins([]));
  }, []);

  useEffect(() => {
    if (creatingAssistant) {
      setForm(EMPTY_FORM);
      setError('');
      return;
    }
    if (assistant) {
      setForm({
        name: assistant.name,
        systemPrompt: assistant.systemPrompt,
        builtinTools: assistant.builtinTools || [],
      });
    } else {
      setForm(EMPTY_FORM);
    }
    setError('');
  }, [assistant, creatingAssistant]);

  const toggleTool = (key: string) => {
    setForm((f) => ({
      ...f,
      builtinTools: f.builtinTools.includes(key)
        ? f.builtinTools.filter((k) => k !== key)
        : [...f.builtinTools, key],
    }));
  };

  const savedForm = useMemo<CreateAssistantRequest>(() => {
    if (creatingAssistant) return EMPTY_FORM;
    if (!assistant) return EMPTY_FORM;
    return {
      name: assistant.name,
      systemPrompt: assistant.systemPrompt,
      builtinTools: assistant.builtinTools || [],
    };
  }, [assistant, creatingAssistant]);

  const isDirty = useMemo(
    () => !formsEqual(form, savedForm),
    [form, savedForm]
  );

  const canSave =
    isDirty && !!form.name.trim() && !!form.systemPrompt.trim();

  const save = async () => {
    if (!form.name.trim() || !form.systemPrompt.trim()) {
      setError('Name and system prompt are required.');
      return;
    }
    if (!isDirty) return;
    setSaving(true);
    try {
      const body: CreateAssistantRequest = {
        name: form.name.trim(),
        systemPrompt: form.systemPrompt,
        builtinTools: form.builtinTools,
      };
      if (creatingAssistant) {
        const created = await assistantsApi.create(body);
        openNotification(`Assistant "${created.name}" created`, 'Success');
        await refreshAssistants();
        setAssistantId(created.id);
      } else if (assistantId) {
        const updated = await assistantsApi.update(assistantId, body);
        openNotification(`Assistant "${updated.name}" updated`, 'Success');
        await refreshAssistants();
      }
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (a: AssistantDto) => {
    try {
      await assistantsApi.delete(a.id);
      openNotification(`Assistant "${a.name}" deleted`, 'Success');
      await refreshAssistants();
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to delete assistant',
        'Error'
      );
    }
  };

  const askDelete = () => {
    if (!assistant) return;
    confirm({
      title: `Delete "${assistant.name}"?`,
      body: 'This removes the assistant and its scoped configuration. This cannot be undone.',
      danger: true,
      okText: 'Delete',
      onOk: () => remove(assistant),
    });
  };

  const pageTitle = creatingAssistant
    ? 'New assistant'
    : assistant?.name || 'General';

  const pageSubtitle = creatingAssistant
    ? 'Set a name, system prompt, and built-in tools. Other settings unlock after you save.'
    : assistant
      ? 'Name, system prompt, and built-in tools for this assistant.'
      : 'Use New assistant in the left menu, or pick an existing assistant from the dropdown.';

  const showForm = creatingAssistant || !!assistant;
  const enabledCount = form.builtinTools.length;

  return (
    <div className={pageStyles.page}>
      <header className={pageStyles.pageHeader}>
        <div className={pageStyles.pageHeaderMain}>
          <div className={pageStyles.pageEyebrow}>General</div>
          <h1 className={pageStyles.pageTitle}>{pageTitle}</h1>
          <p className={pageStyles.pageSubtitle}>{pageSubtitle}</p>
        </div>
        {!creatingAssistant && assistant && (
          <div className="flex items-center gap-2">
            <RevisionHistory
              key={assistant.id}
              resourceType="assistant"
              resourceId={assistant.id}
              resourceLabel={assistant.name}
              onReverted={refreshAssistants}
            />
            <CustomTooltip title={t('deleteAssistant')}>
              <CustomButton
                variant="ghost"
                onClick={askDelete}
                aria-label="Delete assistant"
              >
                <CustomIcon name="delete" size={15} />
                Delete
              </CustomButton>
            </CustomTooltip>
          </div>
        )}
      </header>

      {!showForm ? (
        <div className={pageStyles.emptyState}>
          <CustomIcon name="robot" size={28} />
          <p>No assistant selected</p>
          <span>
            Click <strong>New assistant</strong> in the left menu to create one,
            or pick an assistant from the dropdown above the settings links.
          </span>
        </div>
      ) : (
        <FormTemplate
          sectionClassName={pageStyles.formWrap}
          className={pageStyles.form}
          onSubmit={(e) => {
            e.preventDefault();
            void save();
          }}
        >
          <div className={pageStyles.formBody}>
            <div className={pageStyles.formMeta}>
              <div className={pageStyles.nameField}>
                <label className={styles.fieldLabel}>Name</label>
                <CustomInput
                  value={form.name}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, name: e.target.value }))
                  }
                  placeholder="e.g. Crime Intelligence"
                  fullWidth
                />
              </div>

              <div className={pageStyles.toolsSection}>
                <div className={pageStyles.toolsSectionHeader}>
                  <label className={styles.fieldLabel}>Built-in tools</label>
                  <span className={pageStyles.toolsCount}>
                    {enabledCount}/{builtins.length}
                  </span>
                </div>
                <p className={styles.fieldHelp}>
                  Enable capabilities for this assistant. Use Ask user when the agent
                  should clarify missing or ambiguous input before acting.
                </p>
                {builtins.length === 0 ? (
                  <div className={styles.fieldHelp}>
                    No built-in tools available.
                  </div>
                ) : (
                  <div className={pageStyles.toolGrid}>
                    {builtins.map((t) => {
                      const active = form.builtinTools.includes(t.key);
                      const { title, desc } = parseToolLabel(t.label);
                      const icon = TOOL_ICONS[t.key] || 'tool';
                      return (
                        <div key={t.key} className={pageStyles.toolGridCell}>
                          <CustomTooltip
                            title={desc || t.label}
                            placement="top"
                          >
                            <button
                              type="button"
                              onClick={() => toggleTool(t.key)}
                              className={`${pageStyles.toolCard} ${
                                active ? pageStyles.toolCardActive : ''
                              }`}
                              aria-pressed={active}
                              aria-label={`${title}${active ? ', enabled' : ', disabled'}`}
                            >
                              <span className={pageStyles.toolCardIcon}>
                                <CustomIcon name={icon} size={13} />
                              </span>
                              <span className={pageStyles.toolCardBody}>
                                <span className={pageStyles.toolCardTitle}>
                                  {title}
                                </span>
                              </span>
                              <span className={pageStyles.toolCardToggle}>
                                <CustomIcon
                                  name={active ? 'check' : 'plus'}
                                  size={10}
                                />
                              </span>
                            </button>
                          </CustomTooltip>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            <div className={pageStyles.promptSection}>
              <div className={pageStyles.promptHeader}>
                <label className={styles.fieldLabel}>System prompt</label>
                <span className={pageStyles.promptCount}>
                  {form.systemPrompt.length.toLocaleString()} chars
                </span>
              </div>
              <MarkdownEditor
                fillHeight
                value={form.systemPrompt}
                onChange={(next) =>
                  setForm((f) => ({ ...f, systemPrompt: next }))
                }
                placeholder="Describe how this assistant should behave. Markdown is supported — switch to Preview to see it rendered."
                ariaLabel="System prompt"
              />
            </div>

            {error && <div className={styles.formError}>{error}</div>}
          </div>

          <div className={pageStyles.formFooter}>
            <CustomButton
              variant="primary"
              htmlType="submit"
              loading={saving}
              disabled={!canSave || saving}
            >
              {creatingAssistant ? 'Create assistant' : 'Save changes'}
            </CustomButton>
            {creatingAssistant && (
              <CustomButton
                variant="secondary"
                onClick={cancelCreateAssistant}
                disabled={saving}
              >
                Cancel
              </CustomButton>
            )}
          </div>
        </FormTemplate>
      )}
    </div>
  );
};

export default AssistantsPage;
