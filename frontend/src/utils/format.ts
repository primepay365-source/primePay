const MONEY = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const COUNT = new Intl.NumberFormat('en-IN')
const DATE_TIME = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Kolkata' })
const DATE = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' })
const SHORT_DATE = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', timeZone: 'Asia/Kolkata' })

/** "₹ 1,25,000.00" (Indian digit grouping). */
export function money(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '–'
  return '₹ ' + MONEY.format(Number(value))
}

/** "1,25,000.00" for table cells whose header already says ₹. */
export function amount(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''
  return MONEY.format(Number(value))
}

export function count(value: number | null | undefined): string {
  return value === null || value === undefined ? '–' : COUNT.format(value)
}

export function dateTime(value: string | null | undefined): string {
  return value ? DATE_TIME.format(new Date(value)) : '–'
}

export function date(value: string | null | undefined): string {
  if (!value) return '–'
  return DATE.format(new Date(value.length === 10 ? value + 'T00:00:00+05:30' : value))
}

export function shortDate(value: string): string {
  return SHORT_DATE.format(new Date(value.length === 10 ? value + 'T00:00:00+05:30' : value))
}

export function titleCase(value: string | null | undefined): string {
  if (!value) return ''
  return value.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())
}

/** Today as yyyy-MM-dd in IST, for date inputs. */
export function todayIso(offsetDays = 0): string {
  const d = new Date(Date.now() + offsetDays * 86_400_000)
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(d)
}
