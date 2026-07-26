package com.ksp.agent.analytics.forecast;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monthly crime-count forecasting over the FIR history. Picks the strongest method the data supports:
 * additive Holt-Winters (level + trend + 12-month seasonality) when there are at least two full
 * seasons, else a seasonal-naive repeat of the last year, else a damped linear trend for short series.
 * Output is a small structure the dashboard overlays on the actual trend line.
 */
public final class CrimeForecaster {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int SEASON = 12;
    // Fixed smoothing constants — deliberately simple and reproducible (no per-series fitting).
    private static final double ALPHA = 0.4; // level
    private static final double BETA = 0.1;  // trend
    private static final double GAMMA = 0.3; // seasonality

    private CrimeForecaster() {}

    /** low/high are a 90%-ish prediction interval (null on history points, or when too little data
     * exists to estimate residual spread); null rather than 0 so callers can tell "no interval"
     * apart from "zero-width interval". */
    public record Point(String period, double value, Double low, Double high) {
        public Point(String period, double value) {
            this(period, value, null, null);
        }
    }

    /**
     * Backtest against the tail of the ACTUAL series: fit on everything except the last
     * {@code holdoutMonths}, forecast that many months forward, and compare to what really
     * happened. Makes the forecast falsifiable instead of an unverified curve — doubles as the
     * accuracy benchmark for the PPT slide.
     */
    public record Backtest(int holdoutMonths, double mape, double rmse) {}

    public record Forecast(List<Point> history, List<Point> forecast, String method, Backtest backtest) {}

    /**
     * @param series ordered rows of {period 'YYYY-MM', count} (may have gaps — they are zero-filled)
     * @param horizon months to project forward (clamped 1..24)
     */
    public static Forecast forecast(List<Map<String, Object>> series, int horizon) {
        int h = Math.max(1, Math.min(horizon, 24));
        List<Point> history = zeroFill(series);
        if (history.isEmpty()) {
            return new Forecast(List.of(), List.of(), "no-data", null);
        }
        double[] y = history.stream().mapToDouble(Point::value).toArray();
        YearMonth last = YearMonth.parse(history.get(history.size() - 1).period(), YM);

        Result r = runMethod(y, h, last);
        Backtest bt = backtest(y, last, r.method);
        return new Forecast(history, r.points, r.method, bt);
    }

    private record Result(List<Point> points, String method) {}

    private static Result runMethod(double[] y, int h, YearMonth last) {
        if (y.length >= SEASON * 2) {
            return new Result(holtWinters(y, h, last), "holt-winters");
        } else if (y.length >= SEASON) {
            return new Result(seasonalNaive(y, h, last), "seasonal-naive");
        } else {
            return new Result(linearTrend(y, h, last), "linear-trend");
        }
    }

    /**
     * Holds out the last {@code holdoutMonths} (min 3, max 6, capped at a third of the series) of
     * the ACTUAL series, re-fits on the rest with the SAME method, and scores the held-out months.
     * Returns null when there isn't enough history to hold out a meaningful window.
     */
    private static Backtest backtest(double[] y, YearMonth last, String method) {
        int holdout = Math.min(6, Math.max(3, y.length / 6));
        if (y.length < holdout * 2) {
            return null;
        }
        double[] train = java.util.Arrays.copyOfRange(y, 0, y.length - holdout);
        YearMonth trainLast = last.minusMonths(holdout);
        Result r = runMethod(train, holdout, trainLast);
        double sumAbsPct = 0;
        double sumSq = 0;
        int n = 0;
        for (int i = 0; i < holdout; i++) {
            double actual = y[y.length - holdout + i];
            double predicted = r.points.get(i).value();
            double err = actual - predicted;
            sumSq += err * err;
            if (actual > 0) {
                sumAbsPct += Math.abs(err) / actual;
                n++;
            }
        }
        double mape = n == 0 ? 0 : round1(100.0 * sumAbsPct / n);
        double rmse = round1(Math.sqrt(sumSq / holdout));
        return new Backtest(holdout, mape, rmse);
    }

