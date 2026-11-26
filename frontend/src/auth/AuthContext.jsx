import { createContext, useContext, useMemo, useState } from 'react'
import { api } from '../api/client'

const AuthContext = createContext(null)
const STORAGE_KEY = 'primepay.session'

function readSession() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY))
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)

  const value = useMemo(() => ({
    session,
    async login(username, password) {
      const result = await api.login({ username, password })
      const nextSession = {
        token: result.accessToken,
        expiresAt: result.expiresAt,
        account: result.account,
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession))
      setSession(nextSession)
      return nextSession
    },
    logout() {
      localStorage.removeItem(STORAGE_KEY)
      setSession(null)
    },
  }), [session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
