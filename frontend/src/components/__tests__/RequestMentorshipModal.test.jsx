import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RequestMentorshipModal from '../RequestMentorshipModal'

describe('RequestMentorshipModal Component', () => {
  const defaultProps = {
    visible: true,
    onClose: vi.fn(),
    onSubmit: vi.fn(),
    loading: false,
    error: '',
    mentorName: 'Alice Debugger',
    defaultMessage: ''
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when visible is false', () => {
    const { container } = render(<RequestMentorshipModal {...defaultProps} visible={false} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders correctly when visible is true', () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/send mentorship request/i)).toBeInTheDocument()
    expect(screen.getByText(/Alice Debugger/)).toBeInTheDocument()
  })

  it('enables submit button even with empty message', () => {
    render(<RequestMentorshipModal {...defaultProps} defaultMessage="" />)
    const btn = screen.getByRole('button', { name: /send request/i })
    expect(btn).not.toBeDisabled()
  })

  it('disables submit button when loading=true', () => {
    render(<RequestMentorshipModal {...defaultProps} loading={true} />)
    expect(screen.getByRole('button', { name: /sending.../i })).toBeDisabled()
  })

  it('disables submit button when message exceeds 500 characters', async () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    const textarea = screen.getByRole('textbox')
    fireEvent.change(textarea, { target: { value: 'a'.repeat(501) } })
    
    const btn = screen.getByRole('button', { name: /send request/i })
    expect(btn).toBeDisabled()
    expect(screen.getByText(/501\/500/)).toHaveStyle({ color: '#b91c1c' })
  })

  it('calls onSubmit with trimmed message', async () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    const textarea = screen.getByRole('textbox')
    fireEvent.change(textarea, { target: { value: '   Hello World   ' } })
    const btn = screen.getByRole('button', { name: /send request/i })
    fireEvent.click(btn)
    
    expect(defaultProps.onSubmit).toHaveBeenCalledWith('Hello World')
    expect(defaultProps.onSubmit).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when Escape is pressed', async () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    fireEvent.keyDown(window, { key: 'Escape', code: 'Escape' })
    expect(defaultProps.onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when overlay is clicked', async () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    const overlay = document.querySelector('.modal-overlay')
    fireEvent.mouseDown(overlay, { target: overlay })
    expect(defaultProps.onClose).toHaveBeenCalledTimes(1)
  })

  it('does NOT call onClose when modal content is clicked', async () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    const dialog = screen.getByRole('dialog')
    fireEvent.mouseDown(dialog, { target: dialog })
    expect(defaultProps.onClose).not.toHaveBeenCalled()
  })

  it('displays error message when error prop is present', () => {
    render(<RequestMentorshipModal {...defaultProps} error="Something went wrong!" />)
    expect(screen.getByText('Something went wrong!')).toBeInTheDocument()
  })

  it('updates character count as user types', () => {
    render(<RequestMentorshipModal {...defaultProps} />)
    const textarea = screen.getByRole('textbox')
    fireEvent.change(textarea, { target: { value: '12345' } })
    expect(screen.getByText('5/500')).toBeInTheDocument()
  })
})
