import { useEffect, useState } from 'react'
import { Download, FileText, Image as ImageIcon } from 'lucide-react'

/**
 * Renders a chat attachment by content type.
 *
 * The backend `AttachmentDownloadController` requires JWT auth on every fetch,
 * so we cannot bind `attachment.downloadUrl` directly to `<img src>`. For
 * images we fetch the bytes with credentials, materialize a blob URL, and
 * revoke it when the component unmounts. For documents we provide a button
 * that triggers an authenticated download on click.
 *
 * Props:
 *   attachment: AttachmentSummary (id, downloadUrl, filename, contentType, sizeBytes)
 *   mine: boolean — adjusts foreground color for "my" message bubbles
 */
export default function MessageAttachment({ attachment, mine = false }) {
  const isImage = (attachment?.contentType || '').startsWith('image/')
  if (!attachment) return null
  return isImage
    ? <ImagePreview attachment={attachment} />
    : <DocumentPreview attachment={attachment} mine={mine} />
}

function authHeaders() {
  const token = localStorage.getItem('auth_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function ImagePreview({ attachment }) {
  const [blobUrl, setBlobUrl] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let revoked = false
    let createdUrl = null
    setBlobUrl(null)
    setError(false)
    fetch(attachment.downloadUrl, { headers: authHeaders() })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.blob()
      })
      .then(blob => {
        if (revoked) return
        createdUrl = URL.createObjectURL(blob)
        setBlobUrl(createdUrl)
      })
      .catch(() => { if (!revoked) setError(true) })
    return () => {
      revoked = true
      if (createdUrl) URL.revokeObjectURL(createdUrl)
    }
  }, [attachment.downloadUrl])

  if (error) {
    return (
      <div className="md-attach md-attach--err">
        <ImageIcon size={16} strokeWidth={1.75} />
        <span>Image unavailable</span>
      </div>
    )
  }
  if (!blobUrl) {
    return (
      <div className="md-attach md-attach--loading">
        <ImageIcon size={16} strokeWidth={1.75} />
        <span>Loading image…</span>
      </div>
    )
  }
  return (
    <a
      href={blobUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="md-attach-image-link"
    >
      <img src={blobUrl} alt={attachment.filename} className="md-attach-image" loading="lazy" />
    </a>
  )
}

function DocumentPreview({ attachment, mine }) {
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState(null)

  async function downloadAuthed() {
    setDownloading(true)
    setError(null)
    try {
      const res = await fetch(attachment.downloadUrl, { headers: authHeaders() })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const objectUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = attachment.filename || 'attachment'
      document.body.appendChild(a)
      a.click()
      a.remove()
      // Give the download a moment to start before revoking
      setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
    } catch (err) {
      setError(err.message || 'Download failed')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className={`md-attach md-attach--doc${mine ? ' md-attach--mine' : ''}`}>
      <FileText size={16} strokeWidth={1.75} />
      <span className="md-attach-filename" title={attachment.filename}>
        {attachment.filename}
      </span>
      <button
        type="button"
        className="md-attach-download"
        onClick={downloadAuthed}
        disabled={downloading}
        aria-label="Download attachment"
        title="Download"
      >
        <Download size={14} strokeWidth={2} />
      </button>
      {error && <span className="md-attach-error">{error}</span>}
    </div>
  )
}
