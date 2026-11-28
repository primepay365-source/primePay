import axios, { AxiosError } from 'axios'
import type { ApiEnvelope, ApiFieldMessage } from '../types/api'
import { sessionStore } from './sessionStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** Error thrown by every API call, carrying the server's error code and field messages. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: ApiFieldMessage[]
  readonly requestId?: string

  constructor(status: number, code: string, message: string, fields: ApiFieldMessage[] = [], requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
    this.requestId = requestId
  }
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20_000,
  headers: { 'Content-Type': 'application/json' },
})

let onUnauthorized: (() => void) | null = null

/** The auth provider registers this so an expired or revoked token signs the user out everywhere. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

apiClient.interceptors.request.use((config) => {
  const session = sessionStore.read()
  if (session) config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    throw toApiError(error)
  },
)

export function toApiError(error: AxiosError<ApiEnvelope<unknown>>): ApiError {
  if (!error.response) {
    return new ApiError(0, 'NETWORK_ERROR', "Can't reach PrimePay. Check your connection and try again.")
  }
  const { status, data } = error.response
  const isLogin = /\/auth\/login(\/verify|\/resend)?$/.test(error.config?.url ?? '')
  if (status === 401 && !isLogin) onUnauthorized?.()
  const body = data?.error
  return new ApiError(
    status,
    body?.code ?? 'HTTP_' + status,
    body?.message ?? 'Request failed (' + status + ').',
    body?.fields ?? [],
    data?.requestId,
  )
}

/** Unwraps the standard envelope's data field. */
export function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (!envelope.success || envelope.data === undefined) {
    throw new ApiError(200, envelope.error?.code ?? 'EMPTY_RESPONSE', envelope.error?.message ?? 'Empty response.')
  }
  return envelope.data
}
