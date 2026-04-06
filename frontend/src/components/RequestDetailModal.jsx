import { useState } from 'react'
import { CheckCircle, XCircle, Info, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { acceptMentorshipRequest, declineMentorshipRequest } from '../services/api'

export default function RequestDetailModal({ request, onClose, capacityInfo, onSuccess }) {
  const [loadingAction, setLoadingAction] = useState(null) // 'accept' | 'decline' | null
  
  const isAtCapacity = capacityInfo && capacityInfo.activeMentees >= capacityInfo.maxCapacity
  const menteeName = request.menteeFirstName || 'Mentee'

  async function handleAction(action) {
    setLoadingAction(action)
    try {
      if (action === 'accept') {
        if (isAtCapacity) throw new Error('Maximum capacity reached')
        await acceptMentorshipRequest(request.id)
        toast.success(`${menteeName}'s request accepted!`)
      } else {
        await declineMentorshipRequest(request.id)
        toast.success(`Request declined.`)
      }
      onSuccess(request.id) // Remove from UI
    } catch (err) {
      toast.error(err.message || `Failed to ${action} request.`)
    } finally {
      setLoadingAction(null)
    }
  }

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.65)', zIndex: 100,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px',
      backdropFilter: 'blur(3px)'
    }}>
      <div className="modal-content" style={{
        backgroundColor: 'var(--card-bg, #ffffff)', border: '1px solid var(--border, #e5e7eb)',
        borderRadius: '12px', width: '100%', maxWidth: '500px',
        maxHeight: '90vh', overflowY: 'auto', display: 'flex', flexDirection: 'column',
        boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
      }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px', borderBottom: '1px solid var(--border)' }}>
          <h2 style={{ margin: 0, fontSize: '20px', display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '32px', height: '32px', borderRadius: '50%',
              backgroundColor: 'var(--accent)', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '16px'
            }}>
              {menteeName.charAt(0).toUpperCase()}
            </div>
            Request from {menteeName}
          </h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-light)' }}>
            <X size={24} />
          </button>
        </div>

        {/* Body */}
        <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h4 style={{ margin: '0 0 8px 0', color: 'var(--text-light)', fontSize: '14px', textTransform: 'uppercase', letterSpacing: '1px' }}>Background</h4>
            <p style={{ margin: 0, color: 'var(--text)', lineHeight: '1.6' }}>{request.background || 'No background provided.'}</p>
          </div>

          <div>
            <h4 style={{ margin: '0 0 8px 0', color: 'var(--text-light)', fontSize: '14px', textTransform: 'uppercase', letterSpacing: '1px' }}>Interests</h4>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {(request.interests || []).map((interest, idx) => (
                <span key={idx} style={{ 
                  display: 'inline-block', padding: '4px 10px', 
                  backgroundColor: '#f3f4f6', color: '#374151', 
                  borderRadius: '16px', fontSize: '13px', fontWeight: '500' 
                }}>
                  {interest}
                </span>
              ))}
              {(!request.interests || request.interests.length === 0) && <span style={{ color: 'var(--text-light)', fontStyle: 'italic' }}>No interests specified.</span>}
            </div>
          </div>

          <div style={{ backgroundColor: 'var(--card-bg)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border)' }}>
            <h4 style={{ margin: '0 0 8px 0', color: 'var(--text-light)', fontSize: '14px', textTransform: 'uppercase', letterSpacing: '1px' }}>Message</h4>
            <p style={{ margin: 0, color: 'var(--text)', fontStyle: 'italic', lineHeight: '1.6' }}>
              "{request.message}"
            </p>
          </div>
        </div>

        {/* Action Footer */}
        <div style={{ padding: '20px', borderTop: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {isAtCapacity && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#b45309', backgroundColor: '#fef3c7', padding: '12px', borderRadius: '8px', fontSize: '14px' }}>
              <Info size={18} />
              <span>You have reached your maximum active mentee capacity ({capacityInfo.maxCapacity}). To accept new mentees, please adjust your capacity settings or conclude an active mentorship.</span>
            </div>
          )}
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              onClick={() => handleAction('decline')}
              disabled={loadingAction !== null}
              style={{
                flex: 1, padding: '12px', borderRadius: '8px', border: '1px solid var(--border)',
                background: 'var(--card-bg)', color: 'var(--text)', cursor: loadingAction !== null ? 'not-allowed' : 'pointer',
                display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px',
                fontWeight: '600'
              }}
            >
              {loadingAction === 'decline' ? <span className="spinner-small" /> : <XCircle size={18} />} 
              Decline
            </button>
            <button
              onClick={() => handleAction('accept')}
              disabled={loadingAction !== null || isAtCapacity}
              style={{
                flex: 1, padding: '12px', borderRadius: '8px', border: 'none',
                background: isAtCapacity ? 'var(--border)' : 'var(--accent)', color: '#fff', 
                cursor: (loadingAction !== null || isAtCapacity) ? 'not-allowed' : 'pointer',
                display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px',
                fontWeight: '600', opacity: isAtCapacity ? 0.6 : 1
              }}
              title={isAtCapacity ? 'Capacity full' : 'Accept request'}
            >
              {loadingAction === 'accept' ? <span className="spinner-small" /> : <CheckCircle size={18} />}
              Accept
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
