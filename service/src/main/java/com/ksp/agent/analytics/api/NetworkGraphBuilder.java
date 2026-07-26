package com.ksp.agent.analytics.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes SQL rows into the {@code {nodes:[{id,name,type,val}], links:[{source,target,kind}]}}
 * structure consumed by react-force-graph on the client and by the crime-network ui template.
 */
final class NetworkGraphBuilder {

    private NetworkGraphBuilder() {
    }

    /**
     * Rows of (person_uid, accused_name, case_master_id, crime_no, crime_head, station,
     * station_id), plus an optional victim row list of (case_master_id, victim_master_id,
     * victim_name). Node types: accused, case, location (station) and victim — the requirement
     * names five entity types (accused, victims, locations, financial accounts, incidents); this
     * graph previously rendered only two (accused + case). Financial accounts are a separate graph
     * ({@link #fromMoneyTrail}), not merged in here — an offender's crime network and their
     * financial network are different investigative views, not one combined diagram.
     */
    static Map<String, Object> fromMemberships(List<Map<String, Object>> rows, List<Map<String, Object>> victimRows) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String personId = "p:" + row.get("person_uid");
            String caseId = "c:" + row.get("case_master_id");
            nodes.computeIfAbsent(personId, k -> node(personId,
                    String.valueOf(row.get("accused_name")), "accused"));
            nodes.computeIfAbsent(caseId, k -> {
                Map<String, Object> n = node(caseId, String.valueOf(row.get("crime_no")), "case");
                n.put("crimeHead", row.get("crime_head"));
                n.put("station", row.get("station"));
                return n;
            });
            links.add(Map.of("source", personId, "target", caseId, "kind", "accused_in"));
            Object stationId = row.get("station_id");
            if (stationId != null) {
                String locationId = "l:" + stationId;
                nodes.computeIfAbsent(locationId, k -> node(locationId, String.valueOf(row.get("station")), "location"));
                links.add(Map.of("source", caseId, "target", locationId, "kind", "reported_at"));
            }
        }
        if (victimRows != null) {
            for (Map<String, Object> row : victimRows) {
                String caseId = "c:" + row.get("case_master_id");
                if (!nodes.containsKey(caseId)) {
                    continue; // victim's case fell outside the rendered network (shouldn't happen; defensive)
                }
                String victimId = "v:" + row.get("victim_master_id");
                nodes.computeIfAbsent(victimId, k -> node(victimId, String.valueOf(row.get("victim_name")), "victim"));
                links.add(Map.of("source", victimId, "target", caseId, "kind", "victim_in"));
            }
        }
        return Map.of("nodes", new ArrayList<>(nodes.values()), "links", links);
    }

    /** Rows of (source_uid, source_name, target_uid, target_name, shared_cases). */
    static Map<String, Object> fromCoOffenderPairs(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String source = "p:" + row.get("source_uid");
            String target = "p:" + row.get("target_uid");
            nodes.computeIfAbsent(source, k -> node(source,
                    String.valueOf(row.get("source_name")), "accused"));
            nodes.computeIfAbsent(target, k -> node(target,
                    String.valueOf(row.get("target_name")), "accused"));
            links.add(Map.of("source", source, "target", target,
                    "kind", "co_accused", "sharedCases", row.get("shared_cases")));
        }
        return Map.of("nodes", new ArrayList<>(nodes.values()), "links", links);
    }

    /**
     * Rows of (hop, from_account, from_name, to_account, to_name, amount, txn_date, closes_cycle)
     * — the multi-hop money-trail walk (Area 7). Financial account nodes + transaction-edge links,
     * so a recovered layering chain (or a closed cycle) can render the same way the co-offender
     * graph does, instead of only as a table.
     */
    static Map<String, Object> fromMoneyTrail(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String from = "a:" + row.get("from_account");
            String to = "a:" + row.get("to_account");
            nodes.computeIfAbsent(from, k -> node(from, String.valueOf(row.get("from_name")), "account"));
            nodes.computeIfAbsent(to, k -> node(to, String.valueOf(row.get("to_name")), "account"));
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", from);
            link.put("target", to);
            link.put("kind", "financial_txn");
            link.put("hop", row.get("hop"));
            link.put("amount", row.get("amount"));
            link.put("closesCycle", row.get("closes_cycle"));
            links.add(link);
        }
        return Map.of("nodes", new ArrayList<>(nodes.values()), "links", links);
    }

    private static Map<String, Object> node(String id, String name, String type) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("name", name);
        n.put("type", type);
        return n;
    }
}
