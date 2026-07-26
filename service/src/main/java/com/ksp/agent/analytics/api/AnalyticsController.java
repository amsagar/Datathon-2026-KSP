package com.ksp.agent.analytics.api;

import com.ksp.agent.analytics.forecast.CrimeForecaster;
import com.ksp.agent.analytics.network.OffenderGroupDetector;
import com.ksp.agent.analytics.repo.AnalyticsRepository;
import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.audit.service.AuditService;
import com.ksp.agent.auth.service.SecurityContextService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Crime analytics REST surface for the dashboard, hotspot map, network graph and offender
 * risk pages. All endpoints are read-only aggregates over the FIR schema. Network/offender/
 * financial detail (person-identifying) is restricted to investigation roles (+ ANALYST for the
 * network graph); the dashboard/map aggregates are available to every authenticated role,
 * including POLICYMAKER, and are explicitly annotated (rather than relying on the default
 * authenticated-only URL rule) so the allow-list is self-documenting and survives an unrelated
 * change to the security filter chain. Every endpoint that returns crime-data content (not just
 * static reference lookups like crime-heads/districts) is audited for law-enforcement
 * accountability.
 */
@RestController
@RequestMapping(ApiConstants.ANALYTICS_PATH)
public class AnalyticsController {

    private static final String DEFAULT_FROM = "2019-01-01";
    private static final String DEFAULT_TO = "2026-12-31";
    private static final String ALL_ROLES =
            "hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR','ANALYST','POLICYMAKER')";

    private final AnalyticsRepository repository;
    private final AuditService auditService;
    private final SecurityContextService securityContextService;

    public AnalyticsController(AnalyticsRepository repository, AuditService auditService,
                              SecurityContextService securityContextService) {
        this.repository = repository;
        this.auditService = auditService;
        this.securityContextService = securityContextService;
    }

    /** Traceability for sensitive crime-data access: who pulled what (challenge governance req). */
    private void audit(String action, String target) {
        String actor;
        try {
            actor = securityContextService.currentUserIdOrThrow();
        } catch (RuntimeException e) {
            actor = "unknown";
        }
        auditService.record(actor, action, target, null);
    }

