import { Alert, Box, Button, LinearProgress, Link, TextField, Typography } from '@mui/material'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { RoleChip } from '../../components/common/RoleChip'
import { Facts, Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { AuthLayout } from '../../layouts/AuthLayout'
import { apiClient } from '../../services/apiClient'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { ApiEnvelope } from '../../types/api'
import type { UserRow } from '../../types/domain'
import { dateTime } from '../../utils/format'

const RULES: Array<[string, (p: string) => boolean]> = [
  ['At least 10 characters', (p) => p.length >= 10],
  ['Upper and lower case', (p) => /[a-z]/.test(p) && /[A-Z]/.test(p)],
  ['A number', (p) => /\d/.test(p)],
  ['A symbol', (p) => /[^A-Za-z0-9]/.test(p)],
]

function PasswordRules({ password }: { password: string }) {
  return (
    <Box component="ul" sx={{ m: 0, p: 1.5, listStyle: 'none', bgcolor: colors.canvas, borderRadius: 3, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0.75, fontSize: 13 }}>
      {RULES.map(([label, test]) => (
        <li key={label} style={{ color: test(password) ? colors.successText : colors.textMuted }}>{test(password) ? '✓' : '○'} {label}</li>
      ))}
    </Box>
  )
}

const strong = (p: string) => RULES.every(([, t]) => t(p))

export function ProfilePage() {
  const profile = useApiData(() => api.get<UserRow>('/users/me/profile'), [])
  const [form, setForm] = useState({ displayName: '', businessName: '', email: '', address: '' })
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  useEffect(() => {
    if (profile.data) setForm({ displayName: profile.data.displayName, businessName: profile.data.businessName ?? '', email: profile.data.email ?? '', address: profile.data.address ?? '' })
  }, [profile.data])

  if (profile.error) return <ErrorCard message={profile.error} onRetry={profile.reload} />
  if (!profile.data) return <LoadingCard />
  const p = profile.data

  async function save() {
    setMessage(null)
    try {
      await api.put('/users/me/profile', form)
      setMessage({ ok: true, text: 'Profile saved.' })
      profile.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    }
  }

  return (
    <>
      <PageHeader title={p.displayName} subtitle={<Box component="span" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}><RoleChip role={p.role} /><StatusChip status={p.status} />{p.accountCode}{p.parentCode ? ` · under ${p.parentCode}` : ''}</Box>} />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
        <Section title="Contact and business" sx={{ flex: '2 1 480px', minWidth: 0 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField label="Full name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
            <TextField label="Business name" value={form.businessName} onChange={(e) => setForm({ ...form, businessName: e.target.value })} />
            <TextField label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            <TextField label="Mobile" value={p.mobile ?? ''} disabled helperText="Ask your parent distributor to change your mobile" />
          </Box>
          <TextField label="Business address" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} multiline minRows={2} />
          {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}><Button variant="contained" onClick={save} disabled={!form.displayName.trim()}>Save changes</Button></Box>
        </Section>
        <Section title="Account" sx={{ flex: '1 1 300px', minWidth: 0 }}>
          <Facts items={[['Sign-in username', p.username], ['Member since', dateTime(p.createdAt)], ['Last sign-in', dateTime(p.lastLoginAt)]]} />
          <Button variant="outlined" color="inherit" component={RouterLink} to="/change-password">Change password</Button>
        </Section>
      </Box>
    </>
  )
}

export function ChangePasswordPage() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit() {
    setBusy(true)
    setMessage(null)
    try {
      await api.post('/auth/change-password', { currentPassword: current, newPassword: next })
      setMessage({ ok: true, text: 'Password changed. Use the new password next time you sign in.' })
      setCurrent('')
      setNext('')
      setConfirm('')
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Change password" subtitle="Choose a password you haven't used before." />
      <Section sx={{ maxWidth: 620 }}>
        <TextField label="Current password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        <TextField label="New password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        <TextField label="Confirm new password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
          error={!!confirm && confirm !== next} helperText={confirm && confirm !== next ? "Doesn't match" : ' '} />
        <PasswordRules password={next} />
        {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button variant="contained" onClick={submit} disabled={busy || !current || !strong(next) || next !== confirm}>Update password</Button>
        </Box>
      </Section>
    </>
  )
}

/** Public 4-step reset: account → code → new password → done. */
export function ForgotPasswordPage() {
  const [step, setStep] = useState(0)
  const [identifier, setIdentifier] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [info, setInfo] = useState<{ message: string; devOtp: string | null; maskedDestination: string | null } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function sendCode() {
    setBusy(true)
    setError(null)
    try {
      const { data } = await apiClient.post<ApiEnvelope<{ message: string; devOtp: string | null; maskedDestination: string | null }>>('/auth/forgot-password', { identifier })
      setInfo(data.data ?? null)
      setStep(1)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function reset() {
    setBusy(true)
    setError(null)
    try {
      await apiClient.post('/auth/reset-password', { identifier, otp: code, newPassword: password })
      setStep(3)
    } catch (e) {
      setError(errorMessage(e))
      if (/code/i.test(errorMessage(e))) setStep(1)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <LinearProgress variant="determinate" value={((step + 1) / 4) * 100} aria-label={`Step ${step + 1} of 4`} sx={{ height: 6, borderRadius: 3 }} />
        {step === 0 && (
          <>
            <Box><Typography variant="h1" sx={{ fontSize: 26 }}>Forgot your password?</Typography><Typography color="text.secondary" sx={{ mt: 0.75 }}>Enter the email address or mobile number on your account. We'll send a code to it.</Typography></Box>
            <TextField label="Email or mobile number" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoFocus />
            {error && <Alert severity="error">{error}</Alert>}
            <Button variant="contained" size="large" onClick={sendCode} disabled={busy || !identifier.trim()}>Send code</Button>
          </>
        )}
        {step === 1 && (
          <>
            <Box><Typography variant="h1" sx={{ fontSize: 26 }}>Enter the code</Typography><Typography color="text.secondary" sx={{ mt: 0.75 }}>{info?.maskedDestination ? `Sent to ${info.maskedDestination}. ` : ''}{info?.message}</Typography></Box>
            <TextField label="6-digit code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'one-time-code' } }} />
            {error && <Alert severity="error">{error}</Alert>}
            <Box sx={{ display: 'flex', gap: 1 }}><Button variant="outlined" color="inherit" onClick={() => setStep(0)}>Back</Button><Button variant="contained" sx={{ flex: 1 }} disabled={code.length !== 6} onClick={() => setStep(2)}>Continue</Button></Box>
          </>
        )}
        {step === 2 && (
          <>
            <Typography variant="h1" sx={{ fontSize: 26 }}>Choose a new password</Typography>
            <TextField label="New password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <TextField label="Confirm new password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={!!confirm && confirm !== password} />
            <PasswordRules password={password} />
            {error && <Alert severity="error">{error}</Alert>}
            <Box sx={{ display: 'flex', gap: 1 }}><Button variant="outlined" color="inherit" onClick={() => setStep(1)}>Back</Button><Button variant="contained" sx={{ flex: 1 }} disabled={busy || !strong(password) || password !== confirm} onClick={reset}>Reset password</Button></Box>
          </>
        )}
        {step === 3 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, textAlign: 'center' }}>
            <CheckCircleRounded sx={{ fontSize: 56, color: colors.success }} aria-hidden />
            <Typography variant="h1" sx={{ fontSize: 26 }}>Password updated</Typography>
            <Typography color="text.secondary">Your account is unlocked. Sign in with the new password.</Typography>
            <Button variant="contained" component={RouterLink} to="/login" fullWidth>Back to sign in</Button>
          </Box>
        )}
        {step < 3 && <Link component={RouterLink} to="/login" sx={{ textAlign: 'center', fontWeight: 600 }}>Back to sign in</Link>}
      </Box>
    </AuthLayout>
  )
}
