package com.ksp.agent.analytics.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects co-offender clusters from the co-accused edge list via connected components (union-find).
 * For each component of size &gt;= 2 it reports members, the co-offending edge count, total shared
 * cases, a cohesion score (actual edges / possible edges) and the most-connected member — turning
 * "who co-offends with whom" pairs into named cluster findings.
 *
 * <p>Deliberately called a "cluster", not a "gang" or "organized-crime group": union-find only
 * merges components, it cannot SPLIT one. Two genuinely separate gangs that happen to share a
 * single common member (a fence, a corrupt contact, anyone who worked with both) are reported as
 * one cluster here — a real modularity/community-detection algorithm would be needed to tell them
 * apart, and this class does not attempt that. Cohesion/rankScore help a reviewer notice when a
 * "cluster" looks loosely-connected enough to actually be two groups glued together by one edge.
 */
public final class OffenderGroupDetector {

    private OffenderGroupDetector() {}

    /**
     * @param pairs rows of {source_uid, source_name, target_uid, target_name, shared_cases}
     * @param maxGroups cap on returned groups (largest / most-cohesive first)
     */
    public static List<Map<String, Object>> detect(List<Map<String, Object>> pairs, int maxGroups) {
        Map<String, String> parent = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> degree = new HashMap<>();

        for (Map<String, Object> e : pairs) {
            String a = str(e.get("source_uid"));
            String b = str(e.get("target_uid"));
            if (a == null || b == null) {
                continue;
            }
            names.putIfAbsent(a, str(e.get("source_name")));
            names.putIfAbsent(b, str(e.get("target_name")));
            parent.putIfAbsent(a, a);
            parent.putIfAbsent(b, b);
            union(parent, a, b);
            degree.merge(a, 1, Integer::sum);
            degree.merge(b, 1, Integer::sum);
        }

        // Group members + accumulate edge stats per component root.
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (String uid : parent.keySet()) {
            members.computeIfAbsent(find(parent, uid), k -> new ArrayList<>()).add(uid);
        }
        Map<String, Integer> edgeCount = new HashMap<>();
        Map<String, Integer> sharedTotal = new HashMap<>();
        for (Map<String, Object> e : pairs) {
            String a = str(e.get("source_uid"));
            if (a == null) {
                continue;
            }
            String root = find(parent, a);
            edgeCount.merge(root, 1, Integer::sum);
            sharedTotal.merge(root, num(e.get("shared_cases")), Integer::sum);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : members.entrySet()) {
            List<String> uids = g.getValue();
            int size = uids.size();
            if (size < 2) {
                continue;
            }
            String root = g.getKey();
            int edges = edgeCount.getOrDefault(root, 0);
            int possible = size * (size - 1) / 2;
            double cohesion = possible == 0 ? 0 : Math.round((double) edges / possible * 100.0) / 100.0;
            // Favors tight cliques over loose, merely-large blobs — a group where everyone
            // co-offends with everyone else outranks a sprawling weakly-connected component of the
            // same size.
            double rankScore = Math.round(cohesion * size * 100.0) / 100.0;
            String ringleader = uids.stream()
                    .max((x, y) -> Integer.compare(degree.getOrDefault(x, 0), degree.getOrDefault(y, 0)))
                    .orElse(uids.get(0));

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("size", size);
            group.put("edges", edges);
            group.put("sharedCases", sharedTotal.getOrDefault(root, 0));
            group.put("cohesion", cohesion);
            group.put("rankScore", rankScore);
            group.put("ringleaderUid", ringleader);
            group.put("ringleaderName", names.get(ringleader));
            List<Map<String, Object>> memberList = new ArrayList<>();
            for (String uid : uids) {
                Map<String, Object> mNode = new LinkedHashMap<>();
                mNode.put("personUid", uid);
                mNode.put("name", names.get(uid));
                mNode.put("connections", degree.getOrDefault(uid, 0));
                memberList.add(mNode);
            }
            memberList.sort((x, y) -> Integer.compare((int) y.get("connections"), (int) x.get("connections")));
            group.put("members", memberList);
            groups.add(group);
        }
        // Tightest, most prolific groups first — ranked by cohesion*size (rankScore), not raw size,
        // so a loose large blob no longer outranks a small tight clique. Cohesion/rankScore are
        // Doubles at runtime; unbox via Number to avoid a ClassCastException from an (int) cast.
        groups.sort((x, y) -> {
            int byRank = Double.compare(
                    ((Number) y.get("rankScore")).doubleValue(),
                    ((Number) x.get("rankScore")).doubleValue());
            if (byRank != 0) {
                return byRank;
            }
            int bySize = Integer.compare((int) y.get("size"), (int) x.get("size"));
            if (bySize != 0) {
                return bySize;
            }
            return Integer.compare((int) y.get("sharedCases"), (int) x.get("sharedCases"));
        });
        return groups.stream().limit(Math.max(1, maxGroups)).toList();
    }

    private static String find(Map<String, String> parent, String x) {
        String root = x;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        // Path compression.
        String cur = x;
        while (!cur.equals(root)) {
            String next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
