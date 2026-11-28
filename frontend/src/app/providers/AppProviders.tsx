import { CssBaseline, ThemeProvider } from '@mui/material'
import type { ReactNode } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { theme } from '../../theme/theme'
import { AuthProvider } from './AuthProvider'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>{children}</AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}
