import { Box, Card, CardContent, Typography } from '@mui/material'
import type { ReactNode } from 'react'

/** A titled card section, the basic building block of every page. */
export function Section({ title, actions, children, padded = true, sx }: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  padded?: boolean
  sx?: object
}) {
  return (
    <Card sx={sx}>
      <CardContent sx={{ p: padded ? 2.5 : 1, '&:last-child': { pb: padded ? 2.5 : 1.5 }, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {(title || actions) && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', px: padded ? 0 : 1.5, pt: padded ? 0 : 1 }}>
            {title && <Typography variant="h2" component="h2">{title}</Typography>}
            {actions && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
          </Box>
        )}
        {children}
      </CardContent>
    </Card>
  )
}

/** Label/value pairs in a responsive grid. */
export function Facts({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 2.25 }}>
      {items.map(([label, value]) => (
        <Box key={label}>
          <Typography component="dt" variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>{label}</Typography>
          <Typography component="dd" sx={{ m: 0, fontWeight: 600, fontSize: 14, wordBreak: 'break-word' }}>{value ?? '–'}</Typography>
        </Box>
      ))}
    </Box>
  )
}
