import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ArrowLeft, FileText, Info, Search, Users, Waypoints } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { analyticsApi, NetworkGraph, OffenderGroup } from '@apiCalls/analytics';
import CrimeNetwork, { SelectedNetworkNode } from '@src/uiTemplates/CrimeNetwork';
import CustomInput from '@atoms/CustomInput';
import CustomButton from '@atoms/CustomButton';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@constants/translations';
import * as styles from '@styles/analyticsLayout.module.scss';

const TYPE_HELP: Record<string, string> = {
  accused: 'Person named as accused.',
  case: 'An FIR record.',
  victim: 'Victim linked to a case.',
  station: 'Police station for the case.',
};

const nodeUid = (id: string) => id.replace(/^p:/, '');

const CrimeNetworkView: React.FC = () => {
  const t = useT();
  const [params, setParams] = useSearchParams();
  const personUid = params.get('personUid') ?? '';
  const [graph, setGraph] = useState<NetworkGraph>({ nodes: [], links: [] });
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<SelectedNetworkNode | null>(null);
  const [query, setQuery] = useState(personUid);
  const [groups, setGroups] = useState<OffenderGroup[]>([]);
  const [previewGroup, setPreviewGroup] = useState<number | null>(null);
  const [showTip, setShowTip] = useState(false);

  useEffect(() => {
    setQuery(personUid);
  }, [personUid]);

  useEffect(() => {
    if (personUid) {
      setGroups([]);
      setPreviewGroup(null);
      return;
    }
    analyticsApi
      .offenderGroups(2, 12)
      .then((r) => {
        setGroups(r.groups);
        // Spotlight the top group so highlights are never “empty”.
        setPreviewGroup(r.groups.length ? 0 : null);
      })
      .catch(() => setGroups([]));
  }, [personUid]);

  useEffect(() => {
    setLoading(true);
    setSelected(null);
    // Pass the same group cap the sidebar uses so overview edges include them.
    analyticsApi
      .network(personUid || undefined, personUid ? undefined : 12)
      .then(setGraph)
      .catch(() => setGraph({ nodes: [], links: [] }))
      .finally(() => setLoading(false));
  }, [personUid]);

  const stats = useMemo(() => {
    const people = graph.nodes.filter((n) => n.type === 'accused').length;
    const cases = graph.nodes.filter((n) => n.type === 'case').length;
    return { people, cases, links: graph.links.length };
  }, [graph]);

  const selectedUid = selected?.id?.startsWith('p:')
    ? selected.id.slice(2)
    : selected?.type === 'accused'
      ? selected.id
      : null;

  const explore = (v: string) => {
    const next = new URLSearchParams(params);
    next.set('analytics', 'network');
    if (v.trim()) next.set('personUid', v.trim());
    else next.delete('personUid');
    setParams(next, { replace: true });
  };

  const goBack = () => explore('');

  const preview = previewGroup != null ? groups[previewGroup] : null;

  /** Graph node ids use `p:{uid}` — include both forms for reliable highlighting. */
  const withPersonPrefixes = (uids: string[]) => {
    const out = new Set<string>();
    uids.filter(Boolean).forEach((u) => {
      out.add(u);
      out.add(u.startsWith('p:') ? u.slice(2) : `p:${u}`);
    });
    return [...out];
  };

  const resolveGroupNodeIds = (g: OffenderGroup): string[] => {
    const uidKeys = new Set(
      withPersonPrefixes([
        g.ringleaderUid,
        ...g.members.map((m) => m.personUid),
      ]),
    );
    const names = new Set(
      [g.ringleaderName, ...g.members.map((m) => m.name)]
        .filter(Boolean)
        .map((n) => String(n).trim().toLowerCase()),
    );
    const matched: string[] = [];
    graph.nodes.forEach((n) => {
      if (n.type !== 'accused') return;
      const id = String(n.id ?? '');
      const bare = id.startsWith('p:') ? id.slice(2) : id;
      const name = String(n.name ?? '')
        .trim()
        .toLowerCase();
      if (uidKeys.has(id) || uidKeys.has(bare) || (name && names.has(name))) {
        matched.push(id);
      }
    });
    return matched.length ? matched : [...uidKeys];
  };

  // Resolve to real graph node ids (uid + name fallback) so spotlight always hits.
  const highlightIds = useMemo(() => {
    if (personUid || !preview) return undefined;
    return resolveGroupNodeIds(preview);
  }, [preview, personUid, graph.nodes]);

  const groupOnMap = (g: OffenderGroup) => resolveGroupNodeIds(g).some((id) =>
    graph.nodes.some((n) => String(n.id) === id),
  );

  const focusedNode = useMemo(() => {
    if (!personUid) return null;
    return (
      graph.nodes.find((n) => {
        const id = String(n.id ?? '');
        return (
          id === personUid ||
          id === `p:${personUid}` ||
          id.endsWith(personUid)
        );
      }) ?? null
    );
  }, [graph.nodes, personUid]);

  const focusedName = focusedNode?.name
    ? String(focusedNode.name)
    : personUid || null;

  /** Co-accused people (excluding the focused person), sorted by link count. */
  const coAccused = useMemo(() => {
    if (!personUid) return [];
    const focusKeys = new Set([personUid, `p:${personUid}`]);
    const nodeIds = new Set(graph.nodes.map((n) => String(n.id)));
    // Only count edges that can actually be drawn (both ends exist as nodes).
    const deg = new Map<string, number>();
    graph.links.forEach((l) => {
      const s = String(l.source);
      const t = String(l.target);
      if (!nodeIds.has(s) || !nodeIds.has(t) || s === t) return;
      deg.set(s, (deg.get(s) ?? 0) + 1);
      deg.set(t, (deg.get(t) ?? 0) + 1);
    });
    return graph.nodes
      .filter((n) => n.type === 'accused')
      .filter((n) => !focusKeys.has(String(n.id)) && !focusKeys.has(nodeUid(String(n.id))))
      .map((n) => ({
        id: String(n.id),
        uid: nodeUid(String(n.id)),
        name: String(n.name ?? n.id),
        links: deg.get(String(n.id)) ?? 0,
      }))
      .filter((p) => p.links > 0)
      .sort((a, b) => b.links - a.links);
  }, [graph, personUid]);

  const firNodes = useMemo(
    () =>
      graph.nodes
        .filter((n) => n.type === 'case')
        .map((n) => ({
          id: String(n.id),
          name: String(n.name ?? n.id),
        })),
    [graph.nodes],
  );

  return (
    <>
      {!personUid && (
        <div className={styles.networkHint}>
          <Info className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden />
          <div className={styles.networkHintMain}>
            <p>
              <strong>Co-accused map</strong> — people linked by shared FIRs.
              Hover a group on the right to zoom to it, then <strong>Open</strong>.
            </p>
            {showTip && (
              <p className={styles.networkHintExtra}>
                Each card is an organized group. Gold ring on the map = that
                group. Search an Offender ID to open one person&apos;s case web.
              </p>
            )}
          </div>
          <button
            type="button"
            className={styles.networkHintMore}
            onClick={() => setShowTip((v) => !v)}
            aria-expanded={showTip}
          >
            {showTip ? 'Less' : 'More'}
          </button>
        </div>
      )}

      {/* Single back + context when a person is open */}
      {personUid && (
        <div className={styles.networkBackBar}>
          <CustomButton
            variant="secondary"
            size="small"
            onClick={goBack}
            icon={<ArrowLeft className="size-4" />}
          >
            Back to groups
          </CustomButton>
          <div className={styles.networkBackMeta}>
            <span className={styles.networkBackLabel}>Case web for</span>
            <span className={styles.networkBackName}>
              {focusedName ?? personUid}
            </span>
            <code className={styles.networkCode}>{personUid}</code>
          </div>
          <form
            className={`${styles.toolbarSearch} flex items-center gap-2`}
            onSubmit={(e) => {
              e.preventDefault();
              explore(query);
            }}
          >
            <CustomInput
              placeholder="Another Offender ID…"
              value={query}
              allowClear
              size="small"
              onChange={(e) => setQuery(e.target.value)}
              prefix={<Search className="size-3.5 text-muted-foreground" />}
            />
            <CustomButton variant="primary" size="small" htmlType="submit">
              Go
            </CustomButton>
          </form>
        </div>
      )}

      {!personUid && (
        <div className={styles.toolbar}>
          <div className={styles.toolbarLead}>
            <span className={styles.toolbarMeta}>
              {loading && !graph.nodes.length
                ? 'Loading…'
                : `${stats.people} people · ${stats.links} links`}
            </span>
          </div>
          <form
            className={`${styles.toolbarSearch} flex items-center gap-2`}
            onSubmit={(e) => {
              e.preventDefault();
              explore(query);
            }}
          >
            <CustomInput
              placeholder="Offender ID (e.g. P000123)"
              value={query}
              allowClear
              onChange={(e) => setQuery(e.target.value)}
              prefix={<Search className="size-3.5 text-muted-foreground" />}
            />
            <CustomButton variant="primary" htmlType="submit">
              {t('explore')}
            </CustomButton>
          </form>
        </div>
      )}

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        className={styles.networkShell}
      >
        <div className={styles.networkLayout}>
          <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden py-0">
            <div className="flex flex-shrink-0 items-center gap-2 border-b border-border px-4 py-2.5">
              <Waypoints className="size-4 text-primary" aria-hidden />
              <div className="min-w-0">
                <div className="text-sm font-semibold text-foreground">
                  {personUid
                    ? `${focusedName ?? 'Person'} — case web`
                    : 'Statewide co-accused map'}
                </div>
                <div className="text-xs text-muted-foreground">
                  {personUid
                    ? 'Cards = people & FIRs · red string = links · drag to rearrange'
                    : 'Hover a group on the right to focus its board'}
                </div>
              </div>
              <span className="ml-auto text-xs font-medium text-muted-foreground tabular-nums">
                {loading && !graph.nodes.length
                  ? 'Loading…'
                  : personUid
                    ? `${stats.cases} FIRs · ${stats.people} people`
                    : `${stats.people} people`}
              </span>
            </div>
            {loading && !graph.nodes.length ? (
              <div className="flex-1 p-4">
                <Skeleton className="h-full min-h-[240px] w-full rounded-lg" />
              </div>
            ) : graph.nodes.length ? (
              <div className={`${styles.vizStage} ${styles.vizBleed}`}>
                <CrimeNetwork
                  key={personUid || 'statewide'}
                  data={{ nodes: graph.nodes, links: graph.links }}
                  fillHeight
                  onSelectNode={setSelected}
                  highlightIds={highlightIds}
                  focusNodeId={personUid || null}
                  fitTight={!!personUid}
                  personMode={!!personUid}
                />
                {selected && (
                  <div className={styles.floatCard}>
                    <div className={styles.floatCardEyebrow}>
                      {TYPE_HELP[selected.type] ?? selected.type}
                    </div>
                    <div className={styles.floatCardTitle}>{selected.name}</div>
                    <div className={styles.floatCardBody}>
                      <code>{nodeUid(selected.id)}</code>
                      {selected.degree > 0 && (
                        <>
                          <br />
                          {selected.degree} connection
                          {selected.degree === 1 ? '' : 's'}
                        </>
                      )}
                    </div>
                    {selectedUid && selectedUid !== personUid && (
                      <button
                        type="button"
                        className={styles.offenderLink}
                        onClick={() => explore(selectedUid)}
                      >
                        Open their case web →
                      </button>
                    )}
                    <button
                      type="button"
                      className={styles.floatClose}
                      onClick={() => setSelected(null)}
                    >
                      {t('close')}
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
                <Users className="size-8 text-muted-foreground" aria-hidden />
                <p className="text-sm font-medium text-foreground">
                  {t('noNetworkFound')}
                </p>
                {personUid && (
                  <CustomButton
                    variant="primary"
                    size="small"
                    onClick={goBack}
                    icon={<ArrowLeft className="size-3.5" />}
                  >
                    Back to groups
                  </CustomButton>
                )}
              </div>
            )}
          </Card>

          {/* Statewide: groups list · Person: how-to-read + roster */}
          {personUid ? (
            <Card className={`${styles.networkSideCard} gap-0 py-0`}>
              <div className="border-b border-border px-4 py-2.5">
                <div className="text-sm font-semibold text-foreground">
                  How to read this
                </div>
                <p className={styles.networkReadMe}>
                  This is <strong>{focusedName}</strong>&apos;s web:{' '}
                  <strong>gold</strong> dots are FIRs they appear in;{' '}
                  <strong>red</strong> circles are other people named in those
                  same FIRs. Lines mean “appears in / linked to.”
                </p>
              </div>
              <div className={styles.networkGroupList}>
                <div className={styles.networkRosterSection}>
                  <div className={styles.networkRosterHeading}>
                    <Users className="size-3.5" aria-hidden />
                    Co-accused ({coAccused.length})
                  </div>
                  {coAccused.length === 0 ? (
                    <p className="px-1 text-xs text-muted-foreground">
                      No other accused linked in this sample.
                    </p>
                  ) : (
                    coAccused.map((p, i) => (
                      <motion.button
                        key={p.id}
                        type="button"
                        className={styles.networkRosterRow}
                        onClick={() => explore(p.uid)}
                        initial={{ opacity: 0, x: 10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{
                          delay: Math.min(i * 0.03, 0.35),
                          duration: 0.28,
                          ease: [0.16, 1, 0.3, 1],
                        }}
                      >
                        <span className="min-w-0 flex-1 truncate text-left">
                          <span className="font-semibold text-foreground">
                            {p.name}
                          </span>
                          <span className="mt-0.5 block text-[11px] text-muted-foreground">
                            {p.uid} · {p.links} links
                          </span>
                        </span>
                        <span className={styles.networkRosterAction}>Open</span>
                      </motion.button>
                    ))
                  )}
                </div>
                <div className={styles.networkRosterSection}>
                  <div className={styles.networkRosterHeading}>
                    <FileText className="size-3.5" aria-hidden />
                    FIRs in this web ({firNodes.length})
                  </div>
                  <div className={styles.networkFirChips}>
                    {firNodes.slice(0, 24).map((f, i) => (
                      <motion.span
                        key={f.id}
                        className={styles.networkFirChip}
                        title={f.name}
                        initial={{ opacity: 0, scale: 0.92 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{
                          delay: Math.min(i * 0.015, 0.3),
                          duration: 0.22,
                        }}
                      >
                        {f.name}
                      </motion.span>
                    ))}
                    {firNodes.length > 24 && (
                      <span className={styles.networkFirChip}>
                        +{firNodes.length - 24} more
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </Card>
          ) : (
            <Card className={`${styles.networkSideCard} gap-0 py-0`}>
              <div className="flex items-center gap-2 border-b border-border px-4 py-2.5">
                <Users className="size-4 text-primary" aria-hidden />
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-foreground">
                    Organized groups
                  </div>
                  <div className="text-xs text-muted-foreground">
                    Hover a card to spotlight · Open to explore
                  </div>
                </div>
              </div>
              <div className={styles.networkGroupList}>
                {loading && !groups.length ? (
                  <div className="space-y-2 p-3">
                    <Skeleton className="h-16 w-full" />
                    <Skeleton className="h-16 w-full" />
                  </div>
                ) : groups.length === 0 ? (
                  <p className="p-4 text-sm text-muted-foreground">
                    No strong groups yet. Search an Offender ID above.
                  </p>
                ) : (
                  groups.map((g, i) => {
                    const active = previewGroup === i;
                    const onMap =
                      loading || !graph.nodes.length ? true : groupOnMap(g);
                    return (
                      <motion.div
                        key={g.ringleaderUid}
                        className={`${styles.networkGroupCard} ${
                          active ? styles.networkGroupCardActive : ''
                        }`}
                        onMouseEnter={() => setPreviewGroup(i)}
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{
                          delay: Math.min(i * 0.04, 0.4),
                          duration: 0.32,
                          ease: [0.16, 1, 0.3, 1],
                        }}
                        whileHover={{ scale: 1.01 }}
                      >
                        <div className={styles.networkGroupRank}>#{i + 1}</div>
                        <div className="min-w-0 flex-1">
                          <div className="text-sm font-semibold text-foreground">
                            {g.ringleaderName}
                          </div>
                          <div className={styles.networkGroupStats}>
                            <span>{g.size} members</span>
                            <span>{g.sharedCases} cases</span>
                            <span>{(g.cohesion * 100).toFixed(0)}%</span>
                            {onMap ? (
                              <span>On map</span>
                            ) : (
                              <span>Open to load</span>
                            )}
                          </div>
                        </div>
                        <CustomButton
                          variant="primary"
                          size="small"
                          onClick={() => explore(g.ringleaderUid)}
                        >
                          Open
                        </CustomButton>
                      </motion.div>
                    );
                  })
                )}
              </div>
            </Card>
          )}
        </div>
      </motion.div>
    </>
  );
};

export default CrimeNetworkView;
