import React, { useEffect, useMemo, useState } from 'react';
import { diffLines } from 'diff';
import CustomModal from '@atoms/CustomModal';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomTag from '@atoms/CustomTag';
import CustomTooltip from '@atoms/CustomTooltip';
import { confirm } from '@atoms/CustomConfirm';
import { useNotification } from '@providers/NotificationProviders';
import { isAdmin } from '@apiCalls/auth';
import { assistantsApi, configAuditApi } from '@apiCalls/services';
import SkillFileTree, { folderPathsForFile } from '@molecules/SkillFileTree';
import type { SkillFileNode } from '@interfaces/skill.interface';
import type { BuiltinToolDto } from '@interfaces/assistant.interface';
import type {
  VersionedResourceType,
  RevisionSummaryDto,
  RevisionDto,
  RevisionFileDto,
} from '@interfaces/configAudit.interface';
import * as toolStyles from '@styles/assistantForm.module.scss';

export interface RevisionHistoryProps {
  resourceType: VersionedResourceType;
  resourceId: string;
  /** Shown in the modal title and confirm dialogs, e.g. the assistant/skill name. */
  resourceLabel?: string;
  /** Called after a successful revert so the parent can reload the live resource. */
  onReverted?: () => void;
  /** Set false to skip rendering the "v{n}" badge (e.g. when the page already shows its own
   * version field elsewhere) — the History button still renders. */
  showBadge?: boolean;
}

const ACTION_LABELS: Record<string, string> = {
  create: 'Created',
  update: 'Updated',
  delete: 'Deleted',
  enable: 'Enabled',
  disable: 'Disabled',
  set_default: 'Set default',
  discover: 'Discovered',
  file_edit: 'Edited file',
  tool_enable: 'Tool enabled',
  tool_disable: 'Tool disabled',
  revert: 'Reverted',
};

const humanizeAction = (action: string): string =>
  ACTION_LABELS[action] || action.replace(/_/g, ' ');

const actionTone = (action: string): 'success' | 'error' | 'warning' | 'info' | 'neutral' => {
  switch (action) {
    case 'create':
    case 'enable':
    case 'set_default':
      return 'success';
    case 'delete':
    case 'disable':
      return 'error';
    case 'revert':
      return 'warning';
    default:
      return 'info';
  }
};

// Backend createdAt is epoch SECONDS for this feed (ConfigAuditService uses
// Instant.now().getEpochSecond()), unlike the legacy user_audit_log feed's epoch millis.
const formatDateTime = (epochSeconds: number): string =>
  new Date(epochSeconds * 1000).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

/** Which snapshot fields are "the interesting text" per resource type, verified against the
 * actual response DTOs (assistant.interface.ts, style.interface.ts, skill.interface.ts) rather
 * than assumed. */
const TEXT_FIELDS: Record<VersionedResourceType, string[]> = {
  assistant: ['systemPrompt'],
  response_style: ['instructions'],
  skill: [],
};
const SCALAR_FIELDS: Record<VersionedResourceType, string[]> = {
  assistant: ['name'],
  response_style: ['name', 'description'],
  skill: ['name', 'description'],
};
const BOOL_FIELDS: Record<VersionedResourceType, string[]> = {
  assistant: [],
  response_style: ['defaultStyle'],
  skill: ['enabled'],
};
const ARRAY_FIELDS: Record<VersionedResourceType, string[]> = {
  assistant: ['builtinTools', 'platformSkills'],
  response_style: [],
  skill: [],
};

const FIELD_LABELS: Record<string, string> = {
  systemPrompt: 'System prompt',
  instructions: 'Instructions',
  name: 'Name',
  description: 'Description',
  defaultStyle: 'Default style',
  enabled: 'Enabled',
  builtinTools: 'Built-in tools',
  platformSkills: 'Platform skills',
};

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' ? (value as Record<string, unknown>) : {};

const asString = (value: unknown): string => (typeof value === 'string' ? value : '');

const asStringList = (value: unknown): string[] =>
  Array.isArray(value) ? value.map(String) : [];

