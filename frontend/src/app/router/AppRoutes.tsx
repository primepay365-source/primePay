import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '../../components/layout/AppLayout'
import { ChangePasswordPage, ForgotPasswordPage, ProfilePage } from '../../pages/auth/AccountPages'
import { LoginPage } from '../../pages/auth/LoginPage'
import { CommissionRulesPage } from '../../pages/commissions/CommissionRulesPage'
import { CommissionStatementPage } from '../../pages/commissions/CommissionStatementPage'
import { DashboardPage } from '../../pages/dashboard/DashboardPage'
import { ApprovalsPage } from '../../pages/distributors/ApprovalsPage'
import { OnboardingPage } from '../../pages/distributors/OnboardingPage'
import { HierarchyPage } from '../../pages/hierarchy/HierarchyPage'
import { NotFoundPage } from '../../pages/NotFoundPage'
import { ReportsPage } from '../../pages/reports/ReportsPage'
import { AdjustmentsPage } from '../../pages/settings/AdjustmentsPage'
import { AdminConfigPage } from '../../pages/settings/AdminConfigPage'
import { AuditLogPage } from '../../pages/settings/AuditLogPage'
import { LimitsPage } from '../../pages/settings/LimitsPage'
import { NotificationsPage } from '../../pages/settings/NotificationsPage'
import { ReconciliationPage } from '../../pages/settings/ReconciliationPage'
import { AddFundsPage } from '../../pages/services/AddFundsPage'
import { BillPaymentPage } from '../../pages/services/BillPaymentPage'
import { WithdrawPage } from '../../pages/services/WithdrawPage'
import { TransactionDetailPage } from '../../pages/transactions/TransactionDetailPage'
import { TransactionsPage } from '../../pages/transactions/TransactionsPage'
import { UserDetailPage } from '../../pages/users/UserDetailPage'
import { UsersPage } from '../../pages/users/UsersPage'
import { WalletPage } from '../../pages/wallets/WalletPage'
import { RedirectIfAuthenticated, RequireAuth, RequireRole } from './guards'
import { rolesFor } from './navigation'

const only = (key: string, page: ReactNode) => <RequireRole roles={rolesFor(key)}>{page}</RequireRole>

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated>} />
      <Route path="/forgot-password" element={<RedirectIfAuthenticated><ForgotPasswordPage /></RedirectIfAuthenticated>} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/users" element={only('users', <UsersPage />)} />
        <Route path="/users/:id" element={only('users', <UserDetailPage />)} />
        <Route path="/approvals" element={only('approvals', <ApprovalsPage />)} />
        <Route path="/hierarchy" element={only('hierarchy', <HierarchyPage />)} />
        <Route path="/distributors/onboard" element={only('onboarding', <OnboardingPage />)} />
        <Route path="/wallet" element={only('wallet', <WalletPage />)} />
        <Route path="/wallet/transfer" element={<Navigate to="/add-funds" replace />} />
        <Route path="/bills" element={only('bills', <BillPaymentPage />)} />
        <Route path="/add-funds" element={only('addFunds', <AddFundsPage />)} />
        <Route path="/withdraw" element={only('withdraw', <WithdrawPage />)} />
        <Route path="/payout" element={<Navigate to="/withdraw" replace />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/transactions/:id" element={<TransactionDetailPage />} />
        <Route path="/admin/adjustments" element={only('adjustments', <AdjustmentsPage />)} />
        <Route path="/commissions" element={only('commission', <CommissionRulesPage />)} />
        <Route path="/commissions/statement" element={only('statement', <CommissionStatementPage />)} />
        <Route path="/limits" element={only('limits', <LimitsPage />)} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/admin/reconciliation" element={only('reconciliation', <ReconciliationPage />)} />
        <Route path="/settings" element={only('settings', <AdminConfigPage />)} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/audit" element={only('audit', <AuditLogPage />)} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
