import React, { useState } from 'react';
import CustomModal from '@atoms/CustomModal';
import CustomSelect from '@atoms/CustomSelect';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import CustomFileUpload from '@atoms/CustomFileUpload';
import { TOOL_IMPORT_KINDS } from '@constants/toolSourceKinds';
import { toolsApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type { ToolImportKind } from '@interfaces/tool.interface';
import * as styles from '@styles/resourcePanel.module.scss';

import type { ImportToolsResult } from '@interfaces/toolGroup.interface';

export interface ImportToolsDialogProps {
  open: boolean;
  assistantId: string;
  onClose: () => void;
  onImported: (result: ImportToolsResult) => void;
}

const readFileContent = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('Failed to read file'));
    reader.readAsText(file);
  });

const ImportToolsDialog: React.FC<ImportToolsDialogProps> = ({
  open,
  assistantId,
  onClose,
  onImported,
}) => {
  const openNotification = useNotification();
  const [kind, setKind] = useState<ToolImportKind>('openapi');
  const [file, setFile] = useState<File | null>(null);
  const [specUrl, setSpecUrl] = useState('');
  const [host, setHost] = useState('');
  const [groupName, setGroupName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const reset = () => {
    setKind('openapi');
    setFile(null);
    setSpecUrl('');
    setHost('');
    setGroupName('');
    setError('');
  };

  const submit = async () => {
    if (!assistantId) {
      setError('Pick an assistant first.');
      return;
    }

    setBusy(true);
    setError('');
    try {
      const hostOverride = host.trim() || undefined;
      const groupNameOverride = groupName.trim() || undefined;
      let result: ImportToolsResult;

      if (kind === 'openapi') {
        if (specUrl.trim()) {
          result = await toolsApi.import(kind, assistantId, {
            specUrl: specUrl.trim(),
            host: hostOverride,
            groupName: groupNameOverride,
          });
        } else if (file) {
          const content = await readFileContent(file);
          result = await toolsApi.import(kind, assistantId, {
            content,
            host: hostOverride,
            groupName: groupNameOverride,
          });
        } else {
          setError('Provide a spec URL or upload an OpenAPI JSON file.');
          return;
        }
      } else if (kind === 'postman') {
        if (!file) {
          setError('Upload a Postman collection JSON file.');
          return;
        }
        const content = await readFileContent(file);
        result = await toolsApi.import(kind, assistantId, {
          content,
          host: hostOverride,
          groupName: groupNameOverride,
        });
      } else {
        return;
      }

      openNotification(
        `Imported ${result.count} tool(s) into "${result.groupName || 'group'}"`,
        'Success'
      );
      reset();
      onImported(result);
      onClose();
    } catch (e) {
      setError((e as Error)?.message || 'Import failed');
    } finally {
      setBusy(false);
    }
  };

  const canImport =
    kind === 'postman'
      ? !!file
      : kind === 'openapi'
        ? !!file || !!specUrl.trim()
        : false;

  return (
    <CustomModal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title="Import tools"
      width="lg"
      footer={
        <>
          <CustomButton
            variant="secondary"
            onClick={() => {
              reset();
              onClose();
            }}
            disabled={busy}
          >
            Cancel
          </CustomButton>
          <CustomButton
            variant="primary"
            onClick={() => void submit()}
            loading={busy}
            disabled={!canImport}
          >
            Import
          </CustomButton>
        </>
      }
    >
      <div className={styles.importForm}>
        <div className={styles.importField}>
          <label className={styles.importFieldLabel}>Source format</label>
          <CustomSelect
            options={TOOL_IMPORT_KINDS.map((k) => ({
              value: k.value,
              label: k.label,
            }))}
            value={kind}
            onChange={(v) => {
              setKind(v as ToolImportKind);
              setFile(null);
              setSpecUrl('');
              setError('');
            }}
            fullWidth
          />
        </div>

        <div className={styles.importField}>
          <label className={styles.importFieldLabel}>
            Host override (optional)
          </label>
          <CustomInput
            value={host}
            onChange={(e) => setHost(e.target.value)}
            placeholder="https://api.ksp.example.gov.in"
            fullWidth
          />
          <div className={styles.importFieldHelp}>
            Use to replace the inferred host across all imported endpoints.
          </div>
        </div>

        <div className={styles.importField}>
          <label className={styles.importFieldLabel}>
            Group name (optional)
          </label>
          <CustomInput
            value={groupName}
            onChange={(e) => setGroupName(e.target.value)}
            placeholder="Defaults to the spec or collection title"
            fullWidth
          />
          <div className={styles.importFieldHelp}>
            Imported tools are grouped together so you can manage them as a
            set.
          </div>
        </div>

        {kind === 'openapi' && (
          <>
            <div className={styles.importField}>
              <label className={styles.importFieldLabel}>Spec URL</label>
              <CustomInput
                value={specUrl}
                onChange={(e) => setSpecUrl(e.target.value)}
                placeholder="https://api.ksp.example.gov.in/openapi.json"
                fullWidth
                disabled={!!file}
              />
              <div className={styles.importFieldHelp}>
                Fetch OpenAPI JSON from a public URL. The server resolves the
                spec from this address.
              </div>
            </div>

            <div className={styles.importOr}>or</div>

            <div className={styles.importField}>
              <label className={styles.importFieldLabel}>
                Upload JSON file
              </label>
              <CustomFileUpload
                value={file}
                onChange={(next) => {
                  setFile(next);
                  if (next) setSpecUrl('');
                }}
                accept=".json,application/json"
                dropLabel="Drop an OpenAPI JSON file here"
                buttonLabel="Choose JSON file"
                disabled={!!specUrl.trim()}
              />
            </div>
          </>
        )}

        {kind === 'postman' && (
          <div className={styles.importField}>
            <label className={styles.importFieldLabel}>
              Upload collection JSON
            </label>
            <CustomFileUpload
              value={file}
              onChange={setFile}
              accept=".json,application/json"
              dropLabel="Drop a Postman collection JSON file here"
              buttonLabel="Choose JSON file"
            />
          </div>
        )}

        {error && <div className={styles.formError}>{error}</div>}
      </div>
    </CustomModal>
  );
};

export default ImportToolsDialog;
