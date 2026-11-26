import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight, Check, CheckCircle2, UploadCloud } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/client'

const steps = ['Personal information', 'KYC details', 'Bank information', 'Velocity & commission', 'Agreement']
const agreementVersion = 'MASTER-DISTRIBUTOR-V1'
const initialForm = {
  accountCode: '', fullName: '', mobile: '', email: '', address: '',
  kycDocumentType: 'PAN', kycLastFour: '',
  bankAccountHolder: '', bankName: '', bankIfsc: '', bankAccountLastFour: '',
  velocityPerTransaction: '', velocityDaily: '',
  commissionPayin: '0', commissionPayout: '0', commissionBillPayment: '0',
  agreementVersion,
}

export default function DistributorOnboarding() {
  const { session } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(initialForm)
  const [photo, setPhoto] = useState(null)
  const [photoPreview, setPhotoPreview] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [developmentOtp, setDevelopmentOtp] = useState('')
  const [otpRequested, setOtpRequested] = useState(false)
  const [mobileVerified, setMobileVerified] = useState(false)
  const [accepted, setAccepted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!photo) {
      setPhotoPreview('')
      return undefined
    }
    const url = URL.createObjectURL(photo)
    setPhotoPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    if (field === 'mobile') {
      setMobileVerified(false)
      setOtpRequested(false)
      setDevelopmentOtp('')
    }
  }

  async function requestOtp() {
    setError('')
    setBusy(true)
    try {
      const response = await api.requestOnboardingOtp(session.token, form.mobile)
      setOtpRequested(true)
      setDevelopmentOtp(response.developmentCode || '')
    } catch (requestError) {
      setError(requestError.message || 'Could not request the OTP.')
    } finally {
      setBusy(false)
    }
  }

  async function verifyOtp() {
    setError('')
    setBusy(true)
    try {
      await api.verifyOnboardingOtp(session.token, form.mobile, otpCode)
      setMobileVerified(true)
    } catch (verifyError) {
      setError(verifyError.message || 'OTP verification failed.')
    } finally {
      setBusy(false)
    }
  }

  function next(event) {
    event.preventDefault()
    setError('')
    if (!event.currentTarget.form?.reportValidity?.()) return
    if (step === 0 && !mobileVerified) {
      setError('Verify the mobile number before continuing.')
      return
    }
    if (step === 4 && !accepted) {
      setError('Accept the agreement before submitting.')
      return
    }
    if (step < steps.length - 1) setStep((current) => current + 1)
    else submitOnboarding()
  }

  async function submitOnboarding() {
    setBusy(true)
    setError('')
    try {
      const result = await api.submitDistributorOnboarding(session.token, form, photo)
      setSubmitted(result)
    } catch (submitError) {
      setError(submitError.message || 'Could not submit the distributor onboarding.')
    } finally {
      setBusy(false)
    }
  }

  if (session.account.role !== 'MASTER_DISTRIBUTOR') {
    return <div className="card onboarding-card"><h2>Master Distributor access required</h2><p className="muted">Only a Master Distributor can onboard a Distributor.</p><Link className="btn" to="/master-distributors">Back to Network</Link></div>
  }

  if (submitted) {
    const copyCredentials = async () => {
      await navigator.clipboard.writeText(`Username: ${submitted.username}\nInitial password: ${submitted.initialPassword}`)
      setCopied(true)
    }
    return (
      <div className="card onboarding-success">
        <CheckCircle2 size={54} />
        <p className="onboarding-eyebrow">SUBMISSION COMPLETE</p>
        <h2>Distributor submitted</h2>
        <p>{submitted.fullName} · {submitted.accountCode}</p>
        <p className="muted">Status: {submitted.status}</p>
        <div className="onboarding-credentials"><strong>Share these sign-in details securely</strong><span>Username: {submitted.username}</span><span>Initial password: <code>{submitted.initialPassword}</code></span><button type="button" className="btn" onClick={copyCredentials}>{copied ? 'Copied' : 'Copy credentials'}</button></div>
        <Link className="btn primary" to="/master-distributors">Return to Network</Link>
      </div>
    )
  }

  const fileLabel = photo ? `${photo.name} (${(photo.size / 1024 / 1024).toFixed(2)} MB)` : 'Choose a JPEG, PNG, or WebP photo (max 5 MB)'
  const field = (label, key, props = {}) => (
    <label className="onboarding-field">{label}
      <input value={form[key]} onChange={(event) => update(key, event.target.value)} {...props} />
    </label>
  )

  return (
    <div className="onboarding-wrap">
      <div className="onboarding-heading">
        <div><p className="onboarding-eyebrow">NETWORK ONBOARDING</p><h2>Create Distributor</h2><p>Complete each section, then review and submit the distributor account.</p></div>
        <Link className="btn ghost" to="/master-distributors"><ArrowLeft size={16} /> Back to Network</Link>
      </div>

      <div className="onboarding-stepper" aria-label="Onboarding progress">
        {steps.map((label, index) => <div key={label} className={`onboarding-step ${index === step ? 'current' : ''} ${index < step ? 'done' : ''}`}><span>{index < step ? <Check size={15} /> : index + 1}</span><small>{label}</small></div>)}
      </div>

      <form id="onboarding-form" className="card onboarding-card" onSubmit={(event) => event.preventDefault()}>
        <div className="onboarding-card-head"><div><span>STEP {step + 1} OF {steps.length}</span><h3>{steps[step]}</h3></div></div>

        {step === 0 && <div className="onboarding-grid">
          {field('Distributor account code', 'accountCode', { required: true, maxLength: 40, pattern: '[A-Za-z0-9_-]+', placeholder: 'For example, PSD001' })}
          {field('Full name', 'fullName', { required: true, maxLength: 160, autoComplete: 'name', placeholder: 'Name as shown on KYC document' })}
          {field('Mobile number', 'mobile', { required: true, type: 'tel', pattern: '\\+?[0-9]{7,15}', placeholder: 'Include country code if required', disabled: mobileVerified })}
          {field('Email address', 'email', { required: true, type: 'email', maxLength: 160, autoComplete: 'email', placeholder: 'name@example.com' })}
          <label className="onboarding-field full-width">Address<textarea required maxLength={500} rows="3" value={form.address} onChange={(event) => update('address', event.target.value)} placeholder="Residential or business address" /></label>
          <div className="onboarding-field full-width">
            <span className="field-label">Photo</span>
            <label className="photo-upload"><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setPhoto(event.target.files?.[0] || null)} required={!photo} /><UploadCloud size={21} /><span>{fileLabel}</span></label>
            {photoPreview && <img className="photo-preview" src={photoPreview} alt="Distributor preview" />}
          </div>
          <div className="otp-panel full-width">
            <div><strong>Verify mobile number</strong><p>Request a one-time code before continuing.</p></div>
            {!otpRequested && <button className="btn" type="button" disabled={!form.mobile || busy} onClick={requestOtp}>{busy ? 'Requesting…' : 'Request OTP'}</button>}
            {otpRequested && <div className="otp-controls"><input aria-label="Six digit OTP" inputMode="numeric" maxLength="6" pattern="[0-9]{6}" value={otpCode} onChange={(event) => setOtpCode(event.target.value)} placeholder="6-digit code" disabled={mobileVerified} /><button className="btn" type="button" disabled={busy || mobileVerified || otpCode.length !== 6} onClick={verifyOtp}>{mobileVerified ? 'Verified' : busy ? 'Checking…' : 'Verify'}</button></div>}
            {developmentOtp && <p className="dev-otp">Local development OTP: <strong>{developmentOtp}</strong></p>}
            {mobileVerified && <p className="success-message">Mobile number verified.</p>}
          </div>
        </div>}

        {step === 1 && <div className="onboarding-grid">
          <label className="onboarding-field">KYC document type<select value={form.kycDocumentType} onChange={(event) => update('kycDocumentType', event.target.value)}><option value="PAN">PAN</option><option value="AADHAAR">Aadhaar</option><option value="PASSPORT">Passport</option><option value="DRIVING_LICENSE">Driving licence</option></select></label>
          {field('Last four characters of document ID', 'kycLastFour', { required: true, minLength: 4, maxLength: 4, pattern: '[0-9A-Za-z]{4}', placeholder: '••••' })}
          <p className="privacy-note full-width">Only the last four characters are stored by this prototype. Full identity documents and KYC verification provider integration are not included in the supplied specification.</p>
        </div>}

        {step === 2 && <div className="onboarding-grid">
          {field('Account holder name', 'bankAccountHolder', { required: true, maxLength: 160, placeholder: 'As registered with the bank' })}
          {field('Bank name', 'bankName', { required: true, maxLength: 120, placeholder: 'Bank name' })}
          {field('IFSC code', 'bankIfsc', { required: true, maxLength: 11, minLength: 11, pattern: '[A-Za-z]{4}0[A-Za-z0-9]{6}', placeholder: '11-character IFSC' })}
          {field('Last four digits of bank account', 'bankAccountLastFour', { required: true, maxLength: 4, minLength: 4, pattern: '[0-9]{4}', placeholder: '••••' })}
          <p className="privacy-note full-width">For safety, this prototype stores only the bank account’s last four digits. Connect a PCI-compliant banking/payment vault before handling full account numbers.</p>
        </div>}

        {step === 3 && <div className="onboarding-grid">
          {field('Maximum per transaction (₹)', 'velocityPerTransaction', { required: true, type: 'number', min: '0.01', step: '0.01', placeholder: '0.00' })}
          {field('Maximum per day (₹)', 'velocityDaily', { required: true, type: 'number', min: form.velocityPerTransaction || '0.01', step: '0.01', placeholder: '0.00' })}
          {field('Pay-in commission (%)', 'commissionPayin', { required: true, type: 'number', min: '0', max: '100', step: '0.01' })}
          {field('Payout commission (%)', 'commissionPayout', { required: true, type: 'number', min: '0', max: '100', step: '0.01' })}
          {field('Bill-payment commission (%)', 'commissionBillPayment', { required: true, type: 'number', min: '0', max: '100', step: '0.01' })}
          <p className="privacy-note full-width">Commission units are treated as percentages and velocity values as INR; confirm the business rules before production use.</p>
        </div>}

        {step === 4 && <div className="agreement-panel">
          <h4>Distributor agreement</h4>
          <p>This submission confirms that the Master Distributor reviewed the distributor’s supplied personal, KYC, and bank details; the configured velocity limits and commissions are correct; and the submitted photo and information may be used for account onboarding under PrimePay policies.</p>
          <p className="muted">Agreement version: {agreementVersion}. Replace this placeholder with the approved legal agreement before production.</p>
          <label className="agreement-check"><input type="checkbox" checked={accepted} onChange={(event) => setAccepted(event.target.checked)} /> I confirm the details and accept the distributor agreement.</label>
        </div>}

        {error && <div className="api-error" role="alert">{error}</div>}
        <div className="onboarding-actions">
          <button type="button" className="btn ghost" onClick={() => { setError(''); setStep((current) => Math.max(0, current - 1)) }} disabled={step === 0 || busy}><ArrowLeft size={16} /> Previous</button>
          <button type="button" className="btn primary" onClick={next} disabled={busy || (step === 4 && !accepted)}>{step === 4 ? busy ? 'Submitting…' : 'Accept & submit' : 'Save & continue'} <ArrowRight size={16} /></button>
        </div>
      </form>
    </div>
  )
}
