/** Retailer services: bill payment, add funds (payment gateway) and withdraw funds (saved bank accounts). */

export interface Biller { id: number; category: string; code: string; name: string; enabled: boolean }

export interface FetchedBill {
  fetchRef: string
  billerId: number
  billerName: string
  cardLast4: string
  mobileMasked: string
  customerName: string
  totalDue: number
  minimumDue: number
  dueDate: string
  charge: number
  expiresAt: string
}

export interface GatewayCategory { id: number; code: string; name: string; minAmount: number; maxAmount: number; chargePercent: number; enabled: boolean }

export type CardNetwork = 'VISA' | 'MASTERCARD' | 'RUPAY'

export interface PaymentSession {
  sessionRef: string
  categoryCode: string
  categoryName: string
  cardNetwork: CardNetwork
  cardHolder: string
  cardLast4: string | null
  paymentMode: 'CARD' | 'UPI' | null
  upiMasked: string | null
  amount: number
  charge: number
  netAmount: number
  status: 'CREATED' | 'OTP_SENT' | 'AWAITING_UPI' | 'SUCCESS' | 'FAILED' | 'EXPIRED'
  failureReason: string | null
  transactionId: number | null
  referenceNo: string | null
  expiresAt: string
  devOtp: string | null
}

export interface Beneficiary {
  id: number
  holderName: string
  verifiedName: string | null
  accountMasked: string
  accountLast4: string
  ifsc: string
  bankName: string
  status: 'VERIFIED' | 'REJECTED'
  createdAt: string
}

export interface AddBeneficiaryResult { beneficiary: Beneficiary; verified: boolean; reason: string | null }

export const NETWORK_LABEL: Record<CardNetwork, string> = { VISA: 'Visa', MASTERCARD: 'Mastercard', RUPAY: 'RuPay' }
