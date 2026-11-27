import { Alert, Box, Button, ButtonBase, MenuItem, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import LockOutlined from '@mui/icons-material/LockOutlined'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { Section } from '../../components/common/Section'
import { ReviewRow, StatusDialog } from '../../components/common/StatusDialog'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { LimitStatus, Transaction, WalletSummary } from '../../types/domain'
import { NETWORK_LABEL, type CardNetwork, type GatewayCategory, type PaymentSession } from '../../types/services'
import { money } from '../../utils/format'
import { MainWalletPanel, RecentList, Steps } from './ServiceParts'

type Stage = 'category' | 'details' | 'gateway' | 'otp' | 'upi'

/** Luhn check, so obvious typos are caught before the gateway page. */
function luhn(num: string): boolean {
  let sum = 0
  for (let i = 0; i < num.length; i++) {
    let d = Number(num[num.length - 1 - i])
    if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9 }
    sum += d
  }
  return num.length >= 12 && sum % 10 === 0
}

/** A card is valid through the last day of its expiry month. */
function expired(month: number, year: number): boolean {
  return new Date(2000 + year, month, 1) <= new Date()
}

const charge = (c: GatewayCategory, amt: number) => Math.round(amt * c.chargePercent) / 100

/**
 * Add funds to the main wallet: gateway category (Ecom, Edu, Travel) → card type, holder, card number, amount →
 * payment page (card or UPI) → OTP → status popup. Amount minus the category charge is credited.
 * The full card number, expiry and CVV stay in this browser page; only the last 4 digits are sent to PrimePay.
 */
