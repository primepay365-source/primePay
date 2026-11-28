import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { FullPageLoader } from '../../components/common/FullPageLoader'
import { useAuth } from '../../hooks/useAuth'
import type { Role } from '../../types/auth'

/** Sends signed-out users to /login, remembering where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <>{children}</>
}

/**
 * Hides a route from roles that can't use it. This is a convenience only:
 * the API checks permission and hierarchy scope on every request.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!user || !roles.includes(user.role)) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}

/** Keeps signed-in users away from the login page. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'authenticated') return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
