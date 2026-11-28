import { AppBar, Box, Drawer, IconButton, Toolbar, useMediaQuery } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import MenuOutlined from '@mui/icons-material/MenuOutlined'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { colors } from '../../theme/tokens'
import { Logo } from '../common/Logo'
import { Sidebar } from './Sidebar'

const SIDEBAR_WIDTH = 248

/** Desktop: fixed navy sidebar. Under 900 px: top bar with a menu button that opens the sidebar as a drawer. */
export function AppLayout() {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [open, setOpen] = useState(false)

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', bgcolor: 'background.default' }}>
      {desktop ? (
        <Box sx={{ width: SIDEBAR_WIDTH, flexShrink: 0, position: 'sticky', top: 0, height: '100vh' }}>
          <Sidebar />
        </Box>
      ) : (
        <>
          <AppBar position="fixed" elevation={0} sx={{ bgcolor: colors.navy900 }}>
            <Toolbar sx={{ gap: 1 }}>
              <IconButton color="inherit" edge="start" aria-label="Open menu" onClick={() => setOpen(true)} sx={{ width: 44, height: 44 }}>
                <MenuOutlined />
              </IconButton>
              <Logo tone="light" size={28} />
            </Toolbar>
          </AppBar>
          <Drawer open={open} onClose={() => setOpen(false)} slotProps={{ paper: { sx: { width: SIDEBAR_WIDTH, border: 0 } } }}>
            <Sidebar onNavigate={() => setOpen(false)} />
          </Drawer>
        </>
      )}
      <Box
        component="main"
        sx={{ flex: 1, minWidth: 0, px: { xs: 2, sm: 3.5 }, pt: { xs: 10, md: 3.5 }, pb: 6, display: 'flex', flexDirection: 'column', gap: 3 }}
      >
        <Outlet />
      </Box>
    </Box>
  )
}
