import { Box, Breadcrumbs, Button, Link, Tab, Tabs, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { RoleChip } from '../../components/common/RoleChip'
import { Facts, Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { TransactionTable } from '../../components/tables/TransactionTable'
import { useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { StatusChange, Transaction, UserRow, WalletSummary } from '../../types/domain'
import { dateTime, money } from '../../utils/format'

export function UserDetailPage() {
  const { id } = useParams()
  const { user: me } = useAuth()
  const [tab, setTab] = useState(0)
  const u = useApiData(() => api.get<UserRow>(`/users/${id}`), [id])
  const wallet = useApiData(() => (u.data && u.data.role !== 'ADMIN' && u.data.status === 'ACTIVE' ? api.get<WalletSummary>(`/wallets/${id}`) : Promise.resolve(null)), [id, u.data?.status])
  const history = useApiData(() => api.get<StatusChange[]>(`/users/${id}/status-history`), [id])
  const txns = useApiData(() => api.page<Transaction>('/transactions', { accountId: id, size: 20 }), [id])

  if (u.error) return <ErrorCard message={u.error} onRetry={u.reload} />
  if (!u.data) return <LoadingCard />
  const user = u.data
  const canSend = me && user.parentId === me.accountId && user.status === 'ACTIVE' && me.role !== 'ADMIN' && me.role !== 'RETAILER'

  return (
    <>
      <Breadcrumbs aria-label="Breadcrumb"><Link component={RouterLink} to="/users">Users</Link><Typography color="text.secondary">{user.accountCode}</Typography></Breadcrumbs>
      <PageHeader
        title={user.displayName}
        subtitle={<Box component="span" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}><RoleChip role={user.role} /><StatusChip status={user.status} />{user.accountCode} · parent {user.parentCode ?? 'Platform'}</Box>}
        actions={canSend && <Button variant="contained" component={RouterLink} to={`/wallet/transfer?to=${user.id}`}>Transfer funds</Button>}
      />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} aria-label="User sections" variant="scrollable" sx={{ borderBottom: `1px solid ${colors.border}` }}>
        <Tab label="Overview" />
        <Tab label="Transactions" />
        <Tab label="Status history" />
      </Tabs>
      {tab === 0 && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
          <Section title="Profile" sx={{ flex: '2 1 480px', minWidth: 0 }}>
            <Facts items={[
              ['Business', user.businessName], ['Sign-in username', user.username], ['Email', user.email], ['Mobile', user.mobile],
              ['Address', user.address], ['Created', dateTime(user.createdAt)], ['Last sign-in', dateTime(user.lastLoginAt)],
            ]} />
          </Section>
          {wallet.data && (
            <Box sx={{ flex: '1 1 300px', borderRadius: 4, p: 3, color: '#FFFFFF', background: `linear-gradient(135deg, ${colors.navy900} 0%, ${colors.indigo700} 100%)`, display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Typography variant="overline" sx={{ color: colors.sidebarText }}>Wallet · available</Typography>
              <Typography sx={{ fontSize: 30, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{money(wallet.data.availableBalance)}</Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, fontSize: 13, color: colors.sidebarText }}>
                <span>Ledger<br /><b style={{ color: '#fff' }}>{money(wallet.data.ledgerBalance)}</b></span>
                <span>Blocked<br /><b style={{ color: '#fff' }}>{money(wallet.data.blockedBalance)}</b></span>
                <span>Total credit<br /><b style={{ color: '#fff' }}>{money(wallet.data.totalCredit)}</b></span>
                <span>Total debit<br /><b style={{ color: '#fff' }}>{money(wallet.data.totalDebit)}</b></span>
              </Box>
            </Box>
          )}
        </Box>
      )}
      {tab === 1 && (
        <Section padded={false}>
          {txns.data ? <TransactionTable rows={txns.data.items} /> : <LoadingCard />}
        </Section>
      )}
      {tab === 2 && (
        <Section title="Status history">
          {(history.data ?? []).map((h, i) => (
            <Box key={i} sx={{ display: 'flex', gap: 1.5, alignItems: 'center', flexWrap: 'wrap' }}>
              <StatusChip status={h.toStatus} />
              <Typography variant="body2">{dateTime(h.changedAt)} · by {h.changedBy}{h.reason ? ` · ${h.reason}` : ''}</Typography>
            </Box>
          ))}
          {history.data?.length === 0 && <Typography variant="body2" color="text.secondary">No changes recorded yet.</Typography>}
        </Section>
      )}
    </>
  )
}
