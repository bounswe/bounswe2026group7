import '../styles/main.css'

/**
 * Reusable avatar circle.
 *
 * Props:
 *   src        — photo URL (optional)
 *   initials   — fallback text
 *   size       — 'sm' | 'md' | 'lg'  (default 'md')
 *   status     — 'online' | 'away' | null  (null = no dot/ring)
 *   className  — extra classes
 */
export default function Avatar({ src, initials = '?', size = 'md', status = null, className = '' }) {
  return (
    <div className={`av av--${size}${status ? ` av--${status}` : ''} ${className}`}>
      <span className="av-inner">
        {src
          ? <img src={src} alt={initials} />
          : initials
        }
      </span>
      {status && <span className="av-ring" />}
      {status && <span className={`av-dot av-dot--${status}`} />}
    </div>
  )
}
