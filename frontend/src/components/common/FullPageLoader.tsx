import { Box, CircularProgress, Typography } from '@mui/material'

export function FullPageLoader({ label = 'Loading PrimePay…' }: { label?: string }) {
  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2 }}
    >
      <CircularProgress size={32} />
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  )
}
