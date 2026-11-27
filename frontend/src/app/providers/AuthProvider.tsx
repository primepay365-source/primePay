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

  const login = useCallback(async (username: string, password: string, remember: boolean) => {
    const result = await authService.login(username.trim(), password)
    sessionStore.write({ accessToken: result.accessToken, expiresAt: result.expiresAt })
    sessionStore.setRememberedUsername(remember ? username.trim() : null)
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
    () => ({ status, user, signOutReason, login, logout }),
    [status, user, signOutReason, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
