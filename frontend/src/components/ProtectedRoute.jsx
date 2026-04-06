import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function ProtectedRoute() {
  const { token, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return null // Provide a brief loading state while rehydrating auth
  }

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
