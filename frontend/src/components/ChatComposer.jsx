import { useState, useRef } from 'react'

/**
 * Minimal chat composer: textarea + send button.
 *
 * - Enter sends; Shift+Enter inserts a newline.
 * - Disabled while submitting or when `disabled` prop is true.
 * - The paperclip slot is rendered conditionally when an `attachmentSlot`
 *   element is supplied (issue #122 plugs into this slot).
 *
 * Props:
 *   onSend(content): Promise<void> — must resolve when the send completes.
 *                    The composer clears its textarea on resolve and surfaces
 *                    `error.message` inline on reject.
 *   disabled: boolean — disables the textarea + send.
 *   placeholder: string — textarea placeholder.
 *   attachmentSlot: ReactNode — optional element rendered to the left of the
 *                    textarea (used by #122 to inject the paperclip button).
 */
export default function ChatComposer({
  onSend,
  disabled = false,
  placeholder = 'Type a message…',
  attachmentSlot = null,
}) {
  const [value, setValue] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const textareaRef = useRef(null)

  const isBlocked = disabled || submitting
  const trimmed = value.trim()

  async function submit() {
    if (!trimmed || isBlocked) return
    setSubmitting(true)
    setError(null)
    try {
      await onSend(trimmed)
      setValue('')
      // Refocus so power users can keep typing
      textareaRef.current?.focus()
    } catch (err) {
      setError(err?.message || 'Failed to send')
    } finally {
      setSubmitting(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="md-composer">
      {attachmentSlot}
      <textarea
        ref={textareaRef}
        className="md-composer-input"
        placeholder={placeholder}
        rows={1}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={isBlocked}
        aria-label="Message"
      />
      <button
        type="button"
        className="md-composer-send"
        onClick={submit}
        disabled={isBlocked || !trimmed}
        aria-label="Send message"
      >
        {submitting ? 'Sending…' : 'Send'}
      </button>
      {error && <div className="md-composer-error" role="alert">{error}</div>}
    </div>
  )
}
