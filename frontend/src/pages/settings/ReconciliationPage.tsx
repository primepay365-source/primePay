import { Box, Button, Link, Typography } from '@mui/material'
import CheckRounded from '@mui/icons-material/CheckRounded'
import PriorityHighRounded from '@mui/icons-material/PriorityHighRounded'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { Reconciliation } from '../../types/domain'
import { dateTime, money } from '../../utils/format'

export function ReconciliationPage() {
  const recon = useApiData(() => api.get<Reconciliation>('/reports/reconciliation'), [])
  const r = recon.data
  return (
    <>
      <PageHeader eyebrow="Finance · Platform Admin" title="Reconciliation" subtitle={r ? `Checked ${dateTime(r.ranAt)} IST against the live ledger.` : 'Checks every wallet against its ledger.'}
        actions={<Button variant="contained" onClick={recon.reload} disabled={recon.loading}>{recon.loading ? 'Running…' : 'Run again'}</Button>} />
      {recon.error && <ErrorCard message={recon.error} onRetry={recon.reload} />}
      {!r && !recon.error && <LoadingCard />}
      {r && (
        <>
          <Section>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 2 }}>
              <Box><Typography variant="caption" color="text.secondary">Wallet balances (sum)</Typography><Typography sx={{ fontWeight: 700, fontSize: 20 }}>{money(r.walletTotal)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Ledger totals (sum)</Typography><Typography sx={{ fontWeight: 700, fontSize: 20 }}>{money(r.ledgerTotal)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Result</Typography><Box sx={{ mt: 0.5 }}><StatusChip status={r.matched ? 'SUCCESS' : 'PENDING'} label={r.matched ? 'Matched' : `${r.exceptions.length} exceptions`} /></Box></Box>
            </Box>
          </Section>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
            <Section title="Checks" sx={{ flex: '1 1 380px', minWidth: 0 }}>
              {r.checks.map((c) => (
                <Box key={c.title} sx={{ display: 'flex', gap: 1.5, py: 1, borderBottom: `1px solid ${colors.border}` }}>
                  <Box sx={{ width: 26, height: 26, flex: 'none', borderRadius: '50%', bgcolor: c.passed ? colors.success : colors.warning, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }} aria-label={c.passed ? 'Passed' : 'Needs attention'}>
                    {c.passed ? <CheckRounded sx={{ fontSize: 16 }} /> : <PriorityHighRounded sx={{ fontSize: 16 }} />}
                  </Box>
                  <Box><Typography sx={{ fontWeight: 600, fontSize: 14 }}>{c.title}</Typography><Typography variant="body2" color="text.secondary">{c.detail}</Typography></Box>
                </Box>
              ))}
            </Section>
            <Section title="Exceptions to resolve" sx={{ flex: '2 1 520px', minWidth: 0 }}>
              {r.exceptions.length === 0 ? (
                <Typography color={colors.successText} sx={{ fontWeight: 500 }}>All checks passed. Nothing to resolve.</Typography>
              ) : r.exceptions.map((e, i) => (
                <Box key={i} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', py: 1, borderBottom: `1px solid ${colors.border}` }}>
                  <Box>
                    {e.transactionId ? <Link component={RouterLink} to={`/transactions/${e.transactionId}`} sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{e.reference}</Link> : <Typography sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }}>{e.reference}</Typography>}
                    <Typography variant="body2">{e.issue}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <Typography sx={{ fontWeight: 700 }}>{money(e.amount)}</Typography>
                    <Button size="small" variant="outlined" color="inherit" component={RouterLink} to={e.transactionId ? `/transactions/${e.transactionId}` : '/admin/adjustments'}>{e.transactionId ? 'Open' : 'Raise adjustment'}</Button>
                  </Box>
                </Box>
              ))}
            </Section>
          </Box>
        </>
      )}
    </>
  )
}
