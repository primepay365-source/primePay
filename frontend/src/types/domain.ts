import type { Role } from './auth'

export type AccountStatus =
  | 'DRAFT' | 'PENDING_VERIFICATION' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'ACTIVE' | 'INACTIVE'

export type TxnStatus = 'INITIATED' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'REVERSED' | 'REFUNDED'
export type TxnType =
  | 'WALLET_LOAD' | 'WALLET_TRANSFER' | 'WALLET_CREDIT' | 'WALLET_DEBIT' | 'PAYOUT' | 'BILL_PAYMENT' | 'ADD_FUNDS' | 'REFUND' | 'REVERSAL' | 'ADJUSTMENT' | 'COMMISSION'

export interface UserRow {
  id: number
  accountCode: string
  username: string | null
  displayName: string
  businessName: string | null
  role: Role
  parentId: number | null
  parentCode: string | null
  status: AccountStatus
  email: string | null
  mobile: string | null
  address: string | null
  walletBalance: number | null
  createdAt: string
  lastLoginAt: string | null
}

export interface StatusChange {
  fromStatus: AccountStatus | null
  toStatus: AccountStatus
  reason: string | null
  changedBy: string
  changedAt: string
}

export interface HierarchyNode {
  id: number
  accountCode: string
  displayName: string
  businessName: string | null
  role: Role
  status: AccountStatus
  walletBalance: number | null
  children: HierarchyNode[]
}

export interface WalletSummary {
  walletId: number
  walletCode: string
  accountId: number
  accountCode: string
  status: string
  availableBalance: number
  ledgerBalance: number
  blockedBalance: number
  openingBalanceThisMonth: number
  creditsThisMonth: number
  debitsThisMonth: number
  totalCredit: number
  totalDebit: number
  pendingLoads: number
  createdAt: string
}

export interface StatementRow {
  id: number
  createdAt: string
  reference: string
  transactionId: number
  type: string
  description: string
  direction: 'DR' | 'CR'
  amount: number
  balanceAfter: number
}

export interface Recipient {
  accountId: number
  accountCode: string
  displayName: string
  businessName: string | null
  role: Role
  walletBalance: number
}

export interface LimitStatus {
  serviceCode: string
  minTxn: number
  maxTxn: number
  dailyAmount: number
  usedToday: number
  monthlyAmount: number
  usedThisMonth: number
  dailyCount: number
  countToday: number
}

export interface Transaction {
  id: number
  referenceNo: string
  type: TxnType
  typeLabel: string
  status: TxnStatus
  serviceCode: string
  amount: number
  fee: number
  initiatorCode: string | null
  fromCode: string | null
  fromName: string | null
  toCode: string | null
  toName: string | null
  remarks: string | null
  externalRef: string | null
  payoutMode: string | null
  beneficiaryName: string | null
  beneficiaryAccountLast4: string | null
  beneficiaryIfsc: string | null
  beneficiaryBank: string | null
  billerName: string | null
  customerName: string | null
  cardLast4: string | null
  cardNetwork: string | null
  gatewayCategory: string | null
  customerMobileMasked: string | null
  failureReason: string | null
  originalTxnId: number | null
  createdAt: string
  updatedAt: string
}

export interface TransactionDetail {
  transaction: Transaction
  history: { fromStatus: TxnStatus | null; toStatus: TxnStatus; note: string | null; changedAt: string }[]
  ledger: { entryRef: string; walletCode: string; direction: 'DR' | 'CR'; entryType: string; amount: number; balanceAfter: number; description: string; createdAt: string }[]
  commission: { levelRole: string; earnerCode: string; amount: number; status: string }[]
  related: Transaction[]
}

export interface Kpi {
  label: string
  value: number | null
  format: 'money' | 'count' | 'text'
  hint: string | null
  tone: 'dark' | 'danger' | null
}

export interface Dashboard {
  role: Role
  scope: string
  kpis: Kpi[]
  series: { date: string; success: number; failed: number; amount: number }[]
  breakdownTitle: string
  breakdown: { label: string; value: number; format: 'money' | 'count' }[]
  recent: Transaction[]
  commissionTotal: number
  commissionLines: { label: string; value: number; format: 'money' | 'count' }[]
  network: { label: string; value: number; format: 'money' | 'count' }[]
}

