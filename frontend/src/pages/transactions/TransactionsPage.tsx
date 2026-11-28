import { Box, Button, Chip, MenuItem, TextField, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { TransactionTable } from '../../components/tables/TransactionTable'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import type { Transaction, TxnStatus } from '../../types/domain'
import { count } from '../../utils/format'

const TYPES: Array<[string, string]> = [
  ['', 'All types'], ['WALLET_LOAD', 'Wallet load'], ['WALLET_TRANSFER', 'Wallet transfer'], ['PAYOUT', 'Withdrawal'], ['BILL_PAYMENT', 'Bill payment'], ['ADD_FUNDS', 'Add funds'],
  ['COMMISSION', 'Commission'], ['REVERSAL', 'Reversal'], ['ADJUSTMENT', 'Adjustment'],
]
const STATUSES: Array<TxnStatus | ''> = ['', 'SUCCESS', 'PENDING', 'FAILED', 'REVERSED', 'INITIATED']

export function TransactionsPage() {
  const { user } = useAuth()
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [type, setType] = useState('')
  const [status, setStatus] = useState<TxnStatus | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 300)
    return () => clearTimeout(t)
  }, [q])
  useEffect(() => setPage(0), [query, type, status, from, to])

  const counts = useApiData(() => api.get<Record<TxnStatus, number>>('/transactions/status-counts', { q: query, type, from, to }), [query, type, from, to])
  const list = useApiData(() => api.page<Transaction>('/transactions', { q: query, type, status, from, to, page, size: 25 }), [query, type, status, from, to, page])
  const total = Object.values(counts.data ?? {}).reduce((a, b) => a + b, 0)

  return (
    <>
      <PageHeader
        eyebrow="Money"
        title="Transactions"
        subtitle={user?.role === 'RETAILER' ? 'Your transactions' : user?.role === 'ADMIN' ? 'Every transaction on the platform' : "Yours and your network's"}
        actions={<Button variant="outlined" color="inherit" onClick={() => api.download('/transactions/export.csv', 'primepay-transactions.csv', { q: query, type, status, from, to })}>Export CSV</Button>}
      />
      <Section>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          <TextField size="small" label="Search" placeholder="Reference, user ID or UTR" value={q} onChange={(e) => setQ(e.target.value)} sx={{ flex: '1 1 240px' }} />
          <TextField size="small" type="date" label="From" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField size="small" type="date" label="To" value={to} onChange={(e) => setTo(e.target.value)} />
          <TextField size="small" select label="Type" value={type} onChange={(e) => setType(e.target.value)} sx={{ minWidth: 170 }}>
            {TYPES.map(([v, l]) => <MenuItem key={l} value={v}>{l}</MenuItem>)}
          </TextField>
        </Box>
        <Box role="group" aria-label="Status" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {STATUSES.map((s) => (
            <Chip key={s || 'all'} aria-pressed={status === s} onClick={() => setStatus(s)} color={status === s ? 'secondary' : 'default'} variant={status === s ? 'filled' : 'outlined'}
              label={`${s ? s.charAt(0) + s.slice(1).toLowerCase() : 'All'} · ${count(s ? counts.data?.[s] ?? 0 : total)}`} sx={{ height: 36, fontWeight: 500 }} />
          ))}
        </Box>
      </Section>
      {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
      {!list.data && !list.error && <LoadingCard />}
      {list.data && (
        <Section padded={false}>
          <TransactionTable rows={list.data.items} emptyHint="Change the filters, or check back after the first transfer." />
          {list.data.totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2, flexWrap: 'wrap', gap: 1 }}>
              <Typography variant="body2" color="text.secondary">Page {page + 1} of {list.data.totalPages} · {count(list.data.totalItems)} transactions</Typography>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button size="small" variant="outlined" color="inherit" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
                <Button size="small" variant="outlined" color="inherit" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
              </Box>
            </Box>
          )}
        </Section>
      )}
    </>
  )
}
