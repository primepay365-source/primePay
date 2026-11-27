import { createContext } from 'react'
import type { CurrentUser } from '../types/auth'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'
export type SignOutReason = 'manual' | 'expired'

export interface AuthContextValue {
  status: AuthStatus
  user: CurrentUser | null
  /** Why the last session ended, so the login page can explain it. */
  signOutReason: SignOutReason | null
  login: (username: string, password: string, remember: boolean) => Promise<CurrentUser>
  logout: (reason?: SignOutReason) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
