import { Alert, Box, Button, Link, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { colors } from '../../theme/tokens'
import type { LimitStatus, Transaction, WalletSummary } from '../../types/domain'
import { dateTime, money } from '../../utils/format'

/** Side panel: the main wallet every service uses, plus today's limit for this service. */
export function MainWalletPanel({ wallet, limit, note, countLabel }: { wallet: WalletSummary | null; limit?: LimitStatus; note?: string; countLabel: string }) {
  return (
    <Section title="Main wallet" sx={{ flex: '1 1 300px', minWidth: 0 }}>
      <Typography sx={{ fontSize: 28, fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{money(wallet?.availableBalance)}</Typography>
      <Typography variant="body2" color="text.secondary">Bill payments and withdrawals are paid from this wallet. Add funds tops it up.</Typography>
      {limit && (
        <Typography variant="body2" color="text.secondary">
          Today: {money(limit.usedToday)} of {money(limit.dailyAmount)} · {limit.countToday} of {limit.dailyCount} {countLabel}
        </Typography>
      )}
      {note && <Alert severity="info" variant="outlined">{note}</Alert>}
    </Section>
  )
}

/** Recent transactions of one service, with an optional action per row (for example Recheck on a pending bill). */
export function RecentList({ title, rows, empty, action, describe }: {
  title: string
  rows: Transaction[] | null
  empty: string
  describe: (t: Transaction) => string
  action?: (t: Transaction) => React.ReactNode
}) {
  return (
    <Section title={title} actions={<Button size="small" component={RouterLink} to="/transactions">All transactions</Button>} padded={false}>
      {rows && rows.length === 0 && <Typography color="text.secondary" sx={{ px: 2, pb: 2 }}>{empty}</Typography>}
      {rows && rows.length > 0 && (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow><TableCell>Reference</TableCell><TableCell>Date</TableCell><TableCell>Details</TableCell><TableCell align="right">Amount</TableCell><TableCell align="right">Charge</TableCell><TableCell>Status</TableCell>{action && <TableCell />}</TableRow>
            </TableHead>
            <TableBody>
              {rows.map((t) => (
                <TableRow key={t.id} hover>
                  <TableCell><Link component={RouterLink} to={`/transactions/${t.id}`} sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{t.referenceNo}</Link></TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{dateTime(t.createdAt)}</TableCell>
                  <TableCell>{describe(t)}</TableCell>
                  <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>{money(t.amount)}</TableCell>
                  <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>{money(t.fee)}</TableCell>
                  <TableCell><StatusChip status={t.status} /></TableCell>
                  {action && <TableCell align="right">{action(t)}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
    </Section>
  )
}

/** Step indicator for multi-step flows. */
export function Steps({ steps, active }: { steps: string[]; active: number }) {
  return (
    <Box component="ol" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', p: 0, m: 0, listStyle: 'none' }} aria-label="Progress">
      {steps.map((s, i) => (
        <Box component="li" key={s} aria-current={i === active ? 'step' : undefined}
          sx={{ px: 1.5, py: 0.5, borderRadius: 999, fontSize: 13, fontWeight: 600,
            bgcolor: i === active ? colors.indigo600 : i < active ? colors.indigo50 : colors.canvas,
            color: i === active ? '#fff' : i < active ? colors.indigo700 : colors.textSecondary }}>
          {i + 1}. {s}
        </Box>
      ))}
    </Box>
  )
}
