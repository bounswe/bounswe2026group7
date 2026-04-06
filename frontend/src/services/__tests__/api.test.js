import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  getMatchingMentors,
  getAllMentors,
  getSentMentorshipRequests,
  createMentorshipRequest,
} from '../api'

describe('API - Mentorship Features', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(),
    })
    global.fetch.mockClear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getMatchingMentors calls correct endpoint with auth header', async () => {
    localStorage.getItem.mockReturnValue('test-token')
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [{ id: 1, name: 'Mentor A' }]
    })

    const mentors = await getMatchingMentors()
    expect(global.fetch).toHaveBeenCalledWith('/api/matching/mentors', {
      headers: { Authorization: 'Bearer test-token' }
    })
    expect(mentors).toEqual([{ id: 1, name: 'Mentor A' }])
  })

  it('getMatchingMentors appends keyword to query', async () => {
    localStorage.getItem.mockReturnValue('test-token')
    global.fetch.mockResolvedValue({ ok: true, json: async () => [] })
    await getMatchingMentors('react')
    expect(global.fetch).toHaveBeenCalledWith('/api/matching/mentors?keyword=react', {
      headers: { Authorization: 'Bearer test-token' }
    })
  })

  it('getSentMentorshipRequests calls /api/mentorship-requests/sent with auth header', async () => {
    localStorage.getItem.mockReturnValue('test-token-2')
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ content: [] }) })
    await getSentMentorshipRequests()
    expect(global.fetch).toHaveBeenCalledWith('/api/mentorship-requests/sent', {
      headers: { Authorization: 'Bearer test-token-2' }
    })
  })

  it('getAllMentors calls /api/users/mentors with auth header', async () => {
    localStorage.getItem.mockReturnValue('test-token-3')
    global.fetch.mockResolvedValue({ ok: true, json: async () => [] })
    await getAllMentors()
    expect(global.fetch).toHaveBeenCalledWith('/api/users/mentors', {
      headers: { Authorization: 'Bearer test-token-3' }
    })
  })

  it('createMentorshipRequest calls /api/mentorship-requests correctly', async () => {
    localStorage.getItem.mockReturnValue('test-token-4')
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ id: 10 }) })
    const payload = { mentorId: 5, message: 'Hello' }
    const res = await createMentorshipRequest(payload)

    expect(global.fetch).toHaveBeenCalledWith('/api/mentorship-requests', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token-4'
      },
      body: JSON.stringify(payload)
    })
    expect(res.id).toBe(10)
  })
})
