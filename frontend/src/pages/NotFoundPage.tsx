import { Box, Button, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <Box sx={{ minHeight: '60vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, textAlign: 'center' }}>
      <Typography variant="h1">Page not found</Typography>
      <Typography color="text.secondary">This page doesn't exist or you don't have access to it.</Typography>
      <Button component={RouterLink} to="/dashboard" variant="contained">
        Go to dashboard
      </Button>
    </Box>
  )
}
