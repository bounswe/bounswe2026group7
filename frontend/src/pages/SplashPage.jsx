import { useNavigate } from 'react-router-dom'
import '../styles/main.css'

export default function SplashPage() {
  const navigate = useNavigate()

  return (
    <div className="splash-screen">
      <div className="splash-inner">
        <div className="play-btn">
          <svg viewBox="0 0 24 24" fill="white" width="28" height="28">
            <polygon points="5,3 19,12 5,21" />
          </svg>
        </div>
        <div className="splash-title">
          Grow with<br /><em>purpose.</em>
        </div>
        <div className="splash-sub">
          Connect with mentors who&apos;ve walked your path.<br />
          Build something meaningful together.
        </div>
        <div className="splash-btns">
          <button className="btn-primary" onClick={() => navigate('/register')}>
            Get Started
          </button>
          <button className="btn-secondary" onClick={() => navigate('/login')}>
            I already have an account
          </button>
        </div>
      </div>
    </div>
  )
}
