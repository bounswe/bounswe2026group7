import { useEffect, useRef, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { uploadMessageAttachment, MAX_ATTACHMENT_BYTES } from '../services/attachmentService'

const MAX_IMAGES = 4
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

/**
 * File picker + thumb strip for feed-post image attachments (#507).
 *
 * Owns its own per-file upload state. Files are uploaded as they're picked
 * (POST /api/messages/attachments — the universal upload endpoint), so by
 * the time the user clicks Publish/Save we already have UUIDs to send as
 * `attachmentIds`.
 *
 * Props:
 *   value: AttachmentSummary[] — current attachment list (empty array on compose)
 *   onChange(next: AttachmentSummary[]): called when items are added/removed
 *   disabled: boolean — disable the picker button while parent is busy publishing
 */
export default function FeedImageUploader({ value = [], onChange, disabled = false }) {
  const inputRef = useRef(null)
  const [pending, setPending] = useState([]) // [{ tempId, name, previewUrl }]
  const [error, setError] = useState(null)

  // Revoke object URLs when pending entries leave the list, to avoid leaks.
  useEffect(() => {
    return () => pending.forEach(p => URL.revokeObjectURL(p.previewUrl))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const remaining = MAX_IMAGES - value.length - pending.length
  const canAdd = remaining > 0 && !disabled

  async function handleFiles(files) {
    setError(null)
    const arr = Array.from(files || [])
    if (arr.length === 0) return
    if (arr.length > remaining) {
      setError(`You can attach at most ${MAX_IMAGES} images per post.`)
      return
    }
    for (const file of arr) {
      if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        setError('Only JPEG, PNG, GIF, or WebP images are allowed.')
        continue
      }
      if (file.size > MAX_ATTACHMENT_BYTES) {
        setError('Images must be 5 MB or smaller.')
        continue
      }
      const tempId = `${file.name}-${file.size}-${Date.now()}-${Math.random()}`
      const previewUrl = URL.createObjectURL(file)
      setPending(prev => [...prev, { tempId, name: file.name, previewUrl }])
      try {
        const summary = await uploadMessageAttachment(file)
        setPending(prev => prev.filter(p => p.tempId !== tempId))
        URL.revokeObjectURL(previewUrl)
        onChange?.([...value, summary])
      } catch (err) {
        setPending(prev => prev.filter(p => p.tempId !== tempId))
        URL.revokeObjectURL(previewUrl)
        setError(err?.message || 'Upload failed')
      }
    }
  }

  function removeAt(index) {
    if (disabled) return
    const next = value.slice(0, index).concat(value.slice(index + 1))
    onChange?.(next)
  }

  return (
    <div className="feed-uploader">
      <div className="feed-uploader-thumbs">
        {value.map((att, i) => (
          <UploadedThumb
            key={att.id || `${att.filename}-${i}`}
            attachment={att}
            onRemove={() => removeAt(i)}
            disabled={disabled}
          />
        ))}
        {pending.map(p => (
          <div key={p.tempId} className="feed-uploader-thumb feed-uploader-thumb--pending">
            <img src={p.previewUrl} alt={p.name} />
            <div className="feed-uploader-thumb-spinner" aria-label="Uploading" />
          </div>
        ))}
        {canAdd && (
          <button
            type="button"
            className="feed-uploader-add"
            onClick={() => inputRef.current?.click()}
            aria-label="Add images"
            title={`Add images (${remaining} remaining)`}
          >
            <ImagePlus size={18} strokeWidth={1.75} />
            <span>Add image</span>
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        multiple
        hidden
        onChange={(e) => {
          handleFiles(e.target.files)
          e.target.value = ''
        }}
      />
      {error && <div className="feed-uploader-error">{error}</div>}
    </div>
  )
}

function UploadedThumb({ attachment, onRemove, disabled }) {
  const [blobUrl, setBlobUrl] = useState(null)
  useEffect(() => {
    let revoked = false
    let createdUrl = null
    const token = localStorage.getItem('auth_token')
    fetch(attachment.downloadUrl, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
      .then(r => r.ok ? r.blob() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(blob => {
        if (revoked) return
        createdUrl = URL.createObjectURL(blob)
        setBlobUrl(createdUrl)
      })
      .catch(() => {})
    return () => {
      revoked = true
      if (createdUrl) URL.revokeObjectURL(createdUrl)
    }
  }, [attachment.downloadUrl])

  return (
    <div className="feed-uploader-thumb">
      {blobUrl
        ? <img src={blobUrl} alt={attachment.filename} />
        : <div className="feed-uploader-thumb-placeholder" />}
      {!disabled && (
        <button
          type="button"
          className="feed-uploader-thumb-remove"
          onClick={onRemove}
          aria-label={`Remove ${attachment.filename}`}
          title="Remove"
        >
          <X size={14} strokeWidth={2} />
        </button>
      )}
    </div>
  )
}
