const BASE_URL = '/api'

async function handleResponse(res) {
  if (res.ok) return res.json()
  let message
  try {
    const body = await res.json()
    message = body.message || body.error || JSON.stringify(body)
  } catch {
    message = res.statusText
  }
  throw new Error(message)
}

export async function registerUser({ firstName, lastName, email, password, isMentor }) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firstName, lastName, email, password, isMentor }),
  })
  return handleResponse(res)
}

export async function loginUser({ email, password }) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  return handleResponse(res)
}
