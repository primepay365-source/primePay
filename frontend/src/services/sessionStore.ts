import type { StoredSession } from '../types/auth'

const SESSION_KEY = 'primepay.session'
const REMEMBERED_USERNAME_KEY = 'primepay.rememberedUsername'

/**
 * The access token lives in sessionStorage, so it is gone when the browser tab closes.
 * "Remember me" only remembers the username; long-lived sign-in arrives with refresh tokens in Phase 3.
 * Every storage call is guarded because storage can be unavailable (private mode, blocked site data).
 */
export const sessionStore = {
  read(): StoredSession | null {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY)
      if (!raw) return null
      const parsed = JSON.parse(raw) as StoredSession
      if (!parsed.accessToken || !parsed.expiresAt) return null
      if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
        sessionStorage.removeItem(SESSION_KEY)
        return null
      }
      return parsed
    } catch {
      return null
    }
  },
  write(session: StoredSession) {
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    } catch {
      // Storage unavailable: the session still works for this page load.
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(SESSION_KEY)
    } catch {
      // Ignore.
    }
  },
  rememberedUsername(): string {
    try {
      return localStorage.getItem(REMEMBERED_USERNAME_KEY) ?? ''
    } catch {
      return ''
    }
  },
  setRememberedUsername(username: string | null) {
    try {
      if (username) localStorage.setItem(REMEMBERED_USERNAME_KEY, username)
      else localStorage.removeItem(REMEMBERED_USERNAME_KEY)
    } catch {
      // Ignore.
    }
  },
}
