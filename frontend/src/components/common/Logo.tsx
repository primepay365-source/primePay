import { Box, Typography } from '@mui/material'
import { colors } from '../../theme/tokens'

interface LogoProps {
  size?: number
  /** "light" for navy backgrounds (sidebar), "dark" for white backgrounds. */
  tone?: 'light' | 'dark'
}

export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" aria-hidden="true">
      <rect width="40" height="40" rx="11" fill={colors.indigo600} />
      <path
        d="M14 30V10h8a6 6 0 0 1 0 12h-8"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth="3.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="27.5" cy="28.5" r="2.4" fill={colors.indigo200} />
    </svg>
  )
}

export function Logo({ size = 32, tone = 'dark' }: LogoProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
      <LogoMark size={size} />
      <Typography
        component="span"
        sx={{ fontSize: size * 0.58, fontWeight: 700, letterSpacing: '-0.01em', color: tone === 'light' ? '#FFFFFF' : colors.text }}
      >
        Prime
        <Box component="span" sx={{ color: tone === 'light' ? colors.indigo200 : colors.indigo600 }}>
          Pay
        </Box>
      </Typography>
    </Box>
  )
}
