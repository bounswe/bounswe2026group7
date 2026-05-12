import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Variant of ProtectedRoute. Requires an authenticated session AND role==='ADMIN';
// anyone else is bounced to /home (not /login) so a logged-in non-admin doesn't
// see a confusing auth screen for a route they merely lack permission to view.
export default function AdminRoute() {
  const { token, role, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return null

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (role !== 'ADMIN') {
    return <Navigate to="/home" replace />
  }

  return <Outlet />
}
