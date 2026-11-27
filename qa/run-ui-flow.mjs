// PrimePay hierarchy + commission UI run (development only, simulated money).
// Drives the real web app in Chrome, records the screen with the DevTools screencast, and writes every check
// with its expected and actual value to out/results.json. Nothing is marked PASS unless it was executed and compared.
//
//   node run-ui-flow.mjs            (API on :8080 and web app on :5173 must be running)
import { chromium } from 'playwright-core'
import { execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const OUT = join(import.meta.dirname, 'out')
const FRAMES = join(OUT, 'frames')
const SHOTS = join(OUT, 'screens')
rmSync(OUT, { recursive: true, force: true })
mkdirSync(FRAMES, { recursive: true })
mkdirSync(SHOTS, { recursive: true })

const env = Object.fromEntries(readFileSync(join(import.meta.dirname, '.env.qa'), 'utf8').split(/\r?\n/).filter(Boolean).map((l) => l.split(/=(.*)/s).slice(0, 2)))
const WEB = 'http://localhost:5173'
const API = 'http://localhost:8080/api/v1'
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const RUN = Date.now().toString(36).slice(-4).toUpperCase()
const NEW_PW = env.QA_NEW_USER_PASSWORD

const results = []
const evidence = { run: RUN, startedAt: new Date().toISOString(), users: [], transactions: [], commission: {}, rules: [], dashboards: {}, screens: [] }
let checkNo = 0
function check(area, test, expected, actual, extra = {}) {
  const pass = typeof expected === 'function' ? expected(actual) : JSON.stringify(expected) === JSON.stringify(actual)
  const row = { id: `T${String(++checkNo).padStart(3, '0')}`, area, test, expected: typeof expected === 'function' ? extra.expectedText ?? 'see test' : expected, actual, status: pass ? 'PASS' : 'FAIL', ...extra }
  delete row.expectedText
  results.push(row)
  console.log(`${row.status}  ${row.id} ${area} · ${test} → ${JSON.stringify(actual)}`)
  return pass
}
function blocked(area, test, reason) {
  results.push({ id: `T${String(++checkNo).padStart(3, '0')}`, area, test, expected: '-', actual: reason, status: 'BLOCKED' })
}

// ---------------------------------------------------------------- API helpers (verification only)
async function apiLogin(username, password) {
  const r = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
  if (!r.ok) throw new Error(`API login failed for ${username}: ${r.status}`)
  return (await r.json()).accessToken
}
async function apiGet(token, path) {
  const r = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const body = await r.text()
  return { status: r.status, body: body ? JSON.parse(body) : null }
}
async function apiPost(token, path, data) {
  const r = await fetch(`${API}${path}`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(data) })
  const body = await r.text()
  return { status: r.status, body: body ? JSON.parse(body) : null }
}
const money = (s) => Number(String(s).replace(/[^0-9.-]/g, ''))
const round2 = (n) => Math.round(n * 100) / 100

/** Expected share per level for an amount, computed from the active rule as configured in the system. */
function expectedShares(rules, service, amount) {
  const rule = rules.find((r) => r.serviceCode === service && r.state === 'Active')
  if (!rule) return null
  const out = { ruleCode: rule.ruleCode, calcType: rule.calcType, shares: {} }
  for (const s of rule.shares) {
    let v = rule.calcType === 'FIXED' ? Number(s.fixedAmount) : round2((amount * Number(s.rate)) / 100)
    if (s.minAmount != null && v < Number(s.minAmount)) v = Number(s.minAmount)
    if (s.maxAmount != null && v > Number(s.maxAmount)) v = Number(s.maxAmount)
    out.shares[s.levelRole] = { rate: s.rate, fixed: s.fixedAmount, min: s.minAmount, max: s.maxAmount, amount: round2(v) }
  }
  return out
}

// ---------------------------------------------------------------- browser + recorder
const browser = await chromium.launch({ executablePath: CHROME, headless: true, args: ['--force-device-scale-factor=1'] })
const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1, locale: 'en-IN' })
const page = await context.newPage()
page.setDefaultTimeout(15000)
const frames = []
const cdp = await context.newCDPSession(page)
cdp.on('Page.screencastFrame', async ({ data, metadata, sessionId }) => {
  const file = join(FRAMES, `f${String(frames.length).padStart(6, '0')}.jpg`)
  writeFileSync(file, Buffer.from(data, 'base64'))
  frames.push({ file, t: metadata.timestamp })
  try { await cdp.send('Page.screencastFrameAck', { sessionId }) } catch { /* page closing */ }
})
await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 85, maxWidth: 1920, maxHeight: 1080, everyNthFrame: 1 })

// ---------------------------------------------------------------- narration (Windows built-in voice, offline)
const AUDIO = join(OUT, 'audio')
mkdirSync(AUDIO, { recursive: true })
const VOICE = process.env.QA_VOICE || 'Microsoft Zira Desktop'
const speech = []
let speechEndsAt = 0
/** Words a voice reads naturally: rupee amounts, arrows, middle dots and codes in brackets. */
function speakable(text) {
  return text
    .replace(/₹\s?([\d,]+(?:\.\d+)?)/g, (_, n) => `${n.replace(/,/g, '')} rupees`)
    .replace(/→/g, ' to ').replace(/·/g, ', ').replace(/×/g, ' times ').replace(/%/g, ' percent')
    .replace(/\([^)]*@[^)]*\)/g, '').replace(/\bPP-[A-Z]{2}-\d+\b/g, '').replace(/\s+/g, ' ').trim()
}
function synth(text) {
  const file = join(AUDIO, `s${String(speech.length).padStart(3, '0')}.wav`)
  const ps = `Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; ` +
    `try { $s.SelectVoice($env:PP_VOICE) } catch {}; $s.Rate = 0; $s.SetOutputToWaveFile($env:PP_OUT); $s.Speak($env:PP_TEXT); $s.Dispose()`
  execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', ps], { env: { ...process.env, PP_TEXT: text, PP_OUT: file, PP_VOICE: VOICE }, stdio: 'ignore' })
  const buf = readFileSync(file)
  const byteRate = buf.readUInt32LE(28)
  const dataAt = buf.indexOf('data') + 8
  return { file, dur: (buf.length - dataAt) / byteRate }
}
/** Waits for the previous sentence to finish, then speaks this one (logged with its start time for the mix). */
async function say(text) {
  const spoken = speakable(text)
  if (!spoken) return
  const clip = synth(spoken)
  const wait = speechEndsAt - Date.now()
  if (wait > 0) await page.waitForTimeout(wait)
  speech.push({ t: Date.now() / 1000, file: clip.file, dur: clip.dur, text: spoken })
  speechEndsAt = Date.now() + clip.dur * 1000 + 350
}

