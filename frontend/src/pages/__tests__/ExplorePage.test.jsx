import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import ExplorePage from '../ExplorePage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

describe('ExplorePage Component', () => {
  const mockMentors = [
    { id: 1, firstName: 'Bob', field: 'Software', expertise: 'React', interests: ['Frontend', 'UI'], currentMenteeCount: 0, maxMenteeCapacity: 3 },
    { id: 2, firstName: 'Alice', field: 'Data Science', expertise: 'Python', interests: ['AI/ML'], currentMenteeCount: 2, maxMenteeCapacity: 2 } // at capacity
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    api.getAllMentors.mockResolvedValue(mockMentors)
    api.getSentMentorshipRequests.mockResolvedValue({ content: [] })
    api.getMatchingMentors.mockResolvedValue([])
    api.getMatchingMentees.mockResolvedValue([])
    api.getActiveMentorships.mockResolvedValue([])
    api.getNotifications.mockResolvedValue([])
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE' })
  })

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <MentorshipProvider>
          <ExplorePage />
        </MentorshipProvider>
      </MemoryRouter>
    )
  }

  it('for MENTOR role, renders Find Mentees page and does not render Send Request button', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR' })
    renderComponent()

    expect(screen.getByText('Find Mentees')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /send request/i })).not.toBeInTheDocument()
  })

  it('shows loading state and then renders mentor cards', async () => {
    renderComponent()
    expect(screen.getByText('Loading...')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })
  })

  it('Send Request button disables and shows "At Capacity" when mentor is full', async () => {
    renderComponent()
    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })
    const aliceCard = screen.getByText('Alice').closest('.mentor-card')
    const fullBtn = aliceCard.querySelector('button')
    expect(fullBtn).toBeDisabled()
    expect(fullBtn).toHaveTextContent('At Capacity')
  })

  it('Send Request button shows "Request Sent" when mentor is in requestSentIds', async () => {
    api.getSentMentorshipRequests.mockResolvedValue({ content: [{ mentorId: 1, status: 'PENDING' }] })
    renderComponent()
    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
    const bobCard = screen.getByText('Bob').closest('.mentor-card')
    const sentBtn = bobCard.querySelector('button')
    expect(sentBtn).toBeDisabled()
    expect(sentBtn).toHaveTextContent('Request Sent')
  })

  it('shows active mentor banner when mentee has an active mentorship', async () => {
    api.getActiveMentorships.mockResolvedValue([{ id: 1, status: 'ACTIVE', mentorId: 10 }])
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/already have an active mentor/i)).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
    const bobCard = screen.getByText('Bob').closest('.mentor-card')
    const btn = bobCard.querySelector('button')
    expect(btn).toBeDisabled()
  })

  it('clicking Send Request opens modal, sending request shows toast & updates UI', async () => {
    api.createMentorshipRequest.mockResolvedValue({ id: 100 })
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })

    const bobCard = screen.getByText('Bob').closest('.mentor-card')
    const btn = bobCard.querySelector('button')
    fireEvent.click(btn)

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /send mentorship request/i })).toBeInTheDocument()
      expect(screen.getAllByText(/Bob/).length).toBeGreaterThan(0)
    })

    const modal = screen.getByRole('dialog', { name: /send mentorship request/i })
    const submitBtn = modal.querySelector('button[type="submit"]')
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(api.createMentorshipRequest).toHaveBeenCalledWith({ mentorId: 1, message: '' })
      expect(btn).toHaveTextContent('Request Sent')
      expect(btn).toBeDisabled()
    })

    expect(screen.queryByRole('dialog', { name: /send mentorship request/i })).not.toBeInTheDocument()
    expect(document.querySelector('.toast-success')).toHaveTextContent(/Request sent successfully/i)
  })

  it('keyword search filters mentors by name, expertise, interests', async () => {
    renderComponent()
    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/search topic or mentor/i)
    fireEvent.change(searchInput, { target: { value: 'alice' } })

    expect(screen.queryByText('Bob')).not.toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()

    fireEvent.change(searchInput, { target: { value: 'react' } })
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.queryByText('Alice')).not.toBeInTheDocument()
  })

  it('chip filter applies correctly', async () => {
    renderComponent()
    await waitFor(() => {
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })

    const aiChip = screen.getAllByText('AI/ML')[0]
    fireEvent.click(aiChip)

    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.queryByText('Bob')).not.toBeInTheDocument()
  })

  it('AI matches render factor chips and a diverse-pick pill (spec 1.1.2.5)', async () => {
    api.getMatchingMentors.mockResolvedValue([
      {
        id: 1, firstName: 'Bob', expertise: 'React',
        interests: ['Frontend'], matchScore: 42,
        factors: [
          'interest-match:Frontend',
          'skill-match:Java',
          'major-exact',
          'availability:6h',
          'nearby:23km',
          'diverse-pick',
        ],
      },
    ])
    renderComponent()
    await waitFor(() => expect(screen.getByText('Bob')).toBeInTheDocument())

    const aiBtn = screen.getByRole('button', { name: /find my best matches/i })
    fireEvent.click(aiBtn)

    await waitFor(() => {
      expect(screen.getByText(/your top 1 match/i)).toBeInTheDocument()
    })

    // diverse-pick rendered as a labelled pill (not as a chip)
    expect(screen.getByText('Diverse pick')).toBeInTheDocument()

    // human-readable factor chips
    expect(screen.getByText('Same major')).toBeInTheDocument()
    expect(screen.getByText('6h overlap')).toBeInTheDocument()
    expect(screen.getByText('23km away')).toBeInTheDocument()

    // 'diverse-pick' itself never renders as a raw code
    expect(screen.queryByText('diverse-pick')).not.toBeInTheDocument()
  })

  it('AI matches with no factors render no factor strip and no diverse pill', async () => {
    api.getMatchingMentors.mockResolvedValue([
      { id: 2, firstName: 'Alice', expertise: 'Python', interests: ['AI/ML'], matchScore: 18, factors: [] },
    ])
    renderComponent()
    await waitFor(() => expect(screen.getByText('Bob')).toBeInTheDocument())

    const aiBtn = screen.getByRole('button', { name: /find my best matches/i })
    fireEvent.click(aiBtn)

    await waitFor(() => {
      expect(screen.getByText(/your top 1 match/i)).toBeInTheDocument()
    })
    expect(screen.queryByText('Diverse pick')).not.toBeInTheDocument()
    expect(document.querySelector('.match-factors')).toBeNull()
  })
})
