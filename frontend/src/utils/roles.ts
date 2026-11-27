import type { Role } from '../types/auth'

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Platform Admin',
  SUPER_DISTRIBUTOR: 'Super Distributor',
  MASTER_DISTRIBUTOR: 'Master Distributor',
  DISTRIBUTOR: 'Distributor',
  RETAILER: 'Retailer',
}

/** Badge colours from the design system; each pair passes 4.5:1 contrast. */
export const ROLE_COLORS: Record<Role, { bg: string; fg: string }> = {
  ADMIN: { bg: '#14213D', fg: '#FFFFFF' },
  SUPER_DISTRIBUTOR: { bg: '#DDE4FB', fg: '#1E3488' },
  MASTER_DISTRIBUTOR: { bg: '#E3E0F8', fg: '#3B2F8F' },
  DISTRIBUTOR: { bg: '#DDF0F3', fg: '#155E6B' },
  RETAILER: { bg: '#F1E7DA', fg: '#6B4413' },
}

/** The role a user of the given role may create directly below them, if any. */
export const CHILD_ROLE: Record<Role, Role | null> = {
  ADMIN: 'SUPER_DISTRIBUTOR',
  SUPER_DISTRIBUTOR: 'MASTER_DISTRIBUTOR',
  MASTER_DISTRIBUTOR: 'DISTRIBUTOR',
  DISTRIBUTOR: 'RETAILER',
  RETAILER: null,
}
