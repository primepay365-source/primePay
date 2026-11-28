import type { ApiEnvelope } from '../types/api'
import type { CurrentUser, LoginChallenge, LoginResponse } from '../types/auth'
import { apiClient, unwrap } from './apiClient'

/** Sign-in is two steps: email or mobile + password (a code is sent), then the code (the session token is issued). */
export const authService = {
  async startLogin(identifier: string, password: string): Promise<LoginChallenge> {
    const { data } = await apiClient.post<ApiEnvelope<LoginChallenge>>('/auth/login', { identifier, password })
    return unwrap(data)
  },

  async verifyLogin(challengeId: string, otp: string): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>('/auth/login/verify', { challengeId, otp })
    return data
  },

  async resendLoginCode(challengeId: string): Promise<LoginChallenge> {
    const { data } = await apiClient.post<ApiEnvelope<LoginChallenge>>('/auth/login/resend', { challengeId })
    return unwrap(data)
  },

  async me(): Promise<CurrentUser> {
    const { data } = await apiClient.get<ApiEnvelope<CurrentUser>>('/auth/me')
    return unwrap(data)
  },
}
