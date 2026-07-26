import React, { useEffect, useState } from 'react';
import CustomModal from '@atoms/CustomModal';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomSpinner from '@atoms/CustomSpinner';
import { confirm } from '@atoms/CustomConfirm';
import { sessionsApi, sharesApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type { ShareLinkDto } from '@interfaces/chat.interface';
import * as styles from '@styles/shareDialog.module.scss';

export interface ShareChatDialogProps {
  open: boolean;
  sessionId: string;
  onClose: () => void;
}

const shareUrlFor = (shareId: string) =>
  `${window.location.origin}/share/${shareId}`;

const ShareChatDialog: React.FC<ShareChatDialogProps> = ({
  open,
  sessionId,
  onClose,
}) => {
  const openNotification = useNotification();
  const [link, setLink] = useState<ShareLinkDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!open || !sessionId) return;
    setLoading(true);
    setLink(null);
    sessionsApi
      .getShare(sessionId)
      .then((res) => setLink(res && (res as ShareLinkDto).shareId ? (res as ShareLinkDto) : null))
      .catch(() => setLink(null))
      .finally(() => setLoading(false));
  }, [open, sessionId]);

  const createOrRefresh = async () => {
    setBusy(true);
    try {
      const res = await sessionsApi.createShare(sessionId);
      setLink(res);
      openNotification(
        link ? 'Snapshot updated' : 'Share link created',
        'Success',
      );
    } catch (e) {
      openNotification((e as Error)?.message || 'Failed to create link', 'Error');
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(shareUrlFor(link.shareId));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      openNotification('Could not copy to clipboard', 'Error');
    }
  };

  const revoke = () => {
    if (!link) return;
    confirm({
      title: 'Delete share link?',
      body: 'The link will stop working for everyone. You can create a new one later.',
      danger: true,
      okText: 'Delete',
      onOk: async () => {
        try {
          await sharesApi.revoke(link.shareId);
          setLink(null);
          openNotification('Share link deleted', 'Success');
        } catch (e) {
          openNotification((e as Error)?.message || 'Failed to delete link', 'Error');
        }
      },
    });
  };

  return (
    <CustomModal open={open} onClose={onClose} title="Share chat" width="md">
      <p className={styles.note}>
        Anyone signed in to the platform with this link can view the conversation up to
        now — they can&apos;t reply or continue it. New messages won&apos;t appear
        unless you update the snapshot.
      </p>

      {loading ? (
        <div className={styles.loading}>
          <CustomSpinner tip="Checking for an existing link…" />
        </div>
      ) : link ? (
        <>
          <div className={styles.linkRow}>
            <input
              className={styles.linkInput}
              readOnly
              value={shareUrlFor(link.shareId)}
              onFocus={(e) => e.currentTarget.select()}
            />
            <CustomButton variant="secondary" onClick={() => void copy()}>
              <CustomIcon name={copied ? 'check' : 'copy'} />
              {copied ? 'Copied' : 'Copy'}
            </CustomButton>
          </div>
          <div className={styles.actions}>
            <CustomButton
              variant="primary"
              onClick={() => void createOrRefresh()}
              loading={busy}
            >
              Update snapshot
            </CustomButton>
            <CustomButton variant="danger" onClick={revoke} disabled={busy}>
              Delete link
            </CustomButton>
          </div>
        </>
      ) : (
        <div className={styles.actions}>
          <CustomButton
            variant="primary"
            onClick={() => void createOrRefresh()}
            loading={busy}
          >
            Create share link
          </CustomButton>
        </div>
      )}
    </CustomModal>
  );
};

export default ShareChatDialog;
