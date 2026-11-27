import { Box, Typography } from '@mui/material'
import CheckRounded from '@mui/icons-material/CheckRounded'
import type { ReactNode } from 'react'
import { Logo } from '../components/common/Logo'
import { colors } from '../theme/tokens'

const YEAR = new Date().getFullYear()

const POINTS = [
  'Ledger entry for every wallet movement',
  'Commission split across five levels',
  'Limits checked before every transaction',
]

/** Split screen used by sign-in pages: brand panel on the left (stacks on top on phones), form on the right. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexWrap: 'wrap', bgcolor: 'background.default' }}>
      <Box
        component="section"
        aria-label="About PrimePay"
        sx={{
          flex: '1 1 420px',
          background: `linear-gradient(160deg, ${colors.navy900} 0%, ${colors.navy800} 60%, #24357A 100%)`,
          color: '#FFFFFF',
          p: { xs: 3, md: 6 },
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          gap: 5,
        }}
      >
        <Logo tone="light" size={40} />
        <Box sx={{ display: { xs: 'none', sm: 'flex' }, flexDirection: 'column', gap: 2.5, maxWidth: 460 }}>
          <Typography component="p" sx={{ fontSize: { sm: 28, md: 36 }, fontWeight: 700, lineHeight: 1.2 }}>
            One wallet network, from platform to retailer.
          </Typography>
          <Typography sx={{ color: colors.sidebarText, fontSize: 16, lineHeight: 1.6 }}>
            Manage your distributor hierarchy, move funds between wallets and track every commission, with a full audit trail.
          </Typography>
          <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 1.75 }}>
            {POINTS.map((point) => (
              <Box component="li" key={point} sx={{ display: 'flex', gap: 1.5, alignItems: 'center', color: '#E4E9F7' }}>
                <CheckRounded sx={{ color: colors.indigo200 }} fontSize="small" />
                {point}
              </Box>
            ))}
          </Box>
        </Box>
        <Typography sx={{ fontSize: 13, color: '#AEB9D6', display: { xs: 'none', sm: 'block' } }}>
          © {YEAR} PrimePay
        </Typography>
      </Box>
      <Box
        component="main"
        sx={{ flex: '999 1 480px', minWidth: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', px: 3, py: 6 }}
      >
        <Box sx={{ width: '100%', maxWidth: 420 }}>{children}</Box>
      </Box>
    </Box>
  )
}
