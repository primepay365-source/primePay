import { Alert, Box, Button, ButtonBase, Checkbox, FormControlLabel, MenuItem, TextField, Typography } from '@mui/material'
import CheckRounded from '@mui/icons-material/CheckRounded'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { Section } from '../../components/common/Section'
import { errorMessage } from '../../hooks/useApiData'
import { apiClient } from '../../services/apiClient'
import { colors } from '../../theme/tokens'

const STEPS = ['Personal info', 'KYC', 'Bank details', 'Limits & commission', 'Agreement']
const DRAFT_KEY = 'primepay.onboardingDraft'
const AGREEMENT_VERSION = 'MASTER-DISTRIBUTOR-V1'

interface Draft {
  accountCode: string; fullName: string; mobile: string; email: string; address: string
  kycDocumentType: string; kycNumber: string
  bankAccountHolder: string; bankName: string; bankIfsc: string; accountNumber: string; confirmAccount: string
  velocityPerTransaction: string; velocityDaily: string; commissionPayin: string; commissionPayout: string; commissionBillPayment: string
}

const EMPTY: Draft = {
  accountCode: '', fullName: '', mobile: '', email: '', address: '', kycDocumentType: 'PAN', kycNumber: '',
  bankAccountHolder: '', bankName: '', bankIfsc: '', accountNumber: '', confirmAccount: '',
  velocityPerTransaction: '200000', velocityDaily: '1000000', commissionPayin: '0', commissionPayout: '0.20', commissionBillPayment: '0',
}

function readDraft(): Draft {
  try {
    return { ...EMPTY, ...JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? '{}') }
  } catch {
    return EMPTY
  }
}

