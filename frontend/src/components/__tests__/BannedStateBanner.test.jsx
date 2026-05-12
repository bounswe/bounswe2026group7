import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import BannedStateBanner from '../BannedStateBanner'
import * as AuthContext from '../../context/AuthContext'

vi.mock('../../context/AuthContext')

describe('BannedStateBanner', () => {
  it('renders nothing when auth.banned is null', () => {
    AuthContext.useAuth.mockReturnValue({ banned: null })
    const { container } = render(<BannedStateBanner />)
    expect(container.firstChild).toBeNull()
    expect(screen.queryByTestId('banned-state-banner')).not.toBeInTheDocument()
  })

  it('renders the banner with reason and expiry when banned', () => {
    AuthContext.useAuth.mockReturnValue({
      banned: { reason: 'Spam policy violation', expiresAt: '2026-06-01T12:00:00Z' },
    })
    render(<BannedStateBanner />)
    expect(screen.getByTestId('banned-state-banner')).toBeInTheDocument()
    expect(screen.getByTestId('banned-state-banner-reason').textContent).toContain('Spam policy violation')
    // localized date string is environment-dependent, so just confirm we rendered something non-empty
    expect(screen.getByTestId('banned-state-banner-expires').textContent.length).toBeGreaterThan(0)
  })

  it('renders banner with only reason when expiresAt missing', () => {
    AuthContext.useAuth.mockReturnValue({ banned: { reason: 'Permanent ban' } })
    render(<BannedStateBanner />)
    expect(screen.getByTestId('banned-state-banner')).toBeInTheDocument()
    expect(screen.queryByTestId('banned-state-banner-expires')).not.toBeInTheDocument()
  })
})
