export interface ApiFieldMessage {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  fields?: ApiFieldMessage[]
}

/** Standard PrimePay response envelope. */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  message?: string
  error?: ApiErrorBody
  timestamp: string
  requestId?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
