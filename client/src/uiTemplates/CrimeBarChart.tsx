import React from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { TemplateComponentProps } from './types';

const COLORS = ['#b01722', '#c9962b', '#27ae60', '#8e44ad', '#2c7fb8', '#16a085', '#7f8c8d'];

interface Series {
  name?: string;
  values?: number[];
}
interface BarData {
  title?: string;
  categories?: string[];
  series?: Series[];
}

/** Categorical bar chart (template: crime-bar-chart). */
const CrimeBarChart: React.FC<TemplateComponentProps> = ({ data }) => {
  const d = (data ?? {}) as BarData;
  const categories = Array.isArray(d.categories) ? d.categories : [];
  const series = (Array.isArray(d.series) ? d.series : []).filter((s) => Array.isArray(s.values));
  if (!categories.length || !series.length) return null;

  const chartData = categories.map((c, i) => {
    const row: Record<string, string | number> = { category: c };
    series.forEach((s, si) => {
      row[s.name || `series${si}`] = s.values?.[i] ?? 0;
    });
    return row;
  });

  return (
    <div className="my-2">
      {d.title && (
        <span className="mb-2 block text-sm font-semibold text-foreground">{d.title}</span>
      )}
      <ResponsiveContainer width="100%" height={Math.max(240, categories.length * 8)}>
        <BarChart data={chartData} margin={{ top: 4, right: 16, bottom: 4, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis
            dataKey="category"
            tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
            interval={0}
            angle={categories.length > 8 ? -35 : 0}
            textAnchor={categories.length > 8 ? 'end' : 'middle'}
            height={categories.length > 8 ? 70 : 30}
          />
          <YAxis tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} allowDecimals={false} />
          <Tooltip
            contentStyle={{
              background: 'var(--popover)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              color: 'var(--popover-foreground)',
            }}
          />
          {series.length > 1 && <Legend />}
          {series.map((s, i) => (
            <Bar
              key={s.name || i}
              dataKey={s.name || `series${i}`}
              fill={COLORS[i % COLORS.length]}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default CrimeBarChart;
