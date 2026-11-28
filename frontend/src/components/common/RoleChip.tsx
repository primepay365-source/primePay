import { Chip } from '@mui/material'
import type { Role } from '../../types/auth'
import { ROLE_COLORS, ROLE_LABELS } from '../../utils/roles'

export function RoleChip({ role }: { role: Role }) {
  const { bg, fg } = ROLE_COLORS[role]
  return <Chip size="small" label={ROLE_LABELS[role]} sx={{ bgcolor: bg, color: fg }} />
}
