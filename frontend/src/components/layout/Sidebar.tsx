import { Badge, Box, ButtonBase, List, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material'
import KeyOutlined from '@mui/icons-material/KeyOutlined'
import LogoutOutlined from '@mui/icons-material/LogoutOutlined'
import PersonOutlined from '@mui/icons-material/PersonOutlined'
import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { navItemsFor, navLabel } from '../../app/router/navigation'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import { ROLE_LABELS } from '../../utils/roles'
import { Logo } from '../common/Logo'

const linkSx = {
  minHeight: 42,
  borderRadius: 2.5,
  color: colors.sidebarText,
  '&:hover': { bgcolor: colors.navy700, color: '#FFFFFF' },
  '&.active': { bgcolor: colors.indigo600, color: '#FFFFFF' },
  '&.active .MuiListItemIcon-root': { color: '#FFFFFF' },
  flex: '0 0 auto',
}

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    if (!user) return
    let cancelled = false
    api.get<{ unread: number }>('/notifications/unread-count')
      .then((r) => !cancelled && setUnread(r.unread))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [user, location.pathname])

  if (!user) return null
  const items = navItemsFor(user.role)

  return (
    <Box component="nav" aria-label="Main" sx={{ width: '100%', height: '100%', bgcolor: colors.navy900, color: '#FFFFFF', display: 'flex', flexDirection: 'column', gap: 2, px: 1.75, py: 2.5, overflowY: 'auto' }}>
      <Box sx={{ px: 1 }}>
        <Logo tone="light" />
      </Box>
      <Box sx={{ px: 1.5, py: 1.25, borderRadius: 3, bgcolor: colors.navy800 }}>
        <Typography variant="overline" sx={{ color: '#AEB9D6', display: 'block' }}>Signed in as</Typography>
        <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{ROLE_LABELS[user.role]}</Typography>
        <Typography sx={{ fontSize: 12, color: colors.sidebarText }}>{user.accountCode}</Typography>
      </Box>

      <List sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 0.25, p: 0 }}>
        {items.map((item) => (
          <ListItemButton key={item.key} component={NavLink} to={item.path} end={item.path === '/wallet' || item.path === '/commissions'} onClick={onNavigate} sx={linkSx}>
            <ListItemIcon sx={{ minWidth: 36, color: colors.sidebarText }}>
              {item.key === 'notifications' ? (
                <Badge badgeContent={unread} color="primary" max={99}><item.icon fontSize="small" /></Badge>
              ) : (
                <item.icon fontSize="small" />
              )}
            </ListItemIcon>
            <ListItemText primary={navLabel(item, user.role)} slotProps={{ primary: { sx: { fontSize: 14, fontWeight: 500 } } }} />
          </ListItemButton>
        ))}
      </List>

      <Box sx={{ borderTop: `1px solid ${colors.navy700}`, pt: 1.5, display: 'flex', flexDirection: 'column', gap: 0.25 }}>
        <ListItemButton component={NavLink} to="/profile" onClick={onNavigate} sx={linkSx}>
          <ListItemIcon sx={{ minWidth: 36, color: colors.sidebarText }}><PersonOutlined fontSize="small" /></ListItemIcon>
          <ListItemText primary="My profile" slotProps={{ primary: { sx: { fontSize: 14 } } }} />
        </ListItemButton>
        <ListItemButton component={NavLink} to="/change-password" onClick={onNavigate} sx={linkSx}>
          <ListItemIcon sx={{ minWidth: 36, color: colors.sidebarText }}><KeyOutlined fontSize="small" /></ListItemIcon>
          <ListItemText primary="Change password" slotProps={{ primary: { sx: { fontSize: 14 } } }} />
        </ListItemButton>
        <ButtonBase onClick={() => logout('manual')} sx={{ ...linkSx, justifyContent: 'flex-start', gap: 1.5, px: 2, fontSize: 14 }}>
          <LogoutOutlined fontSize="small" />
          Sign out
        </ButtonBase>
      </Box>
    </Box>
  )
}
