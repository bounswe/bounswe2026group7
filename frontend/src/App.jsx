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
import UserFollowListPage from './pages/UserFollowListPage'
import MentorshipDetailPage from './pages/MentorshipDetailPage'
import MentorshipsListPage from './pages/MentorshipsListPage'
import MessagesPage from './pages/MessagesPage'
import TasksPage from './pages/TasksPage'
import SchedulePage from './pages/SchedulePage'
import CalendarPage from './pages/CalendarPage'
import NotificationsPage from './pages/NotificationsPage'
import FeedPage from './pages/FeedPage'
import FeedPostDetailPage from './pages/FeedPostDetailPage'
import FeedBookmarksPage from './pages/FeedBookmarksPage'

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
          <Route path="/users/:id/followers" element={<UserFollowListPage mode="followers" />} />
          <Route path="/users/:id/following" element={<UserFollowListPage mode="following" />} />
          <Route path="/mentorships" element={<MentorshipsListPage />} />
          <Route path="/mentorships/:id" element={<MentorshipDetailPage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/feed" element={<FeedPage />} />
          <Route path="/feed/bookmarks" element={<FeedBookmarksPage />} />
          <Route path="/feed/:id" element={<FeedPostDetailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
