import { Box, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TableSortLabel, Typography } from '@mui/material'
import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { useState, type ReactNode } from 'react'
import { colors } from '../../theme/tokens'
import { EmptyState } from '../common/PageState'

export interface DataTableProps<T> {
  data: T[]
  columns: ColumnDef<T, unknown>[]
  emptyTitle?: string
  emptyHint?: string
  emptyAction?: ReactNode
  pageSize?: number
  /** Keys of columns whose cells are right-aligned numbers. */
  numeric?: string[]
  rowHighlight?: (row: T) => boolean
  caption?: string
}

/** Sortable, paginated table (TanStack Table) with MUI styling. Wide tables scroll inside their box. */
export function DataTable<T>({ data, columns, emptyTitle = 'Nothing to show yet', emptyHint, emptyAction, pageSize = 25, numeric = [], rowHighlight, caption }: DataTableProps<T>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize } },
  })

  if (data.length === 0) {
    return <EmptyState title={emptyTitle} hint={emptyHint} action={emptyAction} />
  }

  const { pageIndex } = table.getState().pagination
  const total = data.length
  const from = pageIndex * pageSize + 1
  const to = Math.min(total, from + pageSize - 1)

  return (
    <Box>
      <TableContainer sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label={caption}>
          <TableHead>
            {table.getHeaderGroups().map((group) => (
              <TableRow key={group.id}>
                {group.headers.map((header) => {
                  const isNum = numeric.includes(header.column.id)
                  const sorted = header.column.getIsSorted()
                  return (
                    <TableCell
                      key={header.id}
                      align={isNum ? 'right' : 'left'}
                      sx={{ fontSize: 12, fontWeight: 600, color: colors.textMuted, textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap', py: 1.25 }}
                    >
                      {header.isPlaceholder ? null : header.column.getCanSort() ? (
                        <TableSortLabel active={!!sorted} direction={sorted === 'desc' ? 'desc' : 'asc'} onClick={header.column.getToggleSortingHandler()}>
                          {flexRender(header.column.columnDef.header, header.getContext())}
                        </TableSortLabel>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </TableCell>
                  )
                })}
              </TableRow>
            ))}
          </TableHead>
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow key={row.id} hover sx={{ bgcolor: rowHighlight?.(row.original) ? colors.indigo50 : undefined }}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell
                    key={cell.id}
                    align={numeric.includes(cell.column.id) ? 'right' : 'left'}
                    sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums', fontSize: 14, py: 1.25 }}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {total > pageSize && (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1, px: 1.5, pt: 1.5 }}>
          <Typography variant="body2" color="text.secondary">Showing {from} to {to} of {total}</Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button size="small" variant="outlined" color="inherit" onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()} sx={{ minHeight: 36 }}>Previous</Button>
            <Button size="small" variant="outlined" color="inherit" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()} sx={{ minHeight: 36 }}>Next</Button>
          </Box>
        </Box>
      )}
    </Box>
  )
}
