/**
 * Chat-message attachment uploads.
 *
 * Backend (AttachmentController.java): POST /api/messages/attachments
 *   - multipart/form-data, field name `file`
 *   - validates type + size (jpg/png/gif/webp/pdf/docx/txt, ≤ 5 MB) and magic bytes
 *   - returns AttachmentSummary { id, downloadUrl, filename, contentType, sizeBytes }
 *
 * The returned `id` is what callers pass back as `attachmentId` when sending a
 * message (see sendMentorshipMessage). The backend enforces sender == uploader.
 */

const BASE_URL = '/api'

// 5 MB matches the backend cap. Front-loaded check gives instant feedback so
// users don't wait on a 5 MB upload only to see a 400.
export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

export const ALLOWED_ATTACHMENT_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
])

export class AttachmentValidationError extends Error {
  constructor(message, code) {
    super(message)
    this.code = code
  }
}

export function validateAttachment(file) {
  if (!file) {
    throw new AttachmentValidationError('No file selected', 'EMPTY')
  }
  if (file.size > MAX_ATTACHMENT_BYTES) {
    throw new AttachmentValidationError('File too large (max 5 MB)', 'TOO_LARGE')
  }
  // Browsers sometimes report '' for less-known types — let the server be the
  // safety net there. We only refuse types we know are unsupported.
  if (file.type && !ALLOWED_ATTACHMENT_TYPES.has(file.type)) {
    throw new AttachmentValidationError('Unsupported file type', 'UNSUPPORTED_TYPE')
  }
}

export async function uploadMessageAttachment(file) {
  validateAttachment(file)

  const token = localStorage.getItem('auth_token')
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${BASE_URL}/messages/attachments`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  })

  if (res.ok) {
    const text = await res.text()
    return text ? JSON.parse(text) : null
  }
  let message
  try {
    const body = JSON.parse(await res.text())
    message = body.message || body.error || JSON.stringify(body)
  } catch {
    message = res.statusText || 'Upload failed'
  }
  throw new Error(message)
}
