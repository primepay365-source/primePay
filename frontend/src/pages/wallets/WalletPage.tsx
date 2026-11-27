import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Link, MenuItem, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { DataTable } from '../../components/tables/DataTable'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api, newIdempotencyKey } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { StatementRow, Transaction, WalletSummary } from '../../types/domain'
import { amount, dateTime, money, shortDate } from '../../utils/format'

const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 13 }

export function WalletPage() {
  const { user } = useAuth()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [direction, setDirection] = useState('')
  const [loadOpen, setLoadOpen] = useState(false)
  const summary = useApiData(() => api.get<WalletSummary>('/wallets/me'), [])
  const history = useApiData(() => api.get<{ date: string; closingBalance: number }[]>('/wallets/me/balance-history', { days: 30 }), [])
  const statement = useApiData(() => api.get<StatementRow[]>('/wallets/statement', { from, to, direction }), [from, to, direction])
  if (!user) return null
  const canLoad = user.role === 'SUPER_DISTRIBUTOR' || user.role === 'MASTER_DISTRIBUTOR' || user.role === 'DISTRIBUTOR'
  const canSend = canLoad

  const columns: ColumnDef<StatementRow, unknown>[] = [
    { id: 'date', header: 'Date', accessorKey: 'createdAt', cell: ({ getValue }) => dateTime(getValue() as string) },
    { id: 'ref', header: 'Reference', accessorKey: 'reference', cell: ({ row }) => <Link component={RouterLink} to={`/transactions/${row.original.transactionId}`} sx={mono}>{row.original.reference}</Link> },
    { id: 'desc', header: 'Description', accessorKey: 'description' },
    { id: 'debit', header: 'Debit (₹)', accessorFn: (r) => (r.direction === 'DR' ? r.amount : null), cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'credit', header: 'Credit (₹)', accessorFn: (r) => (r.direction === 'CR' ? r.amount : null), cell: ({ getValue }) => <Box component="span" sx={{ color: colors.successText }}>{amount(getValue() as number)}</Box> },
    { id: 'balance', header: 'Balance (₹)', accessorKey: 'balanceAfter', cell: ({ getValue }) => amount(getValue() as number) },
  ]

  const s = summary.data
  return (
    <>
      <PageHeader eyebrow={s ? `Wallet · ${s.walletCode}` : 'Wallet'} title="My wallet" />
      {summary.error && <ErrorCard message={summary.error} onRetry={summary.reload} />}
      {!s && !summary.error && <LoadingCard rows={3} />}
      {s && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
          <Box sx={{ flex: '1 1 340px', borderRadius: 4.5, p: 3, color: '#FFFFFF', background: `linear-gradient(135deg, ${colors.navy900} 0%, ${colors.indigo700} 100%)`, display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="overline" sx={{ color: colors.sidebarText }}>Available balance</Typography>
              <Box sx={{ fontSize: 12, fontWeight: 600, px: 1.25, py: 0.25, borderRadius: 99, bgcolor: 'rgba(255,255,255,0.14)' }}>{s.status === 'ACTIVE' ? 'Active' : s.status}</Box>
            </Box>
            <Typography sx={{ fontSize: 34, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(s.availableBalance)}</Typography>
            <Box sx={{ display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
              {canLoad && <Button variant="contained" onClick={() => setLoadOpen(true)} sx={{ bgcolor: '#FFFFFF', color: colors.navy900, '&:hover': { bgcolor: '#EEF1F6' } }}>Load wallet</Button>}
              {canSend && <Button variant="outlined" component={RouterLink} to="/wallet/transfer" sx={{ color: '#FFFFFF', borderColor: colors.indigo200 }}>Transfer funds</Button>}
              {user.role === 'RETAILER' && <Button variant="contained" component={RouterLink} to="/add-funds" sx={{ bgcolor: '#FFFFFF', color: colors.navy900, '&:hover': { bgcolor: '#EEF1F6' } }}>Add funds</Button>}
            </Box>
          </Box>
          <Section sx={{ flex: '2 1 480px', minWidth: 0 }}>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 2.5 }}>
              {([
                ['Ledger balance', money(s.ledgerBalance)],
                ['Blocked', money(s.blockedBalance)],
                ['Opening balance this month', money(s.openingBalanceThisMonth)],
                ['Credits this month', '+ ' + money(s.creditsThisMonth)],
                ['Debits this month', '− ' + money(s.debitsThisMonth)],
                ['Pending load requests', money(s.pendingLoads)],
              ] as const).map(([label, value]) => (
                <Box key={label}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>{label}</Typography>
                  <Typography sx={{ fontWeight: 700, fontSize: 17, fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
                </Box>
              ))}
            </Box>
          </Section>
        </Box>
      )}
      {history.data && (
        <Section title="Balance history · 30 days">
          <Box role="img" aria-label="Area chart of daily closing wallet balance for the last 30 days" sx={{ height: 180 }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={history.data.map((p) => ({ day: shortDate(p.date), balance: p.closingBalance }))} margin={{ top: 4, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.border} />
                <XAxis dataKey="day" tick={{ fontSize: 12, fill: colors.textMuted }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 12, fill: colors.textMuted }} tickFormatter={(v: number) => (v >= 100000 ? `${(v / 100000).toFixed(1)}L` : String(v))} width={48} />
                <Tooltip formatter={(v) => money(Number(v))} />
                <Area type="monotone" dataKey="balance" name="Closing balance" stroke={colors.indigo600} strokeWidth={2.5} fill={colors.indigo50} />
              </AreaChart>
            </ResponsiveContainer>
          </Box>
        </Section>
      )}
      <Section
        title="Statement"
        padded={false}
        actions={
          <>
            <TextField size="small" type="date" label="From" value={from} onChange={(e) => setFrom(e.target.value)} />
            <TextField size="small" type="date" label="To" value={to} onChange={(e) => setTo(e.target.value)} />
            <TextField size="small" select label="Entries" value={direction} onChange={(e) => setDirection(e.target.value)} sx={{ minWidth: 130 }}>
              <MenuItem value="">All</MenuItem><MenuItem value="CR">Credits</MenuItem><MenuItem value="DR">Debits</MenuItem>
            </TextField>
            <Button variant="outlined" color="inherit" onClick={() => api.download('/wallets/statement.csv', 'primepay-statement.csv', { from, to, direction })}>CSV</Button>
          </>
        }
      >
        {statement.data ? (
          <DataTable data={statement.data} columns={columns} numeric={['debit', 'credit', 'balance']} caption="Wallet statement" emptyTitle="No ledger entries" emptyHint="Every credit and debit to your wallet appears here." />
        ) : (
          <LoadingCard />
        )}
        <Typography variant="caption" color="text.secondary" sx={{ px: 2 }}>Every row is a ledger entry. Balances are never edited directly; corrections appear as reversal or adjustment entries.</Typography>
      </Section>
      {loadOpen && <LoadDialog onClose={() => setLoadOpen(false)} onDone={() => { summary.reload(); statement.reload() }} />}
    </>
  )
}

function LoadDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [value, setValue] = useState('')
  const [mode, setMode] = useState('NEFT')
  const [utr, setUtr] = useState('')
  const [key] = useState(newIdempotencyKey)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<Transaction | null>(null)

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<Transaction>('/wallets/load', { amount: Number(value), mode, utr }, { 'Idempotency-Key': key })
      setDone(data)
      onDone()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 700 }}>Load wallet</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '8px !important' }}>
        {done ? (
          <Alert severity="success">Request {done.referenceNo} for {money(done.amount)} was sent. A Platform Admin credits your wallet once the bank payment is matched.</Alert>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary">Tell us about a bank transfer you made to PrimePay. Funds are credited after an admin matches the payment.</Typography>
            <TextField label="Amount (₹)" type="number" value={value} onChange={(e) => setValue(e.target.value)} slotProps={{ htmlInput: { min: 1, step: '0.01' } }} />
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <TextField select label="Payment mode" value={mode} onChange={(e) => setMode(e.target.value)}>
                {['NEFT', 'RTGS', 'IMPS', 'UPI'].map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
              </TextField>
              <TextField label="UTR / reference" value={utr} onChange={(e) => setUtr(e.target.value.replace(/[^A-Za-z0-9]/g, ''))} helperText="6 to 40 letters or digits" />
            </Box>
            <Alert severity="warning">Development build: loads are simulated. No real money moves until a payment provider is connected and approved.</Alert>
            {error && <Alert severity="error">{error}</Alert>}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" variant="outlined">{done ? 'Close' : 'Cancel'}</Button>
        {!done && <Button variant="contained" onClick={submit} disabled={busy || !value || utr.length < 6}>{busy ? 'Sending…' : 'Submit load request'}</Button>}
      </DialogActions>
    </Dialog>
  )
}