/** Five steps; answers are kept (in this browser tab) while moving between steps. Submitted once at the end. */
export function OnboardingPage() {
  const [step, setStep] = useState(0)
  const [d, setD] = useState<Draft>(readDraft)
  const [photo, setPhoto] = useState<File | null>(null)
  const [otp, setOtp] = useState('')
  const [devOtp, setDevOtp] = useState<string | null>(null)
  const [verified, setVerified] = useState(false)
  const [accepted, setAccepted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<{ accountCode: string; username: string; initialPassword: string } | null>(null)

  useEffect(() => {
    try {
      // Only non-sensitive fields are kept in the draft: no KYC or bank numbers.
      const { kycNumber: _k, accountNumber: _a, confirmAccount: _c, ...safe } = d
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify(safe))
    } catch {
      // storage unavailable; the draft lives only in memory
    }
  }, [d])

  const set = (k: keyof Draft) => (e: React.ChangeEvent<HTMLInputElement>) => setD({ ...d, [k]: e.target.value })

  const stepProblems: string[][] = [
    [
      !/^[A-Za-z0-9_-]{3,40}$/.test(d.accountCode) && 'User ID: 3–40 letters, numbers, - or _',
      !d.fullName.trim() && 'Enter the full name',
      !/^\+?[0-9]{7,15}$/.test(d.mobile) && 'Enter a valid mobile number',
      !/^\S+@\S+\.\S+$/.test(d.email) && 'Enter a valid email',
      !d.address.trim() && 'Enter the business address',
      !photo && 'Upload a photo (JPG, PNG or WebP, up to 5 MB)',
      !verified && 'Verify the mobile number with OTP',
    ].filter(Boolean) as string[],
    [!(d.kycNumber.replace(/\s/g, '').length >= 4) && 'Enter the document number'].filter(Boolean) as string[],
    [
      !d.bankAccountHolder.trim() && 'Enter the account holder name',
      !d.bankName.trim() && 'Enter the bank name',
      !/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/.test(d.bankIfsc) && 'Enter a valid IFSC',
      !/^[0-9]{9,18}$/.test(d.accountNumber) && 'Account number must be 9–18 digits',
      d.accountNumber !== d.confirmAccount && 'Account numbers do not match',
    ].filter(Boolean) as string[],
    [Number(d.velocityDaily) < Number(d.velocityPerTransaction) && 'Daily limit must be at least the per-transaction limit'].filter(Boolean) as string[],
    [!accepted && 'Accept the agreement'].filter(Boolean) as string[],
  ]

  async function requestOtp() {
    setError(null)
    try {
      const { data } = await apiClient.post<{ message: string; developmentCode?: string; code?: string }>('/onboarding/distributors/otp/request', { mobile: d.mobile })
      const code = (data as Record<string, string>).developmentCode ?? (data as Record<string, string>).code ?? null
      setDevOtp(code)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function verifyOtp() {
    setError(null)
    try {
      await apiClient.post('/onboarding/distributors/otp/verify', { mobile: d.mobile, code: otp })
      setVerified(true)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function submit() {
    if (!photo) return
    setBusy(true)
    setError(null)
    try {
      const body = new FormData()
      body.append('request', new Blob([JSON.stringify({
        accountCode: d.accountCode, fullName: d.fullName, mobile: d.mobile, email: d.email, address: d.address,
        kycDocumentType: d.kycDocumentType, kycLastFour: d.kycNumber.replace(/\s/g, '').slice(-4),
        bankAccountHolder: d.bankAccountHolder, bankName: d.bankName, bankIfsc: d.bankIfsc.toUpperCase(), bankAccountLastFour: d.accountNumber.slice(-4),
        velocityPerTransaction: Number(d.velocityPerTransaction), velocityDaily: Number(d.velocityDaily),
        commissionPayin: Number(d.commissionPayin), commissionPayout: Number(d.commissionPayout), commissionBillPayment: Number(d.commissionBillPayment),
        agreementVersion: AGREEMENT_VERSION,
      })], { type: 'application/json' }))
      body.append('photo', photo)
      const { data } = await apiClient.post('/onboarding/distributors', body, { headers: { 'Content-Type': 'multipart/form-data' } })
      setDone(data)
      sessionStorage.removeItem(DRAFT_KEY)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <>
        <PageHeader eyebrow="Network" title="Application submitted" />
        <Section>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, alignItems: 'flex-start' }}>
            <Typography>User ID <b>{done.accountCode}</b> was created for {d.fullName}. Its status is <b>Pending approval</b>: a Platform Admin reviews it next.</Typography>
            <Alert severity="info">Once approved, they sign in with username <b>{done.username}</b> and temporary password <b>{done.initialPassword}</b>. Share these privately; they won't be shown again.</Alert>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button variant="contained" component={RouterLink} to="/approvals">View my applications</Button>
              <Button variant="outlined" color="inherit" onClick={() => { setDone(null); setD(EMPTY); setStep(0); setVerified(false); setAccepted(false); setPhoto(null) }}>Onboard another</Button>
            </Box>
          </Box>
        </Section>
      </>
    )
  }

  return (
    <>
      <PageHeader eyebrow="Network · new Distributor under you" title="Onboard a distributor" subtitle="Your answers stay when you move between steps. KYC and bank numbers are never saved in the draft." />
      <Box component="ol" aria-label="Onboarding steps" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 1.25 }}>
        {STEPS.map((label, i) => {
          const isDone = i < step && stepProblems[i].length === 0
          const current = i === step
          return (
            <li key={label}>
              <ButtonBase onClick={() => setStep(i)} aria-current={current ? 'step' : undefined}
                sx={{ width: '100%', minHeight: 60, justifyContent: 'flex-start', gap: 1.25, px: 1.5, borderRadius: 3, border: `1px solid ${current ? colors.indigo600 : colors.border}`, bgcolor: current ? colors.indigo50 : 'background.paper' }}>
                <Box sx={{ width: 30, height: 30, flex: 'none', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, bgcolor: isDone ? colors.success : current ? colors.indigo600 : '#EEF1F6', color: isDone || current ? '#fff' : colors.textSecondary }}>
                  {isDone ? <CheckRounded sx={{ fontSize: 18 }} /> : i + 1}
                </Box>
                <Box sx={{ textAlign: 'left' }}><Typography variant="caption" color="text.secondary">Step {i + 1}</Typography><Typography sx={{ fontWeight: 600, fontSize: 14 }}>{label}</Typography></Box>
              </ButtonBase>
            </li>
          )
        })}
      </Box>
      <Section title={STEPS[step]}>
        {step === 0 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField label="User ID (also the sign-in username)" value={d.accountCode} onChange={set('accountCode')} placeholder="PP-DT-3400" />
            <TextField label="Full name" value={d.fullName} onChange={set('fullName')} />
            <TextField label="Email" type="email" value={d.email} onChange={set('email')} />
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
              <TextField label="Mobile" value={d.mobile} onChange={(e) => { setD({ ...d, mobile: e.target.value }); setVerified(false) }} sx={{ flex: 1 }} helperText={verified ? '✓ Verified' : ' '} />
              <Button variant="outlined" color="inherit" onClick={requestOtp} disabled={!/^\+?[0-9]{7,15}$/.test(d.mobile) || verified} sx={{ mt: 0.25 }}>Send OTP</Button>
            </Box>
            {!verified && devOtp !== null && (
              <Box sx={{ gridColumn: '1 / -1', display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <TextField label="6-digit OTP" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} sx={{ width: 160 }} />
                <Button variant="contained" onClick={verifyOtp} disabled={otp.length !== 6}>Verify</Button>
                {devOtp && <Typography variant="body2" color="text.secondary">Development mock SMS: the code is <b>{devOtp}</b></Typography>}
              </Box>
            )}
            <TextField label="Business address" value={d.address} onChange={set('address')} multiline minRows={2} sx={{ gridColumn: '1 / -1' }} />
            <Box sx={{ gridColumn: '1 / -1' }}>
              <Button variant="outlined" color="inherit" component="label">{photo ? `Photo: ${photo.name}` : 'Upload photo'}<input hidden type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} /></Button>
            </Box>
          </Box>
        )}
        {step === 1 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField select label="Document" value={d.kycDocumentType} onChange={set('kycDocumentType')}>
              {['PAN', 'AADHAAR', 'PASSPORT', 'DRIVING_LICENSE'].map((k) => <MenuItem key={k} value={k}>{k.replace('_', ' ')}</MenuItem>)}
            </TextField>
            <TextField label="Document number" value={d.kycNumber} onChange={set('kycNumber')} helperText="Only the last 4 characters are sent and stored." type="password" />
            <Alert severity="info" sx={{ gridColumn: '1 / -1' }}>Development uses a mock KYC check. A real KYC provider is connected later, behind the same step.</Alert>
          </Box>
        )}
        {step === 2 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField label="Account holder name" value={d.bankAccountHolder} onChange={set('bankAccountHolder')} />
            <TextField label="Bank name" value={d.bankName} onChange={set('bankName')} />
            <TextField label="IFSC" value={d.bankIfsc} onChange={(e) => setD({ ...d, bankIfsc: e.target.value.toUpperCase() })} />
            <Box />
            <TextField label="Account number" type="password" value={d.accountNumber} onChange={(e) => setD({ ...d, accountNumber: e.target.value.replace(/\D/g, '') })} />
            <TextField label="Confirm account number" value={d.confirmAccount} onChange={(e) => setD({ ...d, confirmAccount: e.target.value.replace(/\D/g, '') })} />
          </Box>
        )}
        {step === 3 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField label="Max per transfer (₹)" type="number" value={d.velocityPerTransaction} onChange={set('velocityPerTransaction')} />
            <TextField label="Daily transfer limit (₹)" type="number" value={d.velocityDaily} onChange={set('velocityDaily')} />
            <TextField label="Payout commission (%)" type="number" value={d.commissionPayout} onChange={set('commissionPayout')} helperText="Recorded with the application; live rates come from the commission rules." />
            <TextField label="Pay-in commission (%)" type="number" value={d.commissionPayin} onChange={set('commissionPayin')} />
          </Box>
        )}
        {step === 4 && (
          <>
            <Box tabIndex={0} aria-label="Distributor agreement text" sx={{ maxHeight: 200, overflowY: 'auto', p: 2, border: `1px solid ${colors.border}`, borderRadius: 3, bgcolor: '#F9FAFC', fontSize: 14, color: colors.textSecondary }}>
              <b style={{ color: colors.text }}>PrimePay Distributor Agreement · {AGREEMENT_VERSION}</b><br />
              [Agreement text to be supplied by your legal team. The version number and acceptance time are stored with the application.]
            </Box>
            <FormControlLabel control={<Checkbox checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />} label="I have read and accept the Distributor Agreement and confirm the details are correct." />
            {stepProblems.slice(0, 4).some((p) => p.length) && <Alert severity="warning">Some earlier steps are incomplete: {stepProblems.slice(0, 4).flat()[0]}.</Alert>}
          </>
        )}
        {stepProblems[step].length > 0 && step < 4 && <Typography variant="body2" color="text.secondary">To continue: {stepProblems[step][0]}.</Typography>}
        {error && <Alert severity="error">{error}</Alert>}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, borderTop: `1px solid ${colors.border}`, pt: 2 }}>
          <Button variant="outlined" color="inherit" disabled={step === 0} onClick={() => setStep(step - 1)}>Back</Button>
          {step < 4 ? (
            <Button variant="contained" disabled={stepProblems[step].length > 0} onClick={() => setStep(step + 1)}>Save and continue</Button>
          ) : (
            <Button variant="contained" disabled={busy || stepProblems.flat().length > 0} onClick={submit}>{busy ? 'Submitting…' : 'Submit application'}</Button>
          )}
        </Box>
      </Section>
    </>
  )
}

