import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { staggerContainer, fadeUp, scaleIn, fadeIn } from '../components/PageTransition'
import '../styles/main.css'

export default function SplashPage() {
  const navigate = useNavigate()

  return (
    <div className="splash-screen">
        <div className="splash-circle splash-circle-top" />
        <div className="splash-circle splash-circle-bottom-left" />
        <div className="splash-circle splash-circle-bottom-right" />

        <motion.div
          className="splash-inner"
          variants={staggerContainer}
          initial="hidden"
          animate="visible"
        >
          <motion.div className="splash-icon-box" variants={scaleIn}>
            <div className="splash-play-ring">
              <svg viewBox="0 0 24 24" width="22" height="22">
                <polygon points="6,4 20,12 6,20" fill="var(--green-dark)" />
              </svg>
            </div>
          </motion.div>

          <motion.div className="splash-title" variants={fadeUp}>
            Grow with<br /><em>purpose.</em>
          </motion.div>

          <motion.div className="splash-sub" variants={fadeUp}>
            Connect with mentors who&apos;ve walked your path.<br />
            Build something meaningful together.
          </motion.div>

          <motion.div className="splash-dots" variants={fadeIn}>
            <span className="splash-dot active" />
            <span className="splash-dot" />
            <span className="splash-dot" />
          </motion.div>

          <motion.div className="splash-btns" variants={fadeUp}>
            <button className="btn-primary" onClick={() => navigate('/register')}>
              Get Started
            </button>
            <button className="btn-secondary" onClick={() => navigate('/login')}>
              I already have an account
            </button>
          </motion.div>

          <motion.div className="splash-version" variants={fadeIn}>
            MentorNet v1.0.0
          </motion.div>
        </motion.div>
      </div>
  )
}
