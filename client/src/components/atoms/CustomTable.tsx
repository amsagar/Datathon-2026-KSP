import React, { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';
import CustomButton from './CustomButton';
import { cn } from '@/lib/utils';

export type CustomColumnAlign =
  | 'left'
  | 'center'
  | 'right'
  | 'start'
  | 'end'
  | 'justify';

/**
 * antd-compatible column definition. Kept structurally compatible with
 * `antd`'s `ColumnType` (title / dataIndex / key / width / align / render)
 * so existing call sites keep type-checking, while the atom itself has no
 * antd dependency. Extra antd-only keys (filters, sorter, …) are tolerated
 * at call sites (named type → no excess-property check) and ignored by the
 * shadcn renderer.
 */
export interface CustomColumn<T = Record<string, unknown>> {
  // `title`/`render` are widened to stay structurally compatible with antd's
  // `ColumnType` (title can be a render fn; render may return a RenderedCell).
  title?: React.ReactNode | ((...args: any[]) => React.ReactNode);
  // A record key, a nested key path, or (antd) `keyof T`. Widened to `any`
  // so antd's broader `DataIndex<T>` stays assignable; only string/number/
  // array paths are actually resolved by the renderer.
  dataIndex?: any;
  key?: React.Key;
  width?: number | string;
  // Accepts any CSS text-align keyword (antd's AlignType) — only the common
  // ones map to a class; the rest fall back to the default left alignment.
  align?: CustomColumnAlign | (string & {});
  render?: (value: any, record: T, index: number) => any;
  className?: string;
  // Extra antd-only column keys (filters, sorter, fixed, …) are accepted at
  // call sites since this is a named type (no excess-property check) and are
  // simply ignored by the shadcn renderer.
}

export interface CustomTablePagination {
  pageSize?: number;
  current?: number;
  total?: number;
  size?: 'small' | 'default' | 'middle';
  onChange?: (page: number, pageSize: number) => void;
  [prop: string]: unknown;
}

export interface CustomTableProps<T extends object = Record<string, unknown>> {
  columns: ReadonlyArray<CustomColumn<T>>;
  dataSource?: readonly T[];
  /** Key extractor. Defaults to the record's `key` field (antd behaviour). */
  rowKey?: string | ((record: T, index: number) => React.Key);
  loading?: boolean;
  /** Falsy → no pager; truthy/object → simple client-side pager. */
  pagination?: boolean | CustomTablePagination;
  locale?: { emptyText?: React.ReactNode };
  size?: 'small' | 'middle' | 'large';
  className?: string;
  onRow?: (record: T, index: number) => React.HTMLAttributes<HTMLElement>;
  rowClassName?: string | ((record: T, index: number) => string);
}

const alignClass: Record<string, string> = {
  left: 'text-left',
  start: 'text-left',
  center: 'text-center',
  right: 'text-right',
  end: 'text-right',
  justify: 'text-justify',
};

const alignCls = (align?: CustomColumnAlign | (string & {})) =>
  align ? alignClass[align] : undefined;

const sizePadding: Record<NonNullable<CustomTableProps['size']>, string> = {
  small: 'py-1',
  middle: 'py-2',
  large: 'py-3.5',
};

function getCellValue<T>(
  record: T,
  dataIndex: CustomColumn<T>['dataIndex'],
): unknown {
  if (dataIndex == null) return undefined;
  const path = Array.isArray(dataIndex) ? dataIndex : [dataIndex];
  let current: unknown = record;
  for (const seg of path) {
    if (current == null) return undefined;
    current = (current as Record<string | number, unknown>)[seg];
  }
  return current;
}

function resolveRowKey<T extends object>(
  record: T,
  index: number,
  rowKey: CustomTableProps<T>['rowKey'],
): React.Key {
  if (typeof rowKey === 'function') return rowKey(record, index);
  const key = (record as Record<string, unknown>)[rowKey ?? 'key'];
  if (typeof key === 'string' || typeof key === 'number') return key;
  return index;
}

function CustomTable<T extends object = Record<string, unknown>>({
  columns,
  dataSource = [],
  rowKey = 'key',
  loading = false,
  pagination = false,
  locale,
  size = 'middle',
  className,
  onRow,
  rowClassName,
}: CustomTableProps<T>) {
  const pagerEnabled = Boolean(pagination);
  const pageSize = useMemo(() => {
    if (pagination && typeof pagination === 'object') {
      return pagination.pageSize ?? 10;
    }
    return 10;
  }, [pagination]);

  const [page, setPage] = useState(1);

  // Reset to the first page when the data shrinks below the current page.
  const pageCount = Math.max(1, Math.ceil(dataSource.length / pageSize));
  useEffect(() => {
    if (page > pageCount) setPage(pageCount);
  }, [page, pageCount]);

  const visibleRows = useMemo(() => {
    if (!pagerEnabled) return dataSource;
    const start = (page - 1) * pageSize;
    return dataSource.slice(start, start + pageSize);
  }, [dataSource, pagerEnabled, page, pageSize]);

  const cellPad = sizePadding[size];
  const cellText = size === 'small' ? 'text-xs' : 'text-sm';

  const renderCells = (record: T, rowIndex: number) =>
    columns.map((col, colIndex) => {
      const value = getCellValue(record, col.dataIndex);
      const content = col.render
        ? col.render(value, record, rowIndex)
        : (value as React.ReactNode);
      return (
        <TableCell
          key={col.key ?? (col.dataIndex as React.Key) ?? colIndex}
          className={cn(
            cellPad,
            cellText,
            alignCls(col.align),
            col.className,
          )}
        >
          {content}
        </TableCell>
      );
    });

  return (
    <div className={cn('w-full', className)}>
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((col, colIndex) => (
              <TableHead
                key={col.key ?? (col.dataIndex as React.Key) ?? colIndex}
                style={col.width != null ? { width: col.width } : undefined}
                className={cn(alignCls(col.align))}
              >
                {typeof col.title === 'function' ? col.title() : col.title}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading ? (
            Array.from({ length: Math.min(pageSize, 5) }).map((_, r) => (
              <TableRow key={`skeleton-${r}`}>
                {columns.map((col, c) => (
                  <TableCell
                    key={c}
                    className={cn(cellPad, alignCls(col.align))}
                  >
                    <Skeleton className="h-4 w-full" />
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : visibleRows.length === 0 ? (
            <TableRow className="hover:bg-transparent">
              <TableCell
                colSpan={columns.length}
                className="text-muted-foreground py-8 text-center text-sm"
              >
                {locale?.emptyText ?? 'No data'}
              </TableCell>
            </TableRow>
          ) : (
            visibleRows.map((record, rowIndex) => {
              const absoluteIndex = pagerEnabled
                ? (page - 1) * pageSize + rowIndex
                : rowIndex;
              const rowProps = onRow?.(record, absoluteIndex);
              const extraClass =
                typeof rowClassName === 'function'
                  ? rowClassName(record, absoluteIndex)
                  : rowClassName;
              return (
                <TableRow
                  key={resolveRowKey(record, absoluteIndex, rowKey)}
                  {...rowProps}
                  className={cn(
                    onRow && 'cursor-pointer',
                    extraClass,
                    rowProps?.className,
                  )}
                >
                  {renderCells(record, absoluteIndex)}
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>

      {pagerEnabled && !loading && dataSource.length > 0 && (
        <div className="text-muted-foreground flex items-center justify-end gap-3 px-2 py-3 text-sm">
          <span>
            {dataSource.length} item{dataSource.length === 1 ? '' : 's'}
          </span>
          <div className="flex items-center gap-1">
            <CustomButton
              variant="ghost"
              size="small"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              aria-label="Previous page"
            >
              <ChevronLeft className="size-4" aria-hidden />
            </CustomButton>
            <span className="min-w-[4.5rem] text-center">
              {page} / {pageCount}
            </span>
            <CustomButton
              variant="ghost"
              size="small"
              disabled={page >= pageCount}
              onClick={() => setPage((p) => Math.min(pageCount, p + 1))}
              aria-label="Next page"
            >
              <ChevronRight className="size-4" aria-hidden />
            </CustomButton>
          </div>
        </div>
      )}
    </div>
  );
}

export default CustomTable;
