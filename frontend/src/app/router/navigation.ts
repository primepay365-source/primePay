import type { SvgIconComponent } from '@mui/icons-material'
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined'
import AddCardOutlined from '@mui/icons-material/AddCardOutlined'
import CreditCardOutlined from '@mui/icons-material/CreditCardOutlined'
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined'
import AdminPanelSettingsOutlined from '@mui/icons-material/AdminPanelSettingsOutlined'
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined'
import BalanceOutlined from '@mui/icons-material/BalanceOutlined'
import DashboardOutlined from '@mui/icons-material/DashboardOutlined'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import GroupsOutlined from '@mui/icons-material/GroupsOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import NotificationsOutlined from '@mui/icons-material/NotificationsOutlined'
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined'
import PercentOutlined from '@mui/icons-material/PercentOutlined'
import PersonAddAltOutlined from '@mui/icons-material/PersonAddAltOutlined'
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined'
import SpeedOutlined from '@mui/icons-material/SpeedOutlined'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import type { Role } from '../../types/auth'
import { ROLES } from '../../types/auth'

export interface NavItem {
  key: string
  label: string | ((role: Role) => string)
  path: string
  icon: SvgIconComponent
  roles: readonly Role[]
}

const BUSINESS: readonly Role[] = ['SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER']
const NETWORK: readonly Role[] = ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR']

/** Single source of truth for the sidebar. The API enforces every permission again; this only hides screens. */
export const NAV_ITEMS: readonly NavItem[] = [
  { key: 'dashboard', label: 'Dashboard', path: '/dashboard', icon: DashboardOutlined, roles: ROLES },
  { key: 'users', label: (r) => (r === 'ADMIN' ? 'Users' : 'My network'), path: '/users', icon: GroupsOutlined, roles: NETWORK },
  { key: 'approvals', label: (r) => (r === 'ADMIN' ? 'Approvals' : 'My applications'), path: '/approvals', icon: FactCheckOutlined, roles: ['ADMIN', 'MASTER_DISTRIBUTOR'] },
  { key: 'hierarchy', label: 'Hierarchy', path: '/hierarchy', icon: AccountTreeOutlined, roles: NETWORK },
  { key: 'onboarding', label: 'Onboard distributor', path: '/distributors/onboard', icon: PersonAddAltOutlined, roles: ['MASTER_DISTRIBUTOR'] },
  { key: 'bills', label: 'Bill payments', path: '/bills', icon: CreditCardOutlined, roles: BUSINESS },
  { key: 'addFunds', label: 'Add funds', path: '/add-funds', icon: AddCardOutlined, roles: BUSINESS },
  { key: 'withdraw', label: 'Withdraw funds', path: '/withdraw', icon: PaymentsOutlined, roles: BUSINESS },
  { key: 'wallet', label: 'Main wallet', path: '/wallet', icon: AccountBalanceWalletOutlined, roles: BUSINESS },
  { key: 'transactions', label: 'Transactions', path: '/transactions', icon: ReceiptLongOutlined, roles: ROLES },
  { key: 'adjustments', label: 'Adjustments', path: '/admin/adjustments', icon: TuneOutlined, roles: ['ADMIN'] },
  { key: 'commission', label: 'Commission rules', path: '/commissions', icon: PercentOutlined, roles: ['ADMIN'] },
  { key: 'statement', label: (r) => (r === 'ADMIN' ? 'Commission report' : 'My commission'), path: '/commissions/statement', icon: PercentOutlined, roles: ROLES },
  { key: 'limits', label: 'Limits', path: '/limits', icon: SpeedOutlined, roles: ['ADMIN'] },
  { key: 'reports', label: 'Reports', path: '/reports', icon: AssessmentOutlined, roles: ROLES },
  { key: 'reconciliation', label: 'Reconciliation', path: '/admin/reconciliation', icon: BalanceOutlined, roles: ['ADMIN'] },
  { key: 'settings', label: 'Admin configuration', path: '/settings', icon: AdminPanelSettingsOutlined, roles: ['ADMIN'] },
  { key: 'notifications', label: 'Notifications', path: '/notifications', icon: NotificationsOutlined, roles: ROLES },
  { key: 'audit', label: 'Audit log', path: '/audit', icon: HistoryOutlined, roles: ['ADMIN'] },
]

export function navItemsFor(role: Role): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.includes(role))
}

export function navLabel(item: NavItem, role: Role): string {
  return typeof item.label === 'function' ? item.label(role) : item.label
}

export function rolesFor(key: string): readonly Role[] {
  return NAV_ITEMS.find((i) => i.key === key)?.roles ?? ROLES
}
