import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SplashPage from './pages/SplashPage'
import HomePage from './pages/HomePage'
import ExplorePage from './pages/ExplorePage'
import MessagesPage from './pages/MessagesPage'
import TasksPage from './pages/TasksPage'
import SchedulePage from './pages/SchedulePage'
import AvailabilityPage from './pages/AvailabilityPage'
import RatePage from './pages/RatePage'
import ProfilePage from './pages/ProfilePage'
import ProtectedRoute from './components/ProtectedRoute'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/splash" replace />} />
        <Route path="/splash" element={<SplashPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/home" element={<HomePage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/availability" element={<AvailabilityPage />} />
          <Route path="/rate" element={<RatePage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
