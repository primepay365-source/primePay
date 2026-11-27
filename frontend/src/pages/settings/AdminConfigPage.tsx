import { Alert, Box, Button, FormControlLabel, Switch, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { Role } from '../../types/auth'
import type { PermissionRow, ServiceView } from '../../types/domain'
import type { GatewayCategory } from '../../types/services'
import { ROLE_LABELS } from '../../utils/roles'

const ROLE_ORDER: Role[] = ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER']
const GRANT: Record<string, { label: string; bg: string; fg: string }> = {
  Y: { label: 'Yes', bg: colors.successBg, fg: colors.successText },
  S: { label: 'Own network', bg: colors.indigo50, fg: colors.indigo700 },
  O: { label: 'Own only', bg: '#EEF1F6', fg: colors.textSecondary },
  '-': { label: '—', bg: 'transparent', fg: colors.textMuted },
}

export function AdminConfigPage() {
  const [tab, setTab] = useState(0)
  return (
    <>
      <PageHeader eyebrow="Platform Admin only" title="Admin configuration" subtitle="Every change is written to the audit log." />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ borderBottom: `1px solid ${colors.border}` }}>
        <Tab label="Roles & permissions" /><Tab label="Services & fees" /><Tab label="Payment gateway" /><Tab label="Security & system" />
      </Tabs>
      {tab === 0 && <Permissions />}
      {tab === 1 && <Services />}
      {tab === 2 && <Gateway />}
      {tab === 3 && <Settings />}
    </>
  )
}

function Permissions() {
  const perms = useApiData(() => api.get<PermissionRow[]>('/admin/permissions'), [])
  if (perms.error) return <ErrorCard message={perms.error} onRetry={perms.reload} />
  if (!perms.data) return <LoadingCard />
  return (
    <Section padded={false}>
      <TableContainer sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label="Permission matrix">
          <TableHead><TableRow><TableCell sx={{ fontWeight: 600 }}>Permission</TableCell>{ROLE_ORDER.map((r) => <TableCell key={r} align="center" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{ROLE_LABELS[r]}</TableCell>)}</TableRow></TableHead>
          <TableBody>
            {perms.data.map((p) => (
              <TableRow key={p.code}>
                <TableCell><Typography sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 12, color: colors.indigo700 }}>{p.code}</Typography><Typography variant="body2">{p.description}</Typography></TableCell>
                {ROLE_ORDER.map((r) => {
                  const g = GRANT[p.byRole[r]]
                  return <TableCell key={r} align="center"><Box component="span" sx={{ px: 1, py: 0.25, borderRadius: 99, fontSize: 12, fontWeight: 600, bgcolor: g.bg, color: g.fg, whiteSpace: 'nowrap' }}>{g.label}</Box></TableCell>
                })}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <Typography variant="caption" color="text.secondary" sx={{ px: 2 }}>Permissions are enforced by the API on every request, with hierarchy scope. This matrix shows the rules; it is not edited here.</Typography>
    </Section>
  )
}

function Services() {
  const services = useApiData(() => api.get<ServiceView[]>('/admin/services'), [])
  const [drafts, setDrafts] = useState<Record<number, { fee: string; enabled: boolean }>>({})
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  if (services.error) return <ErrorCard message={services.error} onRetry={services.reload} />
  if (!services.data) return <LoadingCard />

  async function save(s: ServiceView) {
    const d = drafts[s.id] ?? { fee: String(s.feeAmount), enabled: s.enabled }
    try {
      await api.put(`/admin/services/${s.id}`, { feeAmount: Number(d.fee), enabled: d.enabled })
      setMessage({ ok: true, text: `${s.name} saved.` })
      services.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    }
  }

  return (
    <Section>
      {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
      {services.data.map((s) => {
        const d = drafts[s.id] ?? { fee: String(s.feeAmount), enabled: s.enabled }
        return (
          <Box key={s.id} sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', py: 1.5, borderBottom: `1px solid ${colors.border}` }}>
            <Box sx={{ flex: '1 1 220px' }}><Typography sx={{ fontWeight: 600 }}>{s.name}</Typography><Typography variant="body2" color="text.secondary">{s.serviceCode} · {s.provider}</Typography></Box>
            <TextField size="small" label="Fee per transaction (₹)" type="number" value={d.fee} onChange={(e) => setDrafts({ ...drafts, [s.id]: { ...d, fee: e.target.value } })} sx={{ width: 190 }} />
            <FormControlLabel control={<Switch checked={d.enabled} onChange={(e) => setDrafts({ ...drafts, [s.id]: { ...d, enabled: e.target.checked } })} />} label={d.enabled ? 'Enabled' : 'Disabled'} />
            <Button variant="outlined" color="inherit" onClick={() => save(s)}>Save</Button>
          </Box>
        )
      })}
    </Section>
  )
}

function Settings() {
  const settings = useApiData(() => api.get<{ key: string; value: string; note: string }[]>('/admin/settings'), [])
  if (settings.error) return <ErrorCard message={settings.error} onRetry={settings.reload} />
  if (!settings.data) return <LoadingCard />
  return (
    <Section>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 2.5 }}>
        {settings.data.map((s) => (
          <Box key={s.key}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{s.key}</Typography>
            <Typography sx={{ fontWeight: 600 }}>{s.value}</Typography>
            <Typography variant="body2" color="text.secondary">{s.note}</Typography>
          </Box>
        ))}
      </Box>
      <Typography variant="caption" color="text.secondary">These values come from server configuration and change with a deployment, not from this screen.</Typography>
    </Section>
  )
}

