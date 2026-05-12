/**
 * Lightweight toast helpers. Avoids pulling in a toast library for the
 * handful of one-shot notifications we render. If the call sites grow
 * beyond a few, extract to a real toast provider with a portal root.
 *
 * Two flavours:
 *  - showTransientToast(text)            → auto-dismissing info toast
 *  - showUndoToast(text, onUndo, opts?)  → interactive toast with an Undo
 *                                          button; resolves true when the
 *                                          undo handler ran.
 *
 * Both render to <body> directly so they appear above modals and the
 * sidebar. Existing CSS classes (`.toast`, `.toast-success`) handle the
 * info variant; the undo variant adds `.toast--undo` for layout + the
 * button styling.
 */

export function showTransientToast(text, ms = 2500) {
  const el = document.createElement('div')
  el.className = 'toast toast-success'
  el.textContent = text
  document.body.appendChild(el)
  setTimeout(() => el.remove(), ms)
}

export function showUndoToast(text, onUndo, { ms = 8000 } = {}) {
  const root = document.createElement('div')
  root.className = 'toast toast-success toast--undo'
  root.setAttribute('role', 'status')

  const label = document.createElement('span')
  label.className = 'toast-text'
  label.textContent = text
  root.appendChild(label)

  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = 'toast-undo-btn'
  btn.textContent = 'Undo'
  let dismissed = false
  const dismiss = () => {
    if (dismissed) return
    dismissed = true
    root.remove()
    clearTimeout(timer)
  }
  btn.onclick = () => {
    dismiss()
    try { onUndo?.() } catch { /* swallow — caller logs */ }
  }
  root.appendChild(btn)

  document.body.appendChild(root)
  const timer = setTimeout(dismiss, ms)
}
