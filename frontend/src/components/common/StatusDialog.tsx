import { Box, Button, Dialog, DialogActions, DialogContent, Typography } from '@mui/material'
import CancelRounded from '@mui/icons-material/CancelRounded'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import ScheduleRounded from '@mui/icons-material/ScheduleRounded'
import type { ReactNode } from 'react'
import { colors } from '../../theme/tokens'

export type Outcome = 'SUCCESS' | 'FAILED' | 'PENDING'

const LOOK: Record<Outcome, { icon: typeof CheckCircleRounded; color: string }> = {
  SUCCESS: { icon: CheckCircleRounded, color: colors.success },
  FAILED: { icon: CancelRounded, color: colors.danger },
  PENDING: { icon: ScheduleRounded, color: colors.warning },
}

/** Result popup shown after a money action: success, failed or pending, with the facts that matter. */
export function StatusDialog({ open, outcome, title, message, facts, actions, onClose }: {
  open: boolean
  outcome: Outcome
  title: string
  message: ReactNode
  facts?: Array<[string, ReactNode]>
  actions?: ReactNode
  onClose: () => void
}) {
  const { icon: Icon, color } = LOOK[outcome]
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth aria-labelledby="status-dialog-title">
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 1.25, pt: 4 }}>
        <Icon sx={{ fontSize: 64, color }} aria-hidden />
        <Typography id="status-dialog-title" variant="h1" sx={{ fontSize: 22 }}>{title}</Typography>
        <Typography color="text.secondary">{message}</Typography>
        {facts && facts.length > 0 && (
          <Box sx={{ width: '100%', mt: 1 }}>
            {facts.map(([k, v]) => (
              <Box key={k} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 0.9, borderBottom: `1px solid ${colors.border}`, textAlign: 'left' }}>
                <Typography variant="body2" color="text.secondary">{k}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700, wordBreak: 'break-all', textAlign: 'right' }}>{v}</Typography>
              </Box>
            ))}
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, gap: 1, flexWrap: 'wrap', justifyContent: 'center' }}>
        {actions}
        <Button variant="outlined" color="inherit" onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}

/** One label/value row used on review steps. */
export function ReviewRow({ label, value, strong }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 1.25, borderBottom: `1px solid ${colors.border}` }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ fontWeight: strong ? 800 : 700, textAlign: 'right' }}>{value}</Typography>
    </Box>
  )
}
