import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { DataTable } from '../../components/tables/DataTable'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { CommissionRule } from '../../types/domain'
import { date, money, titleCase, todayIso } from '../../utils/format'

const LEVELS = ['RETAILER', 'DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'SUPER_DISTRIBUTOR', 'PLATFORM']

function share(rule: CommissionRule, level: string) {
  const s = rule.shares.find((x) => x.levelRole === level)
  if (!s) return '–'
  return rule.calcType === 'FIXED' ? money(s.fixedAmount) : `${Number(s.rate)}%`
}

export function CommissionRulesPage() {
  const rules = useApiData(() => api.get<CommissionRule[]>('/commissions/rules'), [])
  const [selected, setSelected] = useState<number | null>(null)
  const [previewAmount, setPreviewAmount] = useState(25000)
  const [newOpen, setNewOpen] = useState(false)
  const rule = rules.data?.find((r) => r.id === selected) ?? rules.data?.find((r) => r.state === 'Active') ?? rules.data?.[0]
  const preview = useApiData(() => (rule ? api.get<{ levelRole: string; rate: number; amount: number }[]>(`/commissions/rules/${rule.id}/preview`, { amount: previewAmount }) : Promise.resolve([])), [rule?.id, previewAmount])
  const max = Math.max(0.01, ...(preview.data ?? []).map((p) => p.amount))
  const total = (preview.data ?? []).reduce((a, p) => a + p.amount, 0)

  const columns: ColumnDef<CommissionRule, unknown>[] = [
    { id: 'code', header: 'Rule', accessorKey: 'ruleCode', cell: ({ row }) => <Button size="small" onClick={() => setSelected(row.original.id)} sx={{ fontFamily: 'ui-monospace, monospace', minHeight: 32 }}>{row.original.ruleCode}</Button> },
    { id: 'service', header: 'Service', accessorKey: 'serviceCode', cell: ({ getValue }) => titleCase(getValue() as string) },
    { id: 'type', header: 'Type', accessorKey: 'calcType', cell: ({ getValue }) => titleCase(getValue() as string) },
    ...LEVELS.map((l) => ({ id: l, header: titleCase(l).replace(' distributor', ''), accessorFn: (r: CommissionRule) => share(r, l) }) as ColumnDef<CommissionRule, unknown>),
    { id: 'slab', header: 'Slab (₹)', accessorFn: (r) => (r.slabFrom == null && r.slabTo == null ? 'Any amount' : `${r.slabFrom ?? 0} – ${r.slabTo ?? '∞'}`) },
    { id: 'effective', header: 'Effective', accessorFn: (r) => `${date(r.effectiveFrom)} → ${r.effectiveTo ? date(r.effectiveTo) : ''}` },
    { id: 'state', header: 'Status', accessorKey: 'state', cell: ({ getValue }) => <StatusChip status={getValue() as string} /> },
    {
      id: 'actions', header: '', enableSorting: false,
      cell: ({ row }) => row.original.status === 'ACTIVE' && (
        <Button size="small" color="error" variant="outlined" sx={{ minHeight: 32 }} onClick={async () => { await api.post(`/commissions/rules/${row.original.id}/deactivate`); rules.reload() }}>Deactivate</Button>
      ),
    },
  ]

  return (
    <>
      <PageHeader eyebrow="Configuration" title="Commission rules" subtitle="Each level's share is stored separately. Rules can't be edited once live: create a new version with a later start date."
        actions={<Button variant="contained" onClick={() => setNewOpen(true)}>New rule</Button>} />
      {rules.error && <ErrorCard message={rules.error} onRetry={rules.reload} />}
      {!rules.data && !rules.error && <LoadingCard />}
      {rules.data && (
        <Section padded={false}>
          <DataTable data={rules.data} columns={columns} caption="Commission rules" rowHighlight={(r) => r.id === rule?.id} />
        </Section>
      )}
      {rule && (
        <Section title={`Split preview · ${rule.ruleCode}`}>
          <Box role="group" aria-label="Transaction amount" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {[5000, 25000, 100000, 200000].map((a) => (
              <Chip key={a} label={money(a).replace('.00', '')} onClick={() => setPreviewAmount(a)} color={a === previewAmount ? 'secondary' : 'default'} variant={a === previewAmount ? 'filled' : 'outlined'} aria-pressed={a === previewAmount} sx={{ height: 36 }} />
            ))}
          </Box>
          {(preview.data ?? []).map((p) => (
            <Box key={p.levelRole} sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2">{titleCase(p.levelRole)}{p.rate != null ? ` · ${Number(p.rate)}%` : ''}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{money(p.amount)}</Typography>
              </Box>
              <Box sx={{ height: 8, borderRadius: 8, bgcolor: '#EEF1F6' }}><Box sx={{ height: 8, borderRadius: 8, bgcolor: colors.indigo600, width: `${(p.amount / max) * 100}%` }} /></Box>
            </Box>
          ))}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', pt: 1, borderTop: `1px solid ${colors.border}` }}>
            <Typography sx={{ fontWeight: 700 }}>Total commission</Typography><Typography sx={{ fontWeight: 700 }}>{money(total)}</Typography>
          </Box>
          <Typography variant="caption" color="text.secondary">Each share is clamped to its min and max and rounded half-up to paise. If a transaction is reversed, every share is reversed with it.</Typography>
        </Section>
      )}
      {newOpen && <NewRuleDialog onClose={() => setNewOpen(false)} onSaved={rules.reload} />}
    </>
  )
}

function NewRuleDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ ruleCode: '', serviceCode: 'PAYOUT', calcType: 'PERCENT', slabFrom: '', slabTo: '', effectiveFrom: todayIso(1), effectiveTo: '' })
  const [values, setValues] = useState<Record<string, { value: string; min: string; max: string }>>(
    Object.fromEntries(LEVELS.map((l) => [l, { value: '', min: '', max: '' }])),
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })
  const num = (v: string) => (v === '' ? null : Number(v))

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await api.post('/commissions/rules', {
        ruleCode: form.ruleCode.toUpperCase(), serviceCode: form.serviceCode, calcType: form.calcType,
        slabFrom: num(form.slabFrom), slabTo: num(form.slabTo), effectiveFrom: form.effectiveFrom, effectiveTo: form.effectiveTo || null,
        shares: LEVELS.filter((l) => values[l].value !== '').map((l) => ({
          levelRole: l,
          rate: form.calcType === 'PERCENT' ? Number(values[l].value) : null,
          fixedAmount: form.calcType === 'FIXED' ? Number(values[l].value) : null,
          minAmount: num(values[l].min), maxAmount: num(values[l].max),
        })),
      })
      onSaved()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ fontWeight: 700 }}>New commission rule</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '8px !important' }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 2 }}>
          <TextField label="Rule code" value={form.ruleCode} onChange={set('ruleCode')} placeholder="CR-PAYOUT-02" />
          <TextField select label="Service" value={form.serviceCode} onChange={set('serviceCode')}><MenuItem value="PAYOUT">Withdraw (payout)</MenuItem><MenuItem value="BILL_PAYMENT">Bill payment</MenuItem></TextField>
          <TextField select label="Type" value={form.calcType} onChange={set('calcType')}><MenuItem value="PERCENT">Percentage</MenuItem><MenuItem value="FIXED">Fixed per transaction</MenuItem></TextField>
          <TextField label="Slab from (₹, optional)" type="number" value={form.slabFrom} onChange={set('slabFrom')} />
          <TextField label="Slab to (₹, optional)" type="number" value={form.slabTo} onChange={set('slabTo')} />
          <Box />
          <TextField label="Effective from" type="date" value={form.effectiveFrom} onChange={set('effectiveFrom')} />
          <TextField label="Effective to (optional)" type="date" value={form.effectiveTo} onChange={set('effectiveTo')} />
        </Box>
        <Typography variant="h3">Shares by level</Typography>
        {LEVELS.map((l) => (
          <Box key={l} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1.4fr 1fr 1fr 1fr' }, gap: 1.5, alignItems: 'center' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{titleCase(l)}</Typography>
            <TextField size="small" type="number" label={form.calcType === 'PERCENT' ? 'Rate %' : 'Amount ₹'} value={values[l].value} onChange={(e) => setValues({ ...values, [l]: { ...values[l], value: e.target.value } })} />
            <TextField size="small" type="number" label="Min ₹" value={values[l].min} onChange={(e) => setValues({ ...values, [l]: { ...values[l], min: e.target.value } })} />
            <TextField size="small" type="number" label="Max ₹" value={values[l].max} onChange={(e) => setValues({ ...values, [l]: { ...values[l], max: e.target.value } })} />
          </Box>
        ))}
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" variant="outlined">Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy || !form.ruleCode}>{busy ? 'Saving…' : 'Save rule'}</Button>
      </DialogActions>
    </Dialog>
  )
}