export interface CommissionRule {
  id: number
  ruleCode: string
  serviceCode: string
  calcType: 'PERCENT' | 'FIXED'
  slabFrom: number | null
  slabTo: number | null
  effectiveFrom: string
  effectiveTo: string | null
  status: string
  state: 'Active' | 'Scheduled' | 'Expired' | 'Inactive'
  shares: { levelRole: string; rate: number | null; fixedAmount: number | null; minAmount: number | null; maxAmount: number | null }[]
}

export interface CommissionRate {
  serviceCode: string; ruleCode: string; calcType: 'PERCENT' | 'FIXED'; levelRole: string
  rate: number | null; fixedAmount: number | null; minAmount: number | null; maxAmount: number | null; slabFrom: number | null; slabTo: number | null
}

export interface CommissionStatement {
  scope: 'PLATFORM' | 'OWN'
  earnedThisMonth: number
  creditedThisMonth: number
  reversedThisMonth: number
  earnedAllTime: number
  paidAllTime: number
  reversedAllTime: number
  platformShareAllTime: number
  pendingAmount: number
  pendingTransactions: number
  children: { accountId: number; accountCode: string; name: string; role: string; commission: number; entries: number }[]
  myRates: CommissionRate[]
  lines: {
    id: number; createdAt: string; transactionRef: string; transactionId: number; fromCode: string; service: string
    baseAmount: number; ruleCode: string; levelRole: string; amount: number; status: string
  }[]
}

export interface LimitView {
  id: number
  scope: 'ROLE' | 'USER'
  roleCode: string | null
  accountId: number | null
  accountCode: string | null
  serviceCode: string
  minTxn: number
  maxTxn: number
  dailyAmount: number
  monthlyAmount: number
  dailyCount: number
  monthlyCount: number
  validUntil: string | null
  setBy: string
  updatedAt: string
}

export interface ReportColumn {
  key: string
  label: string
  numeric: boolean
}

export interface Report {
  type: string
  title: string
  description: string
  summary: Kpi[]
  columns: ReportColumn[]
  rows: Record<string, unknown>[]
}

export interface Reconciliation {
  ranAt: string
  walletTotal: number
  ledgerTotal: number
  matched: boolean
  checks: { title: string; passed: boolean; detail: string }[]
  exceptions: { reference: string; transactionId: number | null; issue: string; amount: number; createdAt: string }[]
}

export interface Adjustment {
  id: number
  requestRef: string
  accountId: number
  accountCode: string
  direction: 'CR' | 'DR'
  amount: number
  reason: string
  details: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedBy: string
  requestedById: number
  decidedBy: string | null
  createdAt: string
  transactionId: number | null
}

export interface Application {
  id: number
  accountId: number
  accountCode: string
  fullName: string
  role: Role
  submittedBy: string
  mobile: string
  email: string
  address: string
  kycDocumentType: string
  kycLastFour: string
  bankAccountHolder: string
  bankName: string
  bankIfsc: string
  bankAccountLastFour: string
  velocityPerTransaction: number
  velocityDaily: number
  commissionPayout: number
  agreementVersion: string
  agreementAcceptedAt: string
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  decisionNote: string | null
  decidedAt: string | null
}

export interface NotificationRow {
  id: number
  category: 'MONEY' | 'ACCOUNT' | 'SECURITY'
  title: string
  body: string
  link: string | null
  read: boolean
  createdAt: string
}

export interface AuditRow {
  id: number
  occurredAt: string
  actor: string
  action: string
  targetType: string | null
  targetId: string | null
  result: 'SUCCESS' | 'DENIED' | 'FAILED'
  detail: string | null
  ipAddress: string | null
  requestId: string | null
}

export interface PermissionRow {
  code: string
  description: string
  byRole: Record<Role, 'Y' | 'S' | 'O' | '-'>
}

export interface ServiceView {
  id: number
  serviceCode: string
  name: string
  feeAmount: number
  enabled: boolean
  provider: string
}
