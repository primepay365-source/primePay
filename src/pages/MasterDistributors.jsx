import { useEffect, useMemo, useState } from 'react'
import { Plus, RefreshCw, Search, X } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/client'

const managedRoleByRole = {
  ADMIN: 'SUPER_DISTRIBUTOR',
  SUPER_DISTRIBUTOR: 'MASTER_DISTRIBUTOR',
  MASTER_DISTRIBUTOR: 'DISTRIBUTOR',
  DISTRIBUTOR: 'RETAILER',
}

const labels = {
  SUPER_DISTRIBUTOR: 'Super Distributors',
  MASTER_DISTRIBUTOR: 'Master Distributors',
  DISTRIBUTOR: 'Distributors',
  RETAILER: 'Retailers',
}

const roleOptionsByRole = {
  ADMIN: ['SUPER_DISTRIBUTOR'],
  SUPER_DISTRIBUTOR: ['MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER'],
  MASTER_DISTRIBUTOR: ['DISTRIBUTOR'],
  DISTRIBUTOR: ['RETAILER'],
}

const requiredParentByRole = {
  SUPER_DISTRIBUTOR: 'ADMIN',
  MASTER_DISTRIBUTOR: 'SUPER_DISTRIBUTOR',
  DISTRIBUTOR: 'MASTER_DISTRIBUTOR',
  RETAILER: 'DISTRIBUTOR',
}

