import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HomePage from '../HomePage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

describe('HomePage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Shared mocks needed by all renders (NotificationBell + both branches)
    api.getNotifications.mockResolvedValue([])
    api.getActiveMentorships.mockResolvedValue([])
    api.getMatchingMentors.mockResolvedValue([])
    api.getReceivedMentorshipRequests.mockResolvedValue({ content: [] })
    api.getOwnProfile.mockResolvedValue({ currentMenteeCount: 2, maxMenteeCapacity: 5 })
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE', firstName: 'Test', lastName: 'User' })
  })

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )
  }

  it('for MENTOR role, renders Dashboard with Incoming Requests and Active Mentorships sections', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR', firstName: 'Test', lastName: 'Mentor' })
    renderComponent()

    expect(screen.getByText(/dashboard/i)).toBeInTheDocument()
    expect(screen.getByText(/incoming requests/i)).toBeInTheDocument()
    expect(screen.getByText(/active mentorships/i)).toBeInTheDocument()
  })

  it('for MENTOR, shows mentor stats row', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR', firstName: 'Test', lastName: 'Mentor' })
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/active mentees/i)).toBeInTheDocument()
      expect(screen.getByText(/capacity/i)).toBeInTheDocument()
    })
  })

  it('for MENTOR with a pending request, shows the mentee name in Incoming Requests', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR', firstName: 'Test', lastName: 'Mentor' })
    api.getReceivedMentorshipRequests.mockResolvedValue({
      content: [{ id: 1, status: 'PENDING', menteeFirstName: 'Bob', createdAt: new Date().toISOString(), message: 'Hi!' }]
    })
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
  })

  it('for MENTOR with an active mentorship, shows the mentee in Active Mentorships', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR', firstName: 'Test', lastName: 'Mentor' })
    api.getActiveMentorships.mockResolvedValue([{
      id: 1,
      status: 'ACTIVE',
      menteeFirstName: 'Carol',
      menteeId: 5,
      duration: 6,
      startDate: new Date().toISOString(),
    }])
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Carol')).toBeInTheDocument()
    })
  })

  it('for MENTEE with no active mentor, shows prompt to Go to Explore', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/find your perfect mentor/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /go to explore/i })).toBeInTheDocument()
  })

  it('for MENTEE with an active mentor, shows the active mentorship hero card', async () => {
    api.getActiveMentorships.mockResolvedValue([{
      id: 1,
      status: 'ACTIVE',
      mentorFirstName: 'Alice',
      mentorId: 10,
      duration: 3,
      startDate: new Date(Date.now() - 30 * 86400000).toISOString(),
      endDate: new Date(Date.now() + 60 * 86400000).toISOString(),
    }])
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeInTheDocument()
      expect(screen.getByText(/your mentor/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /view mentor profile/i })).toBeInTheDocument()
  })

  it('for MENTEE, does not render incoming requests or active mentorships sections', () => {
    renderComponent()

    expect(screen.queryByText(/incoming requests/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/dashboard/i)).not.toBeInTheDocument()
  })

  it('for MENTOR, does not render find-your-mentor prompt', () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR', firstName: 'Test', lastName: 'Mentor' })
    renderComponent()

    expect(screen.queryByText(/find your perfect mentor/i)).not.toBeInTheDocument()
  })
})
