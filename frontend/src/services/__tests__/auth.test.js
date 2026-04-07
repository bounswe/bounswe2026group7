import { describe, it, expect, vi } from 'vitest'
import {
  registerUser,
  loginUser,
  forgotPassword,
  resetPassword,
  verifyEmail,
  resendVerification,
  validateResetToken,
} from '../api'

function mockFetchSuccess(data) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: true,
    text: () => Promise.resolve(JSON.stringify(data)),
  })
}

function mockFetchFailure(body) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: false,
    statusText: 'Bad Request',
    text: () => Promise.resolve(JSON.stringify(body)),
  })
}

describe('API - Auth', () => {

describe('registerUser', () => {
  it('sends POST to /api/auth/register with correct body', async () => {
    mockFetchSuccess({})
    await registerUser({ firstName: 'Ali', lastName: 'Veli', email: 'a@b.com', password: 'Pass1234', isMentor: false })
    expect(fetch).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ firstName: 'Ali', lastName: 'Veli', email: 'a@b.com', password: 'Pass1234', isMentor: false }),
    }))
  })

  it('throws error message on failure', async () => {
    mockFetchFailure({ message: 'Email already in use' })
    await expect(registerUser({ firstName: 'Ali', lastName: 'Veli', email: 'a@b.com', password: 'Pass1234', isMentor: false }))
      .rejects.toThrow('Email already in use')
  })
})

describe('loginUser', () => {
  it('sends POST to /api/auth/login with email and password', async () => {
    mockFetchSuccess({ sessionToken: 'tok', role: 'MENTEE', userId: 1 })
    await loginUser({ email: 'a@b.com', password: 'Pass1234' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: 'a@b.com', password: 'Pass1234' }),
    }))
  })

  it('returns sessionToken, role, and userId on success', async () => {
    mockFetchSuccess({ sessionToken: 'tok', role: 'MENTOR', userId: 42 })
    const data = await loginUser({ email: 'a@b.com', password: 'Pass1234' })
    expect(data).toEqual({ sessionToken: 'tok', role: 'MENTOR', userId: 42 })
  })

  it('throws error message on invalid credentials', async () => {
    mockFetchFailure({ message: 'Invalid credentials' })
    await expect(loginUser({ email: 'a@b.com', password: 'wrong' }))
      .rejects.toThrow('Invalid credentials')
  })
})

describe('forgotPassword', () => {
  it('sends POST to /api/auth/forgot-password with email', async () => {
    mockFetchSuccess({})
    await forgotPassword({ email: 'a@b.com' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/forgot-password', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: 'a@b.com' }),
    }))
  })

  it('throws error message on failure', async () => {
    mockFetchFailure({ message: 'User not found' })
    await expect(forgotPassword({ email: 'a@b.com' }))
      .rejects.toThrow('User not found')
  })
})

describe('resetPassword', () => {
  it('sends POST to /api/auth/reset-password with token and newPassword', async () => {
    mockFetchSuccess({})
    await resetPassword({ token: 'abc123', newPassword: 'NewPass1' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/reset-password', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ token: 'abc123', newPassword: 'NewPass1' }),
    }))
  })

  it('throws error message on failure', async () => {
    mockFetchFailure({ message: 'Token expired' })
    await expect(resetPassword({ token: 'abc123', newPassword: 'NewPass1' }))
      .rejects.toThrow('Token expired')
  })
})

describe('verifyEmail', () => {
  it('sends GET to /api/auth/verify-email with token as query param', async () => {
    mockFetchSuccess({})
    await verifyEmail({ token: 'abc123' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/verify-email?token=abc123')
  })

  it('encodes special characters in token', async () => {
    mockFetchSuccess({})
    await verifyEmail({ token: 'abc+def==/' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/verify-email?token=abc%2Bdef%3D%3D%2F')
  })

  it('throws on invalid token', async () => {
    mockFetchFailure({ message: 'Invalid or expired token' })
    await expect(verifyEmail({ token: 'bad' }))
      .rejects.toThrow('Invalid or expired token')
  })
})

describe('resendVerification', () => {
  it('sends POST to /api/auth/resend-verification with email', async () => {
    mockFetchSuccess({})
    await resendVerification({ email: 'a@b.com' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/resend-verification', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: 'a@b.com' }),
    }))
  })

  it('throws on failure', async () => {
    mockFetchFailure({ message: 'Too many requests' })
    await expect(resendVerification({ email: 'a@b.com' }))
      .rejects.toThrow('Too many requests')
  })
})

describe('validateResetToken', () => {
  it('sends GET to /api/auth/validate-reset-token with token as query param', async () => {
    mockFetchSuccess({})
    await validateResetToken({ token: 'abc123' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/validate-reset-token?token=abc123')
  })

  it('encodes special characters in token', async () => {
    mockFetchSuccess({})
    await validateResetToken({ token: 'abc+def==/' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/validate-reset-token?token=abc%2Bdef%3D%3D%2F')
  })

  it('throws on expired token', async () => {
    mockFetchFailure({ message: 'Token expired' })
    await expect(validateResetToken({ token: 'expired' }))
      .rejects.toThrow('Token expired')
  })
})

describe('handleResponse error fallbacks', () => {
  it('uses error field when message is absent', async () => {
    mockFetchFailure({ error: 'Something went wrong' })
    await expect(loginUser({ email: 'a@b.com', password: 'x' }))
      .rejects.toThrow('Something went wrong')
  })

  it('falls back to statusText when body is not JSON', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      statusText: 'Service Unavailable',
      text: () => Promise.resolve('not json'),
    })
    await expect(loginUser({ email: 'a@b.com', password: 'x' }))
      .rejects.toThrow('Service Unavailable')
  })
})

})
