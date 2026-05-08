import { useState, useRef } from 'react'
import { Paperclip, X } from 'lucide-react'
import { ALLOWED_ATTACHMENT_TYPES, AttachmentValidationError } from '../services/attachmentService'

/**
 * Chat composer: textarea + send button, with optional file attachment.
 *
 * Behavior:
 * - Enter sends; Shift+Enter inserts a newline.
 * - Send is allowed when EITHER text OR an attachment is present.
 * - When `onUpload` is provided, a paperclip button is rendered. Picking a
 *   file uploads it immediately; a chip with the filename + remove button
 *   shows the pending attachment until the message is sent (or dismissed).
 *
 * Props:
 *   onSend(content, attachment): Promise<void>
 *     - `content` is the trimmed text (may be empty if only an attachment is sent)
 *     - `attachment` is the AttachmentSummary returned by `onUpload`, or null
 *   onUpload(file): Promise<AttachmentSummary>     (optional)
 *   disabled: boolean                              (optional)
 *   placeholder: string                            (optional)
 */
export default function ChatComposer({
  onSend,
  onUpload = null,
  disabled = false,
  placeholder = 'Type a message…',
}) {
  const [value, setValue] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const [attachment, setAttachment] = useState(null) // AttachmentSummary or null
  const [uploading, setUploading] = useState(false)

  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)

  const isBlocked = disabled || submitting || uploading
  const trimmed = value.trim()
  const canSend = !isBlocked && (trimmed.length > 0 || attachment != null)

  async function submit() {
    if (!canSend) return
    setSubmitting(true)
    setError(null)
    try {
      await onSend(trimmed, attachment)
      setValue('')
      setAttachment(null)
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

  async function handleFilePick(e) {
    const file = e.target.files?.[0]
    // Reset the input so re-picking the same file fires onChange again
    e.target.value = ''
    if (!file || !onUpload) return
    setError(null)
    setUploading(true)
    try {
      const summary = await onUpload(file)
      setAttachment(summary)
    } catch (err) {
      const msg = err instanceof AttachmentValidationError
        ? err.message
        : (err?.message || 'Upload failed')
      setError(msg)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="md-composer">
      {onUpload && (
        <>
          <input
            ref={fileInputRef}
            type="file"
            accept={Array.from(ALLOWED_ATTACHMENT_TYPES).join(',')}
            onChange={handleFilePick}
            style={{ display: 'none' }}
          />
          <button
            type="button"
            className="md-composer-attach"
            onClick={() => fileInputRef.current?.click()}
            disabled={isBlocked || attachment != null}
            aria-label="Attach file"
            title="Attach file"
          >
            <Paperclip size={18} strokeWidth={1.75} />
          </button>
        </>
      )}

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
        disabled={!canSend}
        aria-label="Send message"
      >
        {submitting ? 'Sending…' : 'Send'}
      </button>

      {attachment && (
        <div className="md-composer-chip" role="status">
          <span className="md-composer-chip-name" title={attachment.filename}>
            📎 {attachment.filename}
          </span>
          <button
            type="button"
            className="md-composer-chip-remove"
            onClick={() => setAttachment(null)}
            aria-label="Remove attachment"
            disabled={submitting}
          >
            <X size={14} strokeWidth={2} />
          </button>
        </div>
      )}
      {uploading && <div className="md-composer-status">Uploading…</div>}
      {error && <div className="md-composer-error" role="alert">{error}</div>}
    </div>
  )
}
