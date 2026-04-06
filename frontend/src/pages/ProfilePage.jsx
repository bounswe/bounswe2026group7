import { useState, useEffect } from 'react'
import MainLayout from '../components/MainLayout'
import { useAuth } from '../context/AuthContext'
import { getOwnProfile, updateOwnProfile } from '../services/api'
import '../styles/main.css'

function mapResponseToForm(data) {
  const isMentor = data.role === 'MENTOR'
  const base = {
    name: [data.firstName, data.lastName].filter(Boolean).join(' '),
    profilePhoto: data.profilePhoto || '',
    interests: (data.interests || []).join(', '),
  }
  if (isMentor) {
    return {
      ...base,
      bio: data.bio || '',
      field: data.field || '',
      expertise: data.expertise || '',
      affiliation: data.affiliation || '',
      maxMenteeCapacity: data.maxMenteeCapacity != null ? String(data.maxMenteeCapacity) : '',
      preferredMenteeMajor: data.preferredMenteeMajor || '',
      mentoringGoals: data.mentoringGoals || '',
      mentorshipDuration: data.mentorshipDuration != null ? String(data.mentorshipDuration) : '',
      preferredMenteeSkills: (data.preferredMenteeSkills || []).join(', '),
    }
  }
  return {
    ...base,
    background: data.backgroundInfo || '',
    goals: data.goals || '',
    skills: (data.skills || []).join(', '),
    major: data.major || '',
    careerInterest: data.careerInterest || '',
    meetingFreqPref: data.meetingFreqPref || '',
    profileVisible: data.profileVisibility !== false,
  }
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

function ViewField({ label, value, visible, chips = false }) {
  const items = chips && value ? value.split(',').map(s => s.trim()).filter(Boolean) : []
  return (
    <div style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '6px' }}>
        <span style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '1px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
          {label}
        </span>
        {visible !== undefined && <PrivacyBadge visible={visible} />}
      </div>
      {chips && items.length > 0 ? (
        <div className="profile-chips">
          {items.map(item => <span className="profile-chip" key={item}>{item}</span>)}
        </div>
      ) : (
        <div style={{ fontSize: '14px', color: 'var(--text-mid)', lineHeight: 1.6 }}>
          {value || <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Not set</span>}
        </div>
      )}
    </div>
  )
}

