import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import usePushNotifications from '../hooks/usePushNotifications'

export default function ProtectedRoute() {
  const { token, isLoading } = useAuth()
  const location = useLocation()

  // #447: bootstrap web push for the authenticated session. The hook is a
  // no-op when VITE_FIREBASE_* env vars are missing or the browser lacks
  // the Push API, so this is safe to mount unconditionally on every
  // authenticated route. Lives here rather than App so it only fires once
  // we're past auth gating.
  usePushNotifications()

  if (isLoading) return null

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
