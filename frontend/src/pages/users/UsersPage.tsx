import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Link, MenuItem, TextField, Typography } from '@mui/material'
import type { ColumnDef } from '@tanstack/react-table'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { RoleChip } from '../../components/common/RoleChip'
import { Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { DataTable } from '../../components/tables/DataTable'
import { errorMessage, useApiData } from '../../hooks/useApiData'
import { useAuth } from '../../hooks/useAuth'
import { api } from '../../services/api'
import type { Role } from '../../types/auth'
import type { AccountStatus, CommissionRate, UserRow } from '../../types/domain'
import { colors } from '../../theme/tokens'
import { describeRate } from '../../utils/commission'
import { amount, date, titleCase } from '../../utils/format'
import { ROLE_LABELS } from '../../utils/roles'

/** Which roles each role may create, and the parent role each child needs (matches the API rules). */
export const CREATABLE: Record<Role, Role[]> = {
  ADMIN: ['ADMIN', 'SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'],
  SUPER_DISTRIBUTOR: ['MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'],
  MASTER_DISTRIBUTOR: ['DISTRIBUTOR', 'RETAILER'],
  DISTRIBUTOR: ['RETAILER'],
  RETAILER: [],
}
/** Levels each role may sit under (matches the API): a Retailer may be placed directly under any level above it. */
const PARENT_ROLES: Record<Role, Role[]> = {
  ADMIN: [], SUPER_DISTRIBUTOR: ['ADMIN'], MASTER_DISTRIBUTOR: ['SUPER_DISTRIBUTOR'], DISTRIBUTOR: ['MASTER_DISTRIBUTOR'],
  RETAILER: ['DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'SUPER_DISTRIBUTOR', 'ADMIN'],
}
const parentText = (role: Role) => PARENT_ROLES[role].map((r) => ROLE_LABELS[r]).join(', ').replace(/, ([^,]*)$/, ' or $1')
const MANAGEABLE: Record<Role, Role[]> = {
  ADMIN: ['SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'],
  SUPER_DISTRIBUTOR: ['MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'],
  MASTER_DISTRIBUTOR: ['DISTRIBUTOR', 'RETAILER'],
  DISTRIBUTOR: ['RETAILER'],
  RETAILER: [],
}

export function UsersPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const [role, setRole] = useState<Role | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [addOpen, setAddOpen] = useState(params.get('add') === '1')
  const [statusTarget, setStatusTarget] = useState<UserRow | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 300)
    return () => clearTimeout(t)
  }, [q])

  const counts = useApiData(() => api.get<Record<Role, number>>('/users/counts'), [])
  const list = useApiData(() => api.get<UserRow[]>('/users', { role, status, q: query }), [role, status, query])
  if (!user) return null
  const total = Object.values(counts.data ?? {}).reduce((a, b) => a + b, 0)
  const tabs: Array<[Role | '', string, number]> = [['', 'All roles', total],
    ...(['SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'] as Role[])
      .filter((r) => (counts.data?.[r] ?? 0) > 0 || CREATABLE[user.role].includes(r))
      .map((r) => [r, ROLE_LABELS[r], counts.data?.[r] ?? 0] as [Role, string, number])]

  const columns: ColumnDef<UserRow, unknown>[] = [
    {
      id: 'name', header: 'User', accessorKey: 'displayName',
      cell: ({ row }) => (
        <Box sx={{ display: 'flex', flexDirection: 'column' }}>
          <Link component={RouterLink} to={`/users/${row.original.id}`} sx={{ fontWeight: 600, color: 'text.primary' }}>{row.original.displayName}</Link>
          <Box component="span" sx={{ fontSize: 12, color: 'text.secondary' }}>{row.original.businessName ?? row.original.username}</Box>
        </Box>
      ),
    },
    { id: 'code', header: 'User ID', accessorKey: 'accountCode' },
    { id: 'role', header: 'Role', accessorKey: 'role', cell: ({ getValue }) => <RoleChip role={getValue() as Role} /> },
    { id: 'parent', header: 'Parent', accessorKey: 'parentCode' },
    { id: 'wallet', header: 'Wallet (₹)', accessorKey: 'walletBalance', cell: ({ getValue }) => amount(getValue() as number) },
    { id: 'status', header: 'Status', accessorKey: 'status', cell: ({ getValue }) => <StatusChip status={getValue() as string} /> },
    { id: 'joined', header: 'Joined', accessorKey: 'createdAt', cell: ({ getValue }) => date(getValue() as string) },
    {
      id: 'actions', header: '', enableSorting: false,
      cell: ({ row }) => (
        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
          <Button size="small" variant="outlined" color="inherit" component={RouterLink} to={`/users/${row.original.id}`} sx={{ minHeight: 36 }}>View</Button>
          {MANAGEABLE[user.role].includes(row.original.role) && ['ACTIVE', 'SUSPENDED', 'INACTIVE'].includes(row.original.status) && (
            <Button size="small" variant="outlined" color={row.original.status === 'ACTIVE' ? 'error' : 'success'} sx={{ minHeight: 36 }} onClick={() => setStatusTarget(row.original)}>
              {row.original.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
            </Button>
          )}
        </Box>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        eyebrow="Network"
        title={user.role === 'ADMIN' ? 'User management' : 'My network'}
        subtitle={`${total} accounts you can see · you can only manage accounts below you`}
        actions={
          <>
            <Button variant="outlined" color="inherit" onClick={() => api.download('/users/export.csv', 'primepay-users.csv', { role, status, q: query })}>Export CSV</Button>
            {user.role === 'MASTER_DISTRIBUTOR' && <Button variant="outlined" color="inherit" component={RouterLink} to="/distributors/onboard">Full onboarding</Button>}
            {CREATABLE[user.role].length > 0 && <Button variant="contained" onClick={() => setAddOpen(true)}>Add user</Button>}
          </>
        }
      />
      <Section>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          <TextField label="Search" placeholder="Name, user ID, mobile or email" value={q} onChange={(e) => setQ(e.target.value)} sx={{ flex: '1 1 260px' }} size="small" />
          <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as AccountStatus | '')} size="small" sx={{ minWidth: 180 }}>
            <MenuItem value="">All statuses</MenuItem>
            {['ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED', 'INACTIVE', 'REJECTED'].map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</MenuItem>)}
          </TextField>
        </Box>
        <Box role="group" aria-label="Filter by role" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {tabs.map(([r, label, n]) => (
            <Chip key={label} label={`${label} · ${n}`} onClick={() => setRole(r)} color={role === r ? 'secondary' : 'default'} variant={role === r ? 'filled' : 'outlined'} aria-pressed={role === r} sx={{ height: 36, fontWeight: 500 }} />
          ))}
        </Box>
      </Section>
      {list.error && <ErrorCard message={list.error} onRetry={list.reload} />}
      {list.loading && !list.data && <LoadingCard />}
      {list.data && (
        <Section padded={false}>
          <DataTable data={list.data} columns={columns} numeric={['wallet']} caption="Users" emptyTitle="No accounts match" emptyHint="Change the filters, or add the first account below you." />
        </Section>
      )}
      {addOpen && (
        <AddUserDialog
          onClose={() => {
            setAddOpen(false)
            if (params.get('add')) setParams({})
          }}
          onCreated={() => {
            list.reload()
            counts.reload()
          }}
        />
      )}
      {statusTarget && (
        <StatusDialog target={statusTarget} onClose={() => setStatusTarget(null)} onDone={() => list.reload()} />
      )}
    </>
  )
}

