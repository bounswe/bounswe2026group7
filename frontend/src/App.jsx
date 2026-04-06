import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SplashPage from './pages/SplashPage'
import HomePage from './pages/HomePage'
import ProfilePage from './pages/ProfilePage'
import ExplorePage from './pages/ExplorePage'
import AvailabilityPage from './pages/AvailabilityPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ProtectedRoute from './components/ProtectedRoute'
import MentorRequestsPage from './pages/MentorRequestsPage'
import { Toaster } from 'react-hot-toast'

export default function App() {
  return (
    <BrowserRouter>
      <Toaster position="top-right" />
      <Routes>
        <Route path="/" element={<Navigate to="/splash" replace />} />
        <Route path="/splash" element={<SplashPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/home" element={<HomePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/profile/:id" element={<ProfilePage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/availability" element={<AvailabilityPage />} />
          <Route path="/mentorship-requests" element={<MentorRequestsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
