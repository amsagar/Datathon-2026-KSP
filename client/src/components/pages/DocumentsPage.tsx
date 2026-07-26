import React, { useCallback, useEffect, useState } from 'react';
import CustomTable, {
  type CustomTableColumnsType,
} from '@templates/CustomTable';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import CustomDropdown from '@atoms/CustomDropdown';
import CustomIcon from '@atoms/CustomIcon';
import CustomModal from '@atoms/CustomModal';
import CustomSwitch from '@atoms/CustomSwitch';
import CustomFileUpload from '@atoms/CustomFileUpload';
import CustomEmptyState from '@atoms/CustomEmptyState';
import { confirm } from '@atoms/CustomConfirm';
import { useSettingsScope } from '@providers/SettingsScopeProvider';
import { documentsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { relativeTime } from '@utils/relativeTime';
import type { DocumentDto } from '@interfaces/document.interface';
import * as styles from '@styles/documentsPage.module.scss';

const fileExtension = (name: string): string => {
  const dot = name.lastIndexOf('.');
  if (dot <= 0 || dot === name.length - 1) return 'file';
  return name.slice(dot + 1).toLowerCase();
};

const DocumentsPage: React.FC = () => {
  const openNotification = useNotification();
  const { assistant, assistantId } = useSettingsScope();
  const [items, setItems] = useState<DocumentDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingDoc, setEditingDoc] = useState<DocumentDto | null>(null);
  const [editName, setEditName] = useState('');
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!assistantId) {
      setItems([]);
      return;
    }
    setLoading(true);
    try {
      setItems(await documentsApi.list(assistantId));
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to load documents',
        'Error'
      );
    } finally {
      setLoading(false);
    }
  }, [assistantId, openNotification]);

  useEffect(() => {
    void refresh();
  }, [assistantId, refresh]);

  const openUpload = () => {
    if (!assistantId) {
      openNotification('Pick an assistant first', 'Warning');
      return;
    }
    setUploadFile(null);
    setUploadOpen(true);
  };

  const submitUpload = async () => {
    if (!uploadFile || !assistantId) return;
    setUploading(true);
    try {
      const created = await documentsApi.upload(assistantId, uploadFile);
      await refresh();
      setUploadOpen(false);
      setUploadFile(null);
      openNotification(`Document "${created.name}" uploaded`, 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to upload document',
        'Error'
      );
    } finally {
      setUploading(false);
    }
  };

  const openEdit = (doc: DocumentDto) => {
    setEditingDoc(doc);
    setEditName(doc.name);
    setEditOpen(true);
  };

  const saveEdit = async () => {
    if (!editingDoc || !editName.trim()) return;
    setSaving(true);
    try {
      await documentsApi.update(editingDoc.id, {
        name: editName.trim(),
        enabled: editingDoc.enabled,
      });
      await refresh();
      setEditOpen(false);
      setEditingDoc(null);
      openNotification('Document updated', 'Success');
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to update document',
        'Error'
      );
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (doc: DocumentDto, enabled: boolean) => {
    setTogglingId(doc.id);
    try {
      await documentsApi.update(doc.id, { enabled });
      setItems((prev) =>
        prev.map((d) => (d.id === doc.id ? { ...d, enabled } : d))
      );
    } catch (e) {
      openNotification(
        (e as Error)?.message || 'Failed to update document',
        'Error'
      );
    } finally {
      setTogglingId(null);
    }
  };

  const remove = (doc: DocumentDto) => {
    confirm({
      title: `Delete "${doc.name}"?`,
      body: 'This removes the document and all of its indexed chunks.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await documentsApi.delete(doc.id);
          await refresh();
          openNotification(`Document "${doc.name}" deleted`, 'Success');
        } catch (e) {
          openNotification(
            (e as Error)?.message || 'Failed to delete document',
            'Error'
          );
        }
      },
    });
  };

  const rowMenuItems = (doc: DocumentDto) => [
    {
      key: 'rename',
      label: (
        <span className={styles.menuItem}>
          <CustomIcon name="edit" size={14} />
          Rename
        </span>
      ),
      onClick: () => openEdit(doc),
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
      onClick: () => remove(doc),
    },
  ];

  const columns: CustomTableColumnsType<DocumentDto> = [
      {
        title: 'Name',
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
        render: (name: string) => (
          <span className={styles.cellName}>{name}</span>
        ),
      },
      {
        title: 'Type',
        key: 'type',
        width: 88,
        render: (_: unknown, record) => (
          <span className={styles.fileBadge}>
            {fileExtension(record.name).toUpperCase()}
          </span>
        ),
      },
      {
        title: 'Chunks',
        dataIndex: 'chunkCount',
        key: 'chunks',
        width: 88,
        align: 'right',
      },
      {
        title: 'Updated',
        key: 'updated',
        width: 110,
        render: (_: unknown, record) =>
          relativeTime(record.updatedAt) || '—',
      },
      {
        title: 'RAG',
        key: 'enabled',
        width: 72,
        align: 'center',
        render: (_: unknown, record) => (
          <CustomSwitch
            checked={record.enabled}
            disabled={togglingId === record.id}
            onChange={(checked) => void toggleEnabled(record, checked)}
            ariaLabel={
              record.enabled ? 'Disable document' : 'Enable document'
            }
          />
        ),
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

  return (
    <>
      <div className={styles.page}>
        <header className={styles.pageHeader}>
          <div className={styles.pageHeaderMain}>
            <div className={styles.pageEyebrow}>Knowledge</div>
            <h1 className={styles.pageTitle}>Documents</h1>
            <p className={styles.pageSubtitle}>
              {assistant
                ? `Reference documents ${assistant.name} can retrieve from (RAG).`
                : 'Reference documents the assistant can retrieve from (RAG).'}
            </p>
          </div>
          <CustomButton
            variant="primary"
            size="small"
            disabled={!assistantId}
            onClick={openUpload}
          >
            <CustomIcon name="upload" size={14} />
            Upload
          </CustomButton>
        </header>

        <div className={styles.tableWrap}>
          {!assistantId ? (
            <CustomEmptyState
              title="No assistant selected"
              description="Pick an assistant in the left menu to manage documents."
            />
          ) : items.length === 0 && !loading ? (
            <CustomEmptyState
              title="No documents yet"
              description="Upload PDF, Markdown, text, or Word files for RAG retrieval."
              action={
                <CustomButton variant="primary" onClick={openUpload}>
                  <CustomIcon name="upload" size={14} />
                  Upload document
                </CustomButton>
              }
            />
          ) : (
            <CustomTable<DocumentDto>
              rowKey="id"
              dataSource={items}
              columns={columns}
              loading={loading}
            />
          )}
        </div>
      </div>

      <CustomModal
        open={uploadOpen}
        title="Upload document"
        onClose={() => {
          if (!uploading) setUploadOpen(false);
        }}
        width="md"
        footer={
          <>
            <CustomButton
              variant="secondary"
              onClick={() => setUploadOpen(false)}
              disabled={uploading}
            >
              Cancel
            </CustomButton>
            <CustomButton
              variant="primary"
              onClick={() => void submitUpload()}
              loading={uploading}
              disabled={!uploadFile}
            >
              Upload
            </CustomButton>
          </>
        }
      >
        <div className={styles.modalBody}>
          <p className={styles.modalHint}>
            Supported: PDF, Markdown, plain text, and Word (.docx). The file
            will be chunked and indexed for retrieval.
          </p>
          <CustomFileUpload
            value={uploadFile}
            onChange={setUploadFile}
            accept=".pdf,.md,.txt,.docx"
            dropLabel="Drop a .pdf, .md, .txt, or .docx file"
            buttonLabel="Choose file"
          />
        </div>
      </CustomModal>

      <CustomModal
        open={editOpen}
        title="Rename document"
        onClose={() => {
          if (!saving) setEditOpen(false);
        }}
        width="sm"
        footer={
          <>
            <CustomButton
              variant="secondary"
              onClick={() => setEditOpen(false)}
              disabled={saving}
            >
              Cancel
            </CustomButton>
            <CustomButton
              variant="primary"
              onClick={() => void saveEdit()}
              loading={saving}
              disabled={!editName.trim()}
            >
              Save
            </CustomButton>
          </>
        }
      >
        <div className={styles.modalBody}>
          <label className={styles.fieldLabel}>Display name</label>
          <CustomInput
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            fullWidth
            autoFocus
          />
        </div>
      </CustomModal>
    </>
  );
};

export default DocumentsPage;
