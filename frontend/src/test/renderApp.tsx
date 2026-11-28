import { CssBaseline, ThemeProvider } from '@mui/material'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../app/providers/AuthProvider'
import { AppRoutes } from '../app/router/AppRoutes'
import { theme } from '../theme/theme'

/** Renders the whole app (real routes, guards and auth provider) at the given URL. */
export function renderApp(path: string) {
  return render(
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}
