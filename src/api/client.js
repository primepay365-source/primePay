const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

async function request(path, { token, ...options } = {}) {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      message = body.detail || body.message || body.error || message
    } catch {
      // Keep the HTTP status message when the response is not JSON.
    }
    throw new Error(message)
  }

  if (response.status === 204) return null
  return response.json()
}

export const api = {
  login: (credentials) => request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  }),
  getAccounts: (token) => request('/accounts', { token }),
  createAccount: (token, account) => request('/accounts', {
    token,
    method: 'POST',
    body: JSON.stringify(account),
  }),
  requestOnboardingOtp: (token, mobile) => request('/onboarding/distributors/otp/request', {
    token,
    method: 'POST',
    body: JSON.stringify({ mobile }),
  }),
  verifyOnboardingOtp: (token, mobile, code) => request('/onboarding/distributors/otp/verify', {
    token,
    method: 'POST',
    body: JSON.stringify({ mobile, code }),
  }),
  submitDistributorOnboarding: (token, onboarding, photo) => {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify(onboarding)], { type: 'application/json' }))
    body.append('photo', photo)
    return request('/onboarding/distributors', { token, method: 'POST', body })
  },
}
