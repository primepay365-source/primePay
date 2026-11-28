import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { authService } from '../../services/authService'
import { renderApp } from '../../test/renderApp'
import type { CurrentUser, LoginChallenge } from '../../types/auth'

vi.mock('../../services/authService', () => ({
  authService: { startLogin: vi.fn(), verifyLogin: vi.fn(), resendLoginCode: vi.fn(), me: vi.fn() },
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

const challenge = (channel: 'EMAIL' | 'SMS'): LoginChallenge => ({
  challengeId: 'ch-1',
  channel,
  destination: channel === 'EMAIL' ? 'a•••@example.com' : '+91 ••••••3210',
  expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  codeLength: 6,
  resendAfterSeconds: 60,
  resendsLeft: 3,
})

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(authService.startLogin).mockReset()
    vi.mocked(authService.verifyLogin).mockReset()
    vi.mocked(authService.me).mockReset()
    sessionStorage.clear()
  })

  it('shows field errors and does not call the API when the form is empty', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Enter your email or mobile number')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(authService.startLogin).not.toHaveBeenCalled()
  })

  it('rejects something that is neither an email nor a mobile number', async () => {
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email or mobile number'), 'arjun.rao')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Enter a valid email or 10-digit mobile number')).toBeInTheDocument()
    expect(authService.startLogin).not.toHaveBeenCalled()
  })

  it('toggles password visibility', async () => {
    renderApp('/login')
    const password = screen.getByLabelText('Password')
    expect(password).toHaveAttribute('type', 'password')

    await userEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
  })

  it('asks for the emailed code and opens the dashboard only after it is verified', async () => {
    vi.mocked(authService.startLogin).mockResolvedValue(challenge('EMAIL'))
    vi.mocked(authService.verifyLogin).mockResolvedValue({
      accessToken: 'token-123',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      account: { id: 7, accountCode: 'PP-DT-3307', displayName: 'Arjun Rao', role: 'DISTRIBUTOR', parentId: 3, active: true, createdAt: '' },
    })
    vi.mocked(authService.me).mockResolvedValue(distributor)

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email or mobile number'), 'arjun@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText(/by email to/)).toBeInTheDocument()
    expect(authService.startLogin).toHaveBeenCalledWith('arjun@example.com', 'correct-horse')
    expect(sessionStorage.getItem('primepay.session')).toBeNull() // no session before the code
    expect(screen.getByRole('button', { name: 'Verify and sign in' })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('One-time code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Verify and sign in' }))

    expect(await screen.findByRole('heading', { level: 1, name: /Arjun Rao/ })).toBeInTheDocument()
    expect(authService.verifyLogin).toHaveBeenCalledWith('ch-1', '123456')
    expect(sessionStorage.getItem('primepay.session')).toContain('token-123')
  })

  it('accepts a mobile number and says the code went by SMS', async () => {
    vi.mocked(authService.startLogin).mockResolvedValue(challenge('SMS'))

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email or mobile number'), '98765 43210')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText(/by SMS to/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Resend code in/ })).toBeDisabled()
  })

  it('shows the server message for a wrong code and stays signed out', async () => {
    vi.mocked(authService.startLogin).mockResolvedValue(challenge('EMAIL'))
    vi.mocked(authService.verifyLogin).mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', 'The code is incorrect. 4 attempts left.'))

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email or mobile number'), 'arjun@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await userEvent.type(await screen.findByLabelText('One-time code'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Verify and sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The code is incorrect. 4 attempts left.')
    expect(sessionStorage.getItem('primepay.session')).toBeNull()
  })

  it('shows the server message when the password is wrong', async () => {
    vi.mocked(authService.startLogin).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid email/mobile or password.'))

    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email or mobile number'), 'arjun@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email/mobile or password.')
    expect(sessionStorage.getItem('primepay.session')).toBeNull()
  })
})