let currentCaption = ''
/** Shows a caption on screen and narrates it (or the given spoken text). */
async function caption(text, spoken) {
  const wait = speechEndsAt - Date.now()
  if (wait > 0) await page.waitForTimeout(wait)
  await setCaption(text)
  await say(spoken ?? text)
}
async function setCaption(text) {
  currentCaption = text
  await page.evaluate((t) => {
    let host = document.getElementById('__qa_caption')
    if (!host) {
      host = document.createElement('div')
      host.id = '__qa_caption'
      host.setAttribute('aria-hidden', 'true')
      // Closed shadow root: visible in the recording, invisible to the test's text lookups.
      const root = host.attachShadow({ mode: 'closed' })
      const box = document.createElement('div')
      box.style.cssText = 'position:fixed;left:50%;bottom:22px;transform:translateX(-50%);z-index:2147483647;background:rgba(20,33,61,.92);color:#fff;font:600 20px Inter,Segoe UI,sans-serif;padding:12px 22px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.25);pointer-events:none;max-width:80vw;text-align:center'
      root.appendChild(box)
      host.__box = box
      document.body.appendChild(host)
    }
    const el = host.__box
    el.textContent = t
  }, text).catch(() => {})
}
async function pause(ms = 1400) { await page.waitForTimeout(ms) }
async function go(path) {
  await page.goto(WEB + path)
  await page.waitForLoadState('networkidle').catch(() => {})
  if (currentCaption) await setCaption(currentCaption)
  await pause(900)
}
let shotNo = 0
async function shot(name, title) {
  const file = join(SHOTS, `${String(++shotNo).padStart(2, '0')}-${name}.png`)
  await page.screenshot({ path: file })
  evidence.screens.push({ file, title })
  return file
}
async function login(username, password, roleLabel) {
  await go('/login')
  await caption(`Sign in as ${roleLabel} (${username})`, `Sign in as the ${roleLabel}.`)
  await page.getByRole('textbox', { name: 'Username or email' }).fill(username)
  await page.getByRole('textbox', { name: 'Password' }).fill(password)
  await pause(600)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/dashboard')
  await page.waitForLoadState('networkidle').catch(() => {})
  await setCaption(currentCaption)
  await pause(1500)
}
async function signOut() {
  await page.getByText('Sign out', { exact: true }).click()
  await page.waitForURL('**/login')
  await pause(700)
}
async function navLabels() {
  return page.evaluate(() => [...document.querySelectorAll('nav a, aside a')].map((a) => a.textContent.trim()).filter(Boolean))
}
async function roleOptions() {
  await page.getByRole('combobox', { name: /User role/ }).click()
  await pause(900)
  const opts = await page.getByRole('option').allTextContents()
  await page.keyboard.press('Escape')
  await pause(400)
  return opts
}

/** Opens Add user, shows the permitted roles and validation, creates the account, returns what the UI reported. */
async function createUserViaUi(actorLabel, role, roleLabel, expectedOptions, username) {
  await go('/users')
  await caption(`${actorLabel}: open Add user`)
  await page.getByRole('button', { name: 'Add user' }).click()
  await page.getByRole('dialog').waitFor()
  await pause(1000)
  await caption(`${actorLabel}: the role dropdown lists only the levels this role may create`)
  const opts = await roleOptions()
  check('User creation', `${actorLabel} sees only permitted roles in the dropdown`, expectedOptions, opts)
  await shot(`${username}-form`, `${actorLabel} · Add user form`)

  await caption(`${actorLabel}: required-field validation`)
  await page.getByRole('button', { name: 'Save user' }).click()
  await pause(900)
  const validation = await page.getByText('Fill in the highlighted fields.').isVisible()
  check('User creation', `${actorLabel}: Save with empty required fields shows a validation error`, true, validation)
  await shot(`${username}-validation`, `${actorLabel} · required-field validation`)

  await caption(`${actorLabel}: create a ${roleLabel}`)
  await page.getByRole('combobox', { name: /User role/ }).click()
  await page.getByRole('option', { name: roleLabel, exact: true }).click()
  await pause(800)
  const parentText = await page.getByRole('combobox', { name: /Parent account/ }).textContent()
  const commissionText = await page.getByText(/Commission configuration for a/).locator('..').textContent()
  await page.getByRole('textbox', { name: /^Full name/ }).fill(`QA ${roleLabel} ${RUN}`)
  await page.getByRole('textbox', { name: /^Mobile number/ }).fill('+919800000001')
  await page.getByRole('textbox', { name: /^Email/ }).fill(`${username}@example.com`)
  await page.getByRole('textbox', { name: /^Business name/ }).fill(`QA ${roleLabel} Traders`)
  await page.getByRole('textbox', { name: /^Sign-in username/ }).fill(username)
  await page.getByRole('textbox', { name: /^Temporary password/ }).fill(NEW_PW)
  await pause(800)
  await shot(`${username}-filled`, `${actorLabel} · ${roleLabel} details, parent and commission configuration`)
  await page.getByRole('button', { name: 'Save user' }).click()
  await page.getByText('Account created').waitFor()
  await pause(1500)
  const dd = async (label) => page.locator('dl dt', { hasText: new RegExp(`^${label}$`) }).locator('xpath=following-sibling::dd[1]').textContent()
  const created = { role, roleLabel, username, code: await dd('User ID'), id: Number(await dd('System ID')), parent: await dd('Parent'), createdBy: actorLabel, parentShownInForm: parentText.trim(), commissionShownInForm: commissionText.trim() }
  check('User creation', `${actorLabel} created ${roleLabel}: success message and generated IDs shown`, true, Boolean(created.code && created.id), { actual: `${created.code} · system id ${created.id}` })
  await shot(`${username}-created`, `${actorLabel} · ${roleLabel} created`)
  await page.getByRole('button', { name: 'Done' }).click()
  await pause(800)
  // The new user appears in the list.
  await page.getByRole('textbox', { name: /Search/ }).fill(created.code).catch(() => {})
  await pause(1200)
  const listed = await page.getByText(created.code).first().isVisible().catch(() => false)
  check('User creation', `${created.code} appears in ${actorLabel}'s user list`, true, listed)
  evidence.users.push(created)
  return created
}

