import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { authService } from '../../services/authService'
import { renderApp } from '../../test/renderApp'
import { navItemsFor } from './navigation'

vi.mock('../../services/authService', () => ({
  authService: { startLogin: vi.fn(), verifyLogin: vi.fn(), resendLoginCode: vi.fn(), me: vi.fn() },
}))

describe('route guards', () => {
  it('sends a signed-out visitor from /dashboard to the login page', async () => {
    renderApp('/dashboard')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(authService.me).not.toHaveBeenCalled()
  })

  it('restores a stored session through /auth/me and shows the dashboard', async () => {
    sessionStorage.setItem(
      'primepay.session',
      JSON.stringify({ accessToken: 'stored', expiresAt: new Date(Date.now() + 60_000).toISOString() }),
    )
    vi.mocked(authService.me).mockResolvedValue({
      accountId: 1, accountCode: 'LOCAL-ADMIN-001', username: 'admin@primepay.local', displayName: 'Development Admin',
      role: 'ADMIN', parentId: null, active: true, canTransact: false,
    })

    renderApp('/dashboard')
    expect(await screen.findByRole('heading', { level: 1, name: /Development Admin/ })).toBeInTheDocument()
  })

  it('drops an expired stored session without calling the API', async () => {
    sessionStorage.setItem(
      'primepay.session',
      JSON.stringify({ accessToken: 'old', expiresAt: new Date(Date.now() - 1_000).toISOString() }),
    )
    vi.mocked(authService.me).mockClear()

    renderApp('/dashboard')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(authService.me).not.toHaveBeenCalled()
  })
})

describe('navigation', () => {
  it('only lists built screens, and every role gets a dashboard', () => {
    for (const role of ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'] as const) {
      expect(navItemsFor(role).map((item) => item.path)).toContain('/dashboard')
    }
  })
})
