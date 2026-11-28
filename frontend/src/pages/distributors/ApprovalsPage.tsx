import { Alert, Box, Button, ButtonBase, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { EmptyState, ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Facts, Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { Application } from '../../types/domain'
import { dateTime, money } from '../../utils/format'

const LABEL: Record<Application['status'], string> = { SUBMITTED: 'Pending approval', APPROVED: 'Approved', REJECTED: 'Rejected' }

export function ApprovalsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const list = useApiData(() => api.get<Application[]>('/onboarding/distributors/applications'), [])
  const [selected, setSelected] = useState<number | null>(null)
  const [note, setNote] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const apps = list.data ?? []
  const app = apps.find((a) => a.id === selected) ?? apps.find((a) => a.status === 'SUBMITTED') ?? apps[0]
  const waiting = apps.filter((a) => a.status === 'SUBMITTED').length

  async function decide(approve: boolean) {
    if (!app) return
    setBusy(true)
    setMessage(null)
    try {
      await api.post(`/onboarding/distributors/applications/${app.id}/${approve ? 'approve' : 'reject'}`, { note: note || null })
      setMessage({ ok: true, text: approve ? `${app.accountCode} is now active. Their wallet was created and the submitter was notified.` : `${app.accountCode} was rejected and the submitter was notified.` })
      setNote('')
      list.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Network" title={isAdmin ? 'Onboarding approvals' : 'My applications'}
        subtitle={isAdmin ? `${waiting} applications waiting. Approving makes the account active and creates its wallet.` : 'Distributors you onboarded and where their approval stands.'}
        actions={!isAdmin && <Button variant="contained" component={RouterLink} to="/distributors/onboard">Onboard distributor</Button>} />
      {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
      {!list.data && !list.error && <LoadingCard />}
      {list.data && apps.length === 0 && <Section><EmptyState title="No applications yet" hint={isAdmin ? 'Applications appear here when a Master Distributor onboards a distributor.' : 'Use Onboard distributor to submit one.'} /></Section>}
      {app && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
          <Section sx={{ flex: '1 1 300px', minWidth: 0 }}>
            {apps.map((a) => (
              <ButtonBase key={a.id} onClick={() => { setSelected(a.id); setMessage(null) }} aria-current={a.id === app.id}
                sx={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', textAlign: 'left', gap: 0.5, p: 1.5, borderRadius: 3, border: `1px solid ${a.id === app.id ? colors.indigo600 : colors.border}`, bgcolor: a.id === app.id ? colors.indigo50 : 'background.paper' }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}><Typography sx={{ fontWeight: 600, fontSize: 14 }}>{a.fullName}</Typography><StatusChip status={a.status} label={LABEL[a.status]} /></Box>
                <Typography variant="caption" color="text.secondary">{a.accountCode} · by {a.submittedBy} · {dateTime(a.submittedAt)}</Typography>
              </ButtonBase>
            ))}
          </Section>
          <Section title={app.fullName} actions={<StatusChip status={app.status} label={LABEL[app.status]} />} sx={{ flex: '2 1 520px', minWidth: 0 }}>
            <Facts items={[
              ['User ID', app.accountCode], ['Submitted by', app.submittedBy], ['Mobile (OTP verified)', app.mobile], ['Email', app.email], ['Address', app.address],
              ['KYC document', `${app.kycDocumentType} ending ${app.kycLastFour}`], ['Bank', `${app.bankName} · ${app.bankIfsc}`], ['Account holder', app.bankAccountHolder],
              ['Account number', `•••• ${app.bankAccountLastFour}`], ['Max per transfer', money(app.velocityPerTransaction)], ['Daily limit', money(app.velocityDaily)],
              ['Agreement', `${app.agreementVersion} · accepted ${dateTime(app.agreementAcceptedAt)}`],
            ]} />
            {app.decisionNote && <Alert severity={app.status === 'REJECTED' ? 'error' : 'info'}>Decision note: {app.decisionNote}</Alert>}
            {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
            {isAdmin && app.status === 'SUBMITTED' && (
              <>
                <TextField label="Note (required to reject, saved to the audit log)" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} />
                <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                  <Button variant="outlined" color="inherit" component={RouterLink} to={`/users/${app.accountId}`}>Open account</Button>
                  <Button variant="outlined" color="error" disabled={busy || !note.trim()} onClick={() => decide(false)}>Reject</Button>
                  <Button variant="contained" disabled={busy} onClick={() => decide(true)}>Approve and activate</Button>
                </Box>
              </>
            )}
            <Typography variant="caption" color="text.secondary">Full KYC and bank numbers are never stored in readable form; only the last four digits are shown.</Typography>
          </Section>
        </Box>
      )}
    </>
  )
}
