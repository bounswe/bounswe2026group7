import { useEffect, useState } from 'react'

/**
 * Renders a feed post's image attachments (#507) as a 1-4 image grid.
 *
 * Backend serves each attachment via the authenticated download endpoint
 * `GET /api/uploads/feed-media/{id}` (FeedMediaDownloadController), so we
 * cannot bind `attachment.downloadUrl` directly to <img src>. Same pattern
 * as MessageAttachment.ImagePreview: fetch with the bearer header, build a
 * blob URL, revoke on unmount.
 */
export default function FeedAttachmentGrid({ attachments }) {
  if (!Array.isArray(attachments) || attachments.length === 0) return null
  const layout = layoutClassFor(attachments.length)
  return (
    <div className={`feed-attach-grid ${layout}`} onClick={(e) => e.stopPropagation()}>
      {attachments.map((att, i) => (
        <AuthImage key={att.id || `${att.filename}-${i}`} attachment={att} />
      ))}
    </div>
  )
}

function layoutClassFor(n) {
  switch (n) {
    case 1: return 'feed-attach-grid--1'
    case 2: return 'feed-attach-grid--2'
    case 3: return 'feed-attach-grid--3'
    default: return 'feed-attach-grid--4'
  }
}

function AuthImage({ attachment }) {
  const [blobUrl, setBlobUrl] = useState(null)
  const [error, setError] = useState(false)
  useEffect(() => {
    let revoked = false
    let createdUrl = null
    setBlobUrl(null)
    setError(false)
    const token = localStorage.getItem('auth_token')
    fetch(attachment.downloadUrl, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
      .then(r => r.ok ? r.blob() : Promise.reject(new Error(`HTTP ${r.status}`)))
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
    return <div className="feed-attach-cell feed-attach-cell--err">Image unavailable</div>
  }
  if (!blobUrl) {
    return <div className="feed-attach-cell feed-attach-cell--loading" />
  }
  return (
    <a
      href={blobUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="feed-attach-cell"
      onClick={(e) => e.stopPropagation()}
    >
      <img src={blobUrl} alt={attachment.filename} loading="lazy" />
    </a>
  )
}
