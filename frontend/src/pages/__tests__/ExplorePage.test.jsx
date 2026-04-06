import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import ExplorePage from '../ExplorePage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'

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
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE' })
  })

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <ExplorePage />
      </MemoryRouter>
    )
  }

  it('shows non-mentee message for MENTOR role and does not render Send Request button', async () => {
    AuthContext.useAuth.mockReturnValue({ role: 'MENTOR' })
    renderComponent()
    
    expect(screen.getByText(/this page is for mentees/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /send request/i })).not.toBeInTheDocument()
  })

  it('shows loading state and then renders mentor cards', async () => {
    renderComponent()
    expect(screen.getByText(/loading mentors/i)).toBeInTheDocument()
    
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

  it('shows active mentor banner when matching API returns 403 and disables send request', async () => {
    api.getMatchingMentors.mockRejectedValue(new Error('403 Forbidden: You already have a mentor'))
    renderComponent()
    
    await waitFor(() => {
      expect(screen.getByText(/already have an active mentor/i)).toBeInTheDocument()
    })
    const bobCard = screen.getByText('Bob').closest('.mentor-card')
    const btn = bobCard.querySelector('button')
    expect(btn).toBeDisabled() // disabled because hasActiveMentor
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
      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getAllByText(/Bob/).length).toBeGreaterThan(0)
    })

    const submitBtn = screen.getByRole('dialog').querySelector('button[type="submit"]')
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(api.createMentorshipRequest).toHaveBeenCalledWith({ mentorId: 1, message: '' })
      expect(btn).toHaveTextContent('Request Sent')
      expect(btn).toBeDisabled()
    })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
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
})
