package com.ksp.agent.analytics.forecast;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The forecast was previously unfalsifiable — no prediction intervals, no backtest metric, just a
 * curve. These tests exercise the backtest/interval machinery added to make it verifiable, plus
 * the method-selection thresholds ({@code y.length >= SEASON}/{@code SEASON*2}).
 */
class CrimeForecasterTest {

    private static List<Map<String, Object>> series(int months, java.util.function.IntUnaryOperator valueForIndex) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int year = 2020;
        int month = 1;
        for (int i = 0; i < months; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", String.format("%04d-%02d", year, month));
            row.put("count", valueForIndex.applyAsInt(i));
            rows.add(row);
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        return rows;
    }

    @Test
    void tooShortForSeasonalUsesLinearTrend() {
        List<Map<String, Object>> data = series(6, i -> 10 + i);
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 3);
        assertThat(f.method()).isEqualTo("linear-trend");
        assertThat(f.forecast()).hasSize(3);
    }

    @Test
    void oneToTwoSeasonsUsesSeasonalNaive() {
        List<Map<String, Object>> data = series(18, i -> 10 + (i % 12));
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 6);
        assertThat(f.method()).isEqualTo("seasonal-naive");
    }

    @Test
    void twoFullSeasonsUsesHoltWinters() {
        List<Map<String, Object>> data = series(24, i -> 10 + (i % 12));
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 6);
        assertThat(f.method()).isEqualTo("holt-winters");
    }

    @Test
    void forecastPointsCarryPredictionIntervals() {
        // A noisy-ish series so sigma > 0 and the interval actually has width.
        List<Map<String, Object>> data = series(30, i -> 20 + (i % 3 == 0 ? 5 : 0));
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 4);
        for (CrimeForecaster.Point p : f.forecast()) {
            assertThat(p.low()).isNotNull();
            assertThat(p.high()).isNotNull();
            assertThat(p.low()).isLessThanOrEqualTo(p.value());
            assertThat(p.high()).isGreaterThanOrEqualTo(p.value());
        }
        // History points are actuals, not predictions — no interval.
        for (CrimeForecaster.Point p : f.history()) {
            assertThat(p.low()).isNull();
            assertThat(p.high()).isNull();
        }
    }

    @Test
    void intervalWidensWithHorizon() {
        List<Map<String, Object>> data = series(30, i -> 20 + (i % 4 == 0 ? 8 : 0));
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 6);
        List<CrimeForecaster.Point> fc = f.forecast();
        double firstWidth = fc.get(0).high() - fc.get(0).low();
        double lastWidth = fc.get(fc.size() - 1).high() - fc.get(fc.size() - 1).low();
        assertThat(lastWidth).isGreaterThanOrEqualTo(firstWidth);
    }

    @Test
    void backtestOnAPerfectlyPredictableSeriesHasNearZeroError() {
        // Flat series: whatever method runs, predicting "the same value again" is exactly right.
        List<Map<String, Object>> data = series(30, i -> 50);
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 3);
        assertThat(f.backtest()).isNotNull();
        assertThat(f.backtest().mape()).isLessThan(1.0);
        assertThat(f.backtest().rmse()).isLessThan(1.0);
    }

    @Test
    void backtestIsNullWhenHistoryIsTooShortToHoldOutAWindow() {
        List<Map<String, Object>> data = series(4, i -> 10 + i);
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(data, 2);
        assertThat(f.backtest()).isNull();
    }

    @Test
    void noDataReturnsEmptyForecastNotAnException() {
        CrimeForecaster.Forecast f = CrimeForecaster.forecast(List.of(), 6);
        assertThat(f.method()).isEqualTo("no-data");
        assertThat(f.history()).isEmpty();
        assertThat(f.forecast()).isEmpty();
        assertThat(f.backtest()).isNull();
    }
}