function StatusDialog({ target, onClose, onDone }: { target: UserRow; onClose: () => void; onDone: () => void }) {
  const suspending = target.status === 'ACTIVE'
  const [reason, setReason] = useState('')
  return (
    <ConfirmDialog
      open
      title={suspending ? `Suspend ${target.displayName}?` : `Reactivate ${target.displayName}?`}
      confirmLabel={suspending ? 'Suspend account' : 'Reactivate'}
      danger={suspending}
      disabled={!reason.trim()}
      onClose={onClose}
      onConfirm={async () => {
        await api.patch(`/users/${target.id}/status`, { status: suspending ? 'SUSPENDED' : 'ACTIVE', reason })
        onDone()
      }}
    >
      <Alert severity={suspending ? 'warning' : 'info'}>
        {suspending
          ? "They won't be able to sign in or move money. Their wallet balance is held, and accounts below them keep working."
          : 'They can sign in and transact again straight away.'}
      </Alert>
      <TextField label="Reason (saved to the audit log)" value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={2} required />
    </ConfirmDialog>
  )
}

const CODE_PREFIX: Record<Role, string> = { ADMIN: 'PP-AD-', SUPER_DISTRIBUTOR: 'PP-SD-', MASTER_DISTRIBUTOR: 'PP-MD-', DISTRIBUTOR: 'PP-DT-', RETAILER: 'PP-RT-' }

