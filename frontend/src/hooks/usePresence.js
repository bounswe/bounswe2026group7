import { useState, useEffect } from 'react'

export default function usePresence() {
  const [status, setStatus] = useState(
    document.visibilityState === 'visible' ? 'online' : 'away'
  )

  useEffect(() => {
    function sync() {
      setStatus(document.visibilityState === 'visible' ? 'online' : 'away')
    }
    document.addEventListener('visibilitychange', sync)
    window.addEventListener('focus', sync)
    window.addEventListener('blur', sync)
    return () => {
      document.removeEventListener('visibilitychange', sync)
      window.removeEventListener('focus', sync)
      window.removeEventListener('blur', sync)
    }
  }, [])

  return status
}
