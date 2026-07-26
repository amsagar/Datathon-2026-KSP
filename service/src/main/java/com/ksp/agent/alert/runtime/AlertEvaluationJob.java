package com.ksp.agent.alert.runtime;

import com.ksp.agent.alert.repo.AlertRepository;
import com.ksp.agent.analytics.network.OffenderGroupDetector;
import com.ksp.agent.analytics.repo.AnalyticsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns the dashboard's read-only "early warnings" into persisted, actionable alerts (Phase
 * 4.12) — previously a banner recomputed on every dashboard load, never stored, with no lifecycle
 * (acknowledge/assign/resolve). Triggered externally like every other scheduled task in this
 * codebase (see {@code InternalCronController}) rather than an in-process {@code @Scheduled}, so a
 * multi-instance deployment doesn't double-fire.
 *
 * <p>Two independent signal types, since the pre-existing early-warning query is purely
 * crime-COUNT-based and has no offender/network term at all, despite the requirement naming
 * "repeat crimes, gang activity, organized crime" explicitly:
 * <ul>
 *   <li>CRIME_SPIKE — district×crime-head volume well above its trailing baseline
 *       ({@link AnalyticsRepository#earlyWarnings()}).
 *   <li>REPEAT_OFFENDER_SURGE — a district with an unusual concentration of high-risk repeat
 *       offenders recently active ({@link AnalyticsRepository#repeatOffenderSurgeByDistrict}).
 *   <li>GANG_ACTIVITY — a tight, sizeable co-offender cluster (candidate organized-crime group;
 *       see {@link OffenderGroupDetector}'s honesty caveat about not being able to split clusters).
 * </ul>
 */
@Component
@Slf4j
public class AlertEvaluationJob {

    private static final int REPEAT_OFFENDER_MIN_CASE_COUNT = 3;
    private static final int REPEAT_OFFENDER_RECENT_DAYS = 90;
    private static final double GANG_MIN_COHESION = 0.5;
    private static final int GANG_MIN_SIZE = 3;

    private final AnalyticsRepository analyticsRepository;
    private final AlertRepository alertRepository;

    public AlertEvaluationJob(AnalyticsRepository analyticsRepository, AlertRepository alertRepository) {
        this.analyticsRepository = analyticsRepository;
        this.alertRepository = alertRepository;
    }

    public void evaluate() {
        long now = Instant.now().getEpochSecond();
        int opened = 0;
        opened += evaluateCrimeSpikes(now);
        opened += evaluateRepeatOffenderSurges(now);
        opened += evaluateGangActivity(now);
        log.info("Alert evaluation run complete: {} new alert(s) opened (existing open alerts for the same condition are left as-is).", opened);
    }

    private int evaluateCrimeSpikes(long now) {
        int opened = 0;
        for (Map<String, Object> row : analyticsRepository.earlyWarnings()) {
            String district = String.valueOf(row.get("district_name"));
            String crimeHead = String.valueOf(row.get("crime_head"));
            Object ratio = row.get("spike_ratio");
            String message = String.format(
                    "%s: %s case volume is %sx the trailing-quarter baseline (%s recent vs ~%s baseline/quarter).",
                    district, crimeHead, ratio, row.get("recent_count"), row.get("baseline_per_quarter"));
            alertRepository.openIfAbsent("CRIME_SPIKE", null, district, crimeHead, message,
                    severityForRatio(ratio), "CRIME_SPIKE:" + district + ":" + crimeHead, now);
            opened++;
        }
        return opened;
    }

    private int evaluateRepeatOffenderSurges(long now) {
        int opened = 0;
        for (Map<String, Object> row : analyticsRepository.repeatOffenderSurgeByDistrict(
                REPEAT_OFFENDER_MIN_CASE_COUNT, REPEAT_OFFENDER_RECENT_DAYS)) {
            Integer districtId = (Integer) row.get("district_id");
            String district = String.valueOf(row.get("district_name"));
            Object count = row.get("repeat_offender_count");
            String message = String.format(
                    "%s: %s distinct high-risk repeat offenders (>= %d cases each) active in the last %d days.",
                    district, count, REPEAT_OFFENDER_MIN_CASE_COUNT, REPEAT_OFFENDER_RECENT_DAYS);
            alertRepository.openIfAbsent("REPEAT_OFFENDER_SURGE", districtId, district, null, message,
                    "HIGH", "REPEAT_OFFENDER_SURGE:" + district, now);
            opened++;
        }
        return opened;
    }

    private int evaluateGangActivity(long now) {
        int opened = 0;
        List<Map<String, Object>> pairs = analyticsRepository.allCoOffenderPairs(2);
        List<Map<String, Object>> groups = OffenderGroupDetector.detect(pairs, 20);
        for (Map<String, Object> group : groups) {
            double cohesion = ((Number) group.get("cohesion")).doubleValue();
            int size = (int) group.get("size");
            if (cohesion < GANG_MIN_COHESION || size < GANG_MIN_SIZE) {
                continue;
            }
            String ringleader = String.valueOf(group.get("ringleaderName"));
            String message = String.format(
                    "Candidate organized-crime cluster around %s: %d members, %s shared cases, cohesion %.2f. "
                            + "Connected-components clustering cannot rule out this being two separate groups sharing one member.",
                    ringleader, size, group.get("sharedCases"), cohesion);
            alertRepository.openIfAbsent("GANG_ACTIVITY", null, null, null, message, "HIGH",
                    "GANG_ACTIVITY:" + group.get("ringleaderUid"), now);
            opened++;
        }
        return opened;
    }

    private static String severityForRatio(Object ratio) {
        double r = ratio instanceof Number n ? n.doubleValue() : 0;
        return r >= 2.5 ? "HIGH" : r >= 1.8 ? "MEDIUM" : "LOW";
    }
}
