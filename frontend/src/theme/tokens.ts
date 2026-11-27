/**
 * PrimePay design tokens, matching the "Design system" board of the PrimePay design canvas.
 * Text colours are chosen to meet WCAG AA (4.5:1) on their backgrounds.
 */
export const colors = {
  navy900: '#14213D',
  navy800: '#1C2C52',
  navy700: '#22325C',
  indigo600: '#3457D5',
  indigo700: '#2A46B0',
  indigo50: '#EAEFFD',
  indigo200: '#9FB4FF',
  canvas: '#F5F7FB',
  surface: '#FFFFFF',
  border: '#E3E8F2',
  borderStrong: '#CBD3E3',
  text: '#14213D',
  textSecondary: '#4A5672',
  textMuted: '#5F6B85',
  sidebarText: '#C9D3EE',
  success: '#16865B',
  successText: '#0F6B47',
  successBg: '#E4F4EC',
  warning: '#B7791F',
  warningText: '#7E5208',
  warningBg: '#FCF1DC',
  danger: '#C43D4D',
  dangerText: '#A12C40',
  dangerBg: '#FBE9EC',
} as const

export const radius = { input: 10, card: 16, dialog: 20 } as const

export const fontFamily = "'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif"
