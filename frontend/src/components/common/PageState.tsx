import { Alert, Box, Button, Card, CardContent, Skeleton, Typography } from '@mui/material'
import InboxOutlined from '@mui/icons-material/InboxOutlined'
import type { ReactNode } from 'react'

export function LoadingCard({ rows = 4 }: { rows?: number }) {
  return (
    <Card aria-busy="true">
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <Skeleton width="30%" height={22} />
        {Array.from({ length: rows }).map((_, i) => (
          <Skeleton key={i} height={34} />
        ))}
      </CardContent>
    </Card>
  )
}

export function ErrorCard({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Alert
      severity="error"
      action={onRetry && <Button color="inherit" size="small" onClick={onRetry}>Try again</Button>}
    >
      {message}
    </Alert>
  )
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <Box sx={{ py: 5, px: 2, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, textAlign: 'center' }}>
      <InboxOutlined sx={{ fontSize: 40, color: 'text.secondary' }} aria-hidden />
      <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
      {hint && <Typography variant="body2" color="text.secondary">{hint}</Typography>}
      {action}
    </Box>
  )
}
