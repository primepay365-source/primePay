import { Box, Button, Collapse, IconButton, MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { Fragment, useEffect, useState } from 'react'
import { PageHeader } from '../../components/common/PageHeader'
import { EmptyState, ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { AuditRow } from '../../types/domain'
import { dateTime } from '../../utils/format'

const CATEGORIES: Array<[string, string]> = [['', 'All actions'], ['LOGIN', 'Sign-ins'], ['USER', 'Users'], ['WALLET', 'Wallet'], ['PAYOUT', 'Withdrawals'], ['BILL_PAYMENT', 'Bill payments'], ['ADD_FUNDS', 'Add funds'], ['BENEFICIARY', 'Bank accounts'], ['TRANSACTION', 'Reversals'], ['ADJUSTMENT', 'Adjustments'], ['COMMISSION', 'Commission'], ['LIMIT', 'Limits'], ['PASSWORD', 'Passwords'], ['ONBOARD', 'Onboarding']]

export function AuditLogPage() {
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<number | null>(null)
  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 300)
    return () => clearTimeout(t)
  }, [q])
  const list = useApiData(() => api.page<AuditRow>('/audit', { q: query, action, page, size: 30 }), [query, action, page])

  return (
    <>
      <PageHeader eyebrow="Compliance" title="Audit log" subtitle="Append-only. Entries can't be edited or deleted, including by admins." />
      <Section>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          <TextField size="small" label="Search" placeholder="Actor, target or action" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} sx={{ flex: '1 1 240px' }} />
          <TextField size="small" select label="Category" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }} sx={{ minWidth: 180 }}>
            {CATEGORIES.map(([v, l]) => <MenuItem key={l} value={v}>{l}</MenuItem>)}
          </TextField>
        </Box>
      </Section>
      {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
      {!list.data && !list.error && <LoadingCard />}
      {list.data && (
        <Section padded={false}>
          {list.data.items.length === 0 ? <EmptyState title="No audit entries match" /> : (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Audit log">
                <TableHead><TableRow>{['Time (IST)', 'Actor', 'Action', 'Target', 'IP', 'Result', ''].map((h) => <TableCell key={h} sx={{ fontWeight: 600, fontSize: 12, color: 'text.secondary', textTransform: 'uppercase' }}>{h}</TableCell>)}</TableRow></TableHead>
                <TableBody>
                  {list.data.items.map((a) => (
                    <Fragment key={a.id}>
                      <TableRow hover sx={{ bgcolor: open === a.id ? colors.indigo50 : undefined }}>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{dateTime(a.occurredAt)}</TableCell>
                        <TableCell sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{a.actor}</TableCell>
                        <TableCell sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13, color: colors.indigo700 }}>{a.action}</TableCell>
                        <TableCell sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{a.targetId}</TableCell>
                        <TableCell>{a.ipAddress}</TableCell>
                        <TableCell><StatusChip status={a.result} /></TableCell>
                        <TableCell>{a.detail && <IconButton size="small" aria-label="Show details" aria-expanded={open === a.id} onClick={() => setOpen(open === a.id ? null : a.id)}><ExpandMoreRounded /></IconButton>}</TableCell>
                      </TableRow>
                      {a.detail && (
                        <TableRow><TableCell colSpan={7} sx={{ py: 0, borderBottom: open === a.id ? undefined : 0 }}>
                          <Collapse in={open === a.id}><Box component="pre" sx={{ m: 1, p: 1.5, borderRadius: 2, bgcolor: '#F5F7FB', fontSize: 12, whiteSpace: 'pre-wrap' }}>{a.detail}{'\n'}request {a.requestId}</Box></Collapse>
                        </TableCell></TableRow>
                      )}
                    </Fragment>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2, flexWrap: 'wrap', gap: 1 }}>
            <Typography variant="body2" color="text.secondary">Page {page + 1} of {Math.max(1, list.data.totalPages)}</Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button size="small" variant="outlined" color="inherit" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
              <Button size="small" variant="outlined" color="inherit" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
            </Box>
          </Box>
        </Section>
      )}
    </>
  )
}
