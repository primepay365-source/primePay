import { Box, Button, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { KpiRow } from '../../components/common/Kpis'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { TransactionTable } from '../../components/tables/TransactionTable'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { CommissionStatement, Dashboard } from '../../types/domain'
import { count, money, shortDate } from '../../utils/format'
import { ROLE_LABELS } from '../../utils/roles'

const DATE_FORMAT = new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Kolkata' })

function greeting(now: Date): string {
  const hour = Number(new Intl.DateTimeFormat('en-IN', { hour: 'numeric', hourCycle: 'h23', timeZone: 'Asia/Kolkata' }).format(now))
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

const CTA: Record<string, [string, string]> = {
  ADMIN: ['Add user', '/users?add=1'],
  SUPER_DISTRIBUTOR: ['Add funds', '/add-funds'],
  MASTER_DISTRIBUTOR: ['Add funds', '/add-funds'],
  DISTRIBUTOR: ['Add funds', '/add-funds'],
  RETAILER: ['Pay a bill', '/bills'],
}

/** Every figure comes from GET /reports/dashboard, computed from the database for the caller's scope. */
export function DashboardPage() {
  const { user } = useAuth()
  const [now] = useState(() => new Date())
  const { data, error, loading, reload } = useApiData(() => api.get<Dashboard>('/reports/dashboard'), [])
  const commission = useApiData(() => api.get<CommissionStatement>('/commissions/statement'), [])
  if (!user) return null
  const [ctaLabel, ctaHref] = CTA[user.role]
  const maxBreak = Math.max(1, ...(data?.breakdown.map((b) => b.value) ?? [1]))

  return (
    <>
      <PageHeader
        eyebrow={`${ROLE_LABELS[user.role]} · ${data?.scope ?? ''}`}
        title={`${greeting(now)}, ${user.displayName}`}
        subtitle={DATE_FORMAT.format(now)}
        actions={
          <>
            <Button component={RouterLink} to="/reports" variant="outlined" color="inherit">Reports</Button>
            <Button component={RouterLink} to={ctaHref} variant="contained">{ctaLabel}</Button>
          </>
        }
      />
      {error && <ErrorCard message={error} onRetry={reload} />}
      {loading && !data && <LoadingCard rows={6} />}
      {data && (
        <>
          <KpiRow kpis={data.kpis} />
          {(data.network.length > 0 || commission.data) && (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {data.network.length > 0 && (
                <Section title={user.role === 'ADMIN' ? 'Users by level' : 'My network'} actions={<Button component={RouterLink} to="/hierarchy" size="small">Hierarchy</Button>} sx={{ flex: '2 1 480px', minWidth: 0 }}>
                  <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 1.5 }}>
                    {data.network.map((n) => (
                      <Box key={n.label} component={RouterLink} to="/users" sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${colors.border}`, textDecoration: 'none', color: 'inherit', '&:hover': { borderColor: colors.indigo600 } }}>
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{n.label === 'Total users' ? n.label : (user.role === 'ADMIN' ? '' : 'My ') + n.label + 's'}</Typography>
                        <Typography sx={{ fontSize: 24, fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{count(n.value)}</Typography>
                      </Box>
                    ))}
                  </Box>
                </Section>
              )}
              {commission.data && (
                <Section title={user.role === 'ADMIN' ? 'Commission (all time)' : 'My commission (all time)'} actions={<Button component={RouterLink} to="/commissions/statement" size="small">{user.role === 'ADMIN' ? 'Report' : 'History'}</Button>} sx={{ flex: '1 1 300px', minWidth: 0 }}>
                  {([
                    [user.role === 'ADMIN' ? 'Total generated' : 'Total earned', commission.data.earnedAllTime],
                    [user.role === 'ADMIN' ? 'Paid to the network' : 'Paid to wallet', commission.data.paidAllTime],
                    ['Pending', commission.data.pendingAmount],
                    ...(user.role === 'ADMIN' ? [['Platform share', commission.data.platformShareAllTime]] : []),
                  ] as Array<[string, number]>).map(([label, value]) => (
                    <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.75, borderBottom: `1px solid ${colors.border}` }}>
                      <Typography variant="body2" color="text.secondary">{label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(value)}</Typography>
                    </Box>
                  ))}
                </Section>
              )}
            </Box>
          )}
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
            <Section title="Transactions · last 14 days" sx={{ flex: '2 1 520px', minWidth: 0 }}>
              <Box role="img" aria-label="Bar chart of successful and failed business transactions per day over the last 14 days" sx={{ height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={data.series.map((p) => ({ ...p, day: shortDate(p.date) }))} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.border} />
                    <XAxis dataKey="day" tick={{ fontSize: 12, fill: colors.textMuted }} interval="preserveStartEnd" />
                    <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: colors.textMuted }} />
                    <Tooltip formatter={(value, name) => [count(Number(value)), String(name)]} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="success" name="Successful" stackId="a" fill={colors.indigo600} radius={[0, 0, 0, 0]} />
                    <Bar dataKey="failed" name="Failed" stackId="a" fill="#E0912F" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            </Section>
            <Section title={data.breakdownTitle} sx={{ flex: '1 1 300px', minWidth: 0 }}>
              {data.breakdown.length === 0 && <Typography variant="body2" color="text.secondary">Nothing yet. Figures appear after the first transactions.</Typography>}
              {data.breakdown.map((row) => (
                <Box key={row.label} sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, fontSize: 14 }}>
                    <Typography variant="body2" noWrap>{row.label}</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{row.format === 'money' ? money(row.value) : count(row.value)}</Typography>
                  </Box>
                  <Box sx={{ height: 8, borderRadius: 8, bgcolor: '#EEF1F6' }}>
                    <Box sx={{ height: 8, borderRadius: 8, bgcolor: colors.indigo600, width: `${Math.max(2, (Number(row.value) / maxBreak) * 100)}%` }} />
                  </Box>
                </Box>
              ))}
            </Section>
          </Box>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
            <Section title="Recent transactions" actions={<Button component={RouterLink} to="/transactions" size="small">View all</Button>} padded={false} sx={{ flex: '2 1 560px', minWidth: 0 }}>
              <TransactionTable rows={data.recent} compact emptyHint="Transactions in your scope appear here." />
            </Section>
            <Section title="Commission this month" actions={<Button component={RouterLink} to="/commissions/statement" size="small">{user.role === 'ADMIN' ? 'Report' : 'Statement'}</Button>} sx={{ flex: '1 1 300px', minWidth: 0 }}>
              <Typography sx={{ fontSize: 28, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(data.commissionTotal)}</Typography>
              {data.commissionLines.map((l) => (
                <Box key={l.label} sx={{ display: 'flex', justifyContent: 'space-between', py: 1, borderBottom: `1px solid ${colors.border}` }}>
                  <Typography variant="body2" color="text.secondary">{l.label}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{l.format === 'money' ? money(l.value) : count(l.value)}</Typography>
                </Box>
              ))}
            </Section>
          </Box>
        </>
      )}
    </>
  )
}
