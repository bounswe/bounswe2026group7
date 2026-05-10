import { useEffect } from 'react'

/**
 * Prompt the user to confirm before they lose unsaved changes.
 *
 * Currently covers hard navigations (close tab, refresh, external link)
 * via `beforeunload`. In-app React Router navigation isn't blocked here —
 * the project uses the BrowserRouter shape, not the Data Router that
 * `useBlocker` requires. If/when the app moves to `createBrowserRouter`,
 * extend this hook with `useBlocker(...)` to also intercept route changes.
 *
 * Pass `dirty=true` whenever the form has unsaved edits; `dirty=false`
 * disables the prompt.
 */
export default function useUnsavedChangesGuard(dirty) {
  useEffect(() => {
    if (!dirty) return undefined
    function handleBeforeUnload(e) {
      e.preventDefault()
      // Modern browsers ignore custom messages and show a generic prompt;
      // setting returnValue is required to trigger the prompt at all.
      e.returnValue = ''
      return ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [dirty])
}
