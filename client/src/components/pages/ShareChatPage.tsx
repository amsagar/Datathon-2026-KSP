import React, { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import ChatThread from '@organisms/ChatThread';
import CustomSpinner from '@atoms/CustomSpinner';
import { sharesApi } from '@apiCalls/services';
import { ROUTE_PATHS } from '@constants/routePaths';
import type { SharedChatDto, UiChatMessage } from '@interfaces/chat.interface';
import * as styles from '@styles/shareChatPage.module.scss';

const ShareChatPage: React.FC = () => {
  const { shareId } = useParams<{ shareId: string }>();
  const [data, setData] = useState<SharedChatDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!shareId) {
      setError('This shared link is invalid.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    sharesApi
      .view(shareId)
      .then((res) => {
        if (active) setData(res);
      })
      .catch(() => {
        if (active) setError('This shared conversation is no longer available.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [shareId]);

  // Snapshot strips tool cards, so map straight to UI messages with no tools.
  const messages = useMemo<UiChatMessage[]>(
    () =>
      (data?.messages || []).map((m) => ({
        role: m.role,
        content: m.content,
        tools: [],
        clarification: null,
      })),
    [data],
  );

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerMain}>
          <span className={styles.title}>{data?.title || 'Shared conversation'}</span>
          <span className={styles.badge}>Shared · view only</span>
        </div>
        <Link className={styles.openLink} to={ROUTE_PATHS.CHAT}>
          Open Crime Intelligence
        </Link>
      </header>

      <div className={styles.body}>
        {loading ? (
          <div className={styles.centered} aria-busy="true">
            <CustomSpinner size="large" tip="Loading shared conversation…" />
          </div>
        ) : error ? (
          <div className={styles.centered}>
            <div className={styles.centeredTitle}>Unavailable</div>
            <div>{error}</div>
            <Link className={styles.openLink} to={ROUTE_PATHS.CHAT}>
              Go to Crime Intelligence
            </Link>
          </div>
        ) : (
          <ChatThread
            messages={messages}
            streaming={false}
            messagesLoading={false}
            hasSession
            readOnly
            assistantName={data?.assistantName || undefined}
          />
        )}
      </div>
    </div>
  );
};

export default ShareChatPage;
