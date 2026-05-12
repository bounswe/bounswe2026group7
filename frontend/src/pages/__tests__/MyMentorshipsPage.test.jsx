import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import MyMentorshipsPage from '../MyMentorshipsPage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

const ROWS = [
  { id: 1, status: 'ACTIVE', mentorId: 10, menteeId: 99, mentorFirstName: 'Alice', menteeFirstName: 'Bob', startDate: '2026-01-01T00:00:00Z', endDate: '2026-07-01T00:00:00Z' },
  { id: 2, status: 'COMPLETED', mentorId: 11, menteeId: 99, mentorFirstName: 'Cara', menteeFirstName: 'Bob', startDate: '2025-01-01T00:00:00Z', endDate: '2025-06-01T00:00:00Z' },
  { id: 3, status: 'CANCELLED', mentorId: 12, menteeId: 99, mentorFirstName: 'Dan',  menteeFirstName: 'Bob', startDate: '2024-01-01T00:00:00Z', endDate: '2024-02-01T00:00:00Z' },
]

describe('MyMentorshipsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getNotifications.mockResolvedValue([])
    api.getActiveMentorships.mockResolvedValue([])
    api.getReceivedMentorshipRequests.mockResolvedValue({ content: [] })
    api.getOwnProfile.mockResolvedValue({})
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE', userId: '99', firstName: 'Bob', lastName: 'B' })
  })

  async function renderPage() {
    let result
    await act(async () => {
      result = render(
        <MemoryRouter>
          <MentorshipProvider>
            <MyMentorshipsPage />
          </MentorshipProvider>
        </MemoryRouter>
      )
    })
    return result
  }

  it('renders Active tab by default and only shows ACTIVE rows', async () => {
    api.listMentorships.mockResolvedValue({ content: ROWS, totalPages: 1 })
    await renderPage()

    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })
    expect(screen.queryByText('Cara')).not.toBeInTheDocument()
    expect(screen.queryByText('Dan')).not.toBeInTheDocument()

    const activeTab = screen.getByTestId('my-mentorships-tab-active')
    expect(activeTab).toHaveAttribute('aria-selected', 'true')
  })

  it('switches to Past tab and renders past statuses only', async () => {
    api.listMentorships.mockResolvedValue({ content: ROWS, totalPages: 1 })
    await renderPage()
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument())

    await act(async () => {
      await userEvent.click(screen.getByTestId('my-mentorships-tab-past'))
    })

    expect(screen.getByText('Cara')).toBeInTheDocument()
    expect(screen.getByText('Dan')).toBeInTheDocument()
    expect(screen.queryByText('Alice')).not.toBeInTheDocument()
  })

  it('shows empty state for Active tab when no mentorships', async () => {
    api.listMentorships.mockResolvedValue({ content: [], totalPages: 1 })
    await renderPage()

    await waitFor(() => {
      expect(screen.getByTestId('my-mentorships-empty-active')).toBeInTheDocument()
    })
    expect(screen.getByText(/no active mentorships/i)).toBeInTheDocument()
  })

  it('shows empty state for Past tab when no past mentorships', async () => {
    api.listMentorships.mockResolvedValue({ content: [ROWS[0]], totalPages: 1 })
    await renderPage()
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument())

    await act(async () => {
      await userEvent.click(screen.getByTestId('my-mentorships-tab-past'))
    })

    expect(screen.getByTestId('my-mentorships-empty-past')).toBeInTheDocument()
  })

  it('row click navigates to mentorship detail', async () => {
    api.listMentorships.mockResolvedValue({ content: ROWS, totalPages: 1 })
    await renderPage()
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument())

    await act(async () => {
      await userEvent.click(screen.getByTestId('my-mentorships-row-1'))
    })

    expect(mockNavigate).toHaveBeenCalledWith('/mentorships/1')
  })
})
