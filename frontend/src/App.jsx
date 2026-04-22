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
import UserProfilePage from './pages/UserProfilePage'
import MentorshipDetailPage from './pages/MentorshipDetailPage'
import MentorshipMeetingsPage from './pages/MentorshipMeetingsPage'
import MentorshipTasksPage from './pages/MentorshipTasksPage'
import MessagesPage from './pages/MessagesPage'

export default function App() {
  return (
    <BrowserRouter>
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
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/availability" element={<AvailabilityPage />} />
          <Route path="/users/:id" element={<UserProfilePage />} />
          <Route path="/mentorships/:id" element={<MentorshipDetailPage />} />
          <Route path="/mentorships/:id/meetings" element={<MentorshipMeetingsPage />} />
          <Route path="/mentorships/:id/tasks" element={<MentorshipTasksPage />} />
          <Route path="/messages" element={<MessagesPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
