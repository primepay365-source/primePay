import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Radio, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import AccountBalanceOutlined from '@mui/icons-material/AccountBalanceOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { PageHeader } from '../../components/common/PageHeader'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { ReviewRow, StatusDialog } from '../../components/common/StatusDialog'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api, newIdempotencyKey } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { LimitStatus, Transaction, WalletSummary } from '../../types/domain'
import type { AddBeneficiaryResult, Beneficiary } from '../../types/services'
import { money } from '../../utils/format'
import { MainWalletPanel, RecentList } from './ServiceParts'

/** Add-account dialog: the bank verifies the account; only a verified account can receive money. */
function AddAccountDialog({ open, onClose, onAdded }: { open: boolean; onClose: () => void; onAdded: (r: AddBeneficiaryResult) => void }) {
  const [form, setForm] = useState({ accountNumber: '', confirm: '', ifsc: '', holderName: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const problems: string[] = []
  if (!/^[0-9]{9,18}$/.test(form.accountNumber)) problems.push('Account number must be 9 to 18 digits.')
  if (form.confirm !== form.accountNumber) problems.push('Account numbers do not match.')
  if (!/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/.test(form.ifsc)) problems.push('Enter a valid IFSC, for example HDFC0001234.')
  if (form.holderName.trim().length < 2) problems.push('Enter the account holder name.')

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<AddBeneficiaryResult>('/beneficiaries', { accountNumber: form.accountNumber, ifsc: form.ifsc, holderName: form.holderName.trim() })
      setForm({ accountNumber: '', confirm: '', ifsc: '', holderName: '' })
      onAdded(data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add bank account</DialogTitle>
      <DialogContent sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, pt: '8px !important' }}>
        <TextField label="Account number" value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value.replace(/\D/g, '').slice(0, 18) })} slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
        <TextField label="Confirm account number" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value.replace(/\D/g, '').slice(0, 18) })} slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          error={!!form.confirm && form.confirm !== form.accountNumber} helperText={form.confirm && form.confirm !== form.accountNumber ? "Doesn't match" : ' '} />
        <TextField label="IFSC" value={form.ifsc} onChange={(e) => setForm({ ...form, ifsc: e.target.value.toUpperCase().slice(0, 11) })} />
        <TextField label="Account holder name" value={form.holderName} onChange={(e) => setForm({ ...form, holderName: e.target.value })} />
        <Box sx={{ gridColumn: '1 / -1', display: 'flex', flexDirection: 'column', gap: 1 }}>
          {(form.accountNumber || form.ifsc) && problems.length > 0 && <Alert severity="warning">{problems[0]}</Alert>}
          {error && <Alert severity="error">{error}</Alert>}
          <Typography variant="body2" color="text.secondary">The bank checks the account and returns the registered name. Development uses a simulated bank: an account ending 9999 can't be verified.</Typography>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={busy || problems.length > 0}>{busy ? 'Verifying…' : 'Verify and add'}</Button>
      </DialogActions>
    </Dialog>
  )
}

