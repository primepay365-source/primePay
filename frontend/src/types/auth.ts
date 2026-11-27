export const ROLES = ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'] as const
export type Role = (typeof ROLES)[number]

/** Body of POST /api/v1/auth/login (returned unwrapped until Phase 3). */
export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  account: {
    id: number
    accountCode: string
    displayName: string
    role: Role
    parentId: number | null
    active: boolean
    createdAt: string
  }
}

/** data of GET /api/v1/auth/me */
export interface CurrentUser {
  accountId: number
  accountCode: string
  username: string
  displayName: string
  role: Role
  parentId: number | null
  active: boolean
  canTransact: boolean
}

export interface StoredSession {
  accessToken: string
  expiresAt: string
}
