import { Chip } from '@mui/material'
import { colors } from '../../theme/tokens'

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const TONES: Record<Tone, { bg: string; fg: string }> = {
  success: { bg: colors.successBg, fg: colors.successText },
  warning: { bg: colors.warningBg, fg: colors.warningText },
  danger: { bg: colors.dangerBg, fg: colors.dangerText },
  info: { bg: colors.indigo50, fg: colors.indigo700 },
  neutral: { bg: '#EEF1F6', fg: colors.textSecondary },
}

const BY_STATUS: Record<string, Tone> = {
  SUCCESS: 'success', ACTIVE: 'success', APPROVED: 'success', CREDITED: 'success', MATCH: 'success', ENABLED: 'success',
  PENDING: 'warning', INITIATED: 'neutral', PENDING_APPROVAL: 'warning', PENDING_VERIFICATION: 'warning', SUBMITTED: 'warning', SCHEDULED: 'warning',
  FAILED: 'danger', REJECTED: 'danger', SUSPENDED: 'danger', DENIED: 'danger',
  REVERSED: 'info', REFUNDED: 'info', PLATFORM: 'info',
  INACTIVE: 'neutral', DRAFT: 'neutral', EXPIRED: 'neutral', DISABLED: 'neutral',
}

/** Status is always a word, never colour alone (accessibility). */
export function StatusChip({ status, label, tone }: { status: string; label?: string; tone?: Tone }) {
  const t = TONES[tone ?? BY_STATUS[status.toUpperCase()] ?? 'neutral']
  const text = label ?? status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ')
  return <Chip size="small" label={text} sx={{ bgcolor: t.bg, color: t.fg, height: 24 }} />
}
