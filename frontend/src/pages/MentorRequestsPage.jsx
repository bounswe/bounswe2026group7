import { useState, useEffect } from 'react'
import { Inbox } from 'lucide-react'
import { getIncomingMentorshipRequests, getMentorCapacityInfo } from '../services/api'
import RequestCard from '../components/RequestCard'
import RequestDetailModal from '../components/RequestDetailModal'
import MainLayout from '../components/MainLayout'

export default function MentorRequestsPage() {
  const [requests, setRequests] = useState([])
  const [capacityInfo, setCapacityInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [selectedRequest, setSelectedRequest] = useState(null)
  
  // Minimal pagination state for the requirements
  const [visibleCount, setVisibleCount] = useState(10)

  useEffect(() => {
    let isMounted = true

    async function fetchData() {
      try {
        setLoading(true)
        const [reqs, cap] = await Promise.all([
          getIncomingMentorshipRequests(),
          getMentorCapacityInfo()
        ])
        if (isMounted) {
          setRequests(reqs)
          setCapacityInfo(cap)
        }
      } catch (err) {
        if (isMounted) setError(err.message || 'Failed to fetch incoming requests.')
      } finally {
        if (isMounted) setLoading(false)
      }
    }
    
    fetchData()
    return () => { isMounted = false }
  }, [])

  function handleActionSuccess(requestId) {
    setRequests(prev => prev.filter(r => r.id !== requestId))
    setSelectedRequest(null)
  }

  function loadMore() {
    setVisibleCount(prev => prev + 10)
  }

  const visibleRequests = requests.slice(0, visibleCount)

  return (
    <MainLayout>
      <div className="page-container" style={{ padding: '24px', maxWidth: '800px', margin: '0 auto' }}>
        <div style={{ paddingBottom: '20px', borderBottom: '1px solid var(--border)', marginBottom: '24px' }}>
          <h1 style={{ margin: '0 0 8px 0', fontSize: '24px', color: 'var(--text-main, #111827)' }}>Mentorship Requests</h1>
          <p style={{ margin: 0, color: 'var(--text-light, #6b7280)' }}>
            Review and manage pending requests from prospective mentees.
            {capacityInfo && (
              <span style={{ marginLeft: '8px', fontWeight: 'bold' }}>
                (Capacity: {capacityInfo.activeMentees}/{capacityInfo.maxCapacity})
              </span>
            )}
          </p>
        </div>

        {loading ? (
          <div style={{ padding: '60px 20px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '16px' }}>
            {/* Inline CSS spinner if no class is available */}
            <div style={{ 
              width: '40px', height: '40px', 
              border: '4px solid var(--border, #e5e7eb)', 
              borderTopColor: 'var(--accent, #4f46e5)', 
              borderRadius: '50%', 
              animation: 'spin 1s linear infinite' 
            }}></div>
            <style>
              {`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}
            </style>
            <div style={{ color: 'var(--text-light, #6b7280)', fontWeight: '500' }}>Loading requests...</div>
          </div>
        ) : error ? (
          <div style={{ color: '#dc2626', padding: '20px', backgroundColor: '#fef2f2', borderRadius: '8px' }}>
            {error}
          </div>
        ) : visibleRequests.length === 0 ? (
        <div style={{ 
          display: 'flex', flexDirection: 'column', alignItems: 'center', 
          padding: '60px 20px', backgroundColor: 'var(--card-bg)', 
          borderRadius: '12px', border: '1px solid var(--border)' 
        }}>
          <Inbox size={48} color="var(--border)" style={{ marginBottom: '16px' }} />
          <h3 style={{ margin: '0 0 8px 0', color: 'var(--text-main)' }}>No pending requests</h3>
          <p style={{ margin: 0, color: 'var(--text-light)', textAlign: 'center' }}>
            You're all caught up! New mentorship requests will appear here.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {visibleRequests.map(req => (
            <RequestCard 
              key={req.id} 
              request={req} 
              onView={(r) => setSelectedRequest(r)} 
            />
          ))}
          
          {visibleCount < requests.length && (
            <button 
              onClick={loadMore}
              style={{
                padding: '12px', marginTop: '8px', backgroundColor: 'transparent',
                border: '1px solid var(--border)', borderRadius: '8px',
                color: 'var(--text)', cursor: 'pointer', fontWeight: '500'
              }}
            >
              Load More
            </button>
          )}
        </div>
        )}

        {selectedRequest && (
          <RequestDetailModal 
            request={selectedRequest}
            capacityInfo={capacityInfo}
            onClose={() => setSelectedRequest(null)}
            onSuccess={handleActionSuccess}
          />
        )}
      </div>
    </MainLayout>
  )
}
