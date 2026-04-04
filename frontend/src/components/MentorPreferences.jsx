import { useState } from 'react'
import '../styles/mentor-preferences.css'

const DURATION_OPTIONS = [
  { value: 1, label: '1 Month' },
  { value: 3, label: '3 Months' },
  { value: 6, label: '6 Months' },
]

export default function MentorPreferences() {
  const [preferences, setPreferences] = useState({
    goals: '',
    criteria: '',
    duration: 3,
    maxCapacity: 5,
  })

  const [isSaving, setIsSaving] = useState(false)
  const [isExtending, setIsExtending] = useState(false)
  const [currentActiveMentees, setCurrentActiveMentees] = useState(2)

  // Mock current user - in production this would come from auth context
  const mentorName = 'Burak Afşar'

  const showToast = (message, type = 'success') => {
    const notification = document.createElement('div')
    notification.className = `toast toast-${type}`
    notification.textContent = message
    document.body.appendChild(notification)
    setTimeout(() => notification.remove(), 3500)
  }

  const handleInputChange = (e) => {
    const { name, value } = e.target
    setPreferences(prev =>({
      ...prev,
      [name]: name === 'maxCapacity' ? parseInt(value) || 0 : value,
    }))
  }

  const handleDurationChange = (value) => {
    setPreferences(prev => ({
      ...prev,
      duration: value,
    }))
  }

  const handleSavePreferences = async () => {
    if (!preferences.goals.trim() || !preferences.criteria.trim()) {
      showToast('Please fill in all fields.', 'error')
      return
    }

    if (preferences.maxCapacity < 1) {
      showToast('Max capacity must be at least 1.', 'error')
      return
    }

    setIsSaving(true)
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1500))
      showToast('Preferences saved successfully!', 'success')
      // In production: POST to /api/mentor/preferences with preferences data
    } catch (err) {
      showToast('Failed to save preferences. Please try again.', 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const handleExtendDuration = async () => {
    setIsExtending(true)
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1200))
      showToast('Mentorship duration extended for all active mentees!', 'success')
      // In production: POST to /api/mentor/extend-duration with current preferences
    } catch (err) {
      showToast('Failed to extend duration. Please try again.', 'error')
    } finally {
      setIsExtending(false)
    }
  }

  const capacityPercentage = Math.min((currentActiveMentees / preferences.maxCapacity) * 100, 100)
  const capacityStatus = currentActiveMentees >= preferences.maxCapacity ? 'full' : 'available'

  return (
    <div className="preferences-container">
      {/* Header */}
      <div className="preferences-header">
        <div>
          <h1 className="preferences-title">Mentor Preferences</h1>
          <p className="preferences-subtitle">Manage your mentorship settings and preferences</p>
        </div>
      </div>

      {/* Main Content Grid */}
      <div className="preferences-grid">
        {/* Form Section */}
        <div className="preferences-section">
          <div className="section-card">
            <div className="section-header">
              <h2 className="section-title">Mentorship Goals & Criteria</h2>
            </div>

            <form className="preferences-form">
              {/* Goals Field */}
              <div className="form-group">
                <label htmlFor="goals" className="form-label">
                  Mentoring Goals
                  <span className="required">*</span>
                </label>
                <textarea
                  id="goals"
                  name="goals"
                  className="form-textarea"
                  rows={5}
                  maxLength={500}
                  value={preferences.goals}
                  onChange={handleInputChange}
                  placeholder="What are your primary mentoring goals? e.g., Help mentees develop skills, guide career progression..."
                />
                <div className="field-meta">
                  <span className="char-count">{preferences.goals.length}/500</span>
                </div>
              </div>

              {/* Criteria Field */}
              <div className="form-group">
                <label htmlFor="criteria" className="form-label">
                  Preferred Mentee Criteria
                  <span className="required">*</span>
                </label>
                <textarea
                  id="criteria"
                  name="criteria"
                  className="form-textarea"
                  rows={5}
                  maxLength={500}
                  value={preferences.criteria}
                  onChange={handleInputChange}
                  placeholder="What type of mentees are you looking for? e.g., Early-career developers, focused on mobile development..."
                />
                <div className="field-meta">
                  <span className="char-count">{preferences.criteria.length}/500</span>
                </div>
              </div>

              {/* Duration & Capacity Row */}
              <div className="form-row">
                {/* Duration */}
                <div className="form-group">
                  <label htmlFor="duration" className="form-label">
                    Default Mentorship Duration
                    <span className="required">*</span>
                  </label>
                  <div className="duration-selector">
                    {DURATION_OPTIONS.map(option => (
                      <button
                        key={option.value}
                        type="button"
                        className={`duration-btn${preferences.duration === option.value ? ' active' : ''}`}
                        onClick={() => handleDurationChange(option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Max Capacity */}
                <div className="form-group">
                  <label htmlFor="maxCapacity" className="form-label">
                    Max Mentee Capacity
                    <span className="required">*</span>
                  </label>
                  <input
                    id="maxCapacity"
                    type="number"
                    name="maxCapacity"
                    className="form-input"
                    min="1"
                    max="20"
                    value={preferences.maxCapacity}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              {/* Save Button */}
              <div className="form-actions">
                <button
                  type="button"
                  className="save-btn"
                  onClick={handleSavePreferences}
                  disabled={isSaving}
                >
                  {isSaving ? 'Saving...' : 'Save Preferences'}
                </button>
              </div>
            </form>
          </div>
        </div>

        {/* Sidebar Section */}
        <div className="preferences-sidebar">
          {/* Capacity Card */}
          <div className="sidebar-card capacity-card">
            <h3 className="card-title">Active Mentees</h3>
            <div className="capacity-info">
              <div className="capacity-number">
                <span className="current">{currentActiveMentees}</span>
                <span className="separator">/</span>
                <span className="max">{preferences.maxCapacity}</span>
              </div>
              <p className="capacity-label">Current / Maximum</p>
            </div>

            <div className="capacity-bar">
              <div
                className={`capacity-fill ${capacityStatus}`}
                style={{ width: `${capacityPercentage}%` }}
              />
            </div>

            <div className="capacity-status">
              {capacityStatus === 'full' ? (
                <span className="status-full">⚠️ At Capacity</span>
              ) : (
                <span className="status-available">
                  ✓ {preferences.maxCapacity - currentActiveMentees} spot{preferences.maxCapacity - currentActiveMentees !== 1 ? 's' : ''} available
                </span>
              )}
            </div>
          </div>

          {/* Duration Extension Card */}
          <div className="sidebar-card extend-card">
            <h3 className="card-title">Extend Duration</h3>
            <p className="card-description">
              Quickly extend mentorship duration for all your current mentees by {preferences.duration} month{preferences.duration !== 1 ? 's' : ''}.
            </p>

            <button
              type="button"
              className={`extend-btn${isExtending ? ' loading' : ''}`}
              onClick={handleExtendDuration}
              disabled={isExtending || currentActiveMentees === 0}
            >
              {isExtending ? 'Extending...' : `Extend All (${preferences.duration}M)`}
            </button>

            {currentActiveMentees === 0 && (
              <p className="card-hint">
                💡 You have no active mentees to extend.
              </p>
            )}
          </div>

          {/* Info Card */}
          <div className="sidebar-card info-card">
            <h3 className="card-title">Tips</h3>
            <ul className="tips-list">
              <li>Set clear mentorship goals to attract aligned mentees</li>
              <li>Define your criteria to ensure good matches</li>
              <li>Adjust capacity based on your schedule and availability</li>
              <li>Extend duration proactively to maintain continuity</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}