/** Withdraw funds: transfer from the main wallet to one of the retailer's verified bank accounts. */
export function WithdrawPage() {
  const wallet = useApiData(() => api.get<WalletSummary>('/wallets/me'), [])
  const limits = useApiData(() => api.get<LimitStatus[]>('/wallets/me/limits'), [])
  const services = useApiData(() => api.get<{ serviceCode: string; feeAmount: number; enabled: boolean }[]>('/services'), [])
  const accounts = useApiData(() => api.get<Beneficiary[]>('/beneficiaries'), [])
  const history = useApiData(() => api.page<Transaction>('/transactions', { type: 'PAYOUT', size: 10 }), [])

  const [selected, setSelected] = useState<number | null>(null)
  const [amountText, setAmountText] = useState('')
  const [mode, setMode] = useState<'IMPS' | 'NEFT'>('IMPS')
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)
  const [removing, setRemoving] = useState<Beneficiary | null>(null)
  const [review, setReview] = useState(false)
  const [key, setKey] = useState(newIdempotencyKey)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Transaction | null>(null)

  const service = services.data?.find((s) => s.serviceCode === 'PAYOUT')
  const fee = Number(service?.feeAmount ?? 0)
  const limit = limits.data?.find((l) => l.serviceCode === 'PAYOUT')
  const verified = (accounts.data ?? []).filter((a) => a.status === 'VERIFIED')
  const account = verified.find((a) => a.id === selected) ?? null
  const amount = Number(amountText)

  const problems: string[] = []
  if (!account) problems.push('Choose a verified bank account.')
  if (!(amount > 0)) problems.push('Enter the amount.')
  if (service && !service.enabled) problems.push('Withdrawals are turned off by the platform admin right now.')
  if (mode === 'IMPS' && amount > 25000) problems.push('IMPS is limited to ₹ 25,000. Use NEFT for larger amounts.')
  if (limit && amount > limit.maxTxn) problems.push(`Maximum per withdrawal is ${money(limit.maxTxn)}.`)
  if (wallet.data && amount + fee > wallet.data.availableBalance) problems.push(`You need ${money(amount + fee)} including the charge; your main wallet has ${money(wallet.data.availableBalance)}.`)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post<Transaction>('/withdraw', { beneficiaryId: account!.id, amount, mode }, { 'Idempotency-Key': key })
      setResult(data)
      setReview(false)
      wallet.reload()
      limits.reload()
      history.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Services" title="Withdraw funds" subtitle="Move money from your main wallet to a saved bank account. Add the account first; only accounts the bank has verified can receive money."
        actions={<Button variant="contained" onClick={() => setAdding(true)}>Add bank account</Button>} />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Box sx={{ flex: '2 1 520px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Section title="Saved bank accounts" padded>
            {notice && <Alert severity={notice.ok ? 'success' : 'error'} onClose={() => setNotice(null)}>{notice.text}</Alert>}
            {accounts.data?.length === 0 && (
              <Box sx={{ textAlign: 'center', py: 3 }}>
                <AccountBalanceOutlined sx={{ fontSize: 40, color: colors.textMuted }} aria-hidden />
                <Typography sx={{ fontWeight: 700 }}>No bank accounts yet</Typography>
                <Typography color="text.secondary" variant="body2">Add an account to withdraw funds to it.</Typography>
              </Box>
            )}
            {(accounts.data ?? []).map((a) => (
              <Box key={a.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: 2, border: `1px solid ${selected === a.id ? colors.indigo600 : colors.border}`,
                bgcolor: selected === a.id ? colors.indigo50 : 'transparent', opacity: a.status === 'VERIFIED' ? 1 : 0.75 }}>
                <Radio checked={selected === a.id} disabled={a.status !== 'VERIFIED'} onChange={() => setSelected(a.id)} inputProps={{ 'aria-label': `${a.bankName} ${a.accountMasked}` }} />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 700 }}>{a.verifiedName ?? a.holderName}</Typography>
                  <Typography variant="body2" color="text.secondary">{a.bankName} · a/c {a.accountMasked} · {a.ifsc}</Typography>
                </Box>
                <StatusChip status={a.status} label={a.status === 'VERIFIED' ? 'Verified' : 'Not verified'} />
                <Tooltip title="Remove"><IconButton aria-label={`Remove ${a.accountMasked}`} onClick={() => setRemoving(a)}><DeleteOutline /></IconButton></Tooltip>
              </Box>
            ))}
          </Section>

          <Section title="Fund transfer">
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
              <TextField label="To account" value={account ? `${account.bankName} ${account.accountMasked}` : 'Select a verified account above'} disabled />
              <TextField label="Amount (₹)" type="number" value={amountText} onChange={(e) => setAmountText(e.target.value)} slotProps={{ htmlInput: { min: 1, step: '0.01' } }} />
            </Box>
            <ToggleButtonGroup exclusive value={mode} onChange={(_, v) => v && setMode(v)} aria-label="Transfer mode">
              <ToggleButton value="IMPS" sx={{ px: 3, textTransform: 'none' }}>IMPS · instant · up to ₹ 25,000</ToggleButton>
              <ToggleButton value="NEFT" sx={{ px: 3, textTransform: 'none' }}>NEFT</ToggleButton>
            </ToggleButtonGroup>
            {amountText && problems.length > 0 && <Alert severity="warning">{problems[0]}</Alert>}
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="contained" disabled={problems.length > 0} onClick={() => { setError(null); setReview(true) }}>Review transfer</Button>
            </Box>
          </Section>
        </Box>
        <MainWalletPanel wallet={wallet.data} limit={limit} countLabel="withdrawals"
          note="Development uses a simulated bank: no real money moves. An account ending 0000 verifies but the transfer is declined, so you can see the refund." />
      </Box>

      <Box sx={{ mt: 2 }}>
        <RecentList title="Withdrawal history" rows={history.data?.items ?? null} empty="No withdrawals yet."
          describe={(t) => `${t.beneficiaryName ?? ''} · ${t.beneficiaryBank ?? ''} ••${t.beneficiaryAccountLast4 ?? ''} · ${t.payoutMode ?? ''}`} />
      </Box>

      <AddAccountDialog open={adding} onClose={() => setAdding(false)} onAdded={(r) => {
        setAdding(false)
        accounts.reload()
        if (r.verified) setSelected(r.beneficiary.id)
        setNotice(r.verified
          ? { ok: true, text: `Verified: ${r.beneficiary.verifiedName} · ${r.beneficiary.bankName} ${r.beneficiary.accountMasked} was added to your list.` }
          : { ok: false, text: `${r.reason ?? 'The bank could not verify this account'}. It can't receive withdrawals.` })
      }} />

      <Dialog open={review} onClose={busy ? undefined : () => setReview(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Confirm withdrawal</DialogTitle>
        <DialogContent>
          {account && <>
            <ReviewRow label="To" value={`${account.verifiedName ?? account.holderName}`} />
            <ReviewRow label="Account" value={`${account.bankName} ${account.accountMasked}`} />
            <ReviewRow label="Mode" value={mode} />
            <ReviewRow label="Amount" value={money(amount)} />
            <ReviewRow label="Service charge" value={money(fee)} />
            <ReviewRow label="Debited from main wallet" value={money(amount + fee)} strong />
          </>}
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={() => setReview(false)} disabled={busy}>Edit</Button>
          <Button variant="contained" onClick={confirm} disabled={busy}>{busy ? 'Sending…' : 'Confirm transfer'}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog open={!!removing} title="Remove bank account?" confirmLabel="Remove" danger onClose={() => setRemoving(null)}
        onConfirm={async () => { await api.del(`/beneficiaries/${removing!.id}`); if (selected === removing!.id) setSelected(null); setRemoving(null); accounts.reload() }}>
        {removing ? `${removing.bankName} ${removing.accountMasked} will be removed from your list. Past withdrawals stay in your history.` : ''}
      </ConfirmDialog>

      {result && (
        <StatusDialog open outcome={result.status === 'SUCCESS' ? 'SUCCESS' : result.status === 'PENDING' ? 'PENDING' : 'FAILED'}
          onClose={() => { setResult(null); setAmountText(''); setKey(newIdempotencyKey()) }}
          title={result.status === 'SUCCESS' ? 'Transfer successful' : result.status === 'PENDING' ? 'Transfer pending' : 'Transfer failed'}
          message={result.status === 'SUCCESS'
            ? `${money(result.amount)} sent to ${result.beneficiaryName} a/c ••${result.beneficiaryAccountLast4}.`
            : result.status === 'PENDING' ? 'The bank has not confirmed yet.' : `${result.failureReason}. The full ${money(result.amount + result.fee)} was refunded to your main wallet.`}
          facts={[['Reference', result.referenceNo], ['Amount', money(result.amount)], ['Charge', money(result.fee)], ...(result.externalRef ? [['Bank reference (UTR)', result.externalRef] as [string, string]] : [])]}
          actions={<Button variant="contained" component={RouterLink} to={`/transactions/${result.id}`}>View receipt</Button>} />
      )}
    </>
  )
}