export default function ProfilePage() {
  const { role, setProfileData } = useAuth()
  const isMentor = role === 'MENTOR'

  const [form, setForm] = useState(null)
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [saveError, setSaveError] = useState('')

  useEffect(() => {
    getOwnProfile()
      .then(data => {
        setForm(mapResponseToForm(data))
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }, [])

  function handleChange(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
    setErrors(prev => ({ ...prev, [field]: '' }))
  }

  function validate() {
    const e = {}
    if (!form.name?.trim()) e.name = 'Name is required.'
    if (form.name?.trim().length > 100) e.name = 'Name must be under 100 characters.'
    return e
  }

  async function handleSave(e) {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setSaving(true)
    setSaveError('')
    setSaveSuccess(false)

    try {
      const nameParts = form.name.trim().split(' ')
      const firstName = nameParts[0]
      const lastName = nameParts.slice(1).join(' ') || nameParts[0]

      const toList = str => str ? str.split(',').map(s => s.trim()).filter(Boolean) : null

      const payload = {
        firstName,
        lastName,
        profilePhoto: form.profilePhoto || null,
        interests: toList(form.interests),
        ...(isMentor ? {
          bio: form.bio || null,
          field: form.field || null,
          expertise: form.expertise || null,
          affiliation: form.affiliation || null,
          maxMenteeCapacity: form.maxMenteeCapacity !== '' ? parseInt(form.maxMenteeCapacity) : null,
          preferredMenteeMajor: form.preferredMenteeMajor || null,
          mentoringGoals: form.mentoringGoals || null,
          mentorshipDuration: form.mentorshipDuration !== '' ? parseInt(form.mentorshipDuration) : null,
          preferredMenteeSkills: toList(form.preferredMenteeSkills),
        } : {
          backgroundInfo: form.background || null,
          goals: form.goals || null,
          major: form.major || null,
          careerInterest: form.careerInterest || null,
          meetingFreqPref: form.meetingFreqPref || null,
          skills: toList(form.skills),
          profileVisibility: form.profileVisible,
        }),
      }

      const updated = await updateOwnProfile(payload)
      setProfileData(updated.firstName, updated.lastName, updated.profilePhoto)
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 3000)
    } catch (err) {
      setSaveError(err.message || 'Failed to save profile.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div className="page-header"><div><div className="page-title">Profile</div></div></div>
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading profile...</div>
      </MainLayout>
    )
  }

  if (!form) {
    return (
      <MainLayout>
        <div className="page-header"><div><div className="page-title">Profile</div></div></div>
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--red-text)' }}>Failed to load profile.</div>
      </MainLayout>
    )
  }

  const initials = form.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Profile</div></div>
      </div>

      <div className="profile-layout">

        {/* Left panel — view */}
        <div>
          <div className="profile-card-hero">
            {form.profilePhoto
              ? <img src={form.profilePhoto} alt={initials} className="profile-avatar-lg" style={{ objectFit: 'cover' }} />
              : <div className="profile-avatar-lg">{initials}</div>
            }
            <div className="profile-name">{form.name}</div>
            <div className="profile-role">{isMentor ? 'Mentor' : 'Mentee'}</div>
          </div>

          <div className="card">
            <div className="section-label">Profile Info</div>
            {isMentor ? (
              <>
                <ViewField label="Bio" value={form.bio} />
                <ViewField label="Field" value={form.field} />
                <ViewField label="Expertise" value={form.expertise} />
                <ViewField label="Affiliation" value={form.affiliation} />
                <ViewField label="Interests" value={form.interests} chips />
                <ViewField label="Mentoring Goals" value={form.mentoringGoals} />
                <ViewField label="Preferred Mentee Major" value={form.preferredMenteeMajor} />
                <ViewField label="Preferred Mentee Skills" value={form.preferredMenteeSkills} chips />
                <ViewField label="Max Mentees" value={form.maxMenteeCapacity} />
                <ViewField label="Mentorship Duration" value={form.mentorshipDuration ? `${form.mentorshipDuration} months` : ''} />
              </>
            ) : (
              <>
                <ViewField label="Background" value={form.background} visible={form.profileVisible} />
                <ViewField label="Goals" value={form.goals} visible={form.profileVisible} />
                <ViewField label="Skills" value={form.skills} visible={form.profileVisible} chips />
                <ViewField label="Interests" value={form.interests} visible={form.profileVisible} chips />
                <ViewField label="Major" value={form.major} visible={form.profileVisible} />
                <ViewField label="Career Interest" value={form.careerInterest} visible={form.profileVisible} />
                <ViewField label="Meeting Preference" value={form.meetingFreqPref} visible={form.profileVisible} />
              </>
            )}
          </div>
        </div>

        {/* Right panel — edit */}
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
              <label className="form-label">Profile Photo URL</label>
              <input
                className="form-input"
                type="url"
                value={form.profilePhoto}
                onChange={e => handleChange('profilePhoto', e.target.value)}
                placeholder="https://example.com/photo.jpg"
              />
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
            </div>

            {isMentor ? (
              <>
                <div className="form-field">
                  <label className="form-label">Bio</label>
                  <textarea
                    className="form-input form-textarea"
                    maxLength={1000}
                    value={form.bio}
                    onChange={e => handleChange('bio', e.target.value)}
                    placeholder="Short bio about yourself..."
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Field</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.field}
                    onChange={e => handleChange('field', e.target.value)}
                    placeholder="e.g. Computer Science"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Expertise</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.expertise}
                    onChange={e => handleChange('expertise', e.target.value)}
                    placeholder="e.g. Backend Development"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Affiliation</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.affiliation}
                    onChange={e => handleChange('affiliation', e.target.value)}
                    placeholder="e.g. Boğaziçi University"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Mentoring Goals</label>
                  <textarea
                    className="form-input form-textarea"
                    value={form.mentoringGoals}
                    onChange={e => handleChange('mentoringGoals', e.target.value)}
                    placeholder="What do you want to help mentees achieve..."
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Preferred Mentee Major</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.preferredMenteeMajor}
                    onChange={e => handleChange('preferredMenteeMajor', e.target.value)}
                    placeholder="e.g. Computer Engineering"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Preferred Mentee Skills</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.preferredMenteeSkills}
                    onChange={e => handleChange('preferredMenteeSkills', e.target.value)}
                    placeholder="e.g. Java, Python"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Max Mentee Capacity</label>
                  <input
                    className="form-input"
                    type="number"
                    min={0}
                    value={form.maxMenteeCapacity}
                    onChange={e => handleChange('maxMenteeCapacity', e.target.value)}
                    placeholder="e.g. 3"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Mentorship Duration (months)</label>
                  <input
                    className="form-input"
                    type="number"
                    min={1}
                    value={form.mentorshipDuration}
                    onChange={e => handleChange('mentorshipDuration', e.target.value)}
                    placeholder="e.g. 3"
                  />
                </div>
              </>
            ) : (
              <>
                <div className="form-field">
                  <label className="form-label">Background</label>
                  <textarea
                    className="form-input form-textarea"
                    value={form.background}
                    onChange={e => handleChange('background', e.target.value)}
                    placeholder="Your educational and professional background..."
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Goals</label>
                  <textarea
                    className="form-input form-textarea"
                    value={form.goals}
                    onChange={e => handleChange('goals', e.target.value)}
                    placeholder="What do you want to achieve..."
                  />
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
                </div>

                <div className="form-field">
                  <label className="form-label">Major</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.major}
                    onChange={e => handleChange('major', e.target.value)}
                    placeholder="e.g. Computer Engineering"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Career Interest</label>
                  <input
                    className="form-input"
                    type="text"
                    value={form.careerInterest}
                    onChange={e => handleChange('careerInterest', e.target.value)}
                    placeholder="e.g. Data Science"
                  />
                </div>

                <div className="form-field">
                  <label className="form-label">Meeting Frequency Preference</label>
                  <select
                    className="form-input"
                    value={form.meetingFreqPref}
                    onChange={e => handleChange('meetingFreqPref', e.target.value)}
                  >
                    <option value="">Not set</option>
                    <option value="Weekly">Weekly</option>
                    <option value="Bi-weekly">Bi-weekly</option>
                    <option value="Monthly">Monthly</option>
                  </select>
                </div>

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
              </>
            )}

            {saveSuccess && (
              <div style={{ color: 'var(--green-dark)', fontSize: '13px', marginBottom: '8px' }}>
                Profile saved successfully.
              </div>
            )}
            {saveError && (
              <div style={{ color: 'var(--red-text)', fontSize: '13px', marginBottom: '8px' }}>
                {saveError}
              </div>
            )}

            <button type="submit" className="save-btn" style={{ marginTop: '16px' }} disabled={saving}>
              {saving ? 'Saving...' : 'Save Changes'}
            </button>
          </form>
        </div>

      </div>
    </MainLayout>
  )
}
