import { Link } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { Link as RouterLink } from 'react-router-dom'
import type { Transaction } from '../../types/domain'
import { amount, dateTime } from '../../utils/format'
import { StatusChip } from '../common/StatusChip'
import { DataTable } from './DataTable'

const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 13 }

export function TransactionTable({ rows, compact, emptyHint }: { rows: Transaction[]; compact?: boolean; emptyHint?: string }) {
  const columns: ColumnDef<Transaction, unknown>[] = [
    {
      id: 'reference',
      header: 'Reference',
      accessorKey: 'referenceNo',
      cell: ({ row }) => <Link component={RouterLink} to={`/transactions/${row.original.id}`} sx={mono}>{row.original.referenceNo}</Link>,
    },
    { id: 'date', header: 'Date and time', accessorKey: 'createdAt', cell: ({ getValue }) => dateTime(getValue() as string) },
    { id: 'type', header: 'Type', accessorKey: 'typeLabel' },
    ...(compact
      ? []
      : ([
          { id: 'from', header: 'From', accessorFn: (t) => t.fromCode ?? '', cell: ({ getValue }) => <span style={mono}>{getValue() as string}</span> },
        ] as ColumnDef<Transaction, unknown>[])),
    { id: 'to', header: compact ? 'Party' : 'To', accessorFn: (t) => t.toCode ?? t.fromCode ?? '', cell: ({ getValue }) => <span style={mono}>{getValue() as string}</span> },
    { id: 'amount', header: 'Amount (₹)', accessorKey: 'amount', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'status', header: 'Status', accessorKey: 'status', cell: ({ getValue }) => <StatusChip status={getValue() as string} /> },
  ]
  return <DataTable data={rows} columns={columns} numeric={['amount']} emptyTitle="No transactions yet" emptyHint={emptyHint} caption="Transactions" />
}
