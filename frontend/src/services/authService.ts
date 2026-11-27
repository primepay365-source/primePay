import type { ApiEnvelope } from '../types/api'
import type { CurrentUser, LoginResponse } from '../types/auth'
import { apiClient, unwrap } from './apiClient'

export const authService = {
  async login(username: string, password: string): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>('/auth/login', { username, password })
    return data
  },

  async me(): Promise<CurrentUser> {
    const { data } = await apiClient.get<ApiEnvelope<CurrentUser>>('/auth/me')
    return unwrap(data)
  },
}
