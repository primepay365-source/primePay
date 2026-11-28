import { createContext } from 'react'
import type { CurrentUser, LoginChallenge } from '../types/auth'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'
export type SignOutReason = 'manual' | 'expired'

export interface AuthContextValue {
  status: AuthStatus
  user: CurrentUser | null
  /** Why the last session ended, so the login page can explain it. */
  signOutReason: SignOutReason | null
  /** Step 1: email or mobile + password. Returns where the one-time code was sent. */
  startLogin: (identifier: string, password: string, remember: boolean) => Promise<LoginChallenge>
  /** Step 2: the code. Signs in and returns the user. */
  completeLogin: (challengeId: string, otp: string) => Promise<CurrentUser>
  resendLoginCode: (challengeId: string) => Promise<LoginChallenge>
  logout: (reason?: SignOutReason) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
