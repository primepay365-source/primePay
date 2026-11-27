import { Alert, Box, Breadcrumbs, Button, Link, MenuItem, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { LogoMark } from '../../components/common/Logo'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { TransactionDetail } from '../../types/domain'
import { amount, dateTime, money, titleCase } from '../../utils/format'

const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 13 }
const REASONS = ['Sent to wrong account', 'Duplicate request', 'Customer dispute', 'Other']

export function TransactionDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const detail = useApiData(() => api.get<TransactionDetail>(`/transactions/${id}`), [id])
  const [reverseOpen, setReverseOpen] = useState(false)
  const [reason, setReason] = useState(REASONS[0])
  const [note, setNote] = useState('')
  const [loadDecision, setLoadDecision] = useState<'approve' | 'reject' | null>(null)
  const [loadNote, setLoadNote] = useState('')

  if (detail.error) return <ErrorCard message={detail.error} onRetry={detail.reload} />
  if (!detail.data) return <LoadingCard />
  const { transaction: t, history, ledger, commission, related } = detail.data
  const isAdmin = user?.role === 'ADMIN'
  const canReverse = isAdmin && t.status === 'SUCCESS' && ['WALLET_TRANSFER', 'PAYOUT', 'WALLET_LOAD'].includes(t.type)
  const pendingLoad = isAdmin && t.type === 'WALLET_LOAD' && t.status === 'PENDING'

  const rows: Array<[string, string | null]> = [
    ['From', t.fromCode ? `${t.fromCode}${t.fromName ? ' · ' + t.fromName : ''}` : null],
    ['To', t.toCode ? `${t.toCode}${t.toName ? ' · ' + t.toName : ''}` : null],
    ['Fee / charge', money(t.fee)],
    ['Biller', t.billerName],
    ['Card holder', t.customerName],
    ['Card', t.cardLast4 ? `${t.cardNetwork ? t.cardNetwork + ' ' : ''}••${t.cardLast4}` : null],
    ['Registered mobile', t.customerMobileMasked],
    ['Gateway category', t.gatewayCategory],
    ...(t.type === 'ADD_FUNDS' && t.status === 'SUCCESS' ? [['Credited to wallet', money(t.amount - t.fee)] as [string, string]] : []),
    ['Mode', t.payoutMode],
    ['Bank', t.beneficiaryBank ? `${t.beneficiaryBank} · ${t.beneficiaryIfsc}` : null],
    ['Bank / provider reference', t.externalRef],
    ['Remarks', t.remarks],
    ['Failure reason', t.failureReason],
    ['Initiated by', t.initiatorCode],
  ]

  return (
    <>
      <Breadcrumbs aria-label="Breadcrumb"><Link component={RouterLink} to="/transactions">Transactions</Link><Typography color="text.secondary">{t.referenceNo}</Typography></Breadcrumbs>
      <PageHeader
        title={t.typeLabel}
        subtitle={<Box component="span" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}><StatusChip status={t.status} />{t.referenceNo} · {dateTime(t.createdAt)} IST</Box>}
        actions={
          <>
            <Button variant="outlined" color="inherit" onClick={() => window.print()}>Print receipt</Button>
            {pendingLoad && <Button variant="outlined" color="error" onClick={() => setLoadDecision('reject')}>Reject load</Button>}
            {pendingLoad && <Button variant="contained" onClick={() => setLoadDecision('approve')}>Approve load</Button>}
            {canReverse && <Button variant="contained" color="error" onClick={() => setReverseOpen(true)}>Reverse transaction</Button>}
          </>
        }
      />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section sx={{ flex: '1 1 360px', minWidth: 0 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', fontWeight: 700 }}><LogoMark size={24} />Receipt</Box>
            <Typography variant="caption" color="text.secondary">Simulated · development</Typography>
          </Box>
          <Typography sx={{ fontSize: 32, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(t.amount)}</Typography>
          {rows.filter(([, v]) => v).map(([k, v]) => (
            <Box key={k} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 1, borderBottom: `1px dashed ${colors.border}` }}>
              <Typography variant="body2" color="text.secondary">{k}</Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, textAlign: 'right' }}>{v}</Typography>
            </Box>
          ))}
        </Section>
        <Box sx={{ flex: '2 1 520px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Section title="Status history">
            {history.map((h, i) => (
              <Box key={i} sx={{ display: 'flex', gap: 1.5, alignItems: 'center', flexWrap: 'wrap' }}>
                <Box sx={{ minWidth: 96 }}><StatusChip status={h.toStatus} /></Box>
                <Typography variant="body2">{dateTime(h.changedAt)}{h.note ? ` · ${h.note}` : ''}</Typography>
              </Box>
            ))}
          </Section>
          <Section title="Ledger entries">
            {ledger.length === 0 ? <Typography variant="body2" color="text.secondary">No money has moved for this transaction{t.status === 'PENDING' ? ' yet' : ''}.</Typography> : (
              <Box sx={{ overflowX: 'auto' }}>
                <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', fontSize: 14, '& th': { textAlign: 'left', fontSize: 12, color: 'text.secondary', textTransform: 'uppercase', py: 1, px: 1.5, borderBottom: `1px solid ${colors.border}` }, '& td': { py: 1.25, px: 1.5, borderBottom: `1px solid ${colors.border}`, whiteSpace: 'nowrap' } }}>
                  <thead><tr><th>Wallet</th><th>Entry</th><th style={{ textAlign: 'right' }}>Debit (₹)</th><th style={{ textAlign: 'right' }}>Credit (₹)</th><th style={{ textAlign: 'right' }}>Balance after (₹)</th></tr></thead>
                  <tbody>
                    {ledger.map((e) => (
                      <tr key={e.entryRef}>
                        <td style={mono}>{e.walletCode}</td><td>{titleCase(e.entryType)}</td>
                        <td style={{ textAlign: 'right' }}>{e.direction === 'DR' ? amount(e.amount) : ''}</td>
                        <td style={{ textAlign: 'right', color: colors.successText }}>{e.direction === 'CR' ? amount(e.amount) : ''}</td>
                        <td style={{ textAlign: 'right' }}>{amount(e.balanceAfter)}</td>
                      </tr>
                    ))}
                  </tbody>
                </Box>
              </Box>
            )}
          </Section>
          {commission.length > 0 && (
            <Section title="Commission split">
              {commission.map((c, i) => (
                <Box key={i} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                  <Typography variant="body2">{titleCase(c.levelRole)} · {c.earnerCode}</Typography>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}><Typography variant="body2" sx={{ fontWeight: 700 }}>{money(c.amount)}</Typography><StatusChip status={c.status} /></Box>
                </Box>
              ))}
            </Section>
          )}
          {related.length > 0 && (
            <Section title="Related transactions">
              {related.map((r) => (
                <Box key={r.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                  <Link component={RouterLink} to={`/transactions/${r.id}`} sx={mono}>{r.referenceNo}</Link>
                  <Typography variant="body2">{r.typeLabel} · {money(r.amount)}</Typography>
                  <StatusChip status={r.status} />
                </Box>
              ))}
            </Section>
          )}
        </Box>
      </Box>
      <ConfirmDialog open={reverseOpen} title={`Reverse ${money(t.amount)}?`} confirmLabel="Reverse transaction" danger onClose={() => setReverseOpen(false)}
        onConfirm={async () => {
          await api.post(`/transactions/${t.id}/reverse`, { reason: note ? `${reason}: ${note}` : reason })
          detail.reload()
        }}>
        <Alert severity="warning">This posts opposite ledger entries{t.type === 'PAYOUT' ? ' and reverses the commission paid' : ''}. If a receiving wallet has already spent the money, the reversal is refused and nothing changes.</Alert>
        <TextField select label="Reason" value={reason} onChange={(e) => setReason(e.target.value)}>{REASONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}</TextField>
        <TextField label="Note for the audit log" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} />
      </ConfirmDialog>
      <ConfirmDialog open={!!loadDecision} title={loadDecision === 'approve' ? `Approve ${money(t.amount)} load?` : 'Reject this load?'}
        confirmLabel={loadDecision === 'approve' ? 'Approve and credit' : 'Reject'} danger={loadDecision === 'reject'}
        disabled={loadDecision === 'reject' && !loadNote.trim()} onClose={() => setLoadDecision(null)}
        onConfirm={async () => {
          await api.post(`/transactions/${t.id}/${loadDecision === 'approve' ? 'approve-load' : 'reject-load'}`, { note: loadNote || null })
          detail.reload()
        }}>
        <Typography variant="body2">{t.remarks}. Check the bank statement shows this payment before approving.</Typography>
        <TextField label={loadDecision === 'reject' ? 'Reason (required)' : 'Note (optional)'} value={loadNote} onChange={(e) => setLoadNote(e.target.value)} />
      </ConfirmDialog>
    </>
  )
}
