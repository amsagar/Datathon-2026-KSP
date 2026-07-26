import React from 'react';
import AtomTable, {
  type CustomColumn,
  type CustomTablePagination,
} from '@atoms/CustomTable';

/**
 * Rich table wrapper — historically an antd `Table` wrapper with column filter
 * popups (via `@atoms/CustomFilter`) and server-side pagination. Rebuilt on the
 * shadcn-based `@atoms/CustomTable` (no antd). The common path — columns render,
 * dataSource, rowKey, loading, empty state, onRow, client-side pagination — is
 * preserved. See DROPPED notes below for antd-only features that were removed.
 *
 * Consumers: AuthProfilesPage, DocumentsPage (neither uses pagination/filters).
 */

/**
 * Column definition kept structurally compatible with the previous antd
 * `TableColumnsType` call sites. Extends the atom's `CustomColumn` with the
 * antd-only keys the pages declared (`ellipsis`, `filters`, `sorter`,
 * `filterDropdown`) so those literals keep type-checking; the extra keys are
 * ignored by the shadcn renderer.
 */
export type CustomTableColumn<T> = CustomColumn<T> & {
  ellipsis?: boolean;
  filters?: { text: React.ReactNode; value: string }[];
  sorter?: unknown;
  filterDropdown?: unknown;
};

export type CustomTableColumnsType<T> = CustomTableColumn<T>[];

export interface CustomTableProps<T extends object> {
  dataSource: T[];
  columns: CustomTableColumnsType<T>;
  rowKey?: string | ((record: T, index: number) => React.Key);
  totalElements?: number;
  currentPage?: number;
  pageSize?: number;
  onPageChange?: (page: number, pageSize: number) => void;
  includePagination?: boolean;
  loading?: boolean;
  scrollY?: number;
  onRow?: (record: T, index?: number) => React.HTMLAttributes<HTMLElement>;
  // Accepted for source-compat with former antd `onChange`; unused now that
  // interactive sort/filter is not wired up. DROPPED.
  onChange?: unknown;
}

function CustomTable<T extends object>({
  dataSource,
  columns,
  rowKey = 'key',
  currentPage = 1,
  pageSize,
  onPageChange,
  includePagination,
  loading,
  onRow,
}: CustomTableProps<T>) {
  // DROPPED (no consumer relies on these):
  //  - Per-column interactive filter popups (antd `filters`/`filterDropdown`
  //    via CustomFilter) and sorters. Data default order is preserved.
  //  - Server-side pagination (totalElements/onPageChange). When
  //    `includePagination` is set we fall back to the atom's client-side pager.
  //  - `scrollY` sticky-header scroll region and per-cell Poppins font override.
  const pagination: CustomTablePagination | false = includePagination
    ? { pageSize: pageSize ?? 10, current: currentPage, onChange: onPageChange }
    : false;

  return (
    <AtomTable<T>
      rowKey={rowKey}
      columns={columns as ReadonlyArray<CustomColumn<T>>}
      dataSource={dataSource}
      pagination={pagination}
      loading={loading}
      onRow={onRow ? (record, index) => onRow(record, index) : undefined}
    />
  );
}

export default CustomTable;
