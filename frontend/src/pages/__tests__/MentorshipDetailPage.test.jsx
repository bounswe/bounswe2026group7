import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MentorshipDetailPage from '../MentorshipDetailPage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')
vi.mock('../../services/mentorshipMocks', () => ({
  getNextUpcomingMeeting: vi.fn().mockResolvedValue(null)
}))

describe('MentorshipDetailPage - Shared Goal Feature', () => {
  const mockMentorshipId = '123'
  const mockUserId = '1'

  beforeEach(() => {
    vi.clearAllMocks()
    AuthContext.useAuth.mockReturnValue({ userId: mockUserId, role: 'MENTOR' })
    api.getNotifications.mockResolvedValue([])
    api.listMilestones.mockResolvedValue([])
    api.getUserById.mockResolvedValue({ id: '2', firstName: 'Jane', lastName: 'Doe' })
  })

  const renderComponent = async (mentorshipData) => {
    api.getMentorshipById.mockResolvedValue(mentorshipData)
    
    await act(async () => {
      render(
        <MemoryRouter initialEntries={[`/mentorships/${mockMentorshipId}`]}>
          <Routes>
            <Route path="/mentorships/:id" element={
              <MentorshipProvider>
                <MentorshipDetailPage />
              </MentorshipProvider>
            } />
          </Routes>
        </MemoryRouter>
      )
    })
  }

  it('renders CTA card when mentorship is ACTIVE and sharedGoal is missing', async () => {
    const activeNoGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: null,
      startDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    await renderComponent(activeNoGoal)

    await waitFor(() => {
      // Heading in the CTA card
      expect(screen.getByText('Define a shared goal')).toBeInTheDocument()
      expect(screen.getByText(/unlocks milestones and progress tracking/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /add goal/i })).toBeInTheDocument()
    })
  })

  it('does not render CTA card when sharedGoal is already defined', async () => {
    const activeWithGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: 'Learn React Testing',
      startDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    await renderComponent(activeWithGoal)

    await waitFor(() => {
      expect(screen.queryByText('Define a shared goal')).not.toBeInTheDocument()
      expect(screen.getByText(/"Learn React Testing"/)).toBeInTheDocument()
    })
  })

  it('does not render CTA card when mentorship is not ACTIVE', async () => {
    const completedNoGoal = {
      id: mockMentorshipId,
      status: 'COMPLETED',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: null,
      startDate: new Date().toISOString(),
      endDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    await renderComponent(completedNoGoal)

    await waitFor(() => {
      expect(screen.queryByText('Define a shared goal')).not.toBeInTheDocument()
    })
  })

  it('opens SharedGoalModal when Add Goal button is clicked', async () => {
    const activeNoGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: null,
      startDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    await renderComponent(activeNoGoal)

    const addGoalBtn = await screen.findByRole('button', { name: /add goal/i })
    fireEvent.click(addGoalBtn)

    expect(screen.getByRole('dialog', { name: /shared goal/i })).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e.g. help the mentee secure a software internship/i)).toBeInTheDocument()
  })

  it('submits a new goal and updates the UI', async () => {
    const activeNoGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: null,
      startDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    const updatedMentorship = { ...activeNoGoal, sharedGoal: 'New Shared Goal' }
    api.updateSharedGoal.mockResolvedValue(updatedMentorship)

    await renderComponent(activeNoGoal)

    const addGoalBtn = await screen.findByRole('button', { name: /add goal/i })
    fireEvent.click(addGoalBtn)

    const textarea = screen.getByPlaceholderText(/e.g. help the mentee/i)
    fireEvent.change(textarea, { target: { value: 'New Shared Goal' } })

    const saveBtn = screen.getByRole('button', { name: /save/i })
    await act(async () => {
      fireEvent.click(saveBtn)
    })

    expect(api.updateSharedGoal).toHaveBeenCalledWith(mockMentorshipId, 'New Shared Goal')
    
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: /shared goal/i })).not.toBeInTheDocument()
      expect(screen.getByText(/"New Shared Goal"/)).toBeInTheDocument()
      expect(screen.queryByText('Define a shared goal')).not.toBeInTheDocument()
    })
  })

  it('shows friendly message in Milestones section when goal is missing', async () => {
    const activeNoGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: null,
      startDate: new Date().toISOString(),
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane'
    }

    await renderComponent(activeNoGoal)

    await waitFor(() => {
      expect(screen.getByText(/please define a shared goal first to unlock milestones/i)).toBeInTheDocument()
    })
  })
})
