import React, { useCallback, useEffect, useState } from 'react';
import dayjs from 'dayjs';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomEmptyState from '@atoms/CustomEmptyState';
import { confirm } from '@atoms/CustomConfirm';
import { Skeleton } from '@/components/ui/skeleton';
import { Star } from 'lucide-react';
import {
  fetchMemories,
  deleteMemory,
  deleteAllMemories,
} from '@apiCalls/memory';
import { useNotification } from '@providers/NotificationProviders';
import type { SemanticFactDto } from '@interfaces/memory.interface';
import * as styles from '@styles/memory.module.scss';

const humanFact = (f: SemanticFactDto): string =>
  `${f.subject} ${f.predicate.replace(/_/g, ' ')} ${f.object}`;

const MemoriesPage: React.FC = () => {
  const openNotification = useNotification();
  const [memories, setMemories] = useState<SemanticFactDto[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setMemories(await fetchMemories());
    } catch {
      openNotification('Failed to load memories', 'Error');
    } finally {
      setLoading(false);
    }
  }, [openNotification]);

  useEffect(() => {
    load();
  }, [load]);

  const handleForget = async (id: string) => {
    // Optimistic removal — restore on failure.
    const prev = memories;
    setMemories((m) => m.filter((f) => f.id !== id));
    try {
      await deleteMemory(id);
    } catch {
      setMemories(prev);
      openNotification('Failed to forget memory', 'Error');
    }
  };

  const handleForgetAll = () => {
    confirm({
      title: 'Forget everything?',
      body: 'The assistant will no longer remember any of these facts about you in future chats. This cannot be undone.',
      okText: 'Forget all',
      danger: true,
      onOk: async () => {
        try {
          await deleteAllMemories();
          setMemories([]);
          openNotification('All memories cleared', 'Success');
        } catch {
          openNotification('Failed to clear memories', 'Error');
        }
      },
    });
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.title}>Memory</h1>
          <p className={styles.subtitle}>
            Facts the assistant has learned about you from past conversations and
            uses to personalize future chats. Remove anything you don&apos;t want
            it to remember.
          </p>
        </div>
        {memories.length > 0 && (
          <CustomButton variant="danger" onClick={handleForgetAll}>
            Forget all
          </CustomButton>
        )}
      </div>

      {loading ? (
        <div className={styles.list} aria-busy="true" aria-label="Loading memories">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className={styles.item}>
              <div className="min-w-0 flex-1 space-y-2.5">
                <Skeleton className="h-4 w-[72%]" />
                <Skeleton className="h-3 w-28" />
              </div>
              <Skeleton className="h-9 w-9 shrink-0 rounded-md" />
            </div>
          ))}
        </div>
      ) : memories.length === 0 ? (
        <CustomEmptyState
          icon={<Star size={40} strokeWidth={1.5} />}
          title="No memories yet"
          description="As you chat, the assistant will remember durable facts about you here."
        />
      ) : (
        <div className={styles.list}>
          {memories.map((f) => (
            <div key={f.id} className={styles.item}>
              <div>
                <div className={styles.factText}>{humanFact(f)}</div>
                {f.lastAccessedAt && (
                  <div className={styles.factMeta}>
                    Last used {dayjs.unix(f.lastAccessedAt).format('MMM D, YYYY')}
                  </div>
                )}
              </div>
              <CustomButton
                variant="secondary"
                onClick={() => handleForget(f.id)}
                aria-label="Forget this memory"
              >
                <CustomIcon name="delete" size={15} />
              </CustomButton>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default MemoriesPage;
