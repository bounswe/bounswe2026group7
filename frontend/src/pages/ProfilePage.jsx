import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import '../styles/main.css'

export default function ProfilePage() {
  const [form, setForm] = useState({
    name: 'Övgü Su Afşar',
    department: 'Computer Engineering',
    bio: "I'm passionate about React Native and mobile development.",
  })

  function handleChange(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Profile</div></div>
      </div>

      <div className="profile-layout">
        <div>
          <div className="profile-card-hero">
            <div className="profile-avatar-lg">ÖA</div>
            <div className="profile-name">{form.name}</div>
            <div className="profile-role">Mentee · {form.department}</div>
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
        </div>

        <div className="card">
          <div className="section-label">Information</div>

          <div className="form-field">
            <label className="form-label">Full Name</label>
            <input
              className="form-input"
              type="text"
              value={form.name}
              onChange={e => handleChange('name', e.target.value)}
            />
          </div>

          <div className="form-field">
            <label className="form-label">Department</label>
            <input
              className="form-input"
              type="text"
              value={form.department}
              onChange={e => handleChange('department', e.target.value)}
            />
          </div>

          <div className="form-field">
            <label className="form-label">About Me</label>
            <textarea
              className="form-input form-textarea"
              value={form.bio}
              onChange={e => handleChange('bio', e.target.value)}
            />
          </div>

          <button className="save-btn">Save Changes</button>
        </div>
      </div>
    </MainLayout>
  )
}