export function AddFundsPage() {
  const wallet = useApiData(() => api.get<WalletSummary>('/wallets/me'), [])
  const limits = useApiData(() => api.get<LimitStatus[]>('/wallets/me/limits'), [])
  const categories = useApiData(() => api.get<GatewayCategory[]>('/add-funds/categories'), [])
  const history = useApiData(() => api.page<Transaction>('/transactions', { type: 'ADD_FUNDS', size: 10 }), [])

  const [stage, setStage] = useState<Stage>('category')
  const [category, setCategory] = useState<GatewayCategory | null>(null)
  const [network, setNetwork] = useState<CardNetwork>('VISA')
  const [holder, setHolder] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [amountText, setAmountText] = useState('')
  const [mode, setMode] = useState<'CARD' | 'UPI'>('CARD')
  const [expiry, setExpiry] = useState('')
  const [cvv, setCvv] = useState('')
  const [upiId, setUpiId] = useState('')
  const [otp, setOtp] = useState('')
  const [session, setSession] = useState<PaymentSession | null>(null)
  const [done, setDone] = useState<PaymentSession | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const amount = Number(amountText)
  const limit = limits.data?.find((l) => l.serviceCode === 'ADD_FUNDS')
  const stepIndex = { category: 0, details: 1, gateway: 2, otp: 3, upi: 3 }[stage]

  const detailProblems: string[] = []
  if (category) {
    if (holder.trim().length < 2) detailProblems.push('Enter the card holder name.')
    if (!luhn(cardNumber)) detailProblems.push('Enter a valid card number.')
    if (!(amount > 0)) detailProblems.push('Enter the amount.')
    else if (amount < category.minAmount || amount > category.maxAmount) detailProblems.push(`${category.name} allows ${money(category.minAmount)} to ${money(category.maxAmount)} per payment.`)
  }
  const gatewayProblems: string[] = []
  if (mode === 'CARD') {
    const m = /^(0[1-9]|1[0-2])\/([0-9]{2})$/.exec(expiry)
    if (!m) gatewayProblems.push('Enter the expiry as MM/YY.')
    else {
      if (expired(Number(m[1]), Number(m[2]))) gatewayProblems.push('This card has expired.')
    }
    if (!/^[0-9]{3,4}$/.test(cvv)) gatewayProblems.push('Enter the CVV.')
  } else if (!/^[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}$/.test(upiId)) gatewayProblems.push('Enter a valid UPI ID, e.g. name@bank.')

  async function run<T>(fn: () => Promise<T>) {
    setBusy(true)
    setError(null)
    try {
      return await fn()
    } catch (e) {
      setError(errorMessage(e))
      return undefined
    } finally {
      setBusy(false)
    }
  }

  const start = () => run(async () => {
    const { data } = await api.post<PaymentSession>('/add-funds/sessions', { categoryCode: category!.code, cardNetwork: network, cardHolder: holder.trim(), amount })
    setSession(data)
    setStage('gateway')
  })

  const choose = () => run(async () => {
    const { data } = await api.post<PaymentSession>(`/add-funds/sessions/${session!.sessionRef}/pay`,
      mode === 'CARD' ? { mode, cardLast4: cardNumber.slice(-4) } : { mode, upiId })
    setSession(data)
    setCvv('') // never kept longer than needed
    setStage(mode === 'CARD' ? 'otp' : 'upi')
  })

  const complete = () => run(async () => {
    const { data } = await api.post<PaymentSession>(`/add-funds/sessions/${session!.sessionRef}/complete`, mode === 'CARD' ? { otp } : { upiId })
    if (data.status === 'SUCCESS' || data.status === 'FAILED') {
      setDone(data)
      wallet.reload()
      limits.reload()
      history.reload()
    }
  })

  function reset() {
    setDone(null)
    setSession(null)
    setStage('category')
    setCategory(null)
    setCardNumber('')
    setExpiry('')
    setCvv('')
    setOtp('')
    setUpiId('')
    setAmountText('')
    setError(null)
  }

  return (
    <>
      <PageHeader eyebrow="Services" title="Add funds" subtitle="Top up your main wallet with a card or UPI through the payment gateway. The amount minus the gateway charge for the category is credited." />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section sx={{ flex: '2 1 520px', minWidth: 0 }}>
          <Steps steps={['Category', 'Card & amount', 'Payment mode', 'Verify']} active={stepIndex} />

          {stage === 'category' && (
            <>
              <Typography variant="h2">Choose a gateway category</Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 1.5 }}>
                {(categories.data ?? []).map((c) => (
                  <ButtonBase key={c.code} onClick={() => { setCategory(c); setStage('details') }} focusRipple
                    sx={{ display: 'block', textAlign: 'left', p: 2, borderRadius: 2, border: `1px solid ${colors.border}`, '&:hover': { borderColor: colors.indigo600, bgcolor: colors.indigo50 } }}>
                    <Typography sx={{ fontWeight: 800, fontSize: 17 }}>{c.name}</Typography>
                    <Typography variant="body2" color="text.secondary">{money(c.minAmount)} – {money(c.maxAmount)}</Typography>
                    <Typography variant="body2" sx={{ mt: 0.5, fontWeight: 600, color: colors.indigo700 }}>Charge {c.chargePercent}%</Typography>
                  </ButtonBase>
                ))}
              </Box>
              {categories.data?.length === 0 && <Alert severity="info">No gateway category is available right now.</Alert>}
            </>
          )}

          {stage === 'details' && category && (
            <>
              <Typography variant="h2">{category.name} · card and amount</Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                <TextField select label="Card type" value={network} onChange={(e) => setNetwork(e.target.value as CardNetwork)}>
                  {(Object.keys(NETWORK_LABEL) as CardNetwork[]).map((n) => <MenuItem key={n} value={n}>{NETWORK_LABEL[n]}</MenuItem>)}
                </TextField>
                <TextField label="Card holder name" value={holder} onChange={(e) => setHolder(e.target.value)} autoComplete="cc-name" />
                <TextField label="Card number" value={cardNumber.replace(/(.{4})/g, '$1 ').trim()} onChange={(e) => setCardNumber(e.target.value.replace(/\D/g, '').slice(0, 19))}
                  autoComplete="cc-number" slotProps={{ htmlInput: { inputMode: 'numeric' } }} helperText="Stays on this page; PrimePay keeps only the last 4 digits." />
                <TextField label="Amount (₹)" type="number" value={amountText} onChange={(e) => setAmountText(e.target.value)}
                  helperText={`${money(category.minAmount)} to ${money(category.maxAmount)}`} slotProps={{ htmlInput: { min: category.minAmount, max: category.maxAmount, step: '0.01' } }} />
              </Box>
              {amount > 0 && (
                <Box>
                  <ReviewRow label="You pay" value={money(amount)} />
                  <ReviewRow label={`Gateway charge (${category.chargePercent}%)`} value={money(charge(category, amount))} />
                  <ReviewRow label="Credited to main wallet" value={money(amount - charge(category, amount))} strong />
                </Box>
              )}
              {(holder || cardNumber || amountText) && detailProblems.length > 0 && <Alert severity="warning">{detailProblems[0]}</Alert>}
              {limit && amount > limit.maxTxn && <Alert severity="warning">Your add-funds limit is {money(limit.maxTxn)} per payment.</Alert>}
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Button variant="outlined" color="inherit" onClick={() => setStage('category')}>Back</Button>
                <Button variant="contained" disabled={busy || detailProblems.length > 0} onClick={start}>{busy ? 'Opening…' : 'Next'}</Button>
              </Box>
            </>
          )}

          {stage === 'gateway' && session && (
            <Box sx={{ border: `1px solid ${colors.borderStrong}`, borderRadius: 2, p: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <LockOutlined fontSize="small" sx={{ color: colors.success }} aria-hidden />
                <Typography sx={{ fontWeight: 700 }}>Secure payment page (simulated gateway)</Typography>
              </Box>
              <ReviewRow label="Amount" value={money(session.amount)} strong />
              <ToggleButtonGroup exclusive value={mode} onChange={(_, v) => v && setMode(v)} aria-label="Payment mode">
                <ToggleButton value="CARD" sx={{ px: 3, textTransform: 'none' }}>{NETWORK_LABEL[session.cardNetwork]} card</ToggleButton>
                <ToggleButton value="UPI" sx={{ px: 3, textTransform: 'none' }}>UPI</ToggleButton>
              </ToggleButtonGroup>
              {mode === 'CARD' ? (
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr' }, gap: 2 }}>
                  <TextField label="Card number" value={`•••• •••• •••• ${cardNumber.slice(-4)}`} disabled />
                  <TextField label="Expiry (MM/YY)" value={expiry} autoComplete="cc-exp"
                    onChange={(e) => { const d = e.target.value.replace(/\D/g, '').slice(0, 4); setExpiry(d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d) }} />
                  <TextField label="CVV" type="password" value={cvv} autoComplete="cc-csc" onChange={(e) => setCvv(e.target.value.replace(/\D/g, '').slice(0, 4))} slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
                </Box>
              ) : (
                <TextField label="UPI ID" value={upiId} onChange={(e) => setUpiId(e.target.value.trim())} placeholder="name@bank" />
              )}
              {(expiry || cvv || upiId) && gatewayProblems.length > 0 && <Alert severity="warning">{gatewayProblems[0]}</Alert>}
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Button variant="outlined" color="inherit" disabled={busy} onClick={reset}>Cancel</Button>
                <Button variant="contained" disabled={busy || gatewayProblems.length > 0} onClick={choose}>{busy ? 'Please wait…' : `Pay ${money(session.amount)}`}</Button>
              </Box>
            </Box>
          )}

          {stage === 'otp' && session && (
            <>
              <Typography variant="h2">Enter the OTP</Typography>
              <Typography color="text.secondary">The card issuer sent a 6-digit OTP to the mobile number linked to the {NETWORK_LABEL[session.cardNetwork]} card ••{session.cardLast4}.</Typography>
              {session.devOtp && <Alert severity="info" variant="outlined">Development only: the simulated OTP is <b>{session.devOtp}</b>.</Alert>}
              <TextField label="OTP" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} sx={{ maxWidth: 220 }}
                autoComplete="one-time-code" slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 6 } }} />
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Button variant="outlined" color="inherit" disabled={busy} onClick={reset}>Cancel</Button>
                <Button variant="contained" disabled={busy || otp.length !== 6} onClick={complete}>{busy ? 'Verifying…' : 'Verify and pay'}</Button>
              </Box>
            </>
          )}

          {stage === 'upi' && session && (
            <>
              <Typography variant="h2">Approve in your UPI app</Typography>
              <Typography color="text.secondary">A collect request for {money(session.amount)} was sent to {session.upiMasked}. Approve it in the UPI app, then confirm here.</Typography>
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Button variant="outlined" color="inherit" disabled={busy} onClick={reset}>Cancel</Button>
                <Button variant="contained" disabled={busy} onClick={complete}>{busy ? 'Checking…' : 'I have approved it'}</Button>
              </Box>
            </>
          )}
        </Section>
        <MainWalletPanel wallet={wallet.data} limit={limit} countLabel="top-ups"
          note="Development uses a simulated gateway: nothing is charged. A card ending 0000, or a UPI ID starting with “fail”, is declined so you can see the failed flow." />
      </Box>

      <Box sx={{ mt: 2 }}>
        <RecentList title="Add funds history" rows={history.data?.items ?? null} empty="No top-ups yet."
          describe={(t) => `${t.gatewayCategory ?? ''} · ${t.fromCode ?? ''}`} />
      </Box>

      {done && (
        <StatusDialog open outcome={done.status === 'SUCCESS' ? 'SUCCESS' : 'FAILED'} onClose={reset}
          title={done.status === 'SUCCESS' ? 'Funds added' : 'Payment failed'}
          message={done.status === 'SUCCESS'
            ? `${money(done.netAmount)} was credited to your main wallet.`
            : `${done.failureReason ?? 'The payment was declined'}. Nothing was added to your wallet and your card or UPI account was not charged.`}
          facts={[['Reference', done.referenceNo ?? '–'], ['Category', done.categoryName], ['Paid with', done.paymentMode === 'UPI' ? `UPI ${done.upiMasked}` : `${NETWORK_LABEL[done.cardNetwork]} ••${done.cardLast4}`],
            ['Amount', money(done.amount)], ['Charge', money(done.charge)], ['Credited', done.status === 'SUCCESS' ? money(done.netAmount) : money(0)]]}
          actions={done.transactionId ? <Button variant="contained" component={RouterLink} to={`/transactions/${done.transactionId}`}>View receipt</Button> : undefined} />
      )}
    </>
  )
}
