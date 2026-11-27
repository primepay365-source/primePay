import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Checkbox, FormControlLabel, IconButton, InputAdornment, Link, TextField, Typography } from '@mui/material'
import VisibilityOffOutlined from '@mui/icons-material/VisibilityOffOutlined'
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { AuthLayout } from '../../layouts/AuthLayout'
import { ApiError } from '../../services/apiClient'
import { sessionStore } from '../../services/sessionStore'
import { loginSchema, type LoginForm } from '../../validation/authSchemas'

export function LoginPage() {
  const { login, signOutReason } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: sessionStore.rememberedUsername(), password: '', remember: !!sessionStore.rememberedUsername() },
  })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    try {
      await login(values.username, values.password, values.remember)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/dashboard', { replace: true })
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Sign-in failed. Try again.')
    }
  })

  return (
    <AuthLayout>
      <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <Box>
          <Typography variant="h1" sx={{ fontSize: 28 }}>
            Sign in
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            Use the username your parent distributor or admin gave you.
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
          name="username"
          control={control}
          render={({ field }) => (
            <TextField
              {...field}
              id="username"
              label="Username or email"
              autoComplete="username"
              autoFocus
              fullWidth
              error={!!errors.username}
              helperText={errors.username?.message}
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
              label="Remember my username on this device"
            />
          )}
        />
        <Button type="submit" variant="contained" size="large" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
        <Typography variant="body2" color="text.secondary">
          <Link component={RouterLink} to="/forgot-password">Forgot your password?</Link>
        </Typography>
      </Box>
    </AuthLayout>
  )
}
