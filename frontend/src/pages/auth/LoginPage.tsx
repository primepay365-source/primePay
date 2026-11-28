import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Checkbox, FormControlLabel, IconButton, InputAdornment, Link, TextField, Typography } from '@mui/material'
import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded'
import SmsOutlined from '@mui/icons-material/SmsOutlined'
import VisibilityOffOutlined from '@mui/icons-material/VisibilityOffOutlined'
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { AuthLayout } from '../../layouts/AuthLayout'
import { ApiError } from '../../services/apiClient'
import { sessionStore } from '../../services/sessionStore'
import type { LoginChallenge } from '../../types/auth'
import { loginSchema, type LoginForm } from '../../validation/authSchemas'

/**
 * Two-step sign-in. Step 1: email or mobile number + password. Step 2: the one-time code sent to that email
 * (or by SMS to that mobile). The dashboard opens only after the code is verified.
 */
export function LoginPage() {
  const { startLogin, signOutReason } = useAuth()
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [challenge, setChallenge] = useState<LoginChallenge | null>(null)

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: sessionStore.rememberedUsername(), password: '', remember: !!sessionStore.rememberedUsername() },
  })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    try {
      setChallenge(await startLogin(values.identifier, values.password, values.remember))
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Sign-in failed. Try again.')
    }
  })

  if (challenge) {
    return <OtpStep challenge={challenge} onChallenge={setChallenge} onBack={() => setChallenge(null)} />
  }

  return (
    <AuthLayout>
      <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <Box>
          <Typography variant="h1" sx={{ fontSize: 28 }}>
            Sign in
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            Use the email address or mobile number on your account. We'll send a one-time code to it.
          </Typography>
        </Box>

        {signOutReason === 'expired' && !serverError && (
          <Alert severity="warning">Your session ended. Sign in again to continue.</Alert>
        )}
        {serverError && (
          <Alert severity="error" role="alert">
            {serverError}
          </Alert>
        )}

        <Controller
          name="identifier"
          control={control}
          render={({ field }) => (
            <TextField
              {...field}
              id="identifier"
              label="Email or mobile number"
              placeholder="name@company.com or 98765 43210"
              autoComplete="username"
              autoFocus
              fullWidth
              error={!!errors.identifier}
              helperText={errors.identifier?.message}
            />
          )}
        />
        <Controller
          name="password"
          control={control}
          render={({ field }) => (
            <TextField
              {...field}
              id="password"
              label="Password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              fullWidth
              error={!!errors.password}
              helperText={errors.password?.message}
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                        onClick={() => setShowPassword((value) => !value)}
                        edge="end"
                        sx={{ width: 44, height: 44 }}
                      >
                        {showPassword ? <VisibilityOffOutlined /> : <VisibilityOutlined />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />
          )}
        />
        <Controller
          name="remember"
          control={control}
          render={({ field }) => (
            <FormControlLabel
              control={<Checkbox checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />}
              label="Remember my email or mobile on this device"
            />
          )}
        />
        <Button type="submit" variant="contained" size="large" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Checking…' : 'Continue'}
        </Button>
        <Typography variant="body2" color="text.secondary">
          <Link component={RouterLink} to="/forgot-password">Forgot your password?</Link>
        </Typography>
      </Box>
    </AuthLayout>
  )
}

function OtpStep({ challenge, onChallenge, onBack }: { challenge: LoginChallenge; onChallenge: (c: LoginChallenge) => void; onBack: () => void }) {
  const { completeLogin, resendLoginCode } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [wait, setWait] = useState(challenge.resendAfterSeconds)

  useEffect(() => {
    if (wait <= 0) return
    const timer = window.setTimeout(() => setWait((w) => w - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [wait])

  const email = challenge.channel === 'EMAIL'
  const complete = code.length === challenge.codeLength

  async function verify(event?: React.FormEvent) {
    event?.preventDefault()
    if (!complete) return
    setBusy(true)
    setError(null)
    try {
      await completeLogin(challenge.challengeId, code)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/dashboard', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The code could not be checked. Try again.')
      setCode('')
    } finally {
      setBusy(false)
    }
  }

  async function resend() {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const next = await resendLoginCode(challenge.challengeId)
      onChallenge(next)
      setWait(next.resendAfterSeconds)
      setNotice(`A new code was sent to ${next.destination}.`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'A new code could not be sent. Try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <Box component="form" noValidate onSubmit={verify} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'primary.main', mb: 1 }}>
            {email ? <MailOutlineRounded aria-hidden /> : <SmsOutlined aria-hidden />}
            <Typography variant="overline" sx={{ fontWeight: 700 }}>Step 2 of 2</Typography>
          </Box>
          <Typography variant="h1" sx={{ fontSize: 28 }}>
            Enter your code
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            We sent a {challenge.codeLength}-digit code {email ? 'by email to' : 'by SMS to'} <b>{challenge.destination}</b>. It expires in a few minutes.
          </Typography>
        </Box>

        {error && <Alert severity="error" role="alert">{error}</Alert>}
        {notice && !error && <Alert severity="success">{notice}</Alert>}

        <TextField
          id="otp"
          label="One-time code"
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, challenge.codeLength))}
          autoFocus
          fullWidth
          autoComplete="one-time-code"
          slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: challenge.codeLength, style: { letterSpacing: '0.5em', fontSize: 22, fontWeight: 700 } } }}
        />
        <Button type="submit" variant="contained" size="large" fullWidth disabled={busy || !complete}>
          {busy ? 'Verifying…' : 'Verify and sign in'}
        </Button>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
          <Button variant="text" onClick={onBack} disabled={busy}>Use a different account</Button>
          <Button variant="text" onClick={resend} disabled={busy || wait > 0 || challenge.resendsLeft <= 0}>
            {challenge.resendsLeft <= 0 ? 'No more resends' : wait > 0 ? `Resend code in ${wait}s` : 'Resend code'}
          </Button>
        </Box>
        <Typography variant="caption" color="text.secondary">
          Never share this code. PrimePay staff will never ask for it.
        </Typography>
      </Box>
    </AuthLayout>
  )
}