    @GetMapping("/trends")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> trends(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) Integer districtId,
                                            @RequestParam(required = false) Integer crimeHeadId) {
        audit("VIEW_TRENDS", districtId == null ? "statewide" : "district:" + districtId);
        return repository.trends(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO),
                districtId, crimeHeadId);
    }

    @GetMapping("/hotspots")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> hotspots(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) Integer districtId,
                                              @RequestParam(required = false) Integer crimeHeadId) {
        audit("VIEW_HOTSPOTS", districtId == null ? "statewide" : "district:" + districtId);
        return repository.hotspots(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO),
                districtId, crimeHeadId);
    }

    @GetMapping("/district-summary")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> districtSummary(@RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        audit("VIEW_DISTRICT_SUMMARY", "statewide");
        return repository.districtSummary(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO));
    }

    @GetMapping("/crime-heads")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> crimeHeads() {
        return repository.crimeHeads();
    }

    @GetMapping("/districts")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> districts() {
        return repository.districts();
    }

    @GetMapping("/early-warnings")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> earlyWarnings() {
        audit("VIEW_EARLY_WARNINGS", "statewide");
        return repository.earlyWarnings();
    }

    @GetMapping("/risk-scores")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR')")
    public List<Map<String, Object>> riskScores(@RequestParam(required = false) Integer limit) {
        audit("VIEW_RISK_SCORES", "offender_risk_score");
        return repository.riskScores(limit);
    }

    @GetMapping("/network")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR','ANALYST')")
    public Map<String, Object> network(@RequestParam(required = false) String personUid,
                                       @RequestParam(required = false) Integer limit) {
        audit("VIEW_NETWORK", personUid == null || personUid.isBlank() ? "statewide" : personUid);
        if (personUid != null && !personUid.isBlank()) {
            String uid = personUid.trim();
            return NetworkGraphBuilder.fromMemberships(repository.networkEdges(uid), repository.victimsForNetwork(uid));
        }
        // Statewide overview must include every "Organized group" cluster — not just the
        // top-N edge sample (that left some sidebar groups with no matching nodes).
        return NetworkGraphBuilder.fromCoOffenderPairs(statewideGroupEdges(limit));
    }

    /**
     * Edges among members of the top detected groups so the map and group list stay in sync.
     */
    private List<Map<String, Object>> statewideGroupEdges(Integer limit) {
        int minShared = 2;
        var pairs = repository.allCoOffenderPairs(minShared);
        int groupCap = limit != null && limit > 0 ? Math.min(limit, 20) : 12;
        var groups = OffenderGroupDetector.detect(pairs, groupCap);

        Set<String> inGroups = new HashSet<>();
        for (Map<String, Object> g : groups) {
            Object rl = g.get("ringleaderUid");
            if (rl != null) {
                inGroups.add(String.valueOf(rl));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) g.get("members");
            if (members == null) {
                continue;
            }
            for (Map<String, Object> m : members) {
                Object uid = m.get("personUid");
                if (uid != null) {
                    inGroups.add(String.valueOf(uid));
                }
            }
        }

        List<Map<String, Object>> overview = new ArrayList<>();
        for (Map<String, Object> p : pairs) {
            Object a = p.get("source_uid");
            Object b = p.get("target_uid");
            if (a == null || b == null) {
                continue;
            }
            if (inGroups.contains(String.valueOf(a)) && inGroups.contains(String.valueOf(b))) {
                overview.add(p);
            }
        }
        if (overview.isEmpty()) {
            return repository.topCoOffenders(limit);
        }
        return overview;
    }

    @GetMapping("/network/{personUid}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR','ANALYST')")
    public Map<String, Object> networkFor(@PathVariable String personUid) {
        return NetworkGraphBuilder.fromMemberships(repository.networkEdges(personUid), repository.victimsForNetwork(personUid));
    }

    @GetMapping("/forecast")
    @PreAuthorize(ALL_ROLES)
    public CrimeForecaster.Forecast forecast(@RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(required = false) Integer districtId,
                                             @RequestParam(required = false) Integer crimeHeadId,
                                             @RequestParam(required = false) Integer horizon) {
        audit("VIEW_FORECAST", districtId == null ? "statewide" : "district:" + districtId);
        var series = repository.monthlyTotals(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO),
                districtId, crimeHeadId);
        return CrimeForecaster.forecast(series, horizon == null ? 6 : horizon);
    }

    /** One ranked forecast slice, per district — see {@link #forecastHotspots}. */
    public record DistrictHotspotForecast(int districtId, String districtName, double baselineTotal,
                                          double forecastTotal, double ratio, String method, Double backtestMape) {}

    @GetMapping("/forecast/hotspots")
    @PreAuthorize(ALL_ROLES)
    public List<DistrictHotspotForecast> forecastHotspots(@RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(required = false) Integer crimeHeadId,
                                                          @RequestParam(required = false) Integer horizon,
                                                          @RequestParam(required = false) Integer limit) {
        audit("VIEW_FORECAST_HOTSPOTS", "statewide");
        int h = horizon == null || horizon <= 0 ? 3 : Math.min(horizon, 12);
        int cap = limit == null || limit <= 0 ? 10 : Math.min(limit, 31);
        var rows = repository.monthlyTotalsByDistrict(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO), crimeHeadId);

        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, List<Map<String, Object>>> byDistrict = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int did = ((Number) row.get("district_id")).intValue();
            names.putIfAbsent(did, String.valueOf(row.get("district_name")));
            byDistrict.computeIfAbsent(did, k -> new ArrayList<>()).add(row);
        }

        List<DistrictHotspotForecast> results = new ArrayList<>();
        for (var entry : byDistrict.entrySet()) {
            CrimeForecaster.Forecast f = CrimeForecaster.forecast(entry.getValue(), h);
            if (f.forecast().isEmpty()) {
                continue;
            }
            double forecastSum = f.forecast().stream().mapToDouble(CrimeForecaster.Point::value).sum();
            // Same-length trailing window of ACTUAL history, so the ratio compares like with like
            // (next h months predicted vs the most recent h months observed) rather than an average.
            List<CrimeForecaster.Point> hist = f.history();
            int n = Math.min(h, hist.size());
            double baselineSum = hist.subList(hist.size() - n, hist.size()).stream()
                    .mapToDouble(CrimeForecaster.Point::value).sum();
            double ratio = baselineSum <= 0 ? (forecastSum > 0 ? 99.0 : 1.0) : forecastSum / baselineSum;
            results.add(new DistrictHotspotForecast(entry.getKey(), names.get(entry.getKey()),
                    Math.round(baselineSum * 10.0) / 10.0, Math.round(forecastSum * 10.0) / 10.0,
                    Math.round(ratio * 100.0) / 100.0, f.method(),
                    f.backtest() == null ? null : f.backtest().mape()));
        }
        results.sort((a, b) -> Double.compare(b.ratio(), a.ratio()));
        return results.stream().limit(cap).toList();
    }

    @GetMapping("/seasonality")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> seasonality(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(required = false) Integer districtId,
                                                 @RequestParam(required = false) Integer crimeHeadId) {
        audit("VIEW_SEASONALITY", districtId == null ? "statewide" : "district:" + districtId);
        return repository.seasonality(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO),
                districtId, crimeHeadId);
    }

    @GetMapping("/demographics")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> demographics(@RequestParam(required = false) String dimension,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(required = false) Integer districtId,
                                                  @RequestParam(required = false) String crimeType) {
        audit("VIEW_DEMOGRAPHICS", districtId == null ? "statewide" : "district:" + districtId);
        return repository.demographics(dimension, orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO),
                districtId, crimeType);
    }

    /**
     * Investigation outcome comparison (Area 6): chargesheet/false-case/undetected disposal rates
     * and average disposal time, by crime head and district. Slices with fewer than 10 cases are
     * dropped (repository-side HAVING) so the rate isn't noise on a handful of cases.
     */
    @GetMapping("/outcomes")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> outcomes(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) Integer districtId) {
        audit("VIEW_OUTCOMES", districtId == null ? "statewide" : "district:" + districtId);
        return repository.outcomeComparison(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO), districtId);
    }

    /**
     * Complainant occupation breakdown by crime head (Area 4). Scoped to complainants — the
     * official schema carries occupation/religion/caste on ComplainantDetails only, not Accused;
     * caste/religion stay excluded from analytics output as a fairness decision.
     */
    @GetMapping("/complainant-occupation")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> complainantOccupation(@RequestParam(required = false) String from,
                                                           @RequestParam(required = false) String to,
                                                           @RequestParam(required = false) Integer crimeHeadId) {
        audit("VIEW_COMPLAINANT_OCCUPATION", "statewide");
        return repository.complainantOccupationByCrimeHead(orDefault(from, DEFAULT_FROM), orDefault(to, DEFAULT_TO), crimeHeadId);
    }

    @GetMapping("/network/groups")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR','ANALYST')")
    public Map<String, Object> offenderGroups(@RequestParam(required = false) Integer minShared,
                                              @RequestParam(required = false) Integer maxGroups) {
        audit("DETECT_OFFENDER_GROUPS", "co_accused_network");
        // Same edge set as GET /network statewide so every listed group has a map cluster.
        int cap = maxGroups == null ? 12 : maxGroups;
        var pairs = statewideGroupEdges(cap);
        return Map.of(
                "groups", OffenderGroupDetector.detect(pairs, cap),
                "pairCount", pairs.size());
    }

    @GetMapping("/similar-cases/{crimeNo}")
    @PreAuthorize(ALL_ROLES)
    public List<Map<String, Object>> similarCases(@PathVariable String crimeNo,
                                                  @RequestParam(required = false) Integer limit) {
        audit("VIEW_SIMILAR_CASES", crimeNo);
        return repository.similarCases(crimeNo, limit == null ? 10 : limit);
    }

    @GetMapping("/offender-profile")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR','ANALYST')")
    public Map<String, Object> offenderProfile(@RequestParam String personUid) {
        audit("VIEW_OFFENDER_PROFILE", personUid);
        return repository.offenderProfile(personUid);
    }

    @GetMapping("/financial/money-trail")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR')")
    public List<Map<String, Object>> moneyTrail(@RequestParam String personUid) {
        audit("VIEW_MONEY_TRAIL", personUid);
        return repository.moneyTrail(personUid);
    }

    /**
     * Multi-hop money trail (Area 7) — recursive walk beyond the single-hop {@code /money-trail},
     * including any recovered layering cycle. Graph-shaped (nodes/links, like {@code /network}) so
     * it can render as a chain/cycle diagram, not just a flat transaction table.
     */
    @GetMapping("/financial/money-trail-multihop")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR')")
    public Map<String, Object> moneyTrailMultiHop(@RequestParam String personUid,
                                                  @RequestParam(required = false) Integer maxDepth) {
        audit("VIEW_MONEY_TRAIL_MULTIHOP", personUid);
        return NetworkGraphBuilder.fromMoneyTrail(
                repository.multiHopMoneyTrail(personUid, maxDepth == null ? 3 : maxDepth));
    }

    @GetMapping("/financial/suspicious")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR')")
    public Map<String, Object> suspiciousFinancial() {
        audit("VIEW_SUSPICIOUS_FINANCIAL", "financial_transaction");
        return Map.of("transactions", repository.suspiciousTransactions(),
                "muleAccounts", repository.fanInAccounts());
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
