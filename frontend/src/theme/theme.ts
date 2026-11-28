import { createTheme } from '@mui/material/styles'
import { colors, fontFamily, radius } from './tokens'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: colors.indigo600, dark: colors.indigo700, light: colors.indigo50, contrastText: '#FFFFFF' },
    secondary: { main: colors.navy900, contrastText: '#FFFFFF' },
    success: { main: colors.success, dark: colors.successText, light: colors.successBg },
    warning: { main: colors.warning, dark: colors.warningText, light: colors.warningBg },
    error: { main: colors.danger, dark: colors.dangerText, light: colors.dangerBg },
    background: { default: colors.canvas, paper: colors.surface },
    text: { primary: colors.text, secondary: colors.textMuted },
    divider: colors.border,
  },
  shape: { borderRadius: radius.input },
  typography: {
    fontFamily,
    h1: { fontSize: '1.625rem', fontWeight: 700, lineHeight: 1.25 },
    h2: { fontSize: '1.0625rem', fontWeight: 600, lineHeight: 1.3 },
    h3: { fontSize: '0.9375rem', fontWeight: 600 },
    body1: { fontSize: '0.9375rem' },
    body2: { fontSize: '0.875rem' },
    caption: { fontSize: '0.75rem' },
    overline: { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.06em', lineHeight: 1.5 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { minHeight: 44, borderRadius: radius.input, paddingInline: 18 } },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { outlined: { borderColor: colors.border, borderRadius: radius.card } },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { borderRadius: radius.card, borderColor: colors.border } },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { borderRadius: radius.input, backgroundColor: colors.surface },
        notchedOutline: { borderColor: colors.borderStrong },
      },
    },
    MuiInputLabel: { defaultProps: { shrink: true } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: radius.dialog } } },
  },
})