const formatScalar = (value: unknown): string => {
  if (value === undefined || value === null || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  return String(value);
};

/** Turns a flat list of file paths (as returned by the revision-files endpoint) into the same
 * tree shape SkillFileTree already renders for the live skill file browser. */
const buildFileTree = (paths: string[]): SkillFileNode[] => {
  const root: SkillFileNode[] = [];
  paths
    .slice()
    .sort()
    .forEach((fullPath) => {
      const parts = fullPath.split('/').filter(Boolean);
      let cursor = root;
      let acc = '';
      parts.forEach((part, i) => {
        acc = acc ? `${acc}/${part}` : part;
        const isLeaf = i === parts.length - 1;
        let node = cursor.find((n) => n.name === part);
        if (!node) {
          node = { path: isLeaf ? fullPath : acc, name: part, type: isLeaf ? 'file' : 'folder', children: [] };
          cursor.push(node);
        }
        cursor = node.children;
      });
    });
  return root;
};

const CharCount: React.FC<{ value: string }> = ({ value }) => (
  <span className="text-[11px] tabular-nums text-muted-foreground">
    {value.length.toLocaleString()} chars
  </span>
);

const PromptBlock: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div>
    <div className="mb-1 flex items-center justify-between">
      <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
        {label}
      </span>
      <CharCount value={value} />
    </div>
    <div className="max-h-72 overflow-auto rounded border border-border bg-muted/30 p-3 text-sm whitespace-pre-wrap">
      {value || <span className="text-muted-foreground">—</span>}
    </div>
  </div>
);

const ChipList: React.FC<{ items: string[] }> = ({ items }) => (
  <div className="flex flex-wrap gap-1.5">
    {items.map((item) => (
      <span
        key={item}
        className="rounded-full border border-border bg-muted/40 px-2.5 py-0.5 text-xs text-foreground"
      >
        {item}
      </span>
    ))}
  </div>
);

