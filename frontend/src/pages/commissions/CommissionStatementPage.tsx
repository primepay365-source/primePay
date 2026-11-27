import { Box, Button, Chip, Link, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { KpiRow } from '../../components/common/Kpis'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { RoleChip } from '../../components/common/RoleChip'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { DataTable } from '../../components/tables/DataTable'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import type { Role } from '../../types/auth'
import type { CommissionStatement } from '../../types/domain'
import { describeRate } from '../../utils/commission'
import { amount, dateTime, money, titleCase } from '../../utils/format'

type Line = CommissionStatement['lines'][number]

/** Commission statement: totals, configured rates, child-wise summary and transaction-wise lines. */
export function CommissionStatementPage() {
  const { user } = useAuth()
  const [status, setStatus] = useState('')
  const statement = useApiData(() => api.get<CommissionStatement>('/commissions/statement', { status }), [status])
  const s = statement.data
  const platform = s?.scope === 'PLATFORM'

  const columns: ColumnDef<Line, unknown>[] = [
    { id: 'date', header: 'Date', accessorKey: 'createdAt', cell: ({ getValue }) => dateTime(getValue() as string) },
    { id: 'ref', header: 'Transaction', accessorKey: 'transactionRef', cell: ({ row }) => <Link component={RouterLink} to={`/transactions/${row.original.transactionId}`} sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{row.original.transactionRef}</Link> },
    { id: 'from', header: 'Made by', accessorKey: 'fromCode' },
    { id: 'service', header: 'Service', accessorKey: 'service' },
    { id: 'base', header: 'Txn amount (₹)', accessorKey: 'baseAmount', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'rule', header: 'Rule', accessorKey: 'ruleCode' },
    { id: 'level', header: platform ? 'Level · earner' : 'Level', accessorKey: 'levelRole', cell: ({ getValue }) => titleCase(getValue() as string) },
    { id: 'amount', header: platform ? 'Commission (₹)' : 'Your share (₹)', accessorKey: 'amount', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'status', header: 'Status', accessorKey: 'status', cell: ({ getValue }) => <StatusChip status={getValue() as string} label={getValue() === 'CREDITED' ? 'Paid' : getValue() === 'PLATFORM' ? 'Platform share' : undefined} /> },
  ]

  return (
    <>
      <PageHeader eyebrow={`${platform ? 'Whole platform' : 'Earnings'} · ${user?.accountCode ?? ''}`} title={platform ? 'Commission report' : 'Commission statement'}
        subtitle={platform ? 'Commission generated on every transaction: what was paid to each level of the network and the platform share.' : 'What you earned on your own transactions and on transactions in your network. Rates are set by the Platform Admin.'}
        actions={<Button variant="outlined" color="inherit" onClick={() => api.download('/commissions/statement.csv', 'primepay-commission-statement.csv', { status })}>Export CSV</Button>} />
      {statement.error && <ErrorCard message={statement.error} onRetry={statement.reload} />}
      {!s && !statement.error && <LoadingCard />}
      {s && (
        <>
          <KpiRow kpis={[
            { label: platform ? 'Total commission generated' : 'Total commission earned', value: s.earnedAllTime, format: 'money', hint: platform ? `${money(s.platformShareAllTime)} platform share` : 'All time, before reversals', tone: 'dark' },
            { label: platform ? 'Paid to the network' : 'Paid to your wallet', value: s.paidAllTime, format: 'money', hint: 'Credited when the transaction succeeds', tone: null },
            { label: 'Pending', value: s.pendingAmount, format: 'money', hint: `${s.pendingTransactions} transaction${s.pendingTransactions === 1 ? '' : 's'} awaiting a final status`, tone: null },
            { label: 'Reversed', value: s.reversedAllTime, format: 'money', hint: 'Taken back when a transaction was reversed', tone: null },
            { label: 'This month', value: s.earnedThisMonth, format: 'money', hint: null, tone: null },
          ]} />

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
            {!platform && (
              <Section title="My commission rates" sx={{ flex: '1 1 360px', minWidth: 0 }}>
                {s.myRates.length === 0 && <Typography color="text.secondary" variant="body2">No active rule pays your level right now.</Typography>}
                {s.myRates.map((r) => (
                  <Box key={r.ruleCode} sx={{ borderBottom: 1, borderColor: 'divider', pb: 1 }}>
                    <Typography sx={{ fontWeight: 700 }}>{titleCase(r.serviceCode)} <Typography component="span" variant="caption" color="text.secondary">· {r.ruleCode}</Typography></Typography>
                    <Typography variant="body2" color="text.secondary">{describeRate(r)}</Typography>
                  </Box>
                ))}
                <Typography variant="caption" color="text.secondary">Your level earns this share on your own transactions and on every transaction in your network.</Typography>
              </Section>
            )}
            <Section title={platform ? 'Commission by Super Distributor network' : 'Child-wise commission'} padded={false} sx={{ flex: '2 1 480px', minWidth: 0 }}>
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="Child-wise commission">
                  <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Level</TableCell><TableCell align="right">Commission rows</TableCell><TableCell align="right">{platform ? 'Commission generated' : 'You earned'}</TableCell></TableRow></TableHead>
                  <TableBody>
                    {s.children.length === 0 && <TableRow><TableCell colSpan={4}><Typography variant="body2" color="text.secondary">No accounts below you yet.</Typography></TableCell></TableRow>}
                    {s.children.map((c) => (
                      <TableRow key={c.accountId} hover>
                        <TableCell>
                          {c.accountId === user?.accountId ? <b>Own transactions</b> : <Link component={RouterLink} to={`/users/${c.accountId}`}>{c.accountCode} · {c.name}</Link>}
                        </TableCell>
                        <TableCell><RoleChip role={c.role as Role} /></TableCell>
                        <TableCell align="right">{c.entries}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(c.commission)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </Section>
          </Box>

          <Box role="group" aria-label="Entry status" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {([['', 'All'], ['CREDITED', 'Paid'], ['REVERSED', 'Reversed'], ...(platform ? [['PLATFORM', 'Platform share']] : [])] as string[][]).map(([v, l]) => (
              <Chip key={l} label={l} onClick={() => setStatus(v)} color={status === v ? 'secondary' : 'default'} variant={status === v ? 'filled' : 'outlined'} aria-pressed={status === v} sx={{ height: 36 }} />
            ))}
          </Box>
          <Section title="Transaction-wise commission" padded={false}>
            <DataTable data={s.lines} columns={columns} numeric={['base', 'amount']} caption="Commission history" emptyTitle="No commission yet"
              emptyHint="Commission is credited when a bill payment or withdrawal in your network succeeds." />
          </Section>
        </>
      )}
    </>
  )
}
