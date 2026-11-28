import { Box, Button, ButtonBase, Link, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { KpiRow } from '../../components/common/Kpis'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { DataTable } from '../../components/tables/DataTable'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { Role } from '../../types/auth'
import type { Report } from '../../types/domain'
import { amount, date, dateTime, todayIso } from '../../utils/format'

interface ReportDef { key: string; label: string; group: string; roles?: Role[] }

const REPORTS: ReportDef[] = [
  { key: 'wallet-load', label: 'Wallet load', group: 'Wallet', roles: ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR'] },
  { key: 'transactions', label: 'Transactions', group: 'Transactions' },
  { key: 'bill-payment', label: 'Bill payments', group: 'Services' },
  { key: 'add-funds', label: 'Add funds', group: 'Services' },
  { key: 'payout', label: 'Withdrawals', group: 'Services' },
  { key: 'failed', label: 'Failed transactions', group: 'Transactions' },
  { key: 'commission', label: 'Commission', group: 'Commission' },
  { key: 'retailer', label: 'Retailers', group: 'Network', roles: ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR'] },
  { key: 'distributor', label: 'Distributors', group: 'Network', roles: ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR'] },
  { key: 'master', label: 'Master Distributors', group: 'Network', roles: ['ADMIN', 'SUPER_DISTRIBUTOR'] },
  { key: 'super', label: 'Super Distributors', group: 'Network', roles: ['ADMIN'] },
  { key: 'business', label: 'Daily business', group: 'Business' },
  { key: 'balances', label: 'Wallet balances', group: 'Wallet' },
  { key: 'audit', label: 'Audit', group: 'Compliance', roles: ['ADMIN'] },
]

function renderCell(key: string, value: unknown, row: Record<string, unknown>, numeric: boolean, type: string) {
  if (value === null || value === undefined) return ''
  if (key === 'status' || key === 'result') return <StatusChip status={String(value)} />
  if ((key === 'reference') && row._id) return <Link component={RouterLink} to={`/transactions/${row._id}`} sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{String(value)}</Link>
  if (key === 'userId' && row._id) return <Link component={RouterLink} to={`/users/${row._id}`}>{String(value)}</Link>
  if (key === 'date' || key === 'time') return type === 'business' ? date(String(value)) : dateTime(String(value))
  if (numeric && typeof value === 'number' && !['count', 'failed', 'network'].includes(key)) return amount(value)
  return String(value)
}

export function ReportsPage() {
  const { user } = useAuth()
  const available = REPORTS.filter((r) => !r.roles || (user && r.roles.includes(user.role)))
  const [type, setType] = useState(available[0]?.key ?? 'transactions')
  const [from, setFrom] = useState(todayIso(-29))
  const [to, setTo] = useState(todayIso())
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 300)
    return () => clearTimeout(t)
  }, [q])
  const report = useApiData(() => api.get<Report>(`/reports/${type}`, { from, to, q: query }), [type, from, to, query])
  const r = report.data

  const columns: ColumnDef<Record<string, unknown>, unknown>[] = (r?.columns ?? []).map((c) => ({
    id: c.key,
    header: c.label,
    accessorFn: (row) => row[c.key] as unknown,
    cell: ({ row, getValue }) => renderCell(c.key, getValue(), row.original, c.numeric, r?.type ?? ''),
  }))

  return (
    <>
      <PageHeader eyebrow="Reports and analytics" title="Reports" subtitle="Every report shows only accounts in your part of the hierarchy." />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Box component="nav" aria-label="Report types" sx={{ flex: '1 1 240px', maxWidth: { md: 280 }, bgcolor: 'background.paper', border: `1px solid ${colors.border}`, borderRadius: 4, p: 1, display: 'flex', flexDirection: 'column', gap: 0.25 }}>
          {available.map((d) => (
            <ButtonBase key={d.key} onClick={() => setType(d.key)} aria-current={type === d.key ? 'page' : undefined}
              sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', px: 1.5, py: 1, borderRadius: 2.5, minHeight: 44, bgcolor: type === d.key ? colors.indigo50 : 'transparent' }}>
              <Typography sx={{ fontSize: 14, fontWeight: 600, color: type === d.key ? colors.indigo700 : 'text.primary' }}>{d.label}</Typography>
              <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{d.group}</Typography>
            </ButtonBase>
          ))}
        </Box>
        <Box sx={{ flex: '999 1 520px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Section title={r?.title ?? 'Report'} actions={<Button variant="outlined" color="inherit" disabled={!r} onClick={() => api.download(`/reports/${type}/export.csv`, `primepay-${type}-report.csv`, { from, to, q: query })}>Export CSV</Button>}>
            {r && <Typography variant="body2" color="text.secondary" sx={{ mt: -1 }}>{r.description}</Typography>}
            <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
              {!['balances', 'audit'].includes(type) && (
                <>
                  <TextField size="small" type="date" label="From" value={from} onChange={(e) => setFrom(e.target.value)} />
                  <TextField size="small" type="date" label="To" value={to} onChange={(e) => setTo(e.target.value)} />
                </>
              )}
              <TextField size="small" label="Search" value={q} onChange={(e) => setQ(e.target.value)} sx={{ flex: '1 1 200px' }} />
            </Box>
          </Section>
          {report.error && <ErrorCard message={report.error} onRetry={report.reload} />}
          {!r && !report.error && <LoadingCard />}
          {r && (
            <>
              <KpiRow kpis={r.summary} />
              <Section padded={false}>
                <DataTable key={r.type} data={r.rows} columns={columns} numeric={r.columns.filter((c) => c.numeric).map((c) => c.key)} caption={r.title}
                  emptyTitle="No records in this period" emptyHint="Widen the date range or clear the search." />
              </Section>
            </>
          )}
        </Box>
      </Box>
    </>
  )
}
