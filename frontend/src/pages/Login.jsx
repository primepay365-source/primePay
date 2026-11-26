import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Eye, EyeOff, ShieldCheck } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(username, password)
      navigate('/', { replace: true })
    } catch (loginError) {
      setError(loginError.message || 'Unable to sign in. Check your credentials and API connection.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="auth-mark">P</span>
          <div><strong>PRIMEPAY</strong><small>ONE STOP SOLUTION FOR ALL PAYMENTS</small></div>
        </div>
        <div className="auth-intro">
          <p className="auth-eyebrow">SECURE ACCOUNT ACCESS</p>
          <h1>Welcome back</h1>
          <p>Sign in to manage your PrimePay business network.</p>
        </div>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="username">User ID</label>
          <input id="username" autoComplete="username" required value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Enter your user ID" />
          <label htmlFor="password">Password</label>
          <div className="auth-password">
            <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" />
            <button type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button>
          </div>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Log in'} {!busy && <ArrowRight size={17} />}
          </button>
        </form>
        <div className="auth-secure"><ShieldCheck size={18} /><span>Credentials are verified by the PrimePay API.</span></div>
      </section>
    </main>
  )
}
