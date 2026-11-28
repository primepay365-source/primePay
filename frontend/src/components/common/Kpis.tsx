import { Box, Card, CardContent, Typography } from '@mui/material'
import type { Kpi } from '../../types/domain'
import { colors } from '../../theme/tokens'
import { count, money } from '../../utils/format'

export function formatKpi(k: Kpi): string {
  if (k.format === 'money') return money(k.value)
  if (k.format === 'count') return count(k.value)
  return k.hint ?? '–'
}

/** Row of KPI cards. "dark" cards use the navy wallet style from the design. */
export function KpiRow({ kpis }: { kpis: Kpi[] }) {
  return (
    <Box component="section" aria-label="Key figures" sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 2 }}>
      {kpis.map((k) => {
        const dark = k.tone === 'dark'
        return (
          <Card key={k.label} sx={dark ? { background: `linear-gradient(135deg, ${colors.navy900} 0%, ${colors.indigo700} 100%)`, border: 0, color: '#FFFFFF' } : undefined}>
            <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <Typography sx={{ fontSize: 13, fontWeight: 500, color: dark ? colors.sidebarText : 'text.secondary' }}>{k.label}</Typography>
              <Typography sx={{ fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: k.tone === 'danger' ? colors.dangerText : undefined }}>
                {formatKpi(k)}
              </Typography>
              {k.format !== 'text' && k.hint && (
                <Typography sx={{ fontSize: 13, color: dark ? colors.sidebarText : 'text.secondary' }}>{k.hint}</Typography>
              )}
            </CardContent>
          </Card>
        )
      })}
    </Box>
  )
}
