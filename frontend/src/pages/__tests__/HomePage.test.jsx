import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HomePage from '../HomePage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

describe('HomePage Component', () => {
  const mockRecommended = [
    { id: 1, firstName: 'Charlie', expertise: 'Node.js', currentMenteeCount: 1, maxMenteeCapacity: 5 },
    { id: 2, firstName: 'Dana', expertise: 'Docker', currentMenteeCount: 3, maxMenteeCapacity: 3 } // At capacity
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    api.getMatchingMentors.mockResolvedValue(mockRecommended)
    api.getSentMentorshipRequests.mockResolvedValue({ content: [] })
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE' })
  })

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )
  }

  it('for MENTOR role, does not render recommended mentors and instead renders incoming requests', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR' })
    renderComponent()

    expect(screen.queryByText(/recommended for you/i)).not.toBeInTheDocument()
    expect(screen.getAllByText(/incoming requests/i).length).toBeGreaterThan(0)
    expect(screen.getByText(/active mentorships/i)).toBeInTheDocument()
  })

  it('for MENTEE role, renders Recommended section and displays mentor cards', async () => {
    renderComponent()
    expect(screen.queryByText(/incoming requests/i)).not.toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText(/recommended for you/i)).toBeInTheDocument()
      expect(screen.getByText('Charlie')).toBeInTheDocument()
    })
  })

  it('for MENTEE with active mentor (403 error), hides recommended section', async () => {
    api.getMatchingMentors.mockRejectedValue(new Error('403 Active mentor already exists'))
    renderComponent()

    await waitFor(() => {
      expect(api.getMatchingMentors).toHaveBeenCalled()
    })
    expect(screen.queryByText(/recommended for you/i)).not.toBeInTheDocument()
  })

  it('Send Request button disables for at-capacity mentors and shows "At Capacity"', async () => {
    renderComponent()
    await waitFor(() => {
      expect(screen.getByText('Dana')).toBeInTheDocument()
    })

    const danaCard = screen.getByText('Dana').closest('.mentor-card')
    const btn = danaCard.querySelector('button')
    expect(btn).toBeDisabled()
    expect(btn).toHaveTextContent(/at capacity/i)
  })

  it('Send Request button shows "Request Sent" for already requested mentors', async () => {
    api.getSentMentorshipRequests.mockResolvedValue({ content: [{ mentorId: 1, status: 'PENDING' }] })
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Charlie')).toBeInTheDocument()
    })

    const charlieCard = screen.getByText('Charlie').closest('.mentor-card')
    const btn = charlieCard.querySelector('button')
    expect(btn).toBeDisabled()
    expect(btn).toHaveTextContent(/request sent/i)
  })
})
