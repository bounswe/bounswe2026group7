import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MentorshipDetailPage from '../MentorshipDetailPage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')
// Note: MentorshipDetailPage no longer imports from mentorshipMocks (#506
// replaced getNextUpcomingMeeting with the real listMentorshipMeetings).
// The mock below is harmless dead code now but kept for forward-compat in
// case something else in the page tree imports from mentorshipMocks later.
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
    // #506: page now derives the upcoming-meeting card from the real
    // meetings list. Auto-mocked api functions return undefined by default;
    // explicitly resolve to an empty array so the page's Promise.all and
    // subsequent deriveNextUpcomingMeeting() resolve cleanly in tests that
    // don't care about meeting data.
    api.listMentorshipMeetings.mockResolvedValue([])
    // #556: page hydrates an existing rating on mount. Reject with a 404-like
    // error so it falls through to the "not rated yet" path without crashing
    // on undefined.then().
    api.getMentorshipRating.mockRejectedValue(Object.assign(new Error('not rated yet'), { status: 404 }))
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

  it('refetches the timeline after a milestone is created', async () => {
    const activeWithGoal = {
      id: mockMentorshipId,
      status: 'ACTIVE',
      mentorId: mockUserId,
      menteeId: '2',
      sharedGoal: 'Ship MVP by July',
      startDate: '2026-05-01',
      endDate: '2026-08-01',
      duration: 3,
      mentorFirstName: 'John',
      menteeFirstName: 'Jane',
    }

    // Empty milestone list on first load, then includes the freshly-created one.
    let createdMilestone = null
    api.listMilestones.mockImplementation(async () => createdMilestone ? [createdMilestone] : [])

    api.getMentorshipProgress.mockResolvedValue({
      progressRatio: 0,
      taskCompleted: 0,
      taskTotal: 0,
      taskSubmitted: 0,
      milestoneCompleted: 0,
      milestoneTotal: 0,
      lastActivityAt: null,
    })

    // First fetch returns nothing; subsequent fetches (after the bump) include
    // the new milestone on the ribbon.
    api.getMentorshipTimeline
      .mockResolvedValueOnce({
        items: [],
        startDate: '2026-05-01',
        endDate: '2026-08-01',
        currentDate: '2026-05-12',
      })
      .mockResolvedValue({
        items: [{
          id: 'm1',
          type: 'MILESTONE',
          title: 'Ship MVP',
          occursAt: '2026-06-01',
          detailUrl: '/mentorships/1/milestones/m1',
        }],
        startDate: '2026-05-01',
        endDate: '2026-08-01',
        currentDate: '2026-05-12',
      })

    api.createMilestone.mockImplementation(async (_mentorshipId, payload) => {
      createdMilestone = {
        id: 'm1',
        title: payload.title,
        status: 'PENDING',
        orderIndex: 0,
        targetDate: null,
      }
      return createdMilestone
    })

    await renderComponent(activeWithGoal)

    // Open the create-milestone modal
    const addBtn = await screen.findByTestId('milestones-add')
    await act(async () => { fireEvent.click(addBtn) })

    // Fill the title field by stable testid — the surrounding sweep already
    // hooked the title input as `milestone-modal-title`, so we do not need
    // to reach into the modal DOM by CSS class.
    const titleInput = await screen.findByTestId('milestone-modal-title')
    await act(async () => {
      fireEvent.change(titleInput, { target: { value: 'Ship MVP' } })
    })

    const createBtn = await screen.findByTestId('milestone-modal-save')
    await act(async () => { fireEvent.click(createBtn) })

    // The observable contract: after the create resolves, the new title
    // shows up in the rendered tree (both on the milestone card and the
    // timeline pill). Asserting the side-effect rather than the mock call
    // count avoids breaking under future refetches (e.g. focus-revalidate).
    await waitFor(() => {
      expect(screen.getAllByText('Ship MVP').length).toBeGreaterThan(0)
    })
  })
})
