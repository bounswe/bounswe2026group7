import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import NotificationsPage from '../NotificationsPage'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

describe('NotificationsPage Component', () => {
  const mockNotifications = [
    {
      id: 1,
      type: 'REQUEST_ACCEPTED',
      title: 'Request Accepted',
      body: 'Your request was accepted by Jane.',
      createdAt: new Date().toISOString(),
      read: false,
      relatedId: 101,
    },
    {
      id: 2,
      type: 'TASK_DEADLINE_REMINDER',
      title: 'Task Deadline',
      body: 'You have a task deadline tomorrow.',
      createdAt: new Date().toISOString(),
      read: true,
      relatedId: 202,
    },
    {
      id: 3,
      type: 'UNKNOWN_TYPE',
      title: 'Unknown Title',
      body: 'Something happened.',
      createdAt: new Date().toISOString(),
      read: false,
      relatedId: 303,
    },
    {
      id: 4,
      type: 'MILESTONE_REMINDER',
      title: 'Milestone Reminder',
      body: 'Check your milestone progress.',
      createdAt: new Date().toISOString(),
      read: false,
      relatedId: 404,
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    api.getNotifications.mockResolvedValue(mockNotifications)
    api.markNotificationAsRead.mockResolvedValue({})
    AuthContext.useAuth.mockReturnValue({ role: 'MENTEE' })
  })

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <MentorshipProvider>
          <NotificationsPage />
        </MentorshipProvider>
      </MemoryRouter>
    )
  }

  it('renders all notification types with appropriate icons and labels', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByTestId('notifications-list')).toBeInTheDocument()
    })

    const list = screen.getByTestId('notifications-list')
    expect(within(list).getByText('Request Accepted')).toBeInTheDocument()
    expect(within(list).getByText('Task Deadline')).toBeInTheDocument()
    expect(within(list).getByText('Milestone Reminder')).toBeInTheDocument()
    expect(within(list).getByText('Unknown Title')).toBeInTheDocument()

    // Check icons (emojis) in the main list
    expect(within(list).getByText('✅')).toBeInTheDocument() // REQUEST_ACCEPTED
    expect(within(list).getByText('⏰')).toBeInTheDocument() // TASK_DEADLINE_REMINDER
    expect(within(list).getByText('🚩')).toBeInTheDocument() // MILESTONE_REMINDER
    expect(within(list).getByText('🔔')).toBeInTheDocument() // UNKNOWN_TYPE (Fallback)
  })

  it('navigates to mentorship detail on REQUEST_ACCEPTED click', async () => {
    renderComponent()
    await waitFor(() => screen.getByTestId('notifications-list'))

    const list = screen.getByTestId('notifications-list')
    const item = within(list).getByText('Request Accepted').closest('li')
    fireEvent.click(item)

    expect(api.markNotificationAsRead).toHaveBeenCalledWith(1)
    expect(mockNavigate).toHaveBeenCalledWith('/mentorships/101')
  })

  it('navigates to tasks page on TASK_DEADLINE_REMINDER click', async () => {
    renderComponent()
    await waitFor(() => screen.getByTestId('notifications-list'))

    const list = screen.getByTestId('notifications-list')
    const item = within(list).getByText('Task Deadline').closest('li')
    fireEvent.click(item)

    expect(api.markNotificationAsRead).not.toHaveBeenCalled()
    expect(mockNavigate).toHaveBeenCalledWith('/tasks?mentorshipId=202')
  })

  it('navigates to milestones panel on MILESTONE_REMINDER click', async () => {
    renderComponent()
    await waitFor(() => screen.getByTestId('notifications-list'))

    const list = screen.getByTestId('notifications-list')
    const item = within(list).getByText('Milestone Reminder').closest('li')
    fireEvent.click(item)

    expect(api.markNotificationAsRead).toHaveBeenCalledWith(4)
    expect(mockNavigate).toHaveBeenCalledWith('/mentorships/404#milestones')
  })

  it('handles unknown types with fallback icon and no navigation', async () => {
    renderComponent()
    await waitFor(() => screen.getByTestId('notifications-list'))

    const list = screen.getByTestId('notifications-list')
    const item = within(list).getByText('Unknown Title').closest('li')
    fireEvent.click(item)

    expect(api.markNotificationAsRead).toHaveBeenCalledWith(3)
    // No navigation call for unknown type
    expect(mockNavigate).not.toHaveBeenCalledWith(expect.stringContaining('303'))
  })
})
