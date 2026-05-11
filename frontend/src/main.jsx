import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext'
import { MentorshipProvider } from './context/MentorshipContext'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <MentorshipProvider>
        <App />
      </MentorshipProvider>
    </AuthProvider>
  </StrictMode>,
)