    private static List<Point> holtWinters(double[] y, int h, YearMonth last) {
        int m = SEASON;
        double[] season = new double[m];
        double seasonAvg = 0;
        for (int i = 0; i < m; i++) {
            season[i] = y[i];
            seasonAvg += y[i];
        }
        seasonAvg /= m;
        for (int i = 0; i < m; i++) {
            season[i] = y[i] - seasonAvg; // additive seasonal component
        }
        double level = seasonAvg;
        double trend = (avg(y, m, 2 * m) - avg(y, 0, m)) / m;

        double residualSumSq = 0;
        for (int t = 0; t < y.length; t++) {
            int s = t % m;
            double oneStepAhead = level + trend + season[s];
            double err = y[t] - oneStepAhead;
            residualSumSq += err * err;
            double prevLevel = level;
            level = ALPHA * (y[t] - season[s]) + (1 - ALPHA) * (level + trend);
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend;
            season[s] = GAMMA * (y[t] - level) + (1 - GAMMA) * season[s];
        }
        double sigma = Math.sqrt(residualSumSq / y.length);
        List<Point> out = new ArrayList<>(h);
        for (int k = 1; k <= h; k++) {
            double val = level + k * trend + season[(y.length + k - 1) % m];
            double v = Math.max(0, round1(val));
            // Interval widens with horizon (sqrt(k), the standard random-walk assumption) since a
            // 6-month-out forecast is genuinely less certain than next month's.
            double halfWidth = 1.645 * sigma * Math.sqrt(k);
            out.add(new Point(last.plusMonths(k).format(YM), v,
                    Math.max(0, round1(v - halfWidth)), round1(v + halfWidth)));
        }
        return out;
    }

    private static List<Point> seasonalNaive(double[] y, int h, YearMonth last) {
        double sigma = residualStdDev(y, SEASON);
        List<Point> out = new ArrayList<>(h);
        for (int k = 1; k <= h; k++) {
            double val = y[y.length - SEASON + ((k - 1) % SEASON)];
            double v = Math.max(0, round1(val));
            double halfWidth = 1.645 * sigma * Math.sqrt(k);
            out.add(new Point(last.plusMonths(k).format(YM), v,
                    Math.max(0, round1(v - halfWidth)), round1(v + halfWidth)));
        }
        return out;
    }

    private static List<Point> linearTrend(double[] y, int h, YearMonth last) {
        int n = y.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += i;
            sy += y[i];
            sxx += (double) i * i;
            sxy += (double) i * y[i];
        }
        double denom = n * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        double residualSumSq = 0;
        for (int i = 0; i < n; i++) {
            double err = y[i] - (intercept + slope * i);
            residualSumSq += err * err;
        }
        double sigma = Math.sqrt(residualSumSq / n);
        List<Point> out = new ArrayList<>(h);
        for (int k = 1; k <= h; k++) {
            double val = intercept + slope * (n - 1 + k);
            double v = Math.max(0, round1(val));
            double halfWidth = 1.645 * sigma * Math.sqrt(k);
            out.add(new Point(last.plusMonths(k).format(YM), v,
                    Math.max(0, round1(v - halfWidth)), round1(v + halfWidth)));
        }
        return out;
    }

    /** One-step-ahead naive residual spread (y[t] vs y[t-period]) — a cheap proxy when there isn't
     * enough data for a fitted model's own residuals. */
    private static double residualStdDev(double[] y, int period) {
        if (y.length <= period) {
            return 0;
        }
        double sumSq = 0;
        int n = 0;
        for (int t = period; t < y.length; t++) {
            double err = y[t] - y[t - period];
            sumSq += err * err;
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(sumSq / n);
    }

    /** Expand a possibly-gappy monthly series into a dense, ordered, zero-filled list of points. */
    private static List<Point> zeroFill(List<Map<String, Object>> series) {
        List<Point> raw = new ArrayList<>();
        for (Map<String, Object> row : series) {
            Object p = row.get("period");
            Object c = row.get("count");
            if (p == null) {
                continue;
            }
            raw.add(new Point(String.valueOf(p), c == null ? 0 : ((Number) c).doubleValue()));
        }
        if (raw.isEmpty()) {
            return raw;
        }
        List<Point> dense = new ArrayList<>();
        YearMonth cursor = YearMonth.parse(raw.get(0).period(), YM);
        YearMonth end = YearMonth.parse(raw.get(raw.size() - 1).period(), YM);
        int idx = 0;
        while (!cursor.isAfter(end)) {
            String key = cursor.format(YM);
            if (idx < raw.size() && raw.get(idx).period().equals(key)) {
                dense.add(raw.get(idx));
                idx++;
            } else {
                dense.add(new Point(key, 0));
            }
            cursor = cursor.plusMonths(1);
        }
        return dense;
    }

    private static double avg(double[] a, int from, int to) {
        double s = 0;
        int n = 0;
        for (int i = from; i < to && i < a.length; i++) {
            s += a[i];
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
