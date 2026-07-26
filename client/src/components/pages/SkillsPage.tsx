import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '@constants/translations';
import CustomButton from '@atoms/CustomButton';
import CustomDropdown from '@atoms/CustomDropdown';
import CustomIcon from '@atoms/CustomIcon';
import CustomTooltip from '@atoms/CustomTooltip';
import { confirm } from '@atoms/CustomConfirm';
import MarkdownEditor from '@molecules/MarkdownEditor';
import RevisionHistory from '@molecules/RevisionHistory';
import SkillFileTree, {
  findFilePath,
  folderPathsForFile,
} from '@molecules/SkillFileTree';
import { assistantsApi, skillsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import type {
  PlatformSkillDto,
  SkillDto,
  SkillFileNode,
} from '@interfaces/skill.interface';
import * as styles from '@styles/skillsPage.module.scss';

const SkillsPage: React.FC = () => {
  const t = useT();
  const openNotification = useNotification();
  const { assistant, assistantId, refreshAssistants } = useSettingsScope();
  const uploadRef = useRef<HTMLInputElement>(null);
  const replaceRef = useRef<HTMLInputElement>(null);

  const [skills, setSkills] = useState<SkillDto[]>([]);
  const [platformSkills, setPlatformSkills] = useState<PlatformSkillDto[]>([]);
  const [togglingPlatformId, setTogglingPlatformId] = useState<string | null>(
    null
  );
  const [expandedSkillIds, setExpandedSkillIds] = useState<Set<string>>(
    () => new Set()
  );
  const [skillTrees, setSkillTrees] = useState<
    Record<string, SkillFileNode[]>
  >({});
  const [loadingSkillIds, setLoadingSkillIds] = useState<Set<string>>(
    () => new Set()
  );
  const [expandedFolders, setExpandedFolders] = useState<
    Record<string, Set<string>>
  >({});
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null);
  const [selectedFilePath, setSelectedFilePath] = useState<string | null>(
    null
  );
  const [replaceSkillId, setReplaceSkillId] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState('');
  const [savedContent, setSavedContent] = useState('');
  const [loadingContent, setLoadingContent] = useState(false);
  const [savingFile, setSavingFile] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const selectedSkill = useMemo(
    () => skills.find((s) => s.id === selectedSkillId) || null,
    [skills, selectedSkillId]
  );

  const refreshSkills = useCallback(async () => {
    if (!assistantId) {
      setSkills([]);
      setPlatformSkills([]);
      return;
    }
    try {
      setSkills(await skillsApi.list(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load skills',
        'Error'
      );
    }
    try {
      setPlatformSkills(await skillsApi.listPlatform(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load platform skills',
        'Error'
      );
    }
  }, [assistantId, openNotification]);

  useEffect(() => {
    void refreshSkills();
    setExpandedSkillIds(new Set());
    setSkillTrees({});
    setExpandedFolders({});
    setSelectedSkillId(null);
    setSelectedFilePath(null);
    setFileContent('');
    setSavedContent('');
  }, [assistantId, refreshSkills]);

  const loadSkillTree = useCallback(
    async (skillId: string, autoSelectFile = true) => {
      setLoadingSkillIds((prev) => new Set(prev).add(skillId));
      setError('');
      try {
        const tree = await skillsApi.listFiles(skillId);
        setSkillTrees((prev) => ({ ...prev, [skillId]: tree }));

        if (autoSelectFile) {
          const nextPath =
            findFilePath(tree, (n) => n.name === 'SKILL.md') ||
            findFilePath(tree, () => true);
          if (nextPath) {
            setExpandedFolders((prev) => ({
              ...prev,
              [skillId]: new Set(folderPathsForFile(nextPath)),
            }));
            setSelectedSkillId(skillId);
            setSelectedFilePath(nextPath);
          }
        }
      } catch (e) {
        setSkillTrees((prev) => ({ ...prev, [skillId]: [] }));
        setError((e as Error)?.message || 'Failed to load skill files');
      } finally {
        setLoadingSkillIds((prev) => {
          const next = new Set(prev);
          next.delete(skillId);
          return next;
        });
      }
    },
    []
  );

  const loadFileContent = useCallback(
    async (skillId: string, path: string) => {
      setLoadingContent(true);
      setError('');
      try {
        const file = await skillsApi.getFile(skillId, path);
        setFileContent(file.content);
        setSavedContent(file.content);
      } catch (e) {
        setFileContent('');
        setSavedContent('');
        setError((e as Error)?.message || 'Failed to load file');
      } finally {
        setLoadingContent(false);
      }
    },
    []
  );

  useEffect(() => {
    if (!selectedSkillId || !selectedFilePath) return;
    void loadFileContent(selectedSkillId, selectedFilePath);
  }, [selectedSkillId, selectedFilePath, loadFileContent]);

  const isFileDirty = fileContent !== savedContent;
  const canSaveFile =
    !!selectedSkillId &&
    !!selectedFilePath &&
    isFileDirty &&
    !loadingContent &&
    !savingFile;

  const discardIfDirty = (action: () => void) => {
    if (!isFileDirty) {
      action();
      return;
    }
    confirm({
      title: 'Discard unsaved changes?',
      body: 'You have unsaved edits in the current file.',
      danger: true,
      okText: 'Discard',
      onOk: action,
    });
  };

  const toggleSkillExpand = (skill: SkillDto) => {
    const isOpen = expandedSkillIds.has(skill.id);
    if (isOpen) {
      setExpandedSkillIds((prev) => {
        const next = new Set(prev);
        next.delete(skill.id);
        return next;
      });
      return;
    }

    discardIfDirty(() => {
      setExpandedSkillIds((prev) => new Set(prev).add(skill.id));
      setSelectedSkillId(skill.id);
      if (!skillTrees[skill.id]) {
        void loadSkillTree(skill.id);
      }
    });
  };

  const selectFile = (skillId: string, path: string) => {
    if (skillId === selectedSkillId && path === selectedFilePath) return;

    const apply = () => {
      setExpandedSkillIds((prev) => new Set(prev).add(skillId));
      setExpandedFolders((prev) => ({
        ...prev,
        [skillId]: new Set([
          ...(prev[skillId] || []),
          ...folderPathsForFile(path),
        ]),
      }));
      setSelectedSkillId(skillId);
      setSelectedFilePath(path);
    };

    discardIfDirty(apply);
  };

  const toggleFolder = (skillId: string, folderPath: string) => {
    setExpandedFolders((prev) => {
      const current = new Set(prev[skillId] || []);
      if (current.has(folderPath)) current.delete(folderPath);
      else current.add(folderPath);
      return { ...prev, [skillId]: current };
    });
  };

  const saveFile = async () => {
    if (!selectedSkillId || !selectedFilePath || !canSaveFile) return;
    setSavingFile(true);
    setError('');
    try {
      const updated = await skillsApi.updateFile(
        selectedSkillId,
        selectedFilePath,
        fileContent
      );
      setSavedContent(fileContent);
      setSkills((prev) =>
        prev.map((s) => (s.id === updated.id ? updated : s))
      );
      openNotification('File saved', 'Success');
    } catch (e) {
      setError((e as Error)?.message || 'Failed to save file');
    } finally {
      setSavingFile(false);
    }
  };

  const togglePlatformSkill = async (skill: PlatformSkillDto) => {
    if (!assistantId || togglingPlatformId) return;
    const nextActiveIds = platformSkills
      .filter((s) => (s.id === skill.id ? !s.active : s.active))
      .map((s) => s.id);
    setTogglingPlatformId(skill.id);
    try {
      await assistantsApi.update(assistantId, {
        platformSkills: nextActiveIds,
      });
      setPlatformSkills((prev) =>
        prev.map((s) => (s.id === skill.id ? { ...s, active: !s.active } : s))
      );
      await refreshAssistants();
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to update platform skill',
        'Error'
      );
    } finally {
      setTogglingPlatformId(null);
    }
  };

  const toggleEnabled = async () => {
    if (!selectedSkill) return;
    try {
      const updated = await skillsApi.updateMeta(selectedSkill.id, {
        enabled: !selectedSkill.enabled,
      });
      setSkills((prev) =>
        prev.map((s) => (s.id === updated.id ? updated : s))
      );
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to update skill',
        'Error'
      );
    }
  };

  const removeSkill = async (skill: SkillDto) => {
    confirm({
      title: `Delete "${skill.name}"?`,
      body: 'This removes the skill and all of its files.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await skillsApi.delete(skill.id);
          setExpandedSkillIds((prev) => {
            const next = new Set(prev);
            next.delete(skill.id);
            return next;
          });
          setSkillTrees((prev) => {
            const next = { ...prev };
            delete next[skill.id];
            return next;
          });
          if (selectedSkillId === skill.id) {
            setSelectedSkillId(null);
            setSelectedFilePath(null);
            setFileContent('');
            setSavedContent('');
          }
          await refreshSkills();
          openNotification(`Skill "${skill.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete skill',
            'Error'
          );
        }
      },
    });
  };

  const startReplace = (skill: SkillDto) => {
    setReplaceSkillId(skill.id);
    replaceRef.current?.click();
  };

  const onReplaceFile = async (file: File | null) => {
    if (!file || !replaceSkillId) return;
    setReplacing(true);
    setError('');
    try {
      const updated = await skillsApi.replaceFile(replaceSkillId, file);
      setSkills((prev) =>
        prev.map((s) => (s.id === updated.id ? updated : s))
      );
      setExpandedSkillIds((prev) => new Set(prev).add(replaceSkillId));
      await loadSkillTree(replaceSkillId);
      openNotification(`Skill "${updated.name}" replaced`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to replace skill',
        'Error'
      );
    } finally {
      setReplacing(false);
      setReplaceSkillId(null);
      if (replaceRef.current) replaceRef.current.value = '';
    }
  };

  const downloadSkill = async (skill: SkillDto) => {
    setDownloading(true);
    try {
      await skillsApi.download(skill.id, skill.name);
      openNotification(`Downloaded "${skill.name}"`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to download skill',
        'Error'
      );
    } finally {
      setDownloading(false);
    }
  };

  const onUpload = async (file: File | null) => {
    if (!file || !assistantId) return;
    setUploading(true);
    setError('');
    try {
      const created = await skillsApi.upload(assistantId, file);
      await refreshSkills();
      setExpandedSkillIds((prev) => new Set(prev).add(created.id));
      await loadSkillTree(created.id);
      openNotification(`Skill "${created.name}" uploaded`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to upload skill',
        'Error'
      );
    } finally {
      setUploading(false);
      if (uploadRef.current) uploadRef.current.value = '';
    }
  };

  const handleSkillReverted = useCallback(async () => {
    await refreshSkills();
    if (selectedSkillId) {
      await loadSkillTree(selectedSkillId, false);
      if (selectedFilePath) {
        await loadFileContent(selectedSkillId, selectedFilePath);
      }
    }
  }, [refreshSkills, selectedSkillId, selectedFilePath, loadSkillTree, loadFileContent]);

  const skillMenuItems = (skill: SkillDto) => [
    {
      key: 'replace',
      label: (
        <span className={styles.menuItem}>
          <CustomIcon name="upload" size={14} />
          Replace
        </span>
      ),
      disabled: replacing,
      onClick: () => startReplace(skill),
    },
    {
      key: 'download',
      label: (
        <span className={styles.menuItem}>
          <CustomIcon name="download" size={14} />
          Download
        </span>
      ),
      disabled: downloading,
      onClick: () => void downloadSkill(skill),
    },
    {
      key: 'delete',
      label: (
        <span className={`${styles.menuItem} ${styles.menuItemDanger}`}>
          <CustomIcon name="delete" size={14} />
          Delete
        </span>
      ),
      danger: true,
      onClick: () => void removeSkill(skill),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.pageHeaderMain}>
          <div className={styles.pageEyebrow}>Skills</div>
          <h1 className={styles.pageTitle}>Skill library</h1>
          <p className={styles.pageSubtitle}>
            {assistant
              ? `Browse and edit skill files for ${assistant.name}.`
              : 'Pick an assistant in the left menu to manage skills.'}
          </p>
        </div>
        <CustomTooltip title={t('uploadSkill')}>
          <CustomButton
            variant="primary"
            size="small"
            disabled={!assistantId || uploading}
            onClick={() => uploadRef.current?.click()}
          >
            <CustomIcon name="plus" size={14} />
            Upload skill
          </CustomButton>
        </CustomTooltip>
      </header>

      <input
        ref={uploadRef}
        type="file"
        className={styles.hiddenInput}
        accept=".md,.json,.zip"
        onChange={(e) => void onUpload(e.target.files?.[0] || null)}
      />
      <input
        ref={replaceRef}
        type="file"
        className={styles.hiddenInput}
        accept=".md,.json,.zip"
        onChange={(e) => void onReplaceFile(e.target.files?.[0] || null)}
      />

      <div className={styles.workspace}>
        <aside className={styles.sidebar}>
          {!assistantId && (
            <div className={styles.emptyList}>Pick an assistant first</div>
          )}
          {assistantId && platformSkills.length > 0 && (
            <div className={styles.platformSection}>
              <div className={styles.platformSectionLabel}>
                Platform skills
              </div>
              {platformSkills.map((skill) => (
                <div key={skill.id} className={styles.platformRow}>
                  <CustomTooltip title={skill.description || skill.name}>
                    <span className={styles.platformRowName}>
                      {skill.name}
                      {skill.defaultEnabled && (
                        <span className={styles.platformBadge}>Default</span>
                      )}
                    </span>
                  </CustomTooltip>
                  <button
                    type="button"
                    className={`${styles.enableToggle} ${
                      skill.active ? styles.enableToggleOn : ''
                    }`}
                    disabled={togglingPlatformId === skill.id}
                    onClick={() => void togglePlatformSkill(skill)}
                    title={
                      skill.active
                        ? `Disable ${skill.name} for this assistant`
                        : `Enable ${skill.name} for this assistant`
                    }
                  >
                    <span className={styles.enableDot} />
                    {skill.active ? 'On' : 'Off'}
                  </button>
                </div>
              ))}
              <div className={styles.platformSectionLabel}>
                Uploaded skills
              </div>
            </div>
          )}
          {assistantId && skills.length === 0 && (
            <div className={styles.emptyList}>No skills yet — upload one</div>
          )}
          {skills.map((skill) => {
            const open = expandedSkillIds.has(skill.id);
            const tree = skillTrees[skill.id];
            const loadingTree = loadingSkillIds.has(skill.id);
            const folders = expandedFolders[skill.id] || new Set<string>();

            return (
              <div key={skill.id} className={styles.skillGroup}>
                <button
                  type="button"
                  className={`${styles.skillGroupToggle} ${
                    open ? styles.skillGroupToggleOpen : ''
                  } ${selectedSkillId === skill.id ? styles.skillGroupToggleSelected : ''}`}
                  onClick={() => toggleSkillExpand(skill)}
                >
                  <span className={styles.skillGroupName}>{skill.name}</span>
                  <span className={styles.skillGroupCaret}>
                    <CustomIcon
                      name={open ? 'caret-down' : 'caret-right'}
                      size={11}
                    />
                  </span>
                </button>

                {open && (
                  <div className={styles.skillGroupBody}>
                    {loadingTree && (
                      <div className={styles.treeStatus}>Loading files…</div>
                    )}
                    {!loadingTree && tree && tree.length === 0 && (
                      <div className={styles.treeStatus}>No files found</div>
                    )}
                    {!loadingTree && tree && tree.length > 0 && (
                      <SkillFileTree
                        nodes={tree}
                        selectedPath={
                          selectedSkillId === skill.id ? selectedFilePath : null
                        }
                        expanded={folders}
                        onToggleFolder={(path) =>
                          toggleFolder(skill.id, path)
                        }
                        onSelectFile={(path) => selectFile(skill.id, path)}
                      />
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </aside>

        <section className={styles.editorPane}>
          {!selectedSkill ? (
            <div className={styles.editorEmpty}>
              <CustomIcon name="document" size={28} />
              <p>Select a skill to edit</p>
              <span>
                Expand a skill in the sidebar, then pick a file — start with{' '}
                <strong>SKILL.md</strong>.
              </span>
            </div>
          ) : (
            <>
              <div className={styles.editorHeader}>
                <div className={styles.editorHeaderMain}>
                  <h2 className={styles.editorFileName}>{selectedSkill.name}</h2>
                  {selectedFilePath && (
                    <p className={styles.editorFilePath}>{selectedFilePath}</p>
                  )}
                  {selectedSkill.description && (
                    <p className={styles.editorDescription}>
                      {selectedSkill.description}
                    </p>
                  )}
                </div>
                <div className={styles.editorHeaderActions}>
                  <RevisionHistory
                    key={selectedSkill.id}
                    resourceType="skill"
                    resourceId={selectedSkill.id}
                    resourceLabel={selectedSkill.name}
                    onReverted={handleSkillReverted}
                  />
                  <button
                    type="button"
                    className={`${styles.enableToggle} ${
                      selectedSkill.enabled ? styles.enableToggleOn : ''
                    }`}
                    onClick={() => void toggleEnabled()}
                    title={
                      selectedSkill.enabled ? 'Disable skill' : 'Enable skill'
                    }
                  >
                    <span className={styles.enableDot} />
                    {selectedSkill.enabled ? 'Enabled' : 'Disabled'}
                  </button>
                  <CustomDropdown
                    items={skillMenuItems(selectedSkill)}
                    placement="bottomRight"
                  >
                    <CustomButton
                      variant="text"
                      size="small"
                      aria-label="Skill actions"
                    >
                      <CustomIcon name="more" size={16} />
                    </CustomButton>
                  </CustomDropdown>
                </div>
              </div>

              {!selectedFilePath ? (
                <div className={styles.editorEmpty}>
                  <p>Pick a file from the tree to start editing.</p>
                </div>
              ) : (
                <>
                  <div className={styles.editorBody}>
                    {loadingContent ? (
                      <div className={styles.treeStatus}>Loading file…</div>
                    ) : (
                      <MarkdownEditor
                        fillHeight
                        value={fileContent}
                        onChange={setFileContent}
                        placeholder="Edit skill file content…"
                        ariaLabel={`Edit ${selectedFilePath}`}
                      />
                    )}
                  </div>

                  {error && <div className={styles.formError}>{error}</div>}

                  <div className={styles.editorFooter}>
                    <CustomButton
                      variant="primary"
                      onClick={() => void saveFile()}
                      loading={savingFile}
                      disabled={!canSaveFile}
                    >
                      Save file
                    </CustomButton>
                    {isFileDirty && (
                      <span className={styles.dirtyHint}>Unsaved changes</span>
                    )}
                  </div>
                </>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
};

export default SkillsPage;
