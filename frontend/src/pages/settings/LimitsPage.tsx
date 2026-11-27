import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Tab, Tabs, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { DataTable } from '../../components/tables/DataTable'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { LimitView, UserRow } from '../../types/domain'
import { amount, count, date, titleCase } from '../../utils/format'

const CHECKS = [
  'Account is active and not locked', 'Wallet is active', 'Sender may pay this receiver in the hierarchy', 'Amount is within min and max',
  'Daily and monthly amount and count stay within limits', 'Available balance covers amount plus fees', 'Idempotency key not reused for a different request',
]

export function LimitsPage() {
  const [tab, setTab] = useState(0)
  const [editing, setEditing] = useState<LimitView | 'new' | null>(null)
  const limits = useApiData(() => api.get<LimitView[]>('/limits'), [])
  const rows = (limits.data ?? []).filter((l) => (tab === 0 ? l.scope === 'ROLE' : l.scope === 'USER'))

  const columns: ColumnDef<LimitView, unknown>[] = [
    { id: 'who', header: tab === 0 ? 'Role' : 'User', accessorFn: (l) => (l.scope === 'ROLE' ? titleCase(l.roleCode) : l.accountCode) },
    { id: 'service', header: 'Service', accessorKey: 'serviceCode', cell: ({ getValue }) => titleCase(getValue() as string) },
    { id: 'min', header: 'Min / txn', accessorKey: 'minTxn', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'max', header: 'Max / txn', accessorKey: 'maxTxn', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'daily', header: 'Daily amount', accessorKey: 'dailyAmount', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'monthly', header: 'Monthly amount', accessorKey: 'monthlyAmount', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'dcount', header: 'Daily count', accessorKey: 'dailyCount', cell: ({ getValue }) => count(getValue() as number) },
    { id: 'mcount', header: 'Monthly count', accessorKey: 'monthlyCount', cell: ({ getValue }) => count(getValue() as number) },
    ...(tab === 1 ? [{ id: 'until', header: 'Valid until', accessorFn: (l: LimitView) => (l.validUntil ? date(l.validUntil) : 'No end date') } as ColumnDef<LimitView, unknown>] : []),
    { id: 'by', header: 'Set by', accessorKey: 'setBy' },
    {
      id: 'actions', header: '', enableSorting: false,
      cell: ({ row }) => (
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button size="small" variant="outlined" color="inherit" sx={{ minHeight: 32 }} onClick={() => setEditing(row.original)}>Edit</Button>
          {row.original.scope === 'USER' && <Button size="small" color="error" variant="outlined" sx={{ minHeight: 32 }} onClick={async () => { await api.del(`/limits/overrides/${row.original.id}`); limits.reload() }}>Remove</Button>}
        </Box>
      ),
    },
  ]

  return (
    <>
      <PageHeader eyebrow="Configuration" title="Velocity and transaction limits" subtitle="Daily limits reset at midnight IST; monthly limits on the 1st. A user override wins over the role default."
        actions={tab === 1 && <Button variant="contained" onClick={() => setEditing('new')}>Add user override</Button>} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ borderBottom: `1px solid ${colors.border}` }}><Tab label="Role defaults" /><Tab label="User overrides" /></Tabs>
      {limits.error && <ErrorCard message={limits.error} onRetry={limits.reload} />}
      {!limits.data && !limits.error && <LoadingCard />}
      {limits.data && (
        <Section padded={false}>
          <DataTable data={rows} columns={columns} numeric={['min', 'max', 'daily', 'monthly', 'dcount', 'mcount']} caption="Limits"
            emptyTitle={tab === 0 ? 'No role defaults' : 'No user overrides'} emptyHint={tab === 1 ? 'Add an override to raise or lower one user’s limits.' : undefined} />
        </Section>
      )}
      <Section title="Checks before every financial transaction">
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 1.5 }}>
          {CHECKS.map((c, i) => (
            <Box key={c} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
              <Box sx={{ width: 26, height: 26, flex: 'none', borderRadius: '50%', bgcolor: colors.indigo50, color: colors.indigo700, fontSize: 12, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{i + 1}</Box>
              <Typography variant="body2">{c}</Typography>
            </Box>
          ))}
        </Box>
        <Typography variant="caption" color="text.secondary">Usage is checked while the payer's wallet row is locked, so two requests at once can't both slip under a limit.</Typography>
      </Section>
      {editing && <LimitDialog limit={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={limits.reload} />}
    </>
  )
}

function LimitDialog({ limit, onClose, onSaved }: { limit: LimitView | null; onClose: () => void; onSaved: () => void }) {
  const users = useApiData(() => (limit ? Promise.resolve([] as UserRow[]) : api.get<UserRow[]>('/users', { status: 'ACTIVE' })), [])
  const [accountId, setAccountId] = useState<number | ''>('')
  const [service, setService] = useState('WALLET_TRANSFER')
  const [f, setF] = useState({
    minTxn: String(limit?.minTxn ?? 100), maxTxn: String(limit?.maxTxn ?? ''), dailyAmount: String(limit?.dailyAmount ?? ''), monthlyAmount: String(limit?.monthlyAmount ?? ''),
    dailyCount: String(limit?.dailyCount ?? 100), monthlyCount: String(limit?.monthlyCount ?? 2000), validUntil: limit?.validUntil?.slice(0, 10) ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const body = { minTxn: Number(f.minTxn), maxTxn: Number(f.maxTxn), dailyAmount: Number(f.dailyAmount), monthlyAmount: Number(f.monthlyAmount), dailyCount: Number(f.dailyCount), monthlyCount: Number(f.monthlyCount), validUntil: f.validUntil || null }

  async function save() {
    setError(null)
    try {
      if (limit) await api.put(`/limits/${limit.id}`, body)
      else await api.post('/limits/overrides', { accountId, serviceCode: service, limit: body })
      onSaved()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 700 }}>{limit ? `Edit ${limit.scope === 'ROLE' ? titleCase(limit.roleCode) : limit.accountCode} · ${titleCase(limit.serviceCode)}` : 'Add user override'}</DialogTitle>
      <DialogContent sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, pt: '8px !important' }}>
        {!limit && (
          <>
            <TextField select label="User" value={accountId} onChange={(e) => setAccountId(Number(e.target.value))}>
              {(users.data ?? []).map((u) => <MenuItem key={u.id} value={u.id}>{u.accountCode} · {u.displayName}</MenuItem>)}
            </TextField>
            <TextField select label="Service" value={service} onChange={(e) => setService(e.target.value)}>
              {['WALLET_TRANSFER', 'PAYOUT', 'BILL_PAYMENT', 'ADD_FUNDS', 'WALLET_LOAD'].map((s) => <MenuItem key={s} value={s}>{titleCase(s)}</MenuItem>)}
            </TextField>
          </>
        )}
        <TextField label="Min per transaction (₹)" type="number" value={f.minTxn} onChange={set('minTxn')} />
        <TextField label="Max per transaction (₹)" type="number" value={f.maxTxn} onChange={set('maxTxn')} />
        <TextField label="Daily amount (₹)" type="number" value={f.dailyAmount} onChange={set('dailyAmount')} />
        <TextField label="Monthly amount (₹)" type="number" value={f.monthlyAmount} onChange={set('monthlyAmount')} />
        <TextField label="Daily count" type="number" value={f.dailyCount} onChange={set('dailyCount')} />
        <TextField label="Monthly count" type="number" value={f.monthlyCount} onChange={set('monthlyCount')} />
        {(!limit || limit.scope === 'USER') && <TextField label="Valid until (optional)" type="date" value={f.validUntil} onChange={set('validUntil')} />}
        {error && <Alert severity="error" sx={{ gridColumn: '1 / -1' }}>{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" variant="outlined">Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!limit && !accountId}>Save</Button>
      </DialogActions>
    </Dialog>
  )
}
