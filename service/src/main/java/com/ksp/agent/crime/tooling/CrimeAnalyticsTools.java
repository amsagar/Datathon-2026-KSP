package com.ksp.agent.crime.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.analytics.forecast.CrimeForecaster;
import com.ksp.agent.analytics.network.OffenderGroupDetector;
import com.ksp.agent.analytics.repo.AnalyticsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Higher-order crime-analytics tools for the chatbot: forecasting, organized-group detection,
 * seasonality, demographics, case similarity and financial money-trail analysis. These wrap the
 * same read-only aggregates the dashboard REST layer uses ({@link AnalyticsRepository}) plus the
 * forecasting/graph algorithms, and return JSON so the model can narrate + render them. Registered
 * in {@code BuiltinToolCatalog} under {@code crime_analytics}.
 */
@Component
@Slf4j
public class CrimeAnalyticsTools {

    private static final String DEFAULT_FROM = "2019-01-01";
    private static final String DEFAULT_TO = "2026-12-31";

    private final AnalyticsRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrimeAnalyticsTools(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Tool(name = "forecast_crime", description = """
            Forecasts future monthly case volume from the FIR history using Holt-Winters / seasonal
            methods. Returns JSON {history, forecast, method}. Use for predictive questions ("how many
            thefts next quarter", "where are cases trending"). Optionally scope by district/crime head.""")
    public String forecastCrime(
            @ToolParam(required = false, description = "Start date YYYY-MM-DD") String fromDate,
            @ToolParam(required = false, description = "End date YYYY-MM-DD") String toDate,
            @ToolParam(required = false, description = "district_id filter") Integer districtId,
            @ToolParam(required = false, description = "crime_head_id filter") Integer crimeHeadId,
            @ToolParam(required = false, description = "months to project (default 6)") Integer horizonMonths) {
        try {
            List<Map<String, Object>> series = repository.monthlyTotals(
                    orDefault(fromDate, DEFAULT_FROM), orDefault(toDate, DEFAULT_TO), districtId, crimeHeadId);
            CrimeForecaster.Forecast f = CrimeForecaster.forecast(series, horizonMonths == null ? 6 : horizonMonths);
            return objectMapper.writeValueAsString(f);
        } catch (Exception e) {
            return error("forecast_crime failed", e);
        }
    }

    @Tool(name = "detect_offender_groups", description = """
            Detects co-offender clusters by connected components on the co-accused network — a
            candidate signal for "gangs"/"organized crime", not a verified one: connected components
            can only merge, never split, so two separate gangs sharing one common member (a fence,
            a corrupt contact) would incorrectly appear as a single cluster. Returns JSON groups
            with members, size, shared cases, cohesion/rankScore (low cohesion is a hint a
            "cluster" may actually be two loosely-joined groups) and the most connected member
            (likely ringleader). Use for "gangs", "organized crime", "networks" — but qualify any
            answer as a candidate finding, not a confirmed one.""")
    public String detectOffenderGroups(
            @ToolParam(required = false, description = "min shared cases per co-offender edge (default 2)") Integer minSharedCases,
            @ToolParam(required = false, description = "max groups to return (default 15)") Integer maxGroups) {
        try {
            List<Map<String, Object>> pairs = repository.allCoOffenderPairs(minSharedCases == null ? 2 : minSharedCases);
            List<Map<String, Object>> groups = OffenderGroupDetector.detect(pairs, maxGroups == null ? 15 : maxGroups);
            // allCoOffenderPairs is capped at 4000 rows; hitting the cap means weaker edges were
            // dropped and cohesion/rankScore for large networks may be understated.
            boolean pairsTruncated = pairs.size() >= 4000;
            return objectMapper.writeValueAsString(Map.of(
                    "groups", groups, "pairCount", pairs.size(), "pairsTruncated", pairsTruncated));
        } catch (Exception e) {
            return error("detect_offender_groups failed", e);
        }
    }

    @Tool(name = "crime_seasonality", description = """
            Returns average case volume by calendar month (1-12) so you can spot seasonal patterns.
            JSON rows {month_num, avg_count, total}. Optionally scope by district/crime head.""")
    public String crimeSeasonality(
            @ToolParam(required = false, description = "Start date YYYY-MM-DD") String fromDate,
            @ToolParam(required = false, description = "End date YYYY-MM-DD") String toDate,
            @ToolParam(required = false, description = "district_id filter") Integer districtId,
            @ToolParam(required = false, description = "crime_head_id filter") Integer crimeHeadId) {
        try {
            return objectMapper.writeValueAsString(repository.seasonality(
                    orDefault(fromDate, DEFAULT_FROM), orDefault(toDate, DEFAULT_TO), districtId, crimeHeadId));
        } catch (Exception e) {
            return error("crime_seasonality failed", e);
        }
    }

    @Tool(name = "accused_demographics", description = """
            Aggregate demographic breakdown of accused by 'age' or 'gender' (caste/religion are
            excluded by design). JSON rows {bucket, count}. Use for sociological/demographic questions.""")
    public String accusedDemographics(
            @ToolParam(required = false, description = "'age' or 'gender' (default age)") String dimension,
            @ToolParam(required = false, description = "Start date YYYY-MM-DD") String fromDate,
            @ToolParam(required = false, description = "End date YYYY-MM-DD") String toDate,
            @ToolParam(required = false, description = "district_id filter") Integer districtId) {
        try {
            return objectMapper.writeValueAsString(repository.demographics(
                    dimension, orDefault(fromDate, DEFAULT_FROM), orDefault(toDate, DEFAULT_TO), districtId));
        } catch (Exception e) {
            return error("accused_demographics failed", e);
        }
    }

    @Tool(name = "find_similar_cases", description = """
            Finds past cases similar to a given crime_no by shared acts/sections and crime type — for
            "similar cases" and investigative leads. JSON rows with crime_no, crime head, district,
            shared_sections, same_sub_head.""")
    public String findSimilarCases(
            @ToolParam(description = "seed crime_no") String crimeNo,
            @ToolParam(required = false, description = "max results (default 10)") Integer limit) {
        try {
            return objectMapper.writeValueAsString(repository.similarCases(crimeNo, limit == null ? 10 : limit));
        } catch (Exception e) {
            return error("find_similar_cases failed", e);
        }
    }

    @Tool(name = "offender_profile", description = """
            Case-mix profile of one offender identity (accused.person_uid): their case mix by crime
            head, most frequent sections, and districts of operation. JSON {crimeHeads, sections,
            districts}. Use for offender profiling beyond the risk score. Not a modus-operandi
            profile — the FIR schema has no structured MO field.""")
    public String offenderProfile(@ToolParam(description = "offender person_uid") String personUid) {
        try {
            return objectMapper.writeValueAsString(repository.offenderProfile(personUid));
        } catch (Exception e) {
            return error("offender_profile failed", e);
        }
    }

    @Tool(name = "list_account_transactions", description = """
            Lists the direct (single-hop) financial transactions to/from all accounts held by an
            offender identity (accused.person_uid). JSON transaction rows with counterparties,
            amount, date, type and a suspicious flag. Does not follow multi-hop trails through
            intermediary accounts — use for one offender's own transactions only.""")
    public String listAccountTransactions(@ToolParam(description = "offender person_uid") String personUid) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "transactions", repository.moneyTrail(personUid)));
        } catch (Exception e) {
            return error("list_account_transactions failed", e);
        }
    }

    @Tool(name = "trace_money_network", description = """
            Multi-hop money trail: walks outgoing transactions from an offender's accounts up to
            maxDepth hops (default 3, max 4) via a recursive graph walk, recovering layering
            chains and round-trip cycles that list_account_transactions (single-hop only) cannot
            see. JSON transaction rows with a hop number and a closesCycle flag for any row that
            loops back to the seed's own starting account.""")
    public String traceMoneyNetwork(@ToolParam(description = "offender person_uid") String personUid,
            @ToolParam(required = false, description = "max hops, default 3, max 4") Integer maxDepth) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "trail", repository.multiHopMoneyTrail(personUid, maxDepth == null ? 3 : maxDepth)));
        } catch (Exception e) {
            return error("trace_money_network failed", e);
        }
    }

    @Tool(name = "suspicious_transactions", description = """
            Lists flagged / high-value transactions and fan-in ("mule") accounts receiving from many
            sources — money-laundering signals. JSON {transactions, muleAccounts}.""")
    public String suspiciousTransactions() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "transactions", repository.suspiciousTransactions(),
                    "muleAccounts", repository.fanInAccounts()));
        } catch (Exception e) {
            return error("suspicious_transactions failed", e);
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String error(String context, Exception e) {
        log.warn("{}: {}", context, e.getMessage());
        return "{\"error\":\"" + context + ": " + String.valueOf(e.getMessage()).replace('"', '\'') + "\"}";
    }
}
