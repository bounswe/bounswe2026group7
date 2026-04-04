import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const MENTEE_DEFAULTS = {
  name: 'Övgü Su Afşar',
  background: 'Computer Engineering student at Boğaziçi University, 3rd year.',
  goals: 'Become a proficient mobile developer and land a job at a top tech company.',
  skills: 'JavaScript, React, Python',
  interests: 'Mobile Development, AI/ML, Open Source',
  profileVisible: true,
}

const MENTOR_DEFAULTS = {
  name: 'Burak Afşar',
  background: 'Senior iOS Developer at Apple with 8 years of industry experience.',
  goals: 'Help young developers grow their skills and successfully enter the industry.',
  skills: 'Swift, iOS, Xcode, React Native',
  interests: 'Mobile Development, Mentoring, Tech Education',
  mentoringPreferences: 'Prefer mentees interested in mobile development.',
  profileVisible: true,
  mentorshipDuration: 3,
  maxCapacity: 3,
  currentActiveMentees: 1,
}

function PrivacyBadge({ visible }) {
  return (
    <span style={{
      display: 'inline-block',
      padding: '2px 8px',
      borderRadius: '20px',
      fontSize: '11px',
      fontWeight: 600,
      background: visible ? 'var(--green-pale)' : '#f3f4f3',
      color: visible ? 'var(--green-dark)' : 'var(--text-muted)',
      marginLeft: '8px',
    }}>
      {visible ? '🔓 Public' : '🔒 Private'}
    </span>
  )
}

function ViewField({ label, value, visible }) {
  return (
    <div style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '4px' }}>
        <span style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '1px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
          {label}
        </span>
        <PrivacyBadge visible={visible} />
      </div>
      <div style={{ fontSize: '14px', color: 'var(--text-mid)', lineHeight: 1.6 }}>
        {value || <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Not set</span>}
      </div>
    </div>
  )
}

