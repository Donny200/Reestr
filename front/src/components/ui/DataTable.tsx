import { useState, type ReactNode } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type RowData,
  type SortingState,
} from '@tanstack/react-table';
import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon, ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { Button } from './Button';
import { SkeletonRows } from './Skeleton';
import { EmptyState, ErrorState } from './States';

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData extends RowData, TValue> {
    className?: string;
    headerClassName?: string;
    hideBelow?: 'sm' | 'md' | 'lg' | 'xl';
    align?: 'left' | 'right';
    mobileHidden?: boolean;
  }
}

const hiddenBelow = {
  sm: 'hidden sm:table-cell',
  md: 'hidden md:table-cell',
  lg: 'hidden lg:table-cell',
  xl: 'hidden xl:table-cell',
} as const;

export interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  rowKey: (row: T) => string | number;
  empty: { title: string; description?: string; action?: ReactNode };
  caption?: string;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  pageSize?: number;
  initialSorting?: SortingState;
}

const STACK_LIMIT = 4;

export function DataTable<T>({ columns, data, rowKey, caption, loading = false, error, onRetry, empty, pageSize = 25, initialSorting = [] }: DataTableProps<T>) {
  const { t } = useI18n();
  const [sorting, setSorting] = useState<SortingState>(initialSorting);
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getRowId: (row) => String(rowKey(row)),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize } },
  });

  if (loading) return <SkeletonRows rows={6} columns={Math.min(columns.length, 6)} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (data.length === 0) return <EmptyState title={empty.title} description={empty.description} action={empty.action} />;

  const rows = table.getRowModel().rows;
  const total = table.getPrePaginationRowModel().rows.length;
  const { pageIndex, pageSize: size } = table.getState().pagination;
  const from = pageIndex * size + 1;
  const to = Math.min(total, (pageIndex + 1) * size);
  const stacked = columns.filter((column) => !column.meta?.mobileHidden).length <= STACK_LIMIT;

  return (
    <div>
      <div className={cn(!stacked && 'max-lg:overflow-x-auto')}>
        <table className={cn('w-full border-collapse text-start text-sm', stacked ? 'data-table-stack' : 'data-table-scroll')}>
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="bg-background md:sticky md:top-[var(--sticky-top,0px)] md:z-10">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id} className="border-b border-line">
                {group.headers.map((header) => {
                  const meta = header.column.columnDef.meta;
                  const sorted = header.column.getIsSorted();
                  const label = flexRender(header.column.columnDef.header, header.getContext());
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={sorted === 'asc' ? 'ascending' : sorted === 'desc' ? 'descending' : undefined}
                      className={cn(
                        'micro h-12 px-4 text-start text-secondary first:ps-5 last:pe-5',
                        meta?.hideBelow && hiddenBelow[meta.hideBelow],
                        meta?.align === 'right' && 'text-end',
                        meta?.headerClassName,
                      )}
                    >
                      {header.isPlaceholder ? null : header.column.getCanSort() ? (
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className={cn('micro inline-flex min-h-9 items-center gap-1.5 rounded-sm transition-colors fine:hover:text-foreground', meta?.align === 'right' && 'flex-row-reverse')}
                        >
                          {label}
                          {sorted === 'asc' ? (
                            <ArrowUpIcon className="h-3.5 w-3.5 text-accent-text" aria-hidden="true" />
                          ) : sorted === 'desc' ? (
                            <ArrowDownIcon className="h-3.5 w-3.5 text-accent-text" aria-hidden="true" />
                          ) : (
                            <ArrowUpDownIcon className="h-3.5 w-3.5 text-subtle" aria-hidden="true" />
                          )}
                        </button>
                      ) : label}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className="border-b border-line transition-colors duration-snap last:border-b-0 fine:hover:bg-surface">
                {row.getVisibleCells().map((cell) => {
                  const meta = cell.column.columnDef.meta;
                  const header = cell.column.columnDef.header;
                  return (
                    <td
                      key={cell.id}
                      data-label={typeof header === 'string' ? header : cell.column.id}
                      className={cn(
                        'px-4 py-3.5 align-middle text-foreground first:ps-5 last:pe-5',
                        meta?.hideBelow && hiddenBelow[meta.hideBelow],
                        meta?.align === 'right' && 'text-end',
                        meta?.mobileHidden && 'max-md:hidden',
                        meta?.className,
                      )}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {total > size && (
        <div className="flex flex-col gap-3 border-t border-line px-5 py-3 text-sm text-secondary sm:flex-row sm:items-center sm:justify-between">
          <p aria-live="polite" className="tabular-nums">{from}–{to} / {total}</p>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" disabled={!table.getCanPreviousPage()} onClick={() => table.previousPage()} icon={<ChevronLeftIcon />}>
              {t('fnAdmin.previous', 'Previous')}
            </Button>
            <span className="tabular-nums">{pageIndex + 1} / {table.getPageCount()}</span>
            <Button variant="outline" size="sm" disabled={!table.getCanNextPage()} onClick={() => table.nextPage()}>
              {t('fnAdmin.next', 'Next')}
              <ChevronRightIcon className="rtl:-scale-x-100" aria-hidden="true" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