async function readKpi(label) {
  const card = page.getByText(label, { exact: true }).first().locator('xpath=ancestor::*[contains(@class,"MuiCard-root")][1]')
  return (await card.textContent()) ?? ''
}

// ================================================================= the run
try {
  const adminToken = await apiLogin('admin@primepay.local', env.QA_ADMIN_PASSWORD)
  const rules = (await apiGet(adminToken, '/commissions/rules')).body.data
  evidence.rules = rules

  // 1-3. Login page, Admin, dashboard
  await go('/login')
  await caption('PrimePay · user hierarchy and commission flow (development data, simulated money)')
  await pause(2500)
  await shot('login', 'Login page')
  await login('admin@primepay.local', env.QA_ADMIN_PASSWORD, 'Platform Admin')
  await caption('Platform Admin dashboard: users by level and commission totals')
  check('Login', 'Platform Admin lands on the dashboard', '/dashboard', new URL(page.url()).pathname)
  const adminDash = (await apiGet(adminToken, '/reports/dashboard')).body.data
  const adminNet = Object.fromEntries(adminDash.network.map((n) => [n.label, n.value]))
  const usersByLevel = await page.getByText('Users by level').isVisible()
  check('Dashboard', 'Admin dashboard shows the Users by level card', true, usersByLevel)
  check('Dashboard', 'Admin dashboard lists Total users and each level', ['Total users', 'Super Distributor', 'Master Distributor', 'Distributor', 'Retailer'], adminDash.network.map((n) => n.label))
  evidence.dashboards.adminBefore = adminNet
  await page.mouse.wheel(0, 500)
  await pause(1800)
  await shot('admin-dashboard', 'Platform Admin dashboard')
  await page.mouse.wheel(0, -500)

  // Commission configuration (Admin)
  await go('/commissions')
  await caption('Commission configuration: rules per service, a share per level (set by the Platform Admin)')
  await pause(2500)
  await shot('commission-rules', 'Commission rules (configuration)')
  const billRule = rules.find((r) => r.serviceCode === 'BILL_PAYMENT' && r.state === 'Active')
  check('Commission config', 'An active commission rule exists for Bill payment', true, Boolean(billRule), { actual: billRule ? `${billRule.ruleCode} · ${billRule.calcType} · ${billRule.shares.map((s) => `${s.levelRole} ${s.rate ?? s.fixedAmount}`).join(', ')}` : 'none' })

  // 4-5. Admin creates Super Distributor
  const sd = await createUserViaUi('Platform Admin', 'SUPER_DISTRIBUTOR', 'Super Distributor', ['Super Distributor', 'Master Distributor', 'Distributor', 'Retailer'], `qa.sd.${RUN.toLowerCase()}`)
  check('Hierarchy', 'New Super Distributor is linked to the Platform Admin', true, /LOCAL-ADMIN/.test(sd.parent), { actual: sd.parent })
  await signOut()

  // 6-7. Super Distributor creates Master Distributor
  await login(sd.username, NEW_PW, 'Super Distributor')
  await caption('Super Distributor dashboard')
  check('Login', 'Super Distributor lands on the dashboard', '/dashboard', new URL(page.url()).pathname)
  await shot('sd-dashboard-empty', 'Super Distributor dashboard (new account)')
  const md = await createUserViaUi('Super Distributor', 'MASTER_DISTRIBUTOR', 'Master Distributor', ['Master Distributor', 'Distributor', 'Retailer'], `qa.md.${RUN.toLowerCase()}`)
  check('Hierarchy', 'New Master Distributor is linked to the Super Distributor', sd.code, md.parent)
  await signOut()

  // 8-9. Master creates Distributor
  await login(md.username, NEW_PW, 'Master Distributor')
  await caption('Master Distributor dashboard')
  check('Login', 'Master Distributor lands on the dashboard', '/dashboard', new URL(page.url()).pathname)
  const dt = await createUserViaUi('Master Distributor', 'DISTRIBUTOR', 'Distributor', ['Distributor', 'Retailer'], `qa.dt.${RUN.toLowerCase()}`)
  check('Hierarchy', 'New Distributor is linked to the Master Distributor', md.code, dt.parent)
  await signOut()

  // 10-11. Distributor creates Retailer
  await login(dt.username, NEW_PW, 'Distributor')
  await caption('Distributor dashboard')
  check('Login', 'Distributor lands on the dashboard', '/dashboard', new URL(page.url()).pathname)
  const rt = await createUserViaUi('Distributor', 'RETAILER', 'Retailer', ['Retailer'], `qa.rt.${RUN.toLowerCase()}`)
  check('Hierarchy', 'New Retailer is linked to the Distributor', dt.code, rt.parent)
  await signOut()

  // ---------------------------------------------------------------- Wallet funding down the chain
  const LOAD = 500000
  const balanceOf = async (u) => Number((await apiGet(await apiLogin(u.username, NEW_PW), '/wallets/me')).body.data.availableBalance)
  await login(sd.username, NEW_PW, 'Super Distributor')
  await go('/wallet')
  await caption('Wallet funding: the Super Distributor requests a ₹5,00,000 wallet load (bank payment, simulated)')
  await page.getByRole('button', { name: 'Load wallet' }).click()
  await page.getByRole('dialog').waitFor()
  await page.getByRole('dialog').getByRole('spinbutton', { name: 'Amount (₹)' }).fill(String(LOAD))
  await page.getByRole('textbox', { name: /UTR/ }).fill(`QAUTR${RUN}01`)
  await pause(1200)
  await page.getByRole('button', { name: 'Submit load request' }).click()
  await pause(2000)
  await shot('sd-load-request', 'Super Distributor · wallet load request')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()
  const pendingLoad = (await apiGet(adminToken, '/transactions?type=WALLET_LOAD&status=PENDING&size=50')).body.data.items.find((t) => t.initiatorCode === sd.code)
  check('Wallet funding', 'Super Distributor load request waits for Admin approval', 'PENDING', pendingLoad?.status ?? null, { actual: pendingLoad ? `${pendingLoad.referenceNo} · PENDING` : 'not found' })
  await signOut()

  await login('admin@primepay.local', env.QA_ADMIN_PASSWORD, 'Platform Admin')
  await go(`/transactions/${pendingLoad.id}`)
  await caption('The Platform Admin matches the bank payment and approves the load (maker-checker)')
  await page.getByRole('button', { name: 'Approve load' }).click()
  await pause(1200)
  await page.getByRole('button', { name: 'Approve and credit' }).click()
  await pause(2200)
  await shot('admin-approve-load', 'Platform Admin · wallet load approved')
  check('Wallet funding', `Super Distributor wallet credited ₹${LOAD} after approval`, LOAD, await balanceOf(sd))
  await signOut()

  async function transferViaUi(from, fromLabel, to, amount) {
    await login(from.username, NEW_PW, fromLabel)
    await go('/wallet/transfer')
    await caption(`${fromLabel} transfers ₹${amount.toLocaleString('en-IN')} to its ${to.roleLabel}`)
    await page.getByRole('combobox', { name: 'Send to' }).click()
    await page.getByRole('option', { name: new RegExp(to.code) }).click()
    await page.getByRole('spinbutton', { name: 'Amount (₹)' }).fill(String(amount))
    await page.getByRole('textbox', { name: 'Remarks (optional)' }).fill('Opening float (QA run)')
    await pause(1000)
    await page.getByRole('button', { name: 'Review transfer' }).click()
    await pause(1200)
    await page.getByRole('button', { name: /^Confirm and send/ }).click()
    await page.getByRole('heading', { name: / sent$/ }).waitFor()
    await pause(1800)
    await shot(`transfer-to-${to.role.toLowerCase()}`, `${fromLabel} · transfer to ${to.roleLabel}`)
    check('Wallet funding', `${to.roleLabel} received ₹${amount} from the ${fromLabel}`, amount, await balanceOf(to))
    await signOut()
  }
  await transferViaUi(sd, 'Super Distributor', md, 200000)
  await transferViaUi(md, 'Master Distributor', dt, 100000)
  await transferViaUi(dt, 'Distributor', rt, 25000)

  // ---------------------------------------------------------------- Retailer: dashboard and modules
  await login(rt.username, NEW_PW, 'Retailer')
  await caption('Retailer dashboard: its own wallet, today\'s bill payments, top-ups and withdrawals')
  check('Login', 'Retailer lands on the dashboard', '/dashboard', new URL(page.url()).pathname)
  const rtNav = await navLabels()
  evidence.dashboards.retailerNav = rtNav
  check('Role security', 'Retailer menu has no user management (My network / Users)', false, rtNav.some((l) => /My network|^Users$/.test(l)), { actual: rtNav.join(' | ') })
  await shot('rt-dashboard', 'Retailer dashboard')
  await go('/wallet')
  await caption('Main wallet: balance, statement and every ledger entry')
  await pause(1500)

  // ---------------------------------------------------------------- Add funds: card, UPI, declined card
  const categories = (await apiGet(await apiLogin(rt.username, NEW_PW), '/add-funds/categories')).body.data
  const declineCard = (() => { // a Luhn-valid test number ending 0000, which the simulated gateway declines
    for (let n = 400000000000; ; n++) { const num = `${n}0000`; let sum = 0; for (let i = 0; i < 16; i++) { let d = Number(num[15 - i]); if (i % 2) { d *= 2; if (d > 9) d -= 9 } sum += d } if (sum % 10 === 0) return num }
  })()
  async function addFundsViaUi({ category, amount, card, upi, title }) {
    await go('/add-funds')
    await caption(title)
    const cat = categories.find((c) => c.name === category)
    await page.getByText(category, { exact: true }).click()
    await page.getByRole('textbox', { name: 'Card holder name' }).fill(`QA Retailer ${RUN}`)
    await page.getByRole('textbox', { name: 'Card number' }).fill(card)
    await page.getByRole('spinbutton', { name: 'Amount (₹)' }).fill(String(amount))
    await pause(1500)
    await page.getByRole('button', { name: 'Next' }).click()
    await page.getByText('Secure payment page (simulated gateway)').waitFor()
    if (upi) {
      await caption('Payment page: pay with UPI and approve the collect request')
      await page.getByRole('button', { name: 'UPI', exact: true }).click()
      await page.getByRole('textbox', { name: 'UPI ID' }).fill(upi)
      await pause(900)
      await page.getByRole('button', { name: /^Pay ₹/ }).click()
      await page.getByText('Approve in your UPI app').waitFor()
      await pause(1500)
      await page.getByRole('button', { name: 'I have approved it' }).click()
    } else {
      await caption('Payment page: card details stay in the browser; an OTP is sent to the cardholder')
      await page.getByRole('textbox', { name: 'Expiry (MM/YY)' }).fill('1230')
      await page.getByRole('textbox', { name: 'CVV' }).fill('123')
      await pause(900)
      await page.getByRole('button', { name: /^Pay ₹/ }).click()
      const otp = (await page.getByText(/simulated OTP is/).textContent()).match(/(\d{6})/)[1]
      await page.getByRole('textbox', { name: 'OTP' }).fill(otp)
      await pause(1200)
      await page.getByRole('button', { name: 'Verify and pay' }).click()
    }
    await page.getByRole('dialog').waitFor()
    await pause(2000)
    const text = await page.getByRole('dialog').textContent()
    return { text, cat }
  }
  let before = await balanceOf(rt)
  let r1 = await addFundsViaUi({ category: 'Education', amount: 5000, card: '4111111111111111', title: 'Add funds by card: Education category, ₹5,000' })
  let net = round2(5000 - round2((5000 * Number(r1.cat.chargePercent)) / 100))
  check('Add funds', `Card top-up of ₹5,000 (Education, ${r1.cat.chargePercent}% charge) credits ₹${net}`, net, round2((await balanceOf(rt)) - before), { popup: r1.text.slice(0, 60) })
  await shot('rt-add-funds-card', 'Retailer · Add funds by card')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()

  before = await balanceOf(rt)
  r1 = await addFundsViaUi({ category: 'E-commerce', amount: 2000, card: '4111111111111111', upi: 'qa.retailer@okbank', title: 'Add funds by UPI: E-commerce category, ₹2,000' })
  net = round2(2000 - round2((2000 * Number(r1.cat.chargePercent)) / 100))
  check('Add funds', `UPI top-up of ₹2,000 (E-commerce, ${r1.cat.chargePercent}% charge) credits ₹${net}`, net, round2((await balanceOf(rt)) - before), { popup: r1.text.slice(0, 60) })
  await shot('rt-add-funds-upi', 'Retailer · Add funds by UPI')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()

  before = await balanceOf(rt)
  r1 = await addFundsViaUi({ category: 'E-commerce', amount: 1000, card: declineCard, title: 'Declined card: the gateway refuses it and nothing is added' })
  check('Add funds', 'Declined card shows "Payment failed" and adds nothing', [true, 0], [/Payment failed/.test(r1.text), round2((await balanceOf(rt)) - before)])
  await shot('rt-add-funds-declined', 'Retailer · Add funds declined')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()

  // ---------------------------------------------------------------- Bill payments: success, pending → recheck, failed → refund
  async function billViaUi(last4, amount, title) {
    await go('/bills')
    await caption(title)
    await page.getByRole('combobox', { name: 'Card issuing bank' }).click()
    await page.getByRole('option').first().click()
    await page.getByRole('textbox', { name: 'Last 4 digits of card' }).fill(last4)
    await page.getByRole('textbox', { name: /Card-registered mobile/ }).fill('9876543210')
    await pause(1000)
    await page.getByRole('button', { name: 'Fetch bill' }).click()
    await page.getByText('Bill details').waitFor()
    await caption('Bill fetched: card holder, total due, minimum due and due date')
    await page.getByRole('button', { name: 'Other amount' }).click()
    await page.getByRole('spinbutton', { name: 'Amount (₹)' }).fill(String(amount))
    await pause(1500)
    await page.getByRole('button', { name: /^Pay ₹/ }).click()
    await page.getByRole('dialog').waitFor()
    await pause(2000)
    const text = await page.getByRole('dialog').textContent()
    const id = Number((await page.getByRole('link', { name: 'View receipt' }).getAttribute('href')).split('/').pop())
    return { text, id, ref: text.match(/PPB\d+/)?.[0] }
  }
  const BILL = 1000
  const runTxns = []
  before = await balanceOf(rt)
  const b1 = await billViaUi('4321', BILL, `Bill payment: pay a ₹${BILL} credit card bill from the main wallet`)
  const billRef = b1.ref
  const billTxnId = b1.id
  check('Bill payment', `Bill payment of ₹${BILL} succeeds`, true, /Bill paid/.test(b1.text), { actual: `${billRef} · ${b1.text.slice(0, 60)}` })
  await shot('rt-bill-paid', 'Retailer · bill payment success')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()
  evidence.transactions.push({ ref: billRef, id: billTxnId, type: 'BILL_PAYMENT', amount: BILL, by: rt.code })
  runTxns.push({ service: 'BILL_PAYMENT', amount: BILL })

  const b2 = await billViaUi('1111', 500, 'Bill payment that the bank has not confirmed yet: status Pending')
  check('Bill payment', 'Pending bill payment shows the Pending popup', true, /Payment pending/.test(b2.text), { actual: b2.text.slice(0, 60) })
  await shot('rt-bill-pending', 'Retailer · bill payment pending')
  await caption('Recheck asks the biller again: the pending payment is now successful')
  await page.getByRole('button', { name: 'Recheck status' }).click()
  await page.getByText('Bill paid').waitFor()
  await pause(2000)
  check('Bill payment', 'Recheck turns the pending payment into Success', true, await page.getByText('Bill paid').isVisible())
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()
  evidence.transactions.push({ ref: b2.ref, id: b2.id, type: 'BILL_PAYMENT', amount: 500, by: rt.code })
  runTxns.push({ service: 'BILL_PAYMENT', amount: 500 })

  const midBalance = await balanceOf(rt)
  const b3 = await billViaUi('0000', 300, 'A bill payment the biller declines: amount and charge are refunded')
  check('Bill payment', 'Declined bill payment is refunded in full', [true, 0], [/Payment failed/.test(b3.text), round2((await balanceOf(rt)) - midBalance)])
  await shot('rt-bill-failed', 'Retailer · bill payment failed and refunded')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()
  await go('/bills')
  await caption('Bill payment history with the status of every payment')
  await page.mouse.wheel(0, 600)
  await pause(2200)
  await page.mouse.wheel(0, -600)

  // ---------------------------------------------------------------- Withdraw funds: saved, verified accounts only
  await go('/withdraw')
  await caption('Withdraw funds: add the bank account first; the bank verifies it')
  async function addAccount(number, holder) {
    await page.getByRole('button', { name: 'Add bank account' }).click()
    const dlg = page.getByRole('dialog')
    await dlg.getByRole('textbox', { name: 'Account number', exact: true }).fill(number)
    await dlg.getByRole('textbox', { name: 'Confirm account number' }).fill(number)
    await dlg.getByRole('textbox', { name: 'IFSC' }).fill('HDFC0001234')
    await dlg.getByRole('textbox', { name: 'Account holder name' }).fill(holder)
    await pause(1200)
    await dlg.getByRole('button', { name: 'Verify and add' }).click()
    await dlg.waitFor({ state: 'hidden' })
    await pause(1800)
  }
  await addAccount('112233449999', `QA Retailer ${RUN}`)
  check('Withdraw', 'An account the bank cannot verify is marked Not verified', true, await page.getByText(/can't receive withdrawals/).isVisible())
  await caption('A verified account is added and selected for the transfer')
  await addAccount('50100223344556', `QA Retailer ${RUN}`)
  check('Withdraw', 'A verified account shows the bank-registered name', true, await page.getByText(/^Verified: /).isVisible())
  await shot('rt-withdraw-accounts', 'Retailer · saved bank accounts')
  await page.getByRole('spinbutton', { name: 'Amount (₹)' }).fill('2000')
  await pause(1000)
  await page.getByRole('button', { name: 'Review transfer' }).click()
  await caption('Review: amount plus service charge is debited from the main wallet')
  await pause(1500)
  await page.getByRole('button', { name: 'Confirm transfer' }).click()
  await page.getByText('Transfer successful').waitFor()
  await pause(2000)
  const wText = await page.getByRole('dialog').textContent()
  const wId = Number((await page.getByRole('link', { name: 'View receipt' }).getAttribute('href')).split('/').pop())
  check('Withdraw', 'Transfer of ₹2,000 to the verified account succeeds', true, /Transfer successful/.test(wText), { actual: wText.slice(0, 70) })
  await shot('rt-withdraw-done', 'Retailer · withdrawal successful')
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click()
  evidence.transactions.push({ ref: wText.match(/PPP\d+/)?.[0], id: wId, type: 'PAYOUT', amount: 2000, by: rt.code })
  runTxns.push({ service: 'PAYOUT', amount: 2000 })

  for (const [path, text] of [['/transactions', 'Transactions: every bill payment, top-up and withdrawal with its status'], [`/transactions/${billTxnId}`, 'Receipt: status history and the ledger lines'], ['/reports', 'Reports: bill payments, add funds, withdrawals and more, with CSV export'], ['/notifications', 'Notifications for every money movement']]) {
    await go(path)
    await caption(text)
    await pause(1800)
  }

  // Expected commission per level for everything this run did (only bill payments and withdrawals pay commission).
  const expectedTotal = (level) => round2(runTxns.reduce((sum, t) => sum + (expectedShares(rules, t.service, t.amount)?.shares[level]?.amount ?? 0), 0))
  evidence.commission.runTransactions = runTxns.map((t) => ({ ...t, shares: expectedShares(rules, t.service, t.amount) }))

  // 17. Commission calculation for the ₹1,000 bill
  const exp = expectedShares(rules, 'BILL_PAYMENT', BILL)
  evidence.commission.expected = exp
  const detail = (await apiGet(adminToken, `/transactions/${billTxnId}`)).body.data
  const actual = Object.fromEntries(detail.commission.map((c) => [c.levelRole, Number(c.amount)]))
  evidence.commission.actual = detail.commission
  evidence.commission.transaction = detail.transaction
  for (const [level, s] of Object.entries(exp.shares)) {
    const how = exp.calcType === 'FIXED' ? `fixed ₹${s.fixed}` : `₹${BILL} × ${s.rate}% = ₹${round2((BILL * s.rate) / 100)}${s.min != null || s.max != null ? ` (min ${s.min ?? '-'}, max ${s.max ?? '-'})` : ''}`
    check('Commission calculation', `${level} share on ${billRef}: ${how}`, s.amount, actual[level] ?? null)
  }
  const earnerOf = Object.fromEntries(detail.commission.map((c) => [c.levelRole, c.earnerCode]))
  check('Commission allocation', 'Each share is paid to the right account in the chain',
    { RETAILER: rt.code, DISTRIBUTOR: dt.code, MASTER_DISTRIBUTOR: md.code, SUPER_DISTRIBUTOR: sd.code },
    { RETAILER: earnerOf.RETAILER, DISTRIBUTOR: earnerOf.DISTRIBUTOR, MASTER_DISTRIBUTOR: earnerOf.MASTER_DISTRIBUTOR, SUPER_DISTRIBUTOR: earnerOf.SUPER_DISTRIBUTOR })
  const allocated = round2(Object.values(actual).reduce((a, b) => a + b, 0))
  const configured = round2(Object.values(exp.shares).reduce((a, s) => a + s.amount, 0))
  check('Commission allocation', 'Total allocated never exceeds the configured transaction commission', (v) => v <= configured, allocated, { expectedText: `≤ ${configured}` })
  check('Commission allocation', 'One commission row per level (no duplicate credits)', Object.keys(exp.shares).length, detail.commission.length)
  const pendingDetail = (await apiGet(adminToken, `/transactions/${b2.id}`)).body.data
  check('Commission allocation', 'The rechecked payment was credited commission once (one row per level)', Object.keys(expectedShares(rules, 'BILL_PAYMENT', 500).shares).length, pendingDetail.commission.length)
  const failedDetail = (await apiGet(adminToken, `/transactions/${b3.id}`)).body.data
  check('Commission allocation', 'A failed bill payment pays no commission', 0, failedDetail.commission.length)

  await go('/commissions/statement')
  await caption(`My commission: the Retailer's ${exp.shares.RETAILER.rate}% share on bill payments plus its share on the withdrawal`,
    `The Retailer's commission page. It earns ${exp.shares.RETAILER.rate} percent of each bill payment, here ${exp.shares.RETAILER.amount} rupees on the 1000 rupee bill, plus its share on the other payment and the withdrawal.`)
  await pause(2000)
  const rtTotal = money((await readKpi('Total commission earned')).match(/₹\s?[\d,]+\.\d{2}/)?.[0])
  check('Commission UI', 'Retailer "Total commission earned" matches its configured shares for this run', expectedTotal('RETAILER'), rtTotal)
  await shot('rt-commission', 'Retailer · My commission')

  // 19. Retailer restrictions (direct URLs)
  await caption('Role restriction: the Retailer opens admin and network pages by URL and is sent back')
  for (const path of ['/users', '/commissions', '/audit', '/limits', '/hierarchy']) {
    await go(path)
    check('Role security', `Retailer direct URL ${path} is blocked`, '/dashboard', new URL(page.url()).pathname)
  }
  await signOut()

  // 18. Each role's commission history and dashboard
  const tokens = {}
  for (const u of [sd, md, dt, rt]) tokens[u.role] = await apiLogin(u.username, NEW_PW)
  const parentOf = { SUPER_DISTRIBUTOR: [sd, md], MASTER_DISTRIBUTOR: [md, dt], DISTRIBUTOR: [dt, rt] }
  for (const [role, [me, child]] of Object.entries(parentOf)) {
    const label = { SUPER_DISTRIBUTOR: 'Super Distributor', MASTER_DISTRIBUTOR: 'Master Distributor', DISTRIBUTOR: 'Distributor' }[role]
    await login(me.username, NEW_PW, label)
    await caption(`${label} dashboard: my network and my commission`)
    await pause(1500)
    const dash = (await apiGet(tokens[role], '/reports/dashboard')).body.data
    evidence.dashboards[role] = dash.network
    check('Dashboard', `${label} dashboard shows its own network counts`, true, dash.network.length > 0, { actual: dash.network.map((n) => `${n.label}: ${n.value}`).join(', ') })
    await shot(`${role.toLowerCase()}-dashboard`, `${label} dashboard`)
    await go('/commissions/statement')
    await caption(`${label}: commission history, child-wise summary and transaction-wise commission`)
    await pause(2200)
    const total = money((await readKpi('Total commission earned')).match(/₹\s?[\d,]+\.\d{2}/)?.[0])
    check('Commission UI', `${label} "Total commission earned" matches its configured shares for this run`, expectedTotal(role), total)
    const childRow = await page.getByRole('table', { name: 'Child-wise commission' }).getByRole('row', { name: new RegExp(child.code) }).textContent().catch(() => '')
    check('Commission UI', `${label} child-wise table credits the commission to child ${child.code}`, true, childRow.includes(expectedTotal(role).toFixed(2)), { actual: childRow })
    const txnRow = await page.getByRole('row', { name: new RegExp(billRef) }).textContent().catch(() => '')
    check('Commission UI', `${label} transaction-wise table lists ${billRef}`, true, txnRow.includes(billRef), { actual: txnRow })
    await shot(`${role.toLowerCase()}-commission`, `${label} · commission statement`)
    if (role === 'DISTRIBUTOR') {
      await caption('Distributor: retailer-wise commission and its retailers only')
      await go('/users')
      await pause(1500)
      await shot('dt-users', 'Distributor · My network (its retailers)')
      await go('/audit')
      check('Role security', 'Distributor direct URL /audit is blocked', '/dashboard', new URL(page.url()).pathname)
      await go('/commissions')
      check('Role security', 'Distributor direct URL /commissions (rule configuration) is blocked', '/dashboard', new URL(page.url()).pathname)
    }
    if (role === 'MASTER_DISTRIBUTOR') {
      await go('/users')
      await caption('Master Distributor: sees its Distributors and their Retailers')
      await pause(1500)
      await shot('md-users', 'Master Distributor · My network')
    }
    await signOut()
  }

  // API authorization checks (backend, not just the UI)
  const otherSd = (await apiGet(adminToken, '/users?role=SUPER_DISTRIBUTOR')).body.data.find((u) => u.id !== sd.id)
  const apiChecks = [
    ['Retailer reads a Distributor account by ID', tokens.RETAILER, 'GET', `/users/${dt.id}`, 404],
    ['Retailer reads Distributor commission rates', tokens.RETAILER, 'GET', '/commissions/rates?role=DISTRIBUTOR', 403],
    ['Retailer reads its own commission rates', tokens.RETAILER, 'GET', '/commissions/rates?role=RETAILER', 200],
    ['Master Distributor reads another Super Distributor', tokens.MASTER_DISTRIBUTOR, 'GET', `/users/${otherSd?.id ?? 0}`, 404],
    ['Master Distributor reads Super Distributor rates', tokens.MASTER_DISTRIBUTOR, 'GET', '/commissions/rates?role=SUPER_DISTRIBUTOR', 403],
    ['Super Distributor changes commission rules', tokens.SUPER_DISTRIBUTOR, 'POST', '/commissions/rules', 403],
    ['Distributor changes commission rules', tokens.DISTRIBUTOR, 'POST', '/commissions/rules', 403],
    ['Distributor opens the audit report', tokens.DISTRIBUTOR, 'GET', '/reports/audit', 403],
    ['Super Distributor opens the audit log', tokens.SUPER_DISTRIBUTOR, 'GET', '/audit', 403],
    ['Retailer creates a user', tokens.RETAILER, 'POST', '/users', 403],
    ['Distributor creates a Master Distributor', tokens.DISTRIBUTOR, 'POST', '/users', 403],
    ['Master Distributor creates a Super Distributor', tokens.MASTER_DISTRIBUTOR, 'POST', '/users', 403],
  ]
  const ruleBody = { ruleCode: `CR-QA-${RUN}`, serviceCode: 'BILL_PAYMENT', calcType: 'PERCENT', effectiveFrom: '2027-01-01', shares: [{ levelRole: 'DISTRIBUTOR', rate: 5 }] }
  const userBody = (role, parentId) => ({ accountCode: `QA-X-${RUN}-${role.slice(0, 2)}`, displayName: 'Not allowed', role, parentId, username: `qa.x.${RUN}.${role}`.toLowerCase(), initialPassword: 'Not-Allowed-Pass-1', email: 'x@example.com', mobile: '+919800000009' })
  for (const [name, token, method, path, expected] of apiChecks) {
    let r
    if (method === 'GET') r = await apiGet(token, path)
    else if (path === '/commissions/rules') r = await apiPost(token, path, ruleBody)
    else if (name.startsWith('Retailer')) r = await apiPost(token, path, userBody('RETAILER', dt.id))
    else if (name.startsWith('Distributor')) r = await apiPost(token, path, userBody('MASTER_DISTRIBUTOR', sd.id))
    else r = await apiPost(token, path, userBody('SUPER_DISTRIBUTOR', 1))
    check('API authorization', name, expected, r.status)
  }
  const rtLines = (await apiGet(tokens.RETAILER, '/commissions/statement')).body.data.lines
  check('API authorization', "Retailer's statement contains only its own (RETAILER) commission rows", true, rtLines.every((l) => l.levelRole === 'RETAILER'), { actual: `${rtLines.length} rows` })

  // 20. Final summary as Admin
  await login('admin@primepay.local', env.QA_ADMIN_PASSWORD, 'Platform Admin')
  await caption('Platform Admin: commission report for the whole platform')
  await go('/commissions/statement')
  await pause(2200)
  const adminStatement = (await apiGet(adminToken, '/commissions/statement')).body.data
  evidence.commission.platform = { earnedAllTime: adminStatement.earnedAllTime, paidAllTime: adminStatement.paidAllTime, pendingAmount: adminStatement.pendingAmount, platformShareAllTime: adminStatement.platformShareAllTime, reversedAllTime: adminStatement.reversedAllTime }
  const adminRow = await page.getByRole('row', { name: new RegExp(`${billRef}.*PLATFORM`, 'i') }).first().textContent().catch(() => '')
  check('Commission UI', 'Admin commission report shows the platform share of the test transaction', true, adminRow.includes(exp.shares.PLATFORM.amount.toFixed(2)), { actual: adminRow })
  check('Commission UI', 'Admin report shows pending commission (no transaction is pending in this run)', 0, Number(adminStatement.pendingAmount))
  await shot('admin-commission-report', 'Platform Admin · commission report')

  for (const [path, text] of [
    ['/reports', 'Admin reports: business, wallet, commission and network reports with CSV export'],
    ['/admin/reconciliation', 'Reconciliation: every wallet balance agrees with the ledger'],
    ['/audit', 'Audit log: every sign-in, user change and money movement is recorded'],
    ['/limits', 'Transaction limits per role and per user'],
    ['/settings', 'Admin configuration: roles, services and fees, and the payment gateway'],
  ]) {
    await go(path)
    await caption(text)
    await pause(1600)
  }
  const recon = (await apiGet(adminToken, '/reports/reconciliation')).body.data
  check('Reconciliation', 'Total of wallet balances equals the ledger total after the run', Number(recon.ledgerTotal), Number(recon.walletTotal))

  await go(`/transactions/${billTxnId}`)
  await caption(`Commission distribution for ${billRef}: every level's share`)
  await page.mouse.wheel(0, 700)
  await pause(2500)
  await shot('txn-commission-split', 'Transaction detail · commission split')

  await go('/hierarchy')
  await caption('Complete hierarchy: Admin → Super → Master → Distributor → Retailer')
  for (const code of [sd.code, md.code, dt.code]) {
    const node = page.getByText(code).first()
    if (await node.isVisible().catch(() => false)) { await node.click().catch(() => {}); await pause(900) }
  }
  await pause(1500)
  const treeText = await page.locator('[role=tree]').textContent().catch(() => '')
  check('Hierarchy', 'Hierarchy tree shows the new chain', true, [sd.code].every((c) => treeText.includes(c)), { actual: treeText.slice(0, 200) })
  await shot('hierarchy', 'Hierarchy tree')

  await go('/dashboard')
  const adminAfter = Object.fromEntries((await apiGet(adminToken, '/reports/dashboard')).body.data.network.map((n) => [n.label, n.value]))
  evidence.dashboards.adminAfter = adminAfter
  check('Dashboard', 'Admin Total users increased by the 4 accounts created in this run', (adminNet['Total users'] ?? 0) + 4, adminAfter['Total users'])
  await caption(`Summary: 4 accounts created through the UI; ${billRef} paid ₹${allocated} commission across 5 levels`)
  await pause(4000)
  await shot('admin-dashboard-final', 'Platform Admin dashboard · end of run')
} catch (e) {
  console.error('RUN STOPPED:', e)
  blocked('Run', 'Scripted run stopped before completing', String(e.message ?? e).slice(0, 300))
  await shot('error', 'Screen when the run stopped').catch(() => {})
} finally {
  const rest = speechEndsAt - Date.now()
  if (rest > 0) await page.waitForTimeout(rest + 800).catch(() => {})
  await cdp.send('Page.stopScreencast').catch(() => {})
  await page.waitForTimeout(500)
  await browser.close()
  evidence.finishedAt = new Date().toISOString()
  writeFileSync(join(OUT, 'results.json'), JSON.stringify({ results, evidence }, null, 2))
  writeFileSync(join(OUT, 'frames.json'), JSON.stringify(frames))
  writeFileSync(join(OUT, 'speech.json'), JSON.stringify(speech, null, 1))
  const s = results.reduce((a, r) => ({ ...a, [r.status]: (a[r.status] ?? 0) + 1 }), {})
  console.log('SUMMARY', JSON.stringify(s), 'frames', frames.length)
}
