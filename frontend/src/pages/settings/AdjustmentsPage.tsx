import { Alert, Box, Button, Link, MenuItem, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { DataTable } from '../../components/tables/DataTable'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { Adjustment, UserRow } from '../../types/domain'
import { amount, dateTime, money } from '../../utils/format'

const REASONS = ['Bank charge refund', 'Duplicate debit correction', 'Settlement difference', 'Goodwill credit', 'Other']

export function AdjustmentsPage() {
  const { user } = useAuth()
  const list = useApiData(() => api.get<Adjustment[]>('/admin/adjustments'), [])
  const users = useApiData(() => api.get<UserRow[]>('/users', { status: 'ACTIVE' }), [])
  const [form, setForm] = useState({ accountId: '' as number | '', direction: 'CR', amount: '', reason: REASONS[0], details: '' })
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit() {
    setBusy(true)
    setMessage(null)
    try {
      const { data } = await api.post<{ requestRef: string }>('/admin/adjustments', { ...form, amount: Number(form.amount) })
      setMessage({ ok: true, text: `${data.requestRef} sent for approval. Another admin must approve it before the balance changes.` })
      setForm({ ...form, amount: '', details: '' })
      list.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  async function decide(id: number, approve: boolean) {
    try {
      await api.post(`/admin/adjustments/${id}/${approve ? 'approve' : 'reject'}`)
      list.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    }
  }

  const columns: ColumnDef<Adjustment, unknown>[] = [
    { id: 'ref', header: 'Request', accessorKey: 'requestRef' },
    { id: 'wallet', header: 'Account', accessorKey: 'accountCode' },
    { id: 'amount', header: 'Amount (₹)', accessorFn: (a) => (a.direction === 'CR' ? a.amount : -a.amount), cell: ({ row }) => <Box component="span" sx={{ color: row.original.direction === 'CR' ? colors.successText : colors.dangerText }}>{row.original.direction === 'CR' ? '+ ' : '− '}{amount(row.original.amount)}</Box> },
    { id: 'reason', header: 'Reason', accessorKey: 'reason' },
    { id: 'by', header: 'Raised by', accessorKey: 'requestedBy' },
    { id: 'date', header: 'Raised', accessorKey: 'createdAt', cell: ({ getValue }) => dateTime(getValue() as string) },
    { id: 'status', header: 'Status', accessorKey: 'status', cell: ({ getValue }) => <StatusChip status={getValue() as string} /> },
    {
      id: 'actions', header: '', enableSorting: false,
      cell: ({ row }) => {
        const a = row.original
        if (a.status !== 'PENDING') return a.transactionId ? <Link component={RouterLink} to={`/transactions/${a.transactionId}`}>View posting</Link> : (a.decidedBy ? `by ${a.decidedBy}` : '')
        if (a.requestedById === user?.accountId) return <Typography variant="caption" color="text.secondary">Needs another admin</Typography>
        return (
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button size="small" variant="contained" sx={{ minHeight: 32 }} onClick={() => decide(a.id, true)}>Approve</Button>
            <Button size="small" variant="outlined" color="error" sx={{ minHeight: 32 }} onClick={() => decide(a.id, false)}>Reject</Button>
          </Box>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader eyebrow="Wallets · Platform Admin" title="Wallet adjustments" subtitle="Manual credits and debits need a second admin to approve. Nobody can approve their own request." />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section title="New adjustment request" sx={{ flex: '1 1 340px', minWidth: 0 }}>
          <TextField select label="Wallet" value={form.accountId} onChange={(e) => setForm({ ...form, accountId: Number(e.target.value) })}>
            {(users.data ?? []).map((u) => <MenuItem key={u.id} value={u.id}>{u.accountCode} · {u.displayName} · {money(u.walletBalance)}</MenuItem>)}
          </TextField>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField select label="Direction" value={form.direction} onChange={(e) => setForm({ ...form, direction: e.target.value })}><MenuItem value="CR">Credit</MenuItem><MenuItem value="DR">Debit</MenuItem></TextField>
            <TextField label="Amount (₹)" type="number" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
          </Box>
          <TextField select label="Reason" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}>{REASONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}</TextField>
          <TextField label="Details and supporting reference" value={form.details} onChange={(e) => setForm({ ...form, details: e.target.value })} multiline minRows={3} />
          {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
          <Button variant="contained" onClick={submit} disabled={busy || !form.accountId || !(Number(form.amount) > 0)}>Submit for approval</Button>
          <Typography variant="caption" color="text.secondary">The balance doesn't change until approved. Approval posts a ledger entry with its own reference; nothing is edited in place.</Typography>
        </Section>
        <Section title="Requests" padded={false} sx={{ flex: '2 1 560px', minWidth: 0 }}>
          {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
          {list.data ? <DataTable data={list.data} columns={columns} numeric={['amount']} caption="Adjustment requests" emptyTitle="No adjustment requests yet" /> : <LoadingCard />}
        </Section>
      </Box>
    </>
  )
}
