import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import AdminConsolePage from '../AdminConsolePage'
import AdminRoute from '../../components/AdminRoute'
import * as api from '../../services/api'
import * as AuthContext from '../../context/AuthContext'
import { MentorshipProvider } from '../../context/MentorshipContext'

vi.mock('../../services/api')
vi.mock('../../context/AuthContext')

describe('AdminConsolePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getNotifications.mockResolvedValue([])
    api.getActiveMentorships.mockResolvedValue([])
    api.getReceivedMentorshipRequests.mockResolvedValue({ content: [] })
    api.getOwnProfile.mockResolvedValue({})
  })

  async function renderConsole() {
    let result
    await act(async () => {
      result = render(
        <MemoryRouter initialEntries={['/admin']}>
          <MentorshipProvider>
            <Routes>
              <Route path="/home" element={<div data-testid="home-page">home</div>} />
              <Route element={<AdminRoute />}>
                <Route path="/admin" element={<AdminConsolePage />} />
              </Route>
            </Routes>
          </MentorshipProvider>
        </MemoryRouter>
      )
    })
    return result
  }

  it('redirects non-admin users away from /admin', async () => {
    AuthContext.useAuth.mockReturnValue({ token: 'tok', role: 'MENTEE', userId: '5', isLoading: false })
    await renderConsole()

    await waitFor(() => {
      expect(screen.getByTestId('home-page')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('admin-console-send')).not.toBeInTheDocument()
  })

  it('allows admin to see the form and successfully send a message', async () => {
    AuthContext.useAuth.mockReturnValue({ token: 'tok', role: 'ADMIN', userId: '1', isLoading: false })
    api.sendAdminDirectMessage.mockResolvedValue({ id: 42 })
    await renderConsole()

    await waitFor(() => {
      expect(screen.getByTestId('admin-console-send')).toBeInTheDocument()
    })

    await act(async () => {
      await userEvent.type(screen.getByTestId('admin-console-recipient'), '7')
      await userEvent.type(screen.getByTestId('admin-console-body'), 'hello mentee')
      await userEvent.click(screen.getByTestId('admin-console-send'))
    })

    await waitFor(() => {
      expect(api.sendAdminDirectMessage).toHaveBeenCalledWith(7, 'hello mentee')
    })
    expect(screen.queryByTestId('admin-console-error')).not.toBeInTheDocument()
  })

  it('renders an error card when send fails', async () => {
    AuthContext.useAuth.mockReturnValue({ token: 'tok', role: 'ADMIN', userId: '1', isLoading: false })
    api.sendAdminDirectMessage.mockRejectedValue(new Error('boom'))
    await renderConsole()

    await waitFor(() => {
      expect(screen.getByTestId('admin-console-send')).toBeInTheDocument()
    })

    await act(async () => {
      await userEvent.type(screen.getByTestId('admin-console-recipient'), '7')
      await userEvent.type(screen.getByTestId('admin-console-body'), 'hello')
      await userEvent.click(screen.getByTestId('admin-console-send'))
    })

    await waitFor(() => {
      expect(screen.getByTestId('admin-console-error')).toBeInTheDocument()
    })
    expect(screen.getByText(/boom/)).toBeInTheDocument()
  })
})