export default function MasterDistributors() {
  const { session } = useAuth()
  const [accounts, setAccounts] = useState([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ accountCode: '', displayName: '', username: '', initialPassword: '', role: managedRoleByRole[session.account.role] || '', parentId: session.account.id })

  const managedRole = managedRoleByRole[session.account.role]
  const roleTitle = {
    ADMIN: 'All Downstream Accounts',
    SUPER_DISTRIBUTOR: 'Complete Business Hierarchy',
    MASTER_DISTRIBUTOR: 'My Distributors and Retailers',
    DISTRIBUTOR: 'My Retailers',
    RETAILER: 'My Account',
  }[session.account.role]
  const title = roleTitle || 'My Network'
  const eligibleParents = form.role === 'SUPER_DISTRIBUTOR'
    ? [session.account]
    : form.role === 'MASTER_DISTRIBUTOR'
      ? [session.account]
      : form.role === 'DISTRIBUTOR'
        ? (session.account.role === 'MASTER_DISTRIBUTOR' ? [session.account] : accounts.filter((account) => account.role === 'MASTER_DISTRIBUTOR'))
        : form.role === 'RETAILER'
          ? (session.account.role === 'DISTRIBUTOR' ? [session.account] : accounts.filter((account) => account.role === 'DISTRIBUTOR'))
          : [session.account]
  const visibleAccounts = useMemo(() => accounts
    .filter((account) => account.id !== session.account.id)
    .filter((account) => `${account.displayName} ${account.accountCode}`.toLowerCase().includes(query.toLowerCase())), [accounts, session.account.id, query])

  async function loadAccounts() {
    setError('')
    setLoading(true)
    try {
      setAccounts(await api.getAccounts(session.token))
    } catch (loadError) {
      setError(loadError.message || 'Could not load accounts from the API.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAccounts() }, [session.token])

  async function createAccount(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await api.createAccount(session.token, {
        ...form,
        role: form.role,
        parentId: Number(form.parentId),
      })
      setForm({ accountCode: '', displayName: '', username: '', initialPassword: '', role: managedRole || '', parentId: session.account.id })
      setShowForm(false)
      await loadAccounts()
    } catch (saveError) {
      setError(saveError.message || 'Could not create the account.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="card">
      <div className="mini-card-head">
        <div>
          <h4>{title} <span className="muted" style={{ fontWeight: 400, marginLeft: 6 }}>{visibleAccounts.length}</span></h4>
          <p>Accounts are loaded from your authorized API hierarchy.</p>
        </div>
        <div className="toolbar">
          <div className="search" style={{ width: 280 }}>
            <Search size={18} />
            <input aria-label="Search accounts" placeholder="Search name or code" value={query} onChange={(event) => setQuery(event.target.value)} />
          </div>
          <button className="btn icon" onClick={loadAccounts} aria-label="Refresh accounts"><RefreshCw size={17} /></button>
          {managedRole && <button className="btn primary" onClick={() => {
            setForm((current) => ({ ...current, role: managedRole, parentId: session.account.id }))
            setShowForm(true)
          }}><Plus size={16} /> Add {labels[managedRole]?.replace(/s$/, '')}</button>}
        </div>
      </div>

      {error && <div className="api-error" role="alert">{error}</div>}

      {showForm && (
        <form className="account-form" onSubmit={createAccount}>
          <div className="row-between"><strong>Create {labels[managedRole]?.replace(/s$/, '')}</strong><button type="button" className="btn icon" onClick={() => setShowForm(false)} aria-label="Close form"><X size={17} /></button></div>
          <label>Account code<input required maxLength={40} value={form.accountCode} onChange={(event) => setForm({ ...form, accountCode: event.target.value })} placeholder="For example, PSD001" /></label>
          <label>Display name<input required maxLength={160} value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} placeholder="Business or contact name" /></label>
          <label>Login username<input required minLength={3} maxLength={80} pattern="[A-Za-z0-9._-]+" autoComplete="off" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="Unique sign-in ID" /></label>
          <label>Initial password<input required minLength={12} maxLength={200} type="password" autoComplete="new-password" value={form.initialPassword} onChange={(event) => setForm({ ...form, initialPassword: event.target.value })} placeholder="At least 12 characters" /></label>
          <label>Account role
            <select value={form.role} onChange={(event) => {
              const nextRole = event.target.value
              const nextParent = nextRole === 'SUPER_DISTRIBUTOR' || nextRole === 'MASTER_DISTRIBUTOR'
                ? session.account.id
                : nextRole === 'DISTRIBUTOR'
                  ? (session.account.role === 'MASTER_DISTRIBUTOR' ? session.account.id : (accounts.find((account) => account.role === 'MASTER_DISTRIBUTOR')?.id || ''))
                  : nextRole === 'RETAILER'
                    ? (session.account.role === 'DISTRIBUTOR' ? session.account.id : (accounts.find((account) => account.role === 'DISTRIBUTOR')?.id || ''))
                    : session.account.id
              setForm({ ...form, role: nextRole, parentId: nextParent })
            }}>
              {(roleOptionsByRole[session.account.role] || []).map((role) => <option key={role} value={role}>{labels[role]}</option>)}
            </select>
          </label>
          <label>Parent account
            <select value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}>
              {eligibleParents.map((account) => <option key={account.id} value={account.id}>{account.displayName} · {account.accountCode}</option>)}
            </select>
          </label>
          <button className="btn primary" type="submit" disabled={saving}>{saving ? 'Creating…' : 'Create account'}</button>
        </form>
      )}

      <table>
        <thead><tr><th>#</th><th>Account</th><th>Role</th><th>Parent</th><th>Status</th><th>Created</th></tr></thead>
        <tbody>
          {loading ? <tr><td className="empty" colSpan="6">Loading accounts…</td></tr> : visibleAccounts.length === 0 ? <tr><td className="empty" colSpan="6">No downstream accounts yet.</td></tr> : visibleAccounts.map((account, index) => {
            const parent = accounts.find((candidate) => candidate.id === account.parentId)
            return (
              <tr key={account.id}>
                <td className="muted">{String(index + 1).padStart(2, '0')}</td>
                <td><div className="agent"><div className="avatar">{account.displayName?.slice(0, 1)?.toUpperCase()}</div><div><b>{account.displayName}</b><div className="muted">{account.accountCode}</div></div></div></td>
                <td>{account.role.replaceAll('_', ' ')}</td>
                <td>{parent ? <div><b>{parent.displayName}</b><div className="muted">{parent.accountCode}</div></div> : '—'}</td>
                <td><span className={`badge ${account.active ? 'active' : 'inactive'}`}>{account.active ? 'Active' : 'Inactive'}</span></td>
                <td>{account.createdAt ? new Date(account.createdAt).toLocaleDateString() : '—'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
