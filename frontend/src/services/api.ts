import type { ApiEnvelope, PageResponse } from '../types/api'
import { apiClient, unwrap } from './apiClient'

type Params = Record<string, string | number | boolean | null | undefined>

function clean(params?: Params) {
  if (!params) return undefined
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

/** Thin typed wrappers that unwrap the standard { success, data } envelope. */
export const api = {
  async get<T>(url: string, params?: Params): Promise<T> {
    const { data } = await apiClient.get<ApiEnvelope<T>>(url, { params: clean(params) })
    return unwrap(data)
  },
  async page<T>(url: string, params?: Params): Promise<PageResponse<T>> {
    return api.get<PageResponse<T>>(url, params)
  },
  async post<T>(url: string, body?: unknown, headers?: Record<string, string>): Promise<{ data: T; message?: string }> {
    const { data } = await apiClient.post<ApiEnvelope<T>>(url, body ?? {}, { headers })
    return { data: data.data as T, message: data.message }
  },
  async put<T>(url: string, body: unknown): Promise<{ data: T; message?: string }> {
    const { data } = await apiClient.put<ApiEnvelope<T>>(url, body)
    return { data: data.data as T, message: data.message }
  },
  async patch<T>(url: string, body: unknown): Promise<{ data: T; message?: string }> {
    const { data } = await apiClient.patch<ApiEnvelope<T>>(url, body)
    return { data: data.data as T, message: data.message }
  },
  async del(url: string): Promise<void> {
    await apiClient.delete(url)
  },
  /** Downloads a CSV (or any file) with the signed-in user's token and saves it in the browser. */
  async download(url: string, fileName: string, params?: Params) {
    const response = await apiClient.get<Blob>(url, { params: clean(params), responseType: 'blob' })
    const href = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = href
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(href)
  },
}

/** A fresh key per user action, so a double click or retry never moves money twice. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
