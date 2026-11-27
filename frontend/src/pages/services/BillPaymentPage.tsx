import { Alert, Box, Button, MenuItem, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import CreditCardOutlined from '@mui/icons-material/CreditCardOutlined'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { Section } from '../../components/common/Section'
import { ReviewRow, StatusDialog, type Outcome } from '../../components/common/StatusDialog'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api, newIdempotencyKey } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { LimitStatus, Transaction, WalletSummary } from '../../types/domain'
import type { Biller, FetchedBill } from '../../types/services'
import { date, money } from '../../utils/format'
import { MainWalletPanel, RecentList, Steps } from './ServiceParts'

type AmountChoice = 'TOTAL' | 'MINIMUM' | 'OTHER'

/**
 * Credit card bill payment: category → bank → last 4 digits + registered mobile → fetch bill →
 * pay from the main wallet → status popup → history with recheck for pending payments.
 */
export function BillPaymentPage() {
  const wallet = useApiData(() => api.get<WalletSummary>('/wallets/me'), [])
  const limits = useApiData(() => api.get<LimitStatus[]>('/wallets/me/limits'), [])
  const categories = useApiData(() => api.get<{ code: string; name: string }[]>('/bills/categories'), [])
  const [category, setCategory] = useState('CREDIT_CARD')
  const billers = useApiData(() => api.get<Biller[]>('/bills/billers', { category }), [category])
  const history = useApiData(() => api.page<Transaction>('/transactions', { type: 'BILL_PAYMENT', size: 10 }), [])

  const [billerId, setBillerId] = useState<number | ''>('')
  const [last4, setLast4] = useState('')
  const [mobile, setMobile] = useState('')
  const [bill, setBill] = useState<FetchedBill | null>(null)
  const [choice, setChoice] = useState<AmountChoice>('TOTAL')
  const [other, setOther] = useState('')
  const [key, setKey] = useState(newIdempotencyKey)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Transaction | null>(null)
  const [rechecking, setRechecking] = useState<number | null>(null)

  const limit = limits.data?.find((l) => l.serviceCode === 'BILL_PAYMENT')
  const amount = bill ? (choice === 'TOTAL' ? bill.totalDue : choice === 'MINIMUM' ? bill.minimumDue : Number(other)) : 0
  const step = result ? 3 : bill ? 2 : 0 + (billerId ? 1 : 0)
  const fetchProblems: string[] = []
  if (!billerId) fetchProblems.push('Select the bank that issued the card.')
  if (!/^[0-9]{4}$/.test(last4)) fetchProblems.push('Enter the last 4 digits of the card.')
  if (!/^[6-9][0-9]{9}$/.test(mobile)) fetchProblems.push('Enter the 10-digit mobile number registered with the card.')
  const payProblems: string[] = []
  if (bill) {
    if (!(amount > 0)) payProblems.push('Enter the amount to pay.')
    if (amount > bill.totalDue) payProblems.push(`You can pay at most the total due of ${money(bill.totalDue)}.`)
    if (limit && amount > 0 && amount < limit.minTxn) payProblems.push(`Minimum bill payment is ${money(limit.minTxn)}.`)
    if (wallet.data && amount + bill.charge > wallet.data.availableBalance) payProblems.push(`You need ${money(amount + bill.charge)} including the charge; your main wallet has ${money(wallet.data.availableBalance)}. Add funds first.`)
  }

  async function fetchBill() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<FetchedBill>('/bills/fetch', { billerId, cardLast4: last4, mobile })
      setBill(data)
      setChoice('TOTAL')
      setOther('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function pay() {
    if (!bill) return
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<Transaction>('/bills/pay', { fetchRef: bill.fetchRef, amount }, { 'Idempotency-Key': key })
      setResult(data)
      wallet.reload()
      limits.reload()
      history.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function recheck(t: Transaction) {
    setRechecking(t.id)
    try {
      const { data } = await api.post<Transaction>(`/bills/${t.id}/recheck`)
      setResult(data)
      wallet.reload()
      history.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setRechecking(null)
    }
  }

  function startOver() {
    setResult(null)
    setBill(null)
    setLast4('')
    setMobile('')
    setKey(newIdempotencyKey())
    setError(null)
  }

  const outcome = (result?.status === 'SUCCESS' || result?.status === 'PENDING' ? result.status : 'FAILED') as Outcome

  return (
    <>
      <PageHeader eyebrow="Services" title="Bill payments" subtitle="Pay a customer's credit card bill from your main wallet. The bill amount plus the service charge is debited; you earn commission on every successful payment." />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section sx={{ flex: '2 1 520px', minWidth: 0 }}>
          <Steps steps={['Category', 'Card details', 'Fetch & pay', 'Status']} active={Math.min(step, 3)} />
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>Bill category</Typography>
            <ToggleButtonGroup exclusive value={category} onChange={(_, v) => v && (setCategory(v), setBillerId(''), setBill(null))} aria-label="Bill category">
              {(categories.data ?? []).map((c) => (
                <ToggleButton key={c.code} value={c.code} sx={{ px: 3, textTransform: 'none', gap: 1 }}><CreditCardOutlined fontSize="small" />{c.name}</ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Box>

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField select label="Card issuing bank" value={billerId} disabled={!!bill} onChange={(e) => setBillerId(Number(e.target.value))} sx={{ gridColumn: { sm: '1 / -1' } }}>
              {(billers.data ?? []).filter((b) => b.enabled).map((b) => <MenuItem key={b.id} value={b.id}>{b.name}</MenuItem>)}
            </TextField>
            <TextField label="Last 4 digits of card" value={last4} disabled={!!bill} onChange={(e) => setLast4(e.target.value.replace(/\D/g, '').slice(0, 4))}
              slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 4 } }} helperText="Only the last 4 digits. Never ask for the full card number." />
            <TextField label="Card-registered mobile" required value={mobile} disabled={!!bill} onChange={(e) => setMobile(e.target.value.replace(/\D/g, '').slice(0, 10))}
              slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 10 } }} helperText="Mandatory: the mobile number linked to the card." />
          </Box>
          {!bill && (
            <>
              {(last4 || mobile) && fetchProblems.length > 0 && <Alert severity="warning">{fetchProblems[0]}</Alert>}
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button variant="contained" disabled={busy || fetchProblems.length > 0} onClick={fetchBill}>{busy ? 'Fetching…' : 'Fetch bill'}</Button>
              </Box>
            </>
          )}

          {bill && (
            <>
              <Box sx={{ border: `1px solid ${colors.border}`, borderRadius: 2, p: 2, bgcolor: colors.canvas }}>
                <Typography variant="h2" sx={{ mb: 1 }}>Bill details</Typography>
                <ReviewRow label="Card holder" value={bill.customerName} />
                <ReviewRow label="Card" value={`${bill.billerName} ••${bill.cardLast4}`} />
                <ReviewRow label="Registered mobile" value={bill.mobileMasked} />
                <ReviewRow label="Total due" value={money(bill.totalDue)} strong />
                <ReviewRow label="Minimum due" value={money(bill.minimumDue)} />
                <ReviewRow label="Due date" value={date(bill.dueDate)} />
              </Box>
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>Amount to pay</Typography>
                <ToggleButtonGroup exclusive value={choice} onChange={(_, v) => v && setChoice(v)} aria-label="Amount to pay" sx={{ flexWrap: 'wrap' }}>
                  <ToggleButton value="TOTAL" sx={{ textTransform: 'none', px: 2 }}>Total due · {money(bill.totalDue)}</ToggleButton>
                  <ToggleButton value="MINIMUM" sx={{ textTransform: 'none', px: 2 }}>Minimum · {money(bill.minimumDue)}</ToggleButton>
                  <ToggleButton value="OTHER" sx={{ textTransform: 'none', px: 2 }}>Other amount</ToggleButton>
                </ToggleButtonGroup>
                {choice === 'OTHER' && <TextField sx={{ mt: 1.5, maxWidth: 260 }} fullWidth label="Amount (₹)" type="number" value={other} onChange={(e) => setOther(e.target.value)} slotProps={{ htmlInput: { min: 1, step: '0.01' } }} />}
              </Box>
              <ReviewRow label="Bill amount" value={money(amount || 0)} />
              <ReviewRow label="Service charge" value={money(bill.charge)} />
              <ReviewRow label="Debited from main wallet" value={money((amount || 0) + bill.charge)} strong />
              {payProblems.length > 0 && amount > 0 && <Alert severity="warning">{payProblems[0]}</Alert>}
              {error && <Alert severity="error">{error}</Alert>}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Button variant="outlined" color="inherit" disabled={busy} onClick={() => { setBill(null); setError(null) }}>Change card</Button>
                <Button variant="contained" disabled={busy || payProblems.length > 0} onClick={pay}>{busy ? 'Paying…' : `Pay ${money((amount || 0) + bill.charge)}`}</Button>
              </Box>
            </>
          )}
        </Section>
        <MainWalletPanel wallet={wallet.data} limit={limit} countLabel="bill payments"
          note="Development uses a simulated biller. Card ending 9999 has no bill; 0000 fails (refunded); 1111 is pending then succeeds on recheck; 2222 is pending then fails on recheck. No real money moves." />
      </Box>

      <Box sx={{ mt: 2 }}>
        <RecentList title="Bill payment history" rows={history.data?.items ?? null} empty="No bill payments yet."
          describe={(t) => `${t.billerName ?? 'Credit card'} ••${t.cardLast4 ?? ''}${t.customerName ? ' · ' + t.customerName : ''}`}
          action={(t) => t.status === 'PENDING'
            ? <Button size="small" variant="outlined" disabled={rechecking === t.id} onClick={() => recheck(t)}>{rechecking === t.id ? 'Checking…' : 'Recheck'}</Button>
            : null} />
      </Box>

      {result && (
        <StatusDialog open outcome={outcome} onClose={startOver}
          title={outcome === 'SUCCESS' ? 'Bill paid' : outcome === 'PENDING' ? 'Payment pending' : 'Payment failed'}
          message={outcome === 'SUCCESS'
            ? `${money(result.amount)} paid to ${result.billerName} ••${result.cardLast4}. ${money(result.amount + result.fee)} was debited from your main wallet.`
            : outcome === 'PENDING'
              ? 'The bank has not confirmed yet. The amount stays debited until it does. Use Recheck in the history to get the final status.'
              : `${result.failureReason ?? 'The biller declined the payment'}. The full ${money(result.amount + result.fee)} was refunded to your main wallet.`}
          facts={[['Reference', result.referenceNo], ['Card holder', result.customerName ?? '–'], ['Bill amount', money(result.amount)], ['Charge', money(result.fee)], ...(result.externalRef ? [['Biller reference', result.externalRef] as [string, string]] : [])]}
          actions={<>
            {outcome === 'PENDING' && <Button variant="outlined" disabled={rechecking !== null} onClick={() => recheck(result)}>Recheck status</Button>}
            <Button variant="contained" component={RouterLink} to={`/transactions/${result.id}`}>View receipt</Button>
          </>} />
      )}
    </>
  )
}
