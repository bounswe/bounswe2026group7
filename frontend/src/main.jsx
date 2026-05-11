import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { MotionConfig } from 'framer-motion'
import './index.css'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext'
import { MentorshipProvider } from './context/MentorshipContext'

// reducedMotion="user" makes every framer-motion component respect the
// OS-level prefers-reduced-motion preference (and the Playwright CI flag
// the e2e suite now sets). Without this wrap, motion.div tweens still
// run even when the browser reports reduced-motion, which is what was
// stretching AT-02's Firefox login click past its 40 s navigation budget.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <AuthProvider>
        <MentorshipProvider>
          <App />
        </MentorshipProvider>
      </AuthProvider>
    </MotionConfig>
  </StrictMode>,
)
