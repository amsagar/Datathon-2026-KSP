import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { TemplateComponentProps } from './types';
import * as styles from '@styles/crimeNetworkBoard.module.scss';

interface NetworkNode {
  id?: string;
  name?: string;
  type?: string;
}
interface NetworkLink {
  source?: string;
  target?: string;
  kind?: string;
  sharedCases?: number;
}
interface NetworkData {
  title?: string;
  nodes?: NetworkNode[];
  links?: NetworkLink[];
}

export interface SelectedNetworkNode {
  id: string;
  name: string;
  type: string;
  degree: number;
  sharedMax: number;
}

type CrimeNetworkProps = TemplateComponentProps & {
  fillHeight?: boolean;
  onSelectNode?: (node: SelectedNetworkNode | null) => void;
  highlightIds?: string[];
  focusNodeId?: string | null;
  fitTight?: boolean;
  personMode?: boolean;
};

type Vec = { x: number; y: number };
type Cam = { x: number; y: number; scale: number };

const TYPE_LABELS: Record<string, string> = {
  accused: 'Person (accused)',
  victim: 'Victim',
  case: 'FIR / case',
  station: 'Police station',
};

const MAX_OVERVIEW = 18;
const MAX_PERSON = 72;
const MAX_GROUP = 24;

const normalizePersonKey = (raw: string) => {
  const s = String(raw ?? '').trim();
  if (!s) return '';
  return s.startsWith('p:') ? s.slice(2) : s;
};

const idMatches = (nodeId: string, candidates: Set<string>) => {
  const nid = String(nodeId ?? '');
  if (candidates.has(nid)) return true;
  const nKey = normalizePersonKey(nid);
  if (nKey && candidates.has(nKey)) return true;
  if (nKey && candidates.has(`p:${nKey}`)) return true;
  for (const c of candidates) {
    if (!c) continue;
    const cKey = normalizePersonKey(c);
    if (!cKey) continue;
    if (nid === c || nid === `p:${cKey}` || nKey === cKey) return true;
  }
  return false;
};

const hashSeed = (s: string) => {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
};

const shortName = (name: string, max = 20) =>
  name.length > max ? `${name.slice(0, max - 1)}…` : name;

const tagFor = (type: string, degree: number, sharedMax: number) => {
  if (type === 'case') return { label: 'FIR', className: styles.tagCase };
  if (type === 'station') return { label: 'Station', className: styles.tagOther };
  if (type === 'victim') return { label: 'Victim', className: styles.tagLink };
  if (degree >= 4 || sharedMax >= 3) {
    return { label: 'Core suspect', className: styles.tagCore };
  }
  if (sharedMax >= 1 || degree >= 1) {
    return { label: 'Confirmed link', className: styles.tagLink };
  }
  return { label: 'Lead', className: styles.tagOther };
};

const roleFor = (type: string) => TYPE_LABELS[type] ?? type ?? 'Subject';

const cardH = (type?: string) => (type === 'case' ? 108 : 128);
const cardWidth = (type: string | undefined, base: number) =>
  type === 'case' ? 104 : base;

/** Pin point (top center) in board coords from stored card center. */
const pinPoint = (center: Vec, type: string | undefined, baseW: number): Vec => {
  const hh = cardH(type) / 2;
  return { x: center.x, y: center.y - hh };
};

const pathBetween = (a: Vec, b: Vec) => {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const dist = Math.hypot(dx, dy) || 1;
  const sag = Math.min(18, dist * 0.08);
  const nx = -dy / dist;
  const ny = dx / dist;
  const cx = (a.x + b.x) / 2 + nx * sag;
  const cy = (a.y + b.y) / 2 + ny * sag;
  return `M ${a.x} ${a.y} Q ${cx} ${cy} ${b.x} ${b.y}`;
};

const PersonIcon = () => (
  <svg className={styles.photoIcon} viewBox="0 0 48 48" fill="none" aria-hidden>
    <circle cx="24" cy="16" r="9" stroke="currentColor" strokeWidth="2" />
    <path
      d="M6 42c2-11 10-16 18-16s16 5 18 16"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    />
  </svg>
);

