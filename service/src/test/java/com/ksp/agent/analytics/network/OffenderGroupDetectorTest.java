package com.ksp.agent.analytics.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Phase 2.7 fix: groups now rank by cohesion*size (rankScore), not raw size, so a
 * small tight clique outranks a larger loose chain — before the fix, the chain (more members)
 * would have sorted first despite being the weaker finding.
 */
class OffenderGroupDetectorTest {

    private static Map<String, Object> edge(String a, String b, int shared) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source_uid", a);
        row.put("source_name", "Name-" + a);
        row.put("target_uid", b);
        row.put("target_name", "Name-" + b);
        row.put("shared_cases", shared);
        return row;
    }

    @Test
    void tightCliqueOutranksLargerLooseChain() {
        List<Map<String, Object>> pairs = new ArrayList<>();
        // Tight 4-clique: A,B,C,D all pairwise connected (6 edges / 6 possible = cohesion 1.0).
        pairs.add(edge("A", "B", 3));
        pairs.add(edge("A", "C", 3));
        pairs.add(edge("A", "D", 3));
        pairs.add(edge("B", "C", 3));
        pairs.add(edge("B", "D", 3));
        pairs.add(edge("C", "D", 3));
        // Loose chain of 6: E-F-G-H-I-J (5 edges / 15 possible = cohesion 0.33), larger than the clique.
        pairs.add(edge("E", "F", 2));
        pairs.add(edge("F", "G", 2));
        pairs.add(edge("G", "H", 2));
        pairs.add(edge("H", "I", 2));
        pairs.add(edge("I", "J", 2));

        List<Map<String, Object>> groups = OffenderGroupDetector.detect(pairs, 10);
        assertThat(groups).hasSize(2);

        Map<String, Object> first = groups.get(0);
        assertThat((int) first.get("size")).isEqualTo(4);
        assertThat(((Number) first.get("cohesion")).doubleValue()).isEqualTo(1.0);

        Map<String, Object> second = groups.get(1);
        assertThat((int) second.get("size")).isEqualTo(6);
        assertThat(((Number) second.get("cohesion")).doubleValue()).isLessThan(1.0);

        // The core assertion: rankScore (cohesion*size) must actually decide the order.
        double firstRank = ((Number) first.get("rankScore")).doubleValue();
        double secondRank = ((Number) second.get("rankScore")).doubleValue();
        assertThat(firstRank).isGreaterThan(secondRank);
    }

    @Test
    void singletonPairsBelowSizeTwoAreExcluded() {
        // A component of size 1 can't happen from an edge list (every edge creates >= 2 members),
        // but detect() should never emit a size-1 group even if the edge list is malformed.
        List<Map<String, Object>> pairs = List.of(edge("X", "Y", 2));
        List<Map<String, Object>> groups = OffenderGroupDetector.detect(pairs, 10);
        assertThat(groups).allSatisfy(g -> assertThat((int) g.get("size")).isGreaterThanOrEqualTo(2));
    }

    @Test
    void ringleaderIsTheMostConnectedMember() {
        List<Map<String, Object>> pairs = new ArrayList<>();
        // Star: HUB connects to A, B, C — HUB has degree 3, everyone else has degree 1.
        pairs.add(edge("HUB", "A", 2));
        pairs.add(edge("HUB", "B", 2));
        pairs.add(edge("HUB", "C", 2));

        List<Map<String, Object>> groups = OffenderGroupDetector.detect(pairs, 10);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("ringleaderUid")).isEqualTo("HUB");
    }
}
