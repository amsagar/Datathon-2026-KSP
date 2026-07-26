import React from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { TemplateComponentProps } from './types';

// Theme-aware series colours (defined for light + dark in tailwind.css).
const COLORS = [
  'var(--chart-1)',
  'var(--chart-2)',
  'var(--chart-3)',
  'var(--chart-4)',
  'var(--chart-5)',
];

interface Point {
  x?: string;
  y?: number;
}
interface Series {
  name?: string;
  points?: Point[];
}
interface TrendData {
  title?: string;
  series?: Series[];
}

/** Time-series line chart (template: crime-trend-line). */
const CrimeTrendLine: React.FC<TemplateComponentProps> = ({ data }) => {
  const d = (data ?? {}) as TrendData;
  const series = (Array.isArray(d.series) ? d.series : []).filter(
    (s) => Array.isArray(s.points) && s.points.length
  );
  if (!series.length) return null;

  // Merge all series onto a shared x axis.
  const xs = Array.from(
    new Set(series.flatMap((s) => (s.points ?? []).map((p) => String(p.x))))
  ).sort();
  const chartData = xs.map((x) => {
    const row: Record<string, string | number | null> = { x };
    series.forEach((s, i) => {
      const point = (s.points ?? []).find((p) => String(p.x) === x);
      row[s.name || `series${i}`] = point?.y ?? null;
    });
    return row;
  });

  return (
    <div style={{ margin: d.title ? '8px 0' : 0 }}>
      {d.title && (
        <span
          style={{
            display: 'block',
            marginBottom: 8,
            fontSize: 14,
            fontWeight: 600,
            color: 'var(--foreground)',
          }}
        >
          {d.title}
        </span>
      )}
      <ResponsiveContainer width="100%" height={380}>
        <LineChart data={chartData} margin={{ top: 8, right: 20, bottom: 8, left: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis
            dataKey="x"
            tick={{ fontSize: 12, fill: 'var(--muted-foreground)' }}
            stroke="var(--border)"
            minTickGap={28}
          />
          <YAxis
            tick={{ fontSize: 12, fill: 'var(--muted-foreground)' }}
            stroke="var(--border)"
            allowDecimals={false}
            width={44}
          />
          <Tooltip
            contentStyle={{
              background: 'var(--card)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
              fontSize: 12,
              color: 'var(--card-foreground)',
            }}
            labelStyle={{ color: 'var(--muted-foreground)', marginBottom: 4, fontWeight: 600 }}
            itemStyle={{ color: 'var(--card-foreground)' }}
            cursor={{ stroke: 'var(--border)', strokeWidth: 1 }}
          />
          {series.length > 1 && <Legend wrapperStyle={{ fontSize: 13, paddingTop: 8 }} />}
          {series.map((s, i) => (
            <Line
              key={s.name || i}
              type="monotone"
              dataKey={s.name || `series${i}`}
              stroke={COLORS[i % COLORS.length]}
              dot={false}
              strokeWidth={2.5}
              connectNulls
              activeDot={{ r: 4 }}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default CrimeTrendLine;
