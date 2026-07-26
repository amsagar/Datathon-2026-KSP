import type { ChatSessionDto } from '@interfaces/chat.interface';

export type SessionGroupLabel =
  | 'Today'
  | 'Yesterday'
  | 'Previous 7 Days'
  | 'Previous 30 Days'
  | 'Older';

export interface SessionGroup {
  label: SessionGroupLabel;
  sessions: ChatSessionDto[];
}

const ORDER: SessionGroupLabel[] = [
  'Today',
  'Yesterday',
  'Previous 7 Days',
  'Previous 30 Days',
  'Older',
];

const labelFor = (epochSeconds: number, now: Date): SessionGroupLabel => {
  const ts = new Date(epochSeconds * 1000);
  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate()
  ).getTime();
  const day = 86_400_000;
  const ms = ts.getTime();

  if (ms >= startOfToday) return 'Today';
  if (ms >= startOfToday - day) return 'Yesterday';
  if (ms >= startOfToday - 7 * day) return 'Previous 7 Days';
  if (ms >= startOfToday - 30 * day) return 'Previous 30 Days';
  return 'Older';
};

export const groupSessionsByDate = (
  sessions: ChatSessionDto[]
): SessionGroup[] => {
  const now = new Date();
  const buckets = new Map<SessionGroupLabel, ChatSessionDto[]>();
  for (const s of sessions) {
    const key = labelFor(s.updatedAt || s.createdAt || 0, now);
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key)!.push(s);
  }
  return ORDER.filter((label) => buckets.has(label)).map((label) => ({
    label,
    sessions: buckets.get(label)!,
  }));
};