const CaseIcon = () => (
  <svg className={styles.photoIcon} viewBox="0 0 48 48" fill="none" aria-hidden>
    <rect x="10" y="8" width="28" height="34" stroke="currentColor" strokeWidth="2" />
    <path
      d="M16 16h4M16 24h4M16 32h4M28 16h4M28 24h4M28 32h4"
      stroke="currentColor"
      strokeWidth="2"
    />
  </svg>
);

const OtherIcon = () => (
  <svg className={styles.photoIcon} viewBox="0 0 48 48" fill="none" aria-hidden>
    <path
      d="M8 20c0-8 7-13 16-13s16 5 16 13c0 10-7 18-16 18S8 30 8 20Z"
      stroke="currentColor"
      strokeWidth="2"
    />
    <circle cx="18" cy="20" r="2.4" fill="currentColor" />
    <circle cx="30" cy="20" r="2.4" fill="currentColor" />
  </svg>
);

const CrimeNetwork: React.FC<CrimeNetworkProps> = ({
  data,
  fillHeight,
  onSelectNode,
  highlightIds,
  focusNodeId,
  fitTight = false,
  personMode = false,
}) => {
  const d = (data ?? {}) as NetworkData;
  const frameRef = useRef<HTMLDivElement>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const pathElsRef = useRef<(SVGPathElement | null)[]>([]);
  const cardElsRef = useRef<Map<string, HTMLDivElement>>(new Map());
  const posRef = useRef<Map<string, Vec>>(new Map());
  const typeRef = useRef<Map<string, string>>(new Map());
  const camRef = useRef<Cam>({ x: 0, y: 0, scale: 1 });
  const graphKeyRef = useRef('');
  const reduceMotionRef = useRef(false);
  const dragRef = useRef<{
    id: string;
    startX: number;
    startY: number;
    orig: Vec;
    moved: boolean;
    pointerId: number;
  } | null>(null);
  const panRef = useRef<{
    startX: number;
    startY: number;
    orig: Cam;
    moved: boolean;
    pointerId: number;
  } | null>(null);

  const [size, setSize] = useState({ width: 800, height: 480 });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [cam, setCam] = useState<Cam>({ x: 0, y: 0, scale: 1 });
  const [camInstant, setCamInstant] = useState(false);
  const [panning, setPanning] = useState(false);
  const [layoutReady, setLayoutReady] = useState(0);
  const [stringRev, setStringRev] = useState(0);

  /** Open world extent (board coords). Frame is only a camera window. */
  const WORLD_MIN = -4000;
  const WORLD_SIZE = 12000;
  const WORLD_MAX = WORLD_MIN + WORLD_SIZE;

  const highlightSet = useMemo(
    () => new Set((highlightIds ?? []).filter(Boolean).map(String)),
    [highlightIds],
  );
  const hasHighlight = highlightSet.size > 0;

  const { allNodes, allLinks, degree, sharedMax } = useMemo(() => {
    const nodesIn = (Array.isArray(d.nodes) ? d.nodes : [])
      .filter((n) => n.id != null)
      .map((n) => ({ ...n, id: String(n.id) }));
    const ids = new Set(nodesIn.map((n) => n.id));
    const linksIn = (Array.isArray(d.links) ? d.links : [])
      .filter((l) => l.source != null && l.target != null)
      .map((l) => ({
        ...l,
        source: String(l.source),
        target: String(l.target),
        sharedCases: Number((l as NetworkLink).sharedCases ?? 1),
      }))
      .filter((l) => ids.has(l.source) && ids.has(l.target) && l.source !== l.target);

    // Deduplicate undirected pairs so we don't draw double strings.
    const seen = new Set<string>();
    const unique = linksIn.filter((l) => {
      const a = l.source < l.target ? l.source : l.target;
      const b = l.source < l.target ? l.target : l.source;
      const key = `${a}||${b}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });

    const deg = new Map<string, number>();
    const sMax = new Map<string, number>();
    unique.forEach((l) => {
      deg.set(l.source, (deg.get(l.source) ?? 0) + 1);
      deg.set(l.target, (deg.get(l.target) ?? 0) + 1);
      sMax.set(l.source, Math.max(sMax.get(l.source) ?? 0, l.sharedCases));
      sMax.set(l.target, Math.max(sMax.get(l.target) ?? 0, l.sharedCases));
    });
    return {
      allNodes: nodesIn,
      allLinks: unique,
      degree: deg,
      sharedMax: sMax,
    };
  }, [d.nodes, d.links]);

  const { nodes, links } = useMemo(() => {
    const byId = new Map(allNodes.map((n) => [n.id, n]));

    /** Neighbors for each node id (from full link list). */
    const adj = new Map<string, string[]>();
    allLinks.forEach((l) => {
      if (!adj.has(l.source)) adj.set(l.source, []);
      if (!adj.has(l.target)) adj.set(l.target, []);
      adj.get(l.source)!.push(l.target);
      adj.get(l.target)!.push(l.source);
    });

    /**
     * Expand a seed set so every kept node also keeps its link partners
     * (within budget). Stops orphan cards that have "1 links" but no string.
     */
    const closeSubgraph = (seedIds: string[], budget: number) => {
      const kept = new Set<string>();
      const queue = [...seedIds.filter((id) => byId.has(id))];
      queue.forEach((id) => kept.add(id));

      // Prefer pulling in case nodes for people, then other people.
      const rank = (id: string) => {
        const t = byId.get(id)?.type;
        if (t === 'case') return 0;
        if (t === 'accused') return 1;
        return 2;
      };

      while (queue.length && kept.size < budget) {
        const id = queue.shift()!;
        const nbrs = [...(adj.get(id) ?? [])].sort(
          (a, b) => rank(a) - rank(b) || (degree.get(b) ?? 0) - (degree.get(a) ?? 0),
        );
        for (const n of nbrs) {
          if (kept.has(n) || !byId.has(n)) continue;
          if (kept.size >= budget) break;
          kept.add(n);
          queue.push(n);
        }
      }

      // Final pass: if a person is kept but none of their cases fit, force one case in
      // by swapping out the lowest-degree non-seed non-case node.
      const seedSet = new Set(seedIds);
      for (const id of [...kept]) {
        if (byId.get(id)?.type !== 'accused') continue;
        const cases = (adj.get(id) ?? []).filter(
          (n) => byId.get(n)?.type === 'case',
        );
        if (!cases.length) continue;
        if (cases.some((c) => kept.has(c))) continue;
        const add = cases.sort(
          (a, b) => (degree.get(b) ?? 0) - (degree.get(a) ?? 0),
        )[0];
        if (kept.size < budget) {
          kept.add(add);
          continue;
        }
        const victim = [...kept]
          .filter(
            (x) =>
              !seedSet.has(x) &&
              byId.get(x)?.type !== 'case' &&
              x !== id,
          )
          .sort((a, b) => (degree.get(a) ?? 0) - (degree.get(b) ?? 0))[0];
        if (victim) {
          kept.delete(victim);
          kept.add(add);
        }
      }

      return [...kept].map((id) => byId.get(id)!).filter(Boolean);
    };

    let picked = allNodes;

    if (hasHighlight && !personMode) {
      const seeds = allNodes
        .filter((n) => idMatches(n.id, highlightSet))
        .map((n) => n.id);
      picked = closeSubgraph(seeds, MAX_GROUP);
    } else if (personMode) {
      const focus =
        allNodes.find((n) =>
          focusNodeId
            ? idMatches(n.id, new Set([focusNodeId]))
            : false,
        ) ?? allNodes.find((n) => n.type === 'accused');

      if (focus) {
        // Seed focus + their FIRs + co-accused on those FIRs so strings stay intact.
        const focusCases = (adj.get(focus.id) ?? []).filter(
          (id) => byId.get(id)?.type === 'case',
        );
        const coOnFocus = new Set<string>();
        focusCases.forEach((cid) => {
          (adj.get(cid) ?? []).forEach((pid) => {
            if (byId.get(pid)?.type === 'accused' && pid !== focus.id) {
              coOnFocus.add(pid);
            }
          });
        });
        // Prefer a balanced seed: not only FIRs (which used to crowd out people).
        const caseCap = Math.min(focusCases.length, 28);
        const peopleCap = Math.min(coOnFocus.size, 32);
        const seeds = [
          focus.id,
          ...focusCases.slice(0, caseCap),
          ...[...coOnFocus]
            .sort((a, b) => (degree.get(b) ?? 0) - (degree.get(a) ?? 0))
            .slice(0, peopleCap),
        ];
        picked = closeSubgraph(seeds, MAX_PERSON);
      } else if (picked.length > MAX_PERSON) {
        picked = closeSubgraph(
          allNodes.slice(0, 1).map((n) => n.id),
          MAX_PERSON,
        );
      }
    } else {
      const topPeople = allNodes
        .filter((n) => n.type === 'accused')
        .sort((a, b) => (degree.get(b.id) ?? 0) - (degree.get(a.id) ?? 0))
        .slice(0, MAX_OVERVIEW)
        .map((n) => n.id);
      picked = closeSubgraph(
        topPeople.length ? topPeople : allNodes.slice(0, 4).map((n) => n.id),
        MAX_OVERVIEW + 8,
      );
    }

    const idSet = new Set(picked.map((n) => n.id));
    // Keep every edge whose endpoints are both on the board.
    const keptLinks = allLinks.filter(
      (l) => idSet.has(l.source) && idSet.has(l.target),
    );
    return { nodes: picked, links: keptLinks };
  }, [
    allNodes,
    allLinks,
    hasHighlight,
    highlightSet,
    personMode,
    focusNodeId,
    degree,
  ]);

  const cardW = nodes.length > 14 ? 108 : 128;
  const graphKey = useMemo(
    () => nodes.map((n) => n.id).sort().join('|'),
    [nodes],
  );

  const seedLayout = useCallback(
    (w: number, h: number) => {
      const n = nodes.length;
      if (!n || !w || !h) return;
      const next = new Map<string, Vec>();
      const types = new Map<string, string>();
      const usableW = Math.max(180, w - 160);
      const usableH = Math.max(160, h - 140);

      nodes.forEach((node, i) => {
        types.set(node.id, String(node.type ?? 'accused'));
        const seed = hashSeed(node.id);
        const angle = (i / Math.max(1, n)) * Math.PI * 2 - Math.PI / 2;
        const ring = 0.24 + (i % 3) * 0.1;
        next.set(node.id, {
          x: w / 2 + Math.cos(angle) * usableW * ring + (seed - 0.5) * 24,
          y: h / 2 + Math.sin(angle) * usableH * ring + (seed - 0.5) * 20,
        });
      });

      for (let iter = 0; iter < 28; iter++) {
        for (let i = 0; i < nodes.length; i++) {
          for (let j = i + 1; j < nodes.length; j++) {
            const a = nodes[i];
            const b = nodes[j];
            const pa = next.get(a.id)!;
            const pb = next.get(b.id)!;
            const minDx = cardW * 0.9;
            const minDy = 92;
            let dx = pb.x - pa.x;
            let dy = pb.y - pa.y;
            if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01) {
              dx = 0.5;
              dy = 0.5;
            }
            const dist = Math.hypot(dx, dy) || 1;
            if (Math.abs(dx) < minDx && Math.abs(dy) < minDy) {
              const nx = dx / dist;
              const ny = dy / dist;
              const ox = (minDx - Math.abs(dx)) * 0.42;
              const oy = (minDy - Math.abs(dy)) * 0.42;
              pa.x -= nx * ox;
              pa.y -= ny * oy;
              pb.x += nx * ox;
              pb.y += ny * oy;
            }
          }
        }
      }

      // No frame clamp — cards may sit anywhere in the open world.
      posRef.current = next;
      typeRef.current = types;
      setLayoutReady((g) => g + 1);
      setStringRev((r) => r + 1);
    },
    [nodes, cardW],
  );

  useEffect(() => {
    reduceMotionRef.current = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches;
  }, []);

  useEffect(() => {
    const el = frameRef.current;
    if (!el) return;
    const measure = () => {
      setSize({
        width: el.clientWidth || 800,
        height: Math.max(el.clientHeight || 0, 200),
      });
    };
    const raf = requestAnimationFrame(measure);
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, [fillHeight, nodes.length]);

  // Reseed ONLY when the visible node set changes — never on drag / resize alone.
  useEffect(() => {
    if (!nodes.length || !size.width) return;
    if (graphKeyRef.current === graphKey) return;
    graphKeyRef.current = graphKey;
    camRef.current = { x: 0, y: 0, scale: 1 };
    setCam({ x: 0, y: 0, scale: 1 });
    seedLayout(size.width, size.height);
  }, [graphKey, size.width, size.height, seedLayout, nodes.length]);

  const applyCam = useCallback((next: Cam, instant = false) => {
    camRef.current = next;
    setCamInstant(instant);
    setCam(next);
    const vp = viewportRef.current;
    if (vp) {
      vp.classList.toggle(styles.viewportInstant, instant);
      vp.style.transform = `translate(${next.x}px, ${next.y}px) scale(${next.scale})`;
    }
  }, []);

  // Non-passive wheel so we can zoom without scrolling the page.
  useEffect(() => {
    const el = frameRef.current;
    if (!el) return;
    const onNativeWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = el.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const prev = camRef.current;
      const factor = e.deltaY > 0 ? 0.92 : 1.08;
      const nextScale = Math.min(2.4, Math.max(0.35, prev.scale * factor));
      const wx = (mx - prev.x) / prev.scale;
      const wy = (my - prev.y) / prev.scale;
      applyCam(
        {
          scale: nextScale,
          x: mx - wx * nextScale,
          y: my - wy * nextScale,
        },
        true,
      );
    };
    el.addEventListener('wheel', onNativeWheel, { passive: false });
    return () => el.removeEventListener('wheel', onNativeWheel);
  }, [applyCam]);

  const fitCamera = useCallback(() => {
    const w = size.width;
    const h = size.height;
    if (!w || !h || !nodes.length) return;

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    nodes.forEach((n) => {
      const p = posRef.current.get(n.id);
      if (!p) return;
      const hw = cardWidth(n.type, cardW) / 2;
      const hh = cardH(n.type) / 2;
      minX = Math.min(minX, p.x - hw);
      maxX = Math.max(maxX, p.x + hw);
      minY = Math.min(minY, p.y - hh);
      maxY = Math.max(maxY, p.y + hh);
    });
    if (!Number.isFinite(minX)) return;

    const pad = fitTight ? 40 : 52;
    const bw = Math.max(60, maxX - minX);
    const bh = Math.max(60, maxY - minY);
    let scale = Math.min((w - pad * 2) / bw, (h - pad * 2) / bh, 1.35);
    scale = Math.max(0.7, Math.min(scale, 1.3));
    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    applyCam(
      {
        scale,
        x: w / 2 - cx * scale,
        y: h / 2 - cy * scale,
      },
      reduceMotionRef.current,
    );
  }, [nodes, size.width, size.height, cardW, fitTight, applyCam]);

  // Fit once after a new layout seeds — not after every string refresh / drag.
  useEffect(() => {
    if (!layoutReady) return;
    const t = window.setTimeout(fitCamera, 30);
    return () => window.clearTimeout(t);
  }, [layoutReady, fitCamera]);

  useEffect(() => {
    if (!focusNodeId || !nodes.length) return;
    const node = nodes.find((n) =>
      idMatches(String(n.id), new Set([focusNodeId])),
    );
    if (node) setSelectedId(String(node.id));
  }, [focusNodeId, nodes, layoutReady]);

  const paintStrings = useCallback(() => {
    links.forEach((l, i) => {
      const el = pathElsRef.current[i];
      const pa = posRef.current.get(l.source);
      const pb = posRef.current.get(l.target);
      if (!el || !pa || !pb) return;
      const a = pinPoint(pa, typeRef.current.get(l.source), cardW);
      const b = pinPoint(pb, typeRef.current.get(l.target), cardW);
      el.setAttribute('d', pathBetween(a, b));
      const hot =
        !!selectedId && (l.source === selectedId || l.target === selectedId);
      el.setAttribute(
        'class',
        [styles.stringPath, hot ? styles.stringPathHot : '']
          .filter(Boolean)
          .join(' '),
      );
      el.setAttribute(
        'stroke-width',
        String(Math.min(3.2, 1.8 + 0.3 * Number(l.sharedCases ?? 1))),
      );
    });
  }, [links, selectedId, cardW]);

  // Paint after DOM/path refs commit — avoids "missing" strings on first layout.
  useLayoutEffect(() => {
    paintStrings();
    const id = requestAnimationFrame(() => paintStrings());
    return () => cancelAnimationFrame(id);
  }, [paintStrings, stringRev, layoutReady, cam, nodes.length, links.length]);

  const placeCard = (id: string) => {
    const el = cardElsRef.current.get(id);
    const p = posRef.current.get(id);
    if (!el || !p) return;
    const type = typeRef.current.get(id);
    const hw = cardWidth(type, cardW) / 2;
    const hh = cardH(type) / 2;
    el.style.transform = `translate(${p.x - hw}px, ${p.y - hh}px)`;
  };

  const emitSelect = (n: (typeof nodes)[number] | null) => {
    if (!n) {
      setSelectedId(null);
      onSelectNode?.(null);
      return;
    }
    const id = String(n.id);
    setSelectedId(id);
    onSelectNode?.({
      id,
      name: String(n.name ?? id),
      type: String(n.type ?? 'accused'),
      degree: degree.get(id) ?? 0,
      sharedMax: sharedMax.get(id) ?? 0,
    });
  };

  const softClamp = (v: number) =>
    Math.min(WORLD_MAX - 40, Math.max(WORLD_MIN + 40, v));

  const onCardPointerDown = (id: string, e: React.PointerEvent) => {
    const p = posRef.current.get(id);
    if (!p) return;
    e.preventDefault();
    e.stopPropagation();
    panRef.current = null;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    dragRef.current = {
      id,
      startX: e.clientX,
      startY: e.clientY,
      orig: { ...p },
      moved: false,
      pointerId: e.pointerId,
    };
    setCamInstant(true);
    viewportRef.current?.classList.add(styles.viewportInstant);
  };

  const onCardPointerMove = (e: React.PointerEvent) => {
    const drag = dragRef.current;
    if (!drag || drag.id !== (e.currentTarget as HTMLElement).dataset.id) return;

    const scale = camRef.current.scale || 1;
    const dx = (e.clientX - drag.startX) / scale;
    const dy = (e.clientY - drag.startY) / scale;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) drag.moved = true;

    // Free placement anywhere on the open board (only soft world limits).
    const next = {
      x: softClamp(drag.orig.x + dx),
      y: softClamp(drag.orig.y + dy),
    };
    posRef.current.set(drag.id, next);
    placeCard(drag.id);
    paintStrings();
  };

  const onCardPointerUp = (id: string, e: React.PointerEvent) => {
    const drag = dragRef.current;
    if (!drag || drag.id !== id) return;
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(drag.pointerId);
    } catch {
      /* ignore */
    }
    dragRef.current = null;
    setCamInstant(false);
    viewportRef.current?.classList.remove(styles.viewportInstant);

    if (drag.moved) {
      setStringRev((r) => r + 1);
    } else {
      emitSelect(nodes.find((n) => n.id === id) ?? null);
    }
  };

  const onBoardPointerDown = (e: React.PointerEvent) => {
    // Pan when pressing empty board (not a card).
    const t = e.target as HTMLElement;
    if (t.closest?.(`.${styles.card}`)) return;
    if (t.closest?.('button')) return;

    e.preventDefault();
    frameRef.current?.setPointerCapture(e.pointerId);
    panRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      orig: { ...camRef.current },
      moved: false,
      pointerId: e.pointerId,
    };
    setPanning(true);
    setCamInstant(true);
    viewportRef.current?.classList.add(styles.viewportInstant);
  };

  const onBoardPointerMove = (e: React.PointerEvent) => {
    const pan = panRef.current;
    if (!pan) return;
    const dx = e.clientX - pan.startX;
    const dy = e.clientY - pan.startY;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) pan.moved = true;
    applyCam(
      {
        ...pan.orig,
        x: pan.orig.x + dx,
        y: pan.orig.y + dy,
      },
      true,
    );
  };

  const onBoardPointerUp = (e: React.PointerEvent) => {
    const pan = panRef.current;
    if (!pan) return;
    try {
      frameRef.current?.releasePointerCapture(pan.pointerId);
    } catch {
      /* ignore */
    }
    panRef.current = null;
    setPanning(false);
    setCamInstant(false);
    viewportRef.current?.classList.remove(styles.viewportInstant);
    if (!pan.moved) emitSelect(null);
  };


  if (!nodes.length) return null;

  return (
    <div
      className={styles.wrap}
      style={{
        flex: fillHeight ? 1 : undefined,
        height: fillHeight ? '100%' : 400,
        minHeight: fillHeight ? 0 : 400,
      }}
    >
      <div
        ref={frameRef}
        className={`${styles.frame}${panning ? ` ${styles.framePanning}` : ''}`}
        onPointerDown={onBoardPointerDown}
        onPointerMove={onBoardPointerMove}
        onPointerUp={onBoardPointerUp}
        onPointerCancel={onBoardPointerUp}
      >
        <button type="button" className={styles.fitBtn} onClick={fitCamera}>
          Fit view
        </button>

        <div
          ref={viewportRef}
          className={`${styles.viewport}${camInstant ? ` ${styles.viewportInstant}` : ''}`}
          style={{
            transform: `translate(${cam.x}px, ${cam.y}px) scale(${cam.scale})`,
          }}
        >
          <svg
            className={styles.strings}
            aria-hidden
            viewBox={`${WORLD_MIN} ${WORLD_MIN} ${WORLD_SIZE} ${WORLD_SIZE}`}
            style={{
              left: WORLD_MIN,
              top: WORLD_MIN,
              width: WORLD_SIZE,
              height: WORLD_SIZE,
            }}
          >
            {links.map((l, i) => (
              <path
                key={`${l.source}-${l.target}-${i}`}
                ref={(el) => {
                  pathElsRef.current[i] = el;
                }}
                className={styles.stringPath}
                d="M 0 0 L 0 0"
              />
            ))}
          </svg>

          {nodes.map((n) => {
            const id = n.id;
            const deg = degree.get(id) ?? 0;
            const sm = sharedMax.get(id) ?? 0;
            const tag = tagFor(String(n.type ?? 'accused'), deg, sm);
            const p = posRef.current.get(id) ?? {
              x: size.width / 2,
              y: size.height / 2,
            };
            const hw = cardWidth(n.type, cardW) / 2;
            const hh = cardH(n.type) / 2;
            const isHot = selectedId === id;

            return (
              <div
                key={id}
                data-id={id}
                ref={(el) => {
                  if (el) {
                    cardElsRef.current.set(id, el);
                    const pos = posRef.current.get(id) ?? p;
                    el.style.transform = `translate(${pos.x - hw}px, ${pos.y - hh}px)`;
                  } else {
                    cardElsRef.current.delete(id);
                  }
                }}
                className={[
                  styles.card,
                  n.type === 'case' ? styles.cardCase : '',
                  isHot ? styles.cardHot : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                style={{
                  ['--card-w' as string]:
                    n.type === 'case' ? '104px' : `${cardW}px`,
                  cursor: 'grab',
                }}
                onPointerDown={(e) => onCardPointerDown(id, e)}
                onPointerMove={onCardPointerMove}
                onPointerUp={(e) => onCardPointerUp(id, e)}
                onPointerCancel={(e) => onCardPointerUp(id, e)}
                title={`${n.name ?? id}\n${roleFor(String(n.type))} · ${deg} links`}
              >
                <div className={styles.photo}>
                  {n.type === 'case' ? (
                    <CaseIcon />
                  ) : n.type === 'accused' || n.type === 'victim' ? (
                    <PersonIcon />
                  ) : (
                    <OtherIcon />
                  )}
                  <div className={styles.idStrip}>
                    FILE{' '}
                    {normalizePersonKey(id).slice(-6).toUpperCase() || '——'}
                  </div>
                </div>
                <div className={styles.name}>
                  {shortName(String(n.name ?? id), n.type === 'case' ? 14 : 18)}
                </div>
                <div className={styles.role}>{roleFor(String(n.type))}</div>
                <span className={`${styles.tag} ${tag.className}`}>
                  {tag.label}
                </span>
              </div>
            );
          })}
        </div>

        <div className={styles.legend}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <i className={styles.legendDot} style={{ background: '#e31c25' }} />
            Person
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <i className={styles.legendDot} style={{ background: '#ffcc00' }} />
            FIR
          </span>
          <span style={{ opacity: 0.9 }}>
            {personMode
              ? 'Open board · drag cards anywhere · pan background'
              : hasHighlight
                ? `Focused group · ${nodes.length} people · ${links.length} links`
                : `Top ${nodes.length} people · hover a group to focus`}
          </span>
        </div>
        <div className={styles.hint}>
          drag card · drag empty space to pan · scroll to zoom
        </div>
      </div>
    </div>
  );
};

export default CrimeNetwork;
