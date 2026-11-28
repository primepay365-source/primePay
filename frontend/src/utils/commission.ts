import type { CommissionRate } from '../types/domain'
import { money } from './format'

/** "0.1% of the transaction amount (min ₹0.01, max ₹200.00)" or "₹5.00 per transaction". */
export function describeRate(r: CommissionRate): string {
  const base = r.calcType === 'FIXED' ? `${money(r.fixedAmount)} per transaction` : `${r.rate ?? 0}% of the transaction amount`
  const caps = [r.minAmount != null ? `min ${money(r.minAmount)}` : null, r.maxAmount != null ? `max ${money(r.maxAmount)}` : null].filter(Boolean)
  const slab = r.slabFrom != null || r.slabTo != null ? ` · for ${money(r.slabFrom ?? 0)}${r.slabTo != null ? ' – ' + money(r.slabTo) : ' and above'}` : ''
  return base + (caps.length ? ` (${caps.join(', ')})` : '') + slab
}