function suggestCode(role: Role) {
  return CODE_PREFIX[role] + String(10000 + (crypto.getRandomValues(new Uint32Array(1))[0] % 90000))
}

function AddUserDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { user } = useAuth()
  const roles = user ? CREATABLE[user.role] : []
  const firstRole = roles.find((r) => r !== 'ADMIN') ?? roles[0]
  const [role, setRole] = useState<Role>(firstRole)
  const [form, setForm] = useState({ accountCode: suggestCode(firstRole ?? 'RETAILER'), displayName: '', businessName: '', email: '', mobile: '', username: '', initialPassword: '', confirmPassword: '' })
  const isAdmin = role === 'ADMIN' // a Platform Admin has no parent
  const [showPassword, setShowPassword] = useState(false)
  const [parentId, setParentId] = useState<number | ''>('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<UserRow | null>(null)
  const candidates = useApiData(() => api.get<UserRow[]>('/users', { status: 'ACTIVE' }), [])
  const rates = useApiData(() => api.get<CommissionRate[]>('/commissions/rates', { role }), [role])

  const parents = useMemo(() => {
    if (!user) return []
    const allowed = PARENT_ROLES[role]
    const own = allowed.includes(user.role as Role) ? [{ id: user.accountId, code: user.accountCode, name: user.displayName, role: user.role as Role, you: true }] : []
    return own.concat((candidates.data ?? []).filter((u) => allowed.includes(u.role)).map((u) => ({ id: u.id, code: u.accountCode, name: u.displayName, role: u.role, you: false })))
  }, [candidates.data, role, user])
  const parent = parents.find((p) => p.id === parentId)

  useEffect(() => {
    // The signed-in user is the parent whenever it can be; otherwise the only candidate, if there is just one.
    const own = parents.find((p) => p.you)
    setParentId(own ? own.id : parents.length === 1 ? parents[0].id : '')
  }, [parents])

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value })

  // Client-side checks mirror the API's validation so mistakes show before saving; the API checks again.
  const problems: Record<string, string> = {}
  if (!/^[A-Za-z0-9_-]{3,30}$/.test(form.accountCode)) problems.accountCode = 'Required: 3–30 letters, numbers, - or _'
  if (form.displayName.trim().length < 2) problems.displayName = 'Required'
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) problems.email = 'Enter a valid email'
  if (!/^\+?[0-9]{10,13}$/.test(form.mobile)) problems.mobile = 'Enter a 10-digit mobile, e.g. +919876543210'
  if (!/^[A-Za-z0-9._@-]{3,80}$/.test(form.username)) problems.username = 'Required: letters, numbers, . - _'
  if (form.initialPassword.length < 12) problems.initialPassword = 'At least 12 characters'
  if (form.confirmPassword !== form.initialPassword) problems.confirmPassword = 'Passwords do not match'
  if (!isAdmin && !parentId) problems.parentId = 'Choose the parent account'
  const shown = (k: string) => fieldErrors[k] ?? (touched ? problems[k] : undefined)

  async function submit() {
    setTouched(true)
    if (Object.keys(problems).length > 0) {
      setError('Fill in the highlighted fields.')
      return
    }
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      const body = { ...form, confirmPassword: undefined }
      const { data } = await api.post<UserRow>('/users', { ...body, role, parentId: isAdmin ? null : parentId })
      setCreated(data)
      onCreated()
    } catch (e) {
      setError(errorMessage(e))
      const fields = (e as { fields?: { field: string; message: string }[] }).fields ?? []
      setFieldErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ fontWeight: 700 }}>{created ? 'Account created' : 'Add user'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '8px !important' }}>
        {created ? (
          <>
            <Alert severity="success">
              {ROLE_LABELS[created.role]} <b>{created.displayName}</b> is active{created.parentCode ? ` under ${created.parentCode}` : ''}.
            </Alert>
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 2 }}>
              {([['User ID', created.accountCode], ['System ID', String(created.id)], ['Level', ROLE_LABELS[created.role]], ['Parent', created.parentCode ?? '–'], ['Sign-in username', form.username], ['Status', created.status]] as const).map(([k, v]) => (
                <Box key={k}><Typography component="dt" variant="caption" color="text.secondary">{k}</Typography><Typography component="dd" sx={{ m: 0, fontWeight: 700 }}>{v}</Typography></Box>
              ))}
            </Box>
            <Alert severity="info">
              Their role, username and password are being emailed to <b>{created.email}</b>. They sign in with their email or mobile and that password, then a one-time code. The password is stored only as a hash in the database.
            </Alert>
          </>
        ) : (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
              <TextField select required label="User role" value={role} onChange={(e) => { const r = e.target.value as Role; setRole(r); setForm({ ...form, accountCode: suggestCode(r) }) }}
                helperText={`You can create: ${roles.map((r) => ROLE_LABELS[r]).join(', ')}`}>
                {roles.map((r) => <MenuItem key={r} value={r}>{ROLE_LABELS[r]}</MenuItem>)}
              </TextField>
              {isAdmin ? (
                <TextField label="Parent account" value="None (top of the hierarchy)" disabled helperText="A Platform Admin has no parent" />
              ) : (
                <TextField select required label="Parent account" value={parentId} onChange={(e) => setParentId(Number(e.target.value))} error={!!shown('parentId') && parents.length > 0}
                  helperText={parents.length === 0 ? `You need an active ${parentText(role)} first` : `A ${ROLE_LABELS[role]} belongs to a ${parentText(role)}`}>
                  {parents.map((p) => <MenuItem key={p.id} value={p.id}>{p.code} · {p.name} · {ROLE_LABELS[p.role]}{p.you ? ' (you)' : ''}</MenuItem>)}
                </TextField>
              )}
              <TextField required label="User ID" value={form.accountCode} onChange={set('accountCode')} error={!!shown('accountCode')} helperText={shown('accountCode') ?? 'Suggested automatically; you can change it'} />
              <TextField required label="Full name" value={form.displayName} onChange={set('displayName')} error={!!shown('displayName')} helperText={shown('displayName')} />
              <TextField required label="Mobile number" value={form.mobile} onChange={set('mobile')} placeholder="+919876543210" error={!!shown('mobile')} helperText={shown('mobile')} />
              <TextField required label="Email" type="email" value={form.email} onChange={set('email')} error={!!shown('email')} helperText={shown('email')} />
              <TextField label="Business name" value={form.businessName} onChange={set('businessName')} />
              <TextField required label="Sign-in username" value={form.username} onChange={set('username')} error={!!shown('username')} helperText={shown('username') ?? 'Letters, numbers, . - _'} />
              <TextField required label="Password" type={showPassword ? 'text' : 'password'} value={form.initialPassword} onChange={set('initialPassword')} error={!!shown('initialPassword')}
                helperText={shown('initialPassword') ?? 'At least 12 characters. Saved as a hash in the database.'} autoComplete="new-password"
                slotProps={{ input: { endAdornment: <Button size="small" onClick={() => setShowPassword(!showPassword)}>{showPassword ? 'Hide' : 'Show'}</Button> } }} />
              <TextField required label="Confirm password" type={showPassword ? 'text' : 'password'} value={form.confirmPassword} onChange={set('confirmPassword')} error={!!shown('confirmPassword')}
                helperText={shown('confirmPassword')} autoComplete="new-password" />
            </Box>

            {parent && (
              <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: colors.canvas, border: `1px solid ${colors.border}` }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Parent user</Typography>
                <Typography sx={{ fontWeight: 700 }}>{parent.code} · {parent.name} <Typography component="span" variant="body2" color="text.secondary">({ROLE_LABELS[parent.role]})</Typography></Typography>
                <Typography variant="body2" color="text.secondary">The new {ROLE_LABELS[role]} is linked to this parent. Commission on its transactions flows up through this parent's chain.</Typography>
              </Box>
            )}

            <Box sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${colors.border}` }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Commission configuration for a {ROLE_LABELS[role]}</Typography>
              {rates.data?.length === 0 && <Typography variant="body2" color="text.secondary">No active commission rule pays this level.</Typography>}
              {rates.data?.map((r) => (
                <Typography key={r.ruleCode} variant="body2"><b>{titleCase(r.serviceCode)}</b> ({r.ruleCode}): {describeRate(r)}</Typography>
              ))}
              <Typography variant="caption" color="text.secondary">Rates are set per level by the Platform Admin under Commission rules and apply to every account of this level.</Typography>
            </Box>
            {error && <Alert severity="error">{error}</Alert>}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" variant="outlined">{created ? 'Done' : 'Cancel'}</Button>
        {!created && <Button onClick={submit} variant="contained" disabled={busy || (!isAdmin && parents.length === 0)}>{busy ? 'Saving…' : 'Save user'}</Button>}
      </DialogActions>
    </Dialog>
  )
}
