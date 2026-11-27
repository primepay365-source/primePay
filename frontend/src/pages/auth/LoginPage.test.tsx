import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { authService } from '../../services/authService'
import { renderApp } from '../../test/renderApp'
import type { CurrentUser } from '../../types/auth'

vi.mock('../../services/authService', () => ({
  authService: { login: vi.fn(), me: vi.fn() },
}))

const distributor: CurrentUser = {
  accountId: 7,
  accountCode: 'PP-DT-3307',
  username: 'arjun.rao',
  displayName: 'Arjun Rao',
  role: 'DISTRIBUTOR',
  parentId: 3,
  active: true,
  canTransact: true,
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(authService.login).mockReset()
    vi.mocked(authService.me).mockReset()
  })

  it('shows field errors and does not call the API when the form is empty', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your username or email')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(authService.login).not.toHaveBeenCalled()
  })

  it('toggles password visibility', async () => {
    renderApp('/login')
    const password = screen.getByLabelText('Password')
    expect(password).toHaveAttribute('type', 'password')

    await userEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
  })

  it('signs in and lands on the role dashboard with data from /auth/me', async () => {
    vi.mocked(authService.login).mockResolvedValue({
      accessToken: 'token-123',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      account: { id: 7, accountCode: 'PP-DT-3307', displayName: 'Arjun Rao', role: 'DISTRIBUTOR', parentId: 3, active: true, createdAt: '' },
    })
    vi.mocked(authService.me).mockResolvedValue(distributor)

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Username or email'), 'arjun.rao')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { level: 1, name: /Arjun Rao/ })).toBeInTheDocument()
    expect(authService.login).toHaveBeenCalledWith('arjun.rao', 'correct-horse')
    expect(sessionStorage.getItem('primepay.session')).toContain('token-123')
  })

  it('shows the server message when credentials are wrong', async () => {
    vi.mocked(authService.login).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid username or password.'))

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Username or email'), 'arjun.rao')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(sessionStorage.getItem('primepay.session')).toBeNull()
  })
})
