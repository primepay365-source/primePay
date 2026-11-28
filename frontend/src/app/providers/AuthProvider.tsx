import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthStatus, type SignOutReason } from '../../context/AuthContext'
import { setUnauthorizedHandler } from '../../services/apiClient'
import { authService } from '../../services/authService'
import { sessionStore } from '../../services/sessionStore'
import type { CurrentUser } from '../../types/auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>(() => (sessionStore.read() ? 'loading' : 'anonymous'))
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [signOutReason, setSignOutReason] = useState<SignOutReason | null>(null)

  const logout = useCallback((reason: SignOutReason = 'manual') => {
    sessionStore.clear()
    setUser(null)
    setStatus('anonymous')
    setSignOutReason(reason)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => logout('expired'))
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // Restore a session left in this tab: the server confirms the token and the account is still active.
  useEffect(() => {
    if (status !== 'loading') return
    let cancelled = false
    authService
      .me()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        sessionStore.clear()
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [status])

  const startLogin = useCallback(async (identifier: string, password: string, remember: boolean) => {
    const challenge = await authService.startLogin(identifier.trim(), password)
    sessionStore.setRememberedUsername(remember ? identifier.trim() : null)
    return challenge
  }, [])

  const resendLoginCode = useCallback((challengeId: string) => authService.resendLoginCode(challengeId), [])

  const completeLogin = useCallback(async (challengeId: string, otp: string) => {
    const result = await authService.verifyLogin(challengeId, otp)
    sessionStore.write({ accessToken: result.accessToken, expiresAt: result.expiresAt })
    try {
      const me = await authService.me()
      setUser(me)
      setStatus('authenticated')
      setSignOutReason(null)
      return me
    } catch (error) {
      sessionStore.clear()
      throw error
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, signOutReason, startLogin, completeLogin, resendLoginCode, logout }),
    [status, user, signOutReason, startLogin, completeLogin, resendLoginCode, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