/** Add-funds gateway categories: amount range and charge percentage per category. */
function Gateway() {
  const cats = useApiData(() => api.get<GatewayCategory[]>('/add-funds/categories', { all: true }), [])
  const [drafts, setDrafts] = useState<Record<number, { min: string; max: string; pct: string; enabled: boolean }>>({})
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  if (cats.error) return <ErrorCard message={cats.error} onRetry={cats.reload} />
  if (!cats.data) return <LoadingCard />
  const draft = (c: GatewayCategory) => drafts[c.id] ?? { min: String(c.minAmount), max: String(c.maxAmount), pct: String(c.chargePercent), enabled: c.enabled }

  async function save(c: GatewayCategory) {
    const d = draft(c)
    try {
      await api.put(`/add-funds/categories/${c.id}`, { minAmount: Number(d.min), maxAmount: Number(d.max), chargePercent: Number(d.pct), enabled: d.enabled })
      setMessage({ ok: true, text: `${c.name} saved.` })
      cats.reload()
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    }
  }

  return (
    <Section>
      <Typography variant="body2" color="text.secondary">Retailers choose one of these categories when they add funds. The charge is deducted from the amount before it reaches the main wallet.</Typography>
      {message && <Alert severity={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
      {cats.data.map((c) => {
        const d = draft(c)
        const set = (patch: Partial<typeof d>) => setDrafts({ ...drafts, [c.id]: { ...d, ...patch } })
        return (
          <Box key={c.id} sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', py: 1.5, borderBottom: `1px solid ${colors.border}` }}>
            <Box sx={{ flex: '1 1 160px' }}><Typography sx={{ fontWeight: 600 }}>{c.name}</Typography><Typography variant="body2" color="text.secondary">{c.code}</Typography></Box>
            <TextField size="small" label="Minimum (₹)" type="number" value={d.min} onChange={(e) => set({ min: e.target.value })} sx={{ width: 140 }} />
            <TextField size="small" label="Maximum (₹)" type="number" value={d.max} onChange={(e) => set({ max: e.target.value })} sx={{ width: 150 }} />
            <TextField size="small" label="Charge (%)" type="number" value={d.pct} onChange={(e) => set({ pct: e.target.value })} sx={{ width: 120 }} slotProps={{ htmlInput: { step: '0.01', min: 0, max: 10 } }} />
            <FormControlLabel control={<Switch checked={d.enabled} onChange={(e) => set({ enabled: e.target.checked })} />} label={d.enabled ? 'Enabled' : 'Disabled'} />
            <Button variant="outlined" color="inherit" onClick={() => save(c)}>Save</Button>
          </Box>
        )
      })}
    </Section>
  )
}