const AssistantSnapshotView: React.FC<{ snapshot: Record<string, unknown> }> = ({ snapshot }) => {
  const [catalog, setCatalog] = useState<BuiltinToolDto[]>([]);

  useEffect(() => {
    let cancelled = false;
    assistantsApi
      .builtinTools()
      .then((list) => {
        if (!cancelled) setCatalog(list);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const name = asString(snapshot.name);
  const systemPrompt = asString(snapshot.systemPrompt);
  const tools = asStringList(snapshot.builtinTools);
  const skills = asStringList(snapshot.platformSkills);

  const labelFor = (key: string): string => {
    const found = catalog.find((t) => t.key === key);
    if (!found) return key.replace(/_/g, ' ');
    const match = found.label.match(/^(.+?)\s*\((.+)\)\s*$/);
    return match ? match[1].trim() : found.label;
  };

  return (
    <div className="space-y-4">
      <div>
        <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Name
        </div>
        <div className="rounded border border-border bg-muted/30 px-3 py-2 text-sm font-medium">
          {name || '—'}
        </div>
      </div>

      <div>
        <div className="mb-1 flex items-center justify-between">
          <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Built-in tools
          </span>
          <span className="text-[11px] text-muted-foreground">{tools.length} enabled</span>
        </div>
        {tools.length === 0 ? (
          <div className="text-sm text-muted-foreground">No built-in tools enabled.</div>
        ) : (
          <div className={toolStyles.toolGrid}>
            {tools.map((key) => (
              <div key={key} className={toolStyles.toolGridCell}>
                <div className={toolStyles.toolCard}>
                  <span className={toolStyles.toolCardBody}>
                    <span className={toolStyles.toolCardTitle}>{labelFor(key)}</span>
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {skills.length > 0 && (
        <div>
          <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Platform skills
          </div>
          <ChipList items={skills} />
        </div>
      )}

      <PromptBlock label="System prompt" value={systemPrompt} />
    </div>
  );
};

const ResponseStyleSnapshotView: React.FC<{ snapshot: Record<string, unknown> }> = ({ snapshot }) => {
  const name = asString(snapshot.name);
  const description = asString(snapshot.description);
  const instructions = asString(snapshot.instructions);
  const isDefault = snapshot.defaultStyle === true;

  return (
    <div className="space-y-4">
      <div>
        <div className="mb-1 flex items-center gap-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Name
          {isDefault && (
            <CustomTag tone="success" className="normal-case">
              Default
            </CustomTag>
          )}
        </div>
        <div className="rounded border border-border bg-muted/30 px-3 py-2 text-sm font-medium">
          {name || '—'}
        </div>
      </div>
      {description && (
        <div>
          <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Description
          </div>
          <div className="text-sm text-foreground/80">{description}</div>
        </div>
      )}
      <PromptBlock label="Instructions" value={instructions} />
    </div>
  );
};

const SkillSnapshotView: React.FC<{ resourceId: string; version: number; snapshot: Record<string, unknown> }> = ({
  resourceId,
  version,
  snapshot,
}) => {
  const name = asString(snapshot.name);
  const description = asString(snapshot.description);
  const enabled = snapshot.enabled === true;

  const [files, setFiles] = useState<RevisionFileDto[] | null>(null);
  const [loadingFiles, setLoadingFiles] = useState(false);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    setLoadingFiles(true);
    setFiles(null);
    setSelectedPath(null);
    configAuditApi
      .getRevisionFiles('skill', resourceId, version)
      .then((list) => {
        if (cancelled) return;
        setFiles(list);
        const skillMd = list.find((f) => f.path.toUpperCase() === 'SKILL.MD') || list[0];
        if (skillMd) {
          setSelectedPath(skillMd.path);
          setExpanded(new Set(folderPathsForFile(skillMd.path)));
        }
      })
      .catch(() => {
        if (!cancelled) setFiles([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingFiles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [resourceId, version]);

  const tree = useMemo(() => buildFileTree((files || []).map((f) => f.path)), [files]);
  const selectedFile = (files || []).find((f) => f.path === selectedPath) || null;

  const toggleFolder = (folderPath: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(folderPath)) next.delete(folderPath);
      else next.add(folderPath);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <div className="mb-1 flex items-center gap-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Name
          <CustomTag tone={enabled ? 'success' : 'neutral'} className="normal-case">
            {enabled ? 'Enabled' : 'Disabled'}
          </CustomTag>
        </div>
        <div className="rounded border border-border bg-muted/30 px-3 py-2 text-sm font-medium">
          {name || '—'}
        </div>
      </div>
      {description && (
        <div>
          <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Description
          </div>
          <div className="text-sm text-foreground/80">{description}</div>
        </div>
      )}

      <div>
        <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Files (v{version})
        </div>
        {loadingFiles && <div className="text-sm text-muted-foreground">Loading files…</div>}
        {!loadingFiles && (files || []).length === 0 && (
          <div className="text-sm text-muted-foreground">No file archive attached to this revision.</div>
        )}
        {!loadingFiles && (files || []).length > 0 && (
          <div className="flex gap-3">
            <div className="w-56 shrink-0 overflow-y-auto rounded border border-border p-1" style={{ maxHeight: 320 }}>
              <SkillFileTree
                nodes={tree}
                selectedPath={selectedPath}
                expanded={expanded}
                onToggleFolder={toggleFolder}
                onSelectFile={setSelectedPath}
              />
            </div>
            <div className="min-w-0 flex-1">
              {selectedFile?.binary ? (
                <div className="text-sm text-muted-foreground">Binary file — no preview.</div>
              ) : (
                <pre className="max-h-80 overflow-auto rounded border border-border bg-muted/30 p-3 font-mono text-xs whitespace-pre-wrap">
                  {selectedFile?.content || ''}
                </pre>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

const RevisionSnapshotView: React.FC<{
  resourceType: VersionedResourceType;
  resourceId: string;
  version: number;
  snapshot: Record<string, unknown>;
}> = ({ resourceType, resourceId, version, snapshot }) => {
  switch (resourceType) {
    case 'assistant':
      return <AssistantSnapshotView snapshot={snapshot} />;
    case 'response_style':
      return <ResponseStyleSnapshotView snapshot={snapshot} />;
    case 'skill':
      return <SkillSnapshotView resourceId={resourceId} version={version} snapshot={snapshot} />;
    default:
      return (
        <pre className="max-h-[60vh] overflow-auto rounded border border-border bg-muted/30 p-3 font-mono text-xs whitespace-pre-wrap">
          {JSON.stringify(snapshot, null, 2)}
        </pre>
      );
  }
};

const TextFieldDiff: React.FC<{ label: string; before: string; after: string }> = ({
  label,
  before,
  after,
}) => {
  if ((before || '') === (after || '')) return null;
  const parts = diffLines(before || '', after || '');
  const added = parts.filter((p) => p.added).length;
  const removed = parts.filter((p) => p.removed).length;
  return (
    <div className="mb-4">
      <div className="mb-1 flex items-center justify-between">
        <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {label}
        </span>
        <span className="flex items-center gap-2 text-[11px]">
          {removed > 0 && <span className="text-red-700 dark:text-red-400">−{removed} removed</span>}
          {added > 0 && <span className="text-green-700 dark:text-green-400">+{added} added</span>}
        </span>
      </div>
      <div className="max-h-72 overflow-auto rounded border border-border bg-muted/30 p-2 font-mono text-xs leading-relaxed">
        {parts.map((part, i) => (
          <div
            key={i}
            className={`whitespace-pre-wrap ${
              part.added
                ? 'bg-green-100 text-green-800 dark:bg-green-500/15 dark:text-green-300'
                : part.removed
                  ? 'bg-red-100 text-red-800 line-through dark:bg-red-500/15 dark:text-red-300'
                  : 'text-foreground/80'
            }`}
          >
            {part.value}
          </div>
        ))}
      </div>
    </div>
  );
};

const ScalarFieldDiff: React.FC<{ label: string; before: unknown; after: unknown }> = ({
  label,
  before,
  after,
}) => {
  if (JSON.stringify(before ?? null) === JSON.stringify(after ?? null)) return null;
  return (
    <div className="mb-3 text-sm">
      <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
        {label}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-800 line-through dark:bg-red-500/15 dark:text-red-300">
          {formatScalar(before)}
        </span>
        <span className="text-muted-foreground">→</span>
        <span className="rounded bg-green-100 px-1.5 py-0.5 text-xs text-green-800 dark:bg-green-500/15 dark:text-green-300">
          {formatScalar(after)}
        </span>
      </div>
    </div>
  );
};

const ArrayFieldDiff: React.FC<{ label: string; before: unknown; after: unknown }> = ({
  label,
  before,
  after,
}) => {
  const b = Array.isArray(before) ? before.map(String) : [];
  const a = Array.isArray(after) ? after.map(String) : [];
  const added = a.filter((x) => !b.includes(x));
  const removed = b.filter((x) => !a.includes(x));
  if (added.length === 0 && removed.length === 0) return null;
  return (
    <div className="mb-3 text-sm">
      <div className="mb-1 flex items-center justify-between">
        <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {label}
        </span>
        <span className="flex items-center gap-2 text-[11px]">
          {removed.length > 0 && (
            <span className="text-red-700 dark:text-red-400">−{removed.length} removed</span>
          )}
          {added.length > 0 && (
            <span className="text-green-700 dark:text-green-400">+{added.length} added</span>
          )}
        </span>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {removed.map((x) => (
          <span
            key={`r-${x}`}
            className="rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-800 line-through dark:bg-red-500/15 dark:text-red-300"
          >
            {x}
          </span>
        ))}
        {added.map((x) => (
          <span
            key={`a-${x}`}
            className="rounded bg-green-100 px-1.5 py-0.5 text-xs text-green-800 dark:bg-green-500/15 dark:text-green-300"
          >
            {x}
          </span>
        ))}
      </div>
    </div>
  );
};

const MetaDiff: React.FC<{
  resourceType: VersionedResourceType;
  prevSnapshot: Record<string, unknown> | null;
  currSnapshot: Record<string, unknown>;
}> = ({ resourceType, prevSnapshot, currSnapshot }) => {
  const prev = prevSnapshot || {};
  const textFields = TEXT_FIELDS[resourceType];
  const scalarFields = SCALAR_FIELDS[resourceType];
  const boolFields = BOOL_FIELDS[resourceType];
  const arrayFields = ARRAY_FIELDS[resourceType];

  const rendered = [
    ...scalarFields.map((f) => (
      <ScalarFieldDiff key={f} label={FIELD_LABELS[f] || f} before={prev[f]} after={currSnapshot[f]} />
    )),
    ...boolFields.map((f) => (
      <ScalarFieldDiff key={f} label={FIELD_LABELS[f] || f} before={prev[f]} after={currSnapshot[f]} />
    )),
    ...arrayFields.map((f) => (
      <ArrayFieldDiff key={f} label={FIELD_LABELS[f] || f} before={prev[f]} after={currSnapshot[f]} />
    )),
    ...textFields.map((f) => (
      <TextFieldDiff
        key={f}
        label={FIELD_LABELS[f] || f}
        before={typeof prev[f] === 'string' ? (prev[f] as string) : ''}
        after={typeof currSnapshot[f] === 'string' ? (currSnapshot[f] as string) : ''}
      />
    )),
  ];

  return <>{rendered}</>;
};

const FileDiffPane: React.FC<{
  currFiles: RevisionFileDto[];
  prevFiles: RevisionFileDto[] | null;
}> = ({ currFiles, prevFiles }) => {
  const allPaths = useMemo(() => {
    const set = new Set<string>();
    currFiles.forEach((f) => set.add(f.path));
    (prevFiles || []).forEach((f) => set.add(f.path));
    return Array.from(set).sort();
  }, [currFiles, prevFiles]);

  const tree = useMemo(() => buildFileTree(allPaths), [allPaths]);

  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => {
    // Auto-select the first file that actually changed between the two versions, so opening
    // the Changes tab immediately shows a meaningful diff rather than an unrelated file.
    const changed = allPaths.find((p) => {
      const curr = currFiles.find((f) => f.path === p);
      const prev = (prevFiles || []).find((f) => f.path === p);
      if (!curr || !prev) return true;
      if (curr.binary || prev.binary) return false;
      return (curr.content || '') !== (prev.content || '');
    });
    const initial = changed || allPaths[0] || null;
    setSelectedPath(initial);
    setExpanded(initial ? new Set(folderPathsForFile(initial)) : new Set());
  }, [allPaths, currFiles, prevFiles]);

  const toggleFolder = (folderPath: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(folderPath)) next.delete(folderPath);
      else next.add(folderPath);
      return next;
    });
  };

  if (allPaths.length === 0) {
    return (
      <div className="text-sm text-muted-foreground">
        No file archive attached to one or both revisions being compared.
      </div>
    );
  }

  const curr = currFiles.find((f) => f.path === selectedPath) || null;
  const prevList = prevFiles || [];
  const prev = prevList.find((f) => f.path === selectedPath) || null;

  let body: React.ReactNode;
  if (!curr && prev) {
    body = prev.binary ? (
      <div className="text-sm text-muted-foreground">Binary file — removed.</div>
    ) : (
      <TextFieldDiff label="File removed" before={prev.content || ''} after="" />
    );
  } else if (curr && !prev) {
    body = curr.binary ? (
      <div className="text-sm text-muted-foreground">Binary file — added.</div>
    ) : (
      <TextFieldDiff label="File added" before="" after={curr.content || ''} />
    );
  } else if (curr && prev) {
    body = curr.binary || prev.binary ? (
      <div className="text-sm text-muted-foreground">Binary file — cannot diff.</div>
    ) : (
      <TextFieldDiff label={selectedPath || ''} before={prev.content || ''} after={curr.content || ''} />
    );
  } else {
    body = null;
  }

  return (
    <div className="flex gap-3">
      <div className="w-56 shrink-0 overflow-y-auto rounded border border-border p-1" style={{ maxHeight: 320 }}>
        <SkillFileTree
          nodes={tree}
          selectedPath={selectedPath}
          expanded={expanded}
          onToggleFolder={toggleFolder}
          onSelectFile={setSelectedPath}
        />
      </div>
      <div className="min-w-0 flex-1">{body}</div>
    </div>
  );
};

const RevisionHistory: React.FC<RevisionHistoryProps> = ({
  resourceType,
  resourceId,
  resourceLabel,
  onReverted,
  showBadge = true,
}) => {
  const notify = useNotification();
  const admin = isAdmin();

  const [canView, setCanView] = useState(admin);
  const [open, setOpen] = useState(false);

  const [revisions, setRevisions] = useState<RevisionSummaryDto[]>([]);
  const [loadingRevisions, setLoadingRevisions] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);

  const [currRevision, setCurrRevision] = useState<RevisionDto | null>(null);
  const [prevRevision, setPrevRevision] = useState<RevisionDto | null>(null);
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [showRaw, setShowRaw] = useState(false);

  const [currFiles, setCurrFiles] = useState<RevisionFileDto[] | null>(null);
  const [prevFiles, setPrevFiles] = useState<RevisionFileDto[] | null>(null);
  const [loadingFiles, setLoadingFiles] = useState(false);

  const [reverting, setReverting] = useState(false);

  // Determine read access: admins always; others only if the admin-configured
  // toggle is on. Mirrors AnalyticsPanel.tsx's synchronous role gating, plus one
  // cheap settings fetch for non-admins (the endpoint has no role gate).
  useEffect(() => {
    if (admin) {
      setCanView(true);
      return;
    }
    let cancelled = false;
    configAuditApi
      .getSettings()
      .then((s) => {
        if (!cancelled) setCanView(s.nonAdminReadEnabled);
      })
      .catch(() => {
        if (!cancelled) setCanView(false);
      });
    return () => {
      cancelled = true;
    };
  }, [admin]);

  // Just the top version, for the small "v{n}" badge — cheap enough to always fetch
  // once access is confirmed, without waiting for the modal to open.
  const [topVersion, setTopVersion] = useState<number | null>(null);
  useEffect(() => {
    if (!canView || !resourceId) {
      setTopVersion(null);
      return;
    }
    let cancelled = false;
    configAuditApi
      .listRevisions(resourceType, resourceId)
      .then((list) => {
        if (cancelled) return;
        const sorted = [...list].sort((a, b) => b.version - a.version);
        setTopVersion(sorted[0]?.version ?? null);
      })
      .catch(() => {
        if (!cancelled) setTopVersion(null);
      });
    return () => {
      cancelled = true;
    };
  }, [canView, resourceType, resourceId]);

  const loadRevisions = async () => {
    setLoadingRevisions(true);
    try {
      const list = await configAuditApi.listRevisions(resourceType, resourceId);
      const sorted = [...list].sort((a, b) => b.version - a.version);
      setRevisions(sorted);
      setTopVersion(sorted[0]?.version ?? null);
      if (sorted.length > 0) {
        setSelectedVersion((current) =>
          current !== null && sorted.some((r) => r.version === current)
            ? current
            : sorted[0].version
        );
      } else {
        setSelectedVersion(null);
      }
    } catch (e) {
      notify((e as Error)?.message || 'Failed to load revision history', 'Error');
      setRevisions([]);
      setSelectedVersion(null);
    } finally {
      setLoadingRevisions(false);
    }
  };

  const openModal = () => {
    setOpen(true);
    setShowRaw(false);
    void loadRevisions();
  };

  // Fetch the selected + immediately-previous revision whenever the selection changes.
  useEffect(() => {
    if (!open || selectedVersion === null) {
      setCurrRevision(null);
      setPrevRevision(null);
      return;
    }
    let cancelled = false;
    setLoadingDiff(true);
    const hasPrev = revisions.some((r) => r.version === selectedVersion - 1);
    Promise.all([
      configAuditApi.getRevision(resourceType, resourceId, selectedVersion),
      hasPrev
        ? configAuditApi.getRevision(resourceType, resourceId, selectedVersion - 1)
        : Promise.resolve(null),
    ])
      .then(([curr, prev]) => {
        if (cancelled) return;
        setCurrRevision(curr);
        setPrevRevision(prev);
        // No earlier revision to diff against (e.g. this is v1) — a "changes" view against
        // nothing isn't meaningful, so default straight to the full snapshot instead.
        setShowRaw(!prev);
      })
      .catch((e) => {
        if (cancelled) return;
        notify((e as Error)?.message || 'Failed to load revision detail', 'Error');
        setCurrRevision(null);
        setPrevRevision(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingDiff(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, selectedVersion, resourceType, resourceId]);

  // Skill file-content diff: only when both compared revisions archived a file bundle.
  useEffect(() => {
    if (resourceType !== 'skill' || !currRevision) {
      setCurrFiles(null);
      setPrevFiles(null);
      return;
    }
    const currSummary = revisions.find((r) => r.version === currRevision.version);
    const prevSummary = prevRevision
      ? revisions.find((r) => r.version === prevRevision.version)
      : null;
    if (!currSummary?.hasContent) {
      setCurrFiles(null);
      setPrevFiles(null);
      return;
    }
    let cancelled = false;
    setLoadingFiles(true);
    Promise.all([
      configAuditApi.getRevisionFiles('skill', resourceId, currRevision.version),
      prevRevision && prevSummary?.hasContent
        ? configAuditApi.getRevisionFiles('skill', resourceId, prevRevision.version)
        : Promise.resolve(null),
    ])
      .then(([cur, prev]) => {
        if (cancelled) return;
        setCurrFiles(cur);
        setPrevFiles(prev);
      })
      .catch(() => {
        if (!cancelled) {
          setCurrFiles([]);
          setPrevFiles(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingFiles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [resourceType, resourceId, currRevision, prevRevision, revisions]);

  const doRevert = (row: RevisionSummaryDto) => {
    confirm({
      title: `Revert to v${row.version}?`,
      body: `This restores ${resourceLabel || resourceType} to its v${row.version} snapshot${
        resourceType === 'skill' ? ' (including file contents)' : ''
      }. A new revision recording this revert will be created, and the change is not undoable except by reverting again.`,
      danger: true,
      okText: 'Revert',
      onOk: async () => {
        setReverting(true);
        try {
          await configAuditApi.revert(resourceType, resourceId, row.version);
          notify(`Reverted to v${row.version}`, 'Success');
          await loadRevisions();
          onReverted?.();
        } catch (e) {
          notify((e as Error)?.message || 'Failed to revert', 'Error');
        } finally {
          setReverting(false);
        }
      },
    });
  };

  if (!canView) return null;

  const currSnapshot = currRevision ? asRecord(currRevision.snapshot) : null;
  const prevSnapshot = prevRevision ? asRecord(prevRevision.snapshot) : null;

  return (
    <>
      <span className="inline-flex items-center gap-2">
        {showBadge && topVersion !== null && (
          <CustomTag tone="neutral">v{topVersion}</CustomTag>
        )}
        <CustomTooltip title="View revision history">
          <CustomButton
            variant="ghost"
            size="small"
            onClick={openModal}
            aria-label="Revision history"
          >
            <CustomIcon name="history" size={15} />
            History
          </CustomButton>
        </CustomTooltip>
      </span>

      <CustomModal
        open={open}
        onClose={() => setOpen(false)}
        width="wide"
        title={`Revision history${resourceLabel ? ` — ${resourceLabel}` : ''}`}
      >
        <div className="flex gap-4" style={{ minHeight: 420 }}>
          <aside className="w-64 shrink-0 overflow-y-auto border-r border-border pr-3">
            {loadingRevisions && (
              <div className="text-sm text-muted-foreground">Loading…</div>
            )}
            {!loadingRevisions && revisions.length === 0 && (
              <div className="text-sm text-muted-foreground">No revision history yet.</div>
            )}
            <ul className="space-y-1">
              {revisions.map((r, idx) => {
                const isCurrent = idx === 0;
                const isSelected = r.version === selectedVersion;
                return (
                  <li key={r.version}>
                    <button
                      type="button"
                      onClick={() => setSelectedVersion(r.version)}
                      className={`w-full rounded border px-2 py-1.5 text-left text-xs ${
                        isSelected
                          ? 'border-primary bg-primary/10'
                          : 'border-transparent hover:bg-accent hover:text-accent-foreground'
                      }`}
                    >
                      <div className="mb-0.5 flex items-center gap-1.5">
                        <span className="font-semibold text-foreground">v{r.version}</span>
                        <CustomTag tone={actionTone(r.action)} className="px-1 py-0 text-[10px]">
                          {humanizeAction(r.action)}
                        </CustomTag>
                        {isCurrent && (
                          <CustomTag tone="neutral" className="px-1 py-0 text-[10px]">
                            Current
                          </CustomTag>
                        )}
                      </div>
                      <div className="text-[11px] text-muted-foreground">
                        {r.actor} · {formatDateTime(r.createdAt)}
                      </div>
                      {r.summary && (
                        <div className="mt-0.5 truncate text-[11px] text-muted-foreground">
                          {r.summary}
                        </div>
                      )}
                    </button>
                    {admin && !isCurrent && (
                      <div className="mt-1 pl-2">
                        <CustomButton
                          variant="text"
                          size="small"
                          disabled={reverting}
                          onClick={() => doRevert(r)}
                        >
                          <CustomIcon name="undo" size={12} />
                          Revert to this
                        </CustomButton>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          </aside>

          <section className="min-w-0 flex-1 overflow-y-auto">
            {loadingDiff && <div className="text-sm text-muted-foreground">Loading diff…</div>}
            {!loadingDiff && currRevision && currSnapshot && (
              <>
                <div className="mb-3 flex items-center justify-between">
                  <div className="text-xs text-muted-foreground">
                    {!prevRevision ? (
                      `v${currRevision.version} — initial version (no earlier revision to diff against)`
                    ) : showRaw ? (
                      `v${currRevision.version} details`
                    ) : (
                      <span className="flex items-center gap-3">
                        <span>
                          Changes from v{prevRevision.version} → v{currRevision.version}
                        </span>
                        <span className="flex items-center gap-2 text-[10px]">
                          <span className="flex items-center gap-1">
                            <span className="inline-block h-2 w-2 rounded-sm bg-red-400/70" />
                            Removed
                          </span>
                          <span className="flex items-center gap-1">
                            <span className="inline-block h-2 w-2 rounded-sm bg-green-500/70" />
                            Added
                          </span>
                        </span>
                      </span>
                    )}
                  </div>
                  {prevRevision && (
                    <CustomButton
                      variant="ghost"
                      size="small"
                      onClick={() => setShowRaw((v) => !v)}
                    >
                      {showRaw ? 'Show diff' : 'Full details'}
                    </CustomButton>
                  )}
                </div>

                {showRaw ? (
                  <RevisionSnapshotView
                    resourceType={resourceType}
                    resourceId={resourceId}
                    version={currRevision.version}
                    snapshot={currSnapshot}
                  />
                ) : (
                  <>
                    <MetaDiff
                      resourceType={resourceType}
                      prevSnapshot={prevSnapshot}
                      currSnapshot={currSnapshot}
                    />
                    {resourceType === 'skill' && (
                      <div className="mt-4">
                        <div className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                          Files
                        </div>
                        {loadingFiles && (
                          <div className="text-sm text-muted-foreground">Loading files…</div>
                        )}
                        {!loadingFiles && currFiles && (
                          <FileDiffPane currFiles={currFiles} prevFiles={prevFiles} />
                        )}
                        {!loadingFiles && !currFiles && (
                          <div className="text-sm text-muted-foreground">
                            No file archive attached to this revision.
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}
              </>
            )}
            {!loadingDiff && !currRevision && revisions.length > 0 && (
              <div className="text-sm text-muted-foreground">Select a revision to view its diff.</div>
            )}
          </section>
        </div>
      </CustomModal>
    </>
  );
};

export default RevisionHistory;
