import { Alert, Box, Button, MenuItem, TextField, Typography } from '@mui/material'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import { useMemo, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api, newIdempotencyKey } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { LimitStatus, Recipient, Transaction, WalletSummary } from '../../types/domain'
import { money } from '../../utils/format'
import { ROLE_LABELS } from '../../utils/roles'

type Step = 'form' | 'review' | 'done'

export function TransferPage() {
  const [params] = useSearchParams()
  const recipients = useApiData(() => api.get<Recipient[]>('/wallets/transfer/recipients'), [])
  const wallet = useApiData(() => api.get<WalletSummary>('/wallets/me'), [])
  const limits = useApiData(() => api.get<LimitStatus[]>('/wallets/me/limits'), [])
  const [to, setTo] = useState<number | ''>(params.get('to') ? Number(params.get('to')) : '')
  const [value, setValue] = useState('')
  const [remarks, setRemarks] = useState('')
  const [step, setStep] = useState<Step>('form')
  const [key, setKey] = useState(newIdempotencyKey)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Transaction | null>(null)

  const recipient = recipients.data?.find((r) => r.accountId === to)
  const limit = limits.data?.find((l) => l.serviceCode === 'WALLET_TRANSFER')
  const num = Number(value)
  const problems = useMemo(() => {
    const out: string[] = []
    if (!to) out.push('Choose who to send to.')
    if (!(num > 0)) out.push('Enter an amount.')
    if (wallet.data && num > wallet.data.availableBalance) out.push(`That's more than your available ${money(wallet.data.availableBalance)}.`)
    if (limit && num > 0 && num < limit.minTxn) out.push(`Minimum per transfer is ${money(limit.minTxn)}.`)
    if (limit && num > limit.maxTxn) out.push(`Maximum per transfer is ${money(limit.maxTxn)}.`)
    if (limit && num + limit.usedToday > limit.dailyAmount) out.push(`Only ${money(limit.dailyAmount - limit.usedToday)} of today's limit is left.`)
    return out
  }, [to, num, wallet.data, limit])

  async function send() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<Transaction>('/wallets/transfer', { toAccountId: to, amount: num, remarks: remarks || null }, { 'Idempotency-Key': key })
      setResult(data)
      setStep('done')
      wallet.reload()
      limits.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function reset() {
    setStep('form')
    setValue('')
    setRemarks('')
    setResult(null)
    setError(null)
    setKey(newIdempotencyKey())
  }

  return (
    <>
      <PageHeader eyebrow="Wallet" title="Transfer funds" subtitle="Send money down your network. You can only send to active accounts directly below you." />
      {recipients.error && <ErrorCard message={recipients.error} onRetry={recipients.reload} />}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section sx={{ flex: '2 1 520px', minWidth: 0 }}>
          {step === 'form' && (
            <>
              <TextField select label="Send to" value={to} onChange={(e) => setTo(Number(e.target.value))}
                helperText={recipients.data?.length === 0 ? 'No active accounts directly below you yet.' : 'Accounts directly below you'}>
                {(recipients.data ?? []).map((r) => (
                  <MenuItem key={r.accountId} value={r.accountId}>{r.accountCode} · {r.displayName}{r.businessName ? ` · ${r.businessName}` : ''} ({ROLE_LABELS[r.role]})</MenuItem>
                ))}
              </TextField>
              <TextField label="Amount (₹)" type="number" value={value} onChange={(e) => setValue(e.target.value)} slotProps={{ htmlInput: { min: 1, step: '0.01', style: { fontSize: 22, fontWeight: 700 } } }} />
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {[10000, 25000, 50000, 100000].map((v) => (
                  <Button key={v} size="small" variant="outlined" color="inherit" onClick={() => setValue(String(v))} sx={{ minHeight: 36 }}>{money(v).replace('.00', '')}</Button>
                ))}
              </Box>
              <TextField label="Remarks (optional)" value={remarks} onChange={(e) => setRemarks(e.target.value)} slotProps={{ htmlInput: { maxLength: 300 } }} />
              {value && problems.length > 0 && <Alert severity="warning">{problems[0]}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button variant="contained" disabled={problems.length > 0} onClick={() => setStep('review')}>Review transfer</Button>
              </Box>
            </>
          )}
          {step === 'review' && recipient && (
            <>
              <Typography variant="h2">Review and confirm</Typography>
              {([
                ['To', `${recipient.accountCode} · ${recipient.displayName}`],
                ['Amount', money(num)],
                ['Fee', money(0)],
                ['Your balance after', money((wallet.data?.availableBalance ?? 0) - num)],
                ['Remarks', remarks || '–'],
              ] as const).map(([k, v]) => (
                <Box key={k} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 1.25, borderBottom: `1px solid ${colors.border}` }}>
                  <Typography color="text.secondary">{k}</Typography><Typography sx={{ fontWeight: 700 }}>{v}</Typography>
                </Box>
              ))}
              <Alert severity="info" variant="outlined">Pressing Confirm twice won't send twice: this request carries a one-time key.</Alert>
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
                <Button variant="outlined" color="inherit" onClick={() => setStep('form')} disabled={busy}>Edit</Button>
                <Button variant="contained" onClick={send} disabled={busy}>{busy ? 'Sending…' : `Confirm and send ${money(num)}`}</Button>
              </Box>
            </>
          )}
          {step === 'done' && result && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, alignItems: 'flex-start' }}>
              <CheckCircleRounded sx={{ fontSize: 56, color: colors.success }} aria-hidden />
              <Typography variant="h1">{money(result.amount)} sent</Typography>
              <Typography color="text.secondary">{result.toCode} · {result.toName} was credited. Both wallets got a ledger entry in the same database transaction.</Typography>
              <Typography>Reference <b style={{ fontFamily: 'ui-monospace, monospace' }}>{result.referenceNo}</b></Typography>
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Button variant="contained" component={RouterLink} to={`/transactions/${result.id}`}>View receipt</Button>
                <Button variant="outlined" color="inherit" onClick={reset}>New transfer</Button>
              </Box>
            </Box>
          )}
        </Section>
        <Section title="Checked before sending" sx={{ flex: '1 1 300px', minWidth: 0 }}>
          <Typography variant="body2">Available: <b>{money(wallet.data?.availableBalance)}</b></Typography>
          {limit ? (
            <>
              <Typography variant="body2">Per transfer: {money(limit.minTxn)} to {money(limit.maxTxn)}</Typography>
              <Typography variant="body2">Today: {money(limit.usedToday)} of {money(limit.dailyAmount)} used · {limit.countToday} of {limit.dailyCount} transfers</Typography>
              <Typography variant="body2">This month: {money(limit.usedThisMonth)} of {money(limit.monthlyAmount)}</Typography>
            </>
          ) : (
            <Typography variant="body2" color="text.secondary">No transfer limit is set for your account.</Typography>
          )}
          <Typography variant="caption" color="text.secondary">The server checks account status, hierarchy, balance and limits again when you confirm.</Typography>
        </Section>
      </Box>
    </>
  )
}