export default function ProfilePage() {
  const { role } = useAuth()
  const isMentor = role === 'MENTOR'
  const defaults = isMentor ? MENTOR_DEFAULTS : MENTEE_DEFAULTS

  const [form, setForm] = useState({ ...defaults })
  const [errors, setErrors] = useState({})

  function handleChange(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
    setErrors(prev => ({ ...prev, [field]: '' }))
  }

  function validate() {
    const e = {}
    if (!form.name.trim()) e.name = 'Name is required.'
    if (form.name.trim().length > 100) e.name = 'Name must be under 100 characters.'
    if (!form.background.trim()) e.background = 'Background is required.'
    if (form.background.trim().length > 500) e.background = 'Background must be under 500 characters.'
    if (!form.goals.trim()) e.goals = 'Goals are required.'
    if (form.goals.trim().length > 500) e.goals = 'Goals must be under 500 characters.'
    if (!form.skills.trim()) e.skills = 'Skills are required.'
    if (!form.interests.trim()) e.interests = 'Interests are required.'
    if (isMentor && !form.mentoringPreferences?.trim()) e.mentoringPreferences = 'Mentoring preferences are required.'
    return e
  }

  function handleSave(e) {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }
    // TODO: connect to API
  }

  const initials = form.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()

  const capacityPercentage = isMentor ? Math.min((form.currentActiveMentees / form.maxCapacity) * 100, 100) : 0

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Profile</div></div>
      </div>

      <div className="profile-layout">

        {/* Left panel — view (#98) */}
        <div>
          <div className="profile-card-hero">
            <div className="profile-avatar-lg">{initials}</div>
            <div className="profile-name">{form.name}</div>
            <div className="profile-role">{isMentor ? 'Mentor' : 'Mentee'}</div>
            <div className="profile-badge">Active Mentorship: 1</div>
          </div>

          <div className="profile-stats">
            <div className="ps-item">
              <div className="ps-num">12</div>
              <div className="ps-lbl">Tasks</div>
            </div>
            <div className="ps-item">
              <div className="ps-num">3</div>
              <div className="ps-lbl">Meetings</div>
            </div>
            <div className="ps-item">
              <div className="ps-num">4.8</div>
              <div className="ps-lbl">Rating</div>
            </div>
          </div>

          <div className="card">
            <div className="section-label">Profile Info</div>
            <ViewField label="Background" value={form.background} visible={form.profileVisible} />
            <ViewField label="Goals" value={form.goals} visible={form.profileVisible} />
            <ViewField label="Skills" value={form.skills} visible={form.profileVisible} />
            <ViewField label="Interests" value={form.interests} visible={form.profileVisible} />
            {isMentor && (
              <>
                <ViewField label="Mentoring Preferences" value={form.mentoringPreferences} visible={form.profileVisible} />
                <ViewField label="Mentorship Duration" value={`${form.mentorshipDuration} Month${form.mentorshipDuration !== 1 ? 's' : ''}`} visible={form.profileVisible} />
              </>
            )}
          </div>
        </div>

        {/* Right panel — edit (#100) */}
        <div className="card">
          <div className="section-label">Edit Information</div>

          <form onSubmit={handleSave} noValidate>
            <div className="form-field">
              <label className="form-label">Full Name</label>
              <input
                className="form-input"
                type="text"
                maxLength={100}
                value={form.name}
                onChange={e => handleChange('name', e.target.value)}
              />
              {errors.name && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.name}</div>}
            </div>

            <div className="form-field">
              <label className="form-label">Background</label>
              <textarea
                className="form-input form-textarea"
                maxLength={500}
                value={form.background}
                onChange={e => handleChange('background', e.target.value)}
                placeholder="Your educational and professional background..."
              />
              {errors.background && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.background}</div>}
            </div>

            <div className="form-field">
              <label className="form-label">Goals</label>
              <textarea
                className="form-input form-textarea"
                maxLength={500}
                value={form.goals}
                onChange={e => handleChange('goals', e.target.value)}
                placeholder="What do you want to achieve..."
              />
              {errors.goals && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.goals}</div>}
            </div>

            <div className="form-field">
              <label className="form-label">Skills</label>
              <input
                className="form-input"
                type="text"
                value={form.skills}
                onChange={e => handleChange('skills', e.target.value)}
                placeholder="e.g. JavaScript, React, Python"
              />
              {errors.skills && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.skills}</div>}
            </div>

            <div className="form-field">
              <label className="form-label">Interests</label>
              <input
                className="form-input"
                type="text"
                value={form.interests}
                onChange={e => handleChange('interests', e.target.value)}
                placeholder="e.g. Mobile Development, AI/ML"
              />
              {errors.interests && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.interests}</div>}
            </div>

            {isMentor && (
              <div className="form-field">
                <label className="form-label">Mentoring Preferences</label>
                <textarea
                  className="form-input form-textarea"
                  maxLength={500}
                  value={form.mentoringPreferences || ''}
                  onChange={e => handleChange('mentoringPreferences', e.target.value)}
                  placeholder="Preferred mentee criteria, mentoring style, duration..."
                />
                {errors.mentoringPreferences && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '4px' }}>{errors.mentoringPreferences}</div>}
              </div>
            )}

            {isMentor && (
              <>
                {/* Mentorship Duration */}
                <div className="form-field">
                  <label className="form-label">Mentorship Duration</label>
                  <div className="duration-selector">
                    {[1, 3, 6].map(month => (
                      <button
                        key={month}
                        type="button"
                        className={`duration-btn${form.mentorshipDuration === month ? ' active' : ''}`}
                        onClick={() => handleChange('mentorshipDuration', month)}
                      >
                        {month}M
                      </button>
                    ))}
                  </div>
                </div>

                {/* Max Capacity */}
                <div className="form-field">
                  <label className="form-label">Max Mentee Capacity</label>
                  <input
                    className="form-input"
                    type="number"
                    min="1"
                    max="20"
                    value={form.maxCapacity}
                    onChange={e => handleChange('maxCapacity', parseInt(e.target.value) || 1)}
                  />
                </div>

                {/* Active Mentees Tracker */}
                <div className="form-field">
                  <label className="form-label">Active Mentees</label>
                  <div className="capacity-display">
                    <div className="capacity-number">
                      <span className="current">{form.currentActiveMentees}</span>
                      <span className="separator">/</span>
                      <span className="max">{form.maxCapacity}</span>
                    </div>
                    <div className="capacity-bar">
                      <div
                        className="capacity-fill"
                        style={{ width: `${capacityPercentage}%` }}
                      />
                    </div>
                    <div className="capacity-status">
                      {form.currentActiveMentees >= form.maxCapacity ? (
                        <span className="status-full">⚠️ At Capacity</span>
                      ) : (
                        <span className="status-available">
                          ✓ {form.maxCapacity - form.currentActiveMentees} spot{form.maxCapacity - form.currentActiveMentees !== 1 ? 's' : ''} available
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </>
            )}

            <div className="divider" />

            <div className="section-label">Privacy Settings</div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 0' }}>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 500 }}>Profile Visibility</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                  {form.profileVisible ? 'Your profile is visible to matched users' : 'Your profile is hidden'}
                </div>
              </div>
              <button
                type="button"
                className={`toggle${form.profileVisible ? '' : ' off'}`}
                onClick={() => handleChange('profileVisible', !form.profileVisible)}
                aria-label="Toggle profile visibility"
              />
            </div>

            <button type="submit" className="save-btn" style={{ marginTop: '16px' }}>
              Save Changes
            </button>
          </form>
        </div>

      </div>
    </MainLayout>
  )
}
