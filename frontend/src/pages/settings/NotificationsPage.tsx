import { Box, Button, Chip, Link, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { EmptyState, ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { NotificationRow } from '../../types/domain'
import type { PageResponse } from '../../types/api'
import { dateTime } from '../../utils/format'

const ICON: Record<NotificationRow['category'], { glyph: string; bg: string; fg: string }> = {
  MONEY: { glyph: '₹', bg: colors.successBg, fg: colors.successText },
  ACCOUNT: { glyph: 'i', bg: colors.indigo50, fg: colors.indigo700 },
  SECURITY: { glyph: '!', bg: colors.warningBg, fg: colors.warningText },
}

export function NotificationsPage() {
  const [filter, setFilter] = useState<'all' | 'unread' | NotificationRow['category']>('all')
  const list = useApiData(() => api.get<PageResponse<NotificationRow>>('/notifications', {
    unread: filter === 'unread', category: filter === 'all' || filter === 'unread' ? undefined : filter, size: 50,
  }), [filter])
  const unread = (list.data?.items ?? []).filter((n) => !n.read).length

  async function open(n: NotificationRow) {
    if (!n.read) await api.post(`/notifications/${n.id}/read`).catch(() => undefined)
  }

  return (
    <>
      <PageHeader title="Notifications" subtitle="Money movements, account changes and security alerts. SMS and email are sent through a mock provider in development."
        actions={<Button variant="outlined" color="inherit" onClick={async () => { await api.post('/notifications/read-all'); list.reload() }}>Mark all as read</Button>} />
      <Box role="group" aria-label="Filter" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {([['all', 'All'], ['unread', 'Unread'], ['MONEY', 'Money'], ['ACCOUNT', 'Accounts'], ['SECURITY', 'Security']] as const).map(([v, l]) => (
          <Chip key={v} label={l} onClick={() => setFilter(v)} color={filter === v ? 'secondary' : 'default'} variant={filter === v ? 'filled' : 'outlined'} aria-pressed={filter === v} sx={{ height: 36 }} />
        ))}
      </Box>
      {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
      {!list.data && !list.error && <LoadingCard />}
      {list.data && (
        <Section padded={false}>
          {list.data.items.length === 0 && <EmptyState title={filter === 'unread' ? "You're all caught up" : 'No notifications yet'} />}
          {list.data.items.map((n) => (
            <Box key={n.id} sx={{ display: 'flex', gap: 2, p: 2, borderRadius: 3, bgcolor: n.read ? 'transparent' : '#F7F9FE' }}>
              <Box aria-hidden sx={{ width: 38, height: 38, flex: 'none', borderRadius: 2.5, bgcolor: ICON[n.category].bg, color: ICON[n.category].fg, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{ICON[n.category].glyph}</Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
                  <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{n.title}{!n.read && <Box component="span" sx={{ ml: 1, width: 8, height: 8, borderRadius: '50%', bgcolor: colors.indigo600, display: 'inline-block' }} aria-label="Unread" />}</Typography>
                  <Typography variant="caption" color="text.secondary">{dateTime(n.createdAt)}</Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">{n.body}</Typography>
                {n.link && <Link component={RouterLink} to={n.link} onClick={() => open(n)} sx={{ fontSize: 13, fontWeight: 600 }}>Open</Link>}
              </Box>
            </Box>
          ))}
          {unread > 0 && <Typography variant="caption" color="text.secondary" sx={{ px: 2 }}>{unread} unread on this page</Typography>}
        </Section>
      )}
    </>
  )
}
