import { useCallback, useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import {
  sendAdminDirectMessage,
  listAdminUsers,
  banAdminUser,
  unbanAdminUser,
  clearBotFlag,
  listAdminReports,
  updateAdminReportStatus,
  getAdminDirectInbox,
  broadcastAdminMessage,
  listAdminBroadcasts,
} from '../services/api'
import { showTransientToast } from '../utils/toast'
import '../styles/main.css'

/**
 * Admin console (#279). Three tabs:
 *  1. Users — paginated list with role / ban / keyword filters, drill-in
 *     actions (ban, unban, clear bot flag).
 *  2. Reports — moderation queue with status / target-type filters, drill-in
 *     status transitions.
 *  3. Messages — direct-message composer (shipped earlier via #573).
 *
 * Page is gated server-side by hasRole('ADMIN') on every endpoint and
 * client-side by AdminRoute. Existing test IDs preserved for the unit
 * tests added in #573.
 */

const TABS = [
  { key: 'users', label: 'Users' },
  { key: 'reports', label: 'Reports' },
  { key: 'messages', label: 'Messages' },
]

export default function AdminConsolePage() {
  const [tab, setTab] = useState('users')

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Admin Console</div>
          <div className="page-sub">User moderation, report review, and admin messaging.</div>
        </div>
      </div>

      <div className="feed-tabs" role="tablist" aria-label="Admin sections">
        {TABS.map(t => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`feed-tab${tab === t.key ? ' feed-tab--active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'users' && <UsersTab />}
      {tab === 'reports' && <ReportsTab />}
      {tab === 'messages' && <MessagesTab />}
    </MainLayout>
  )
}

// ── Users tab ─────────────────────────────────────────────────────────

function UsersTab() {
  const [roleFilter, setRoleFilter] = useState('')
  const [banFilter, setBanFilter] = useState('')
  const [q, setQ] = useState('')
  const [draftQ, setDraftQ] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], totalPages: 1, totalElements: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busyUserId, setBusyUserId] = useState(null)
  const [banTarget, setBanTarget] = useState(null) // { id, name }
  const [banReason, setBanReason] = useState('')
  const [banHours, setBanHours] = useState(168)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await listAdminUsers({
        role: roleFilter || undefined,
        banStatus: banFilter || undefined,
        q: q || undefined,
        page,
        size: 20,
      })
      setData(res || { content: [], totalPages: 1, totalElements: 0 })
    } catch (err) {
      setError(err?.message || 'Failed to load users.')
      setData({ content: [], totalPages: 1, totalElements: 0 })
    } finally {
      setLoading(false)
    }
  }, [roleFilter, banFilter, q, page])

  useEffect(() => { load() }, [load])

  function applySearch(e) {
    e.preventDefault()
    setPage(0)
    setQ(draftQ.trim())
  }

  function openBanModal(u) {
    setBanTarget({ id: u.id, name: [u.firstName, u.lastName].filter(Boolean).join(' ') || `user ${u.id}` })
    setBanReason('')
    setBanHours(168)
  }

  async function confirmBan() {
    if (!banTarget) return
    if (!banReason.trim() || !Number.isFinite(Number(banHours)) || Number(banHours) < 1) return
    setBusyUserId(banTarget.id)
    try {
      await banAdminUser(banTarget.id, { reason: banReason.trim(), durationHours: Number(banHours) })
      showTransientToast(`${banTarget.name} banned for ${banHours}h.`)
      setBanTarget(null)
      load()
    } catch (err) {
      showTransientToast(err?.message || 'Failed to ban user.')
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleUnban(u) {
    const name = [u.firstName, u.lastName].filter(Boolean).join(' ') || `user ${u.id}`
    if (!window.confirm(`Lift the active ban for ${name}?`)) return
    setBusyUserId(u.id)
    try {
      await unbanAdminUser(u.id)
      showTransientToast(`${name} unbanned.`)
      load()
    } catch (err) {
      showTransientToast(err?.message || 'Failed to unban user.')
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleClearBot(u) {
    const name = [u.firstName, u.lastName].filter(Boolean).join(' ') || `user ${u.id}`
    setBusyUserId(u.id)
    try {
      await clearBotFlag(u.id)
      showTransientToast(`Suspected-bot flag cleared for ${name}.`)
      load()
    } catch (err) {
      showTransientToast(err?.message || 'Failed to clear flag.')
    } finally {
      setBusyUserId(null)
    }
  }

  return (
    <div data-testid="admin-users-tab">
      <form
        className="feed-search"
        onSubmit={applySearch}
        style={{ marginBottom: '12px' }}
      >
        <input
          type="text"
          className="feed-search-input"
          placeholder="Search by name or email…"
          value={draftQ}
          onChange={(e) => setDraftQ(e.target.value)}
        />
        <select
          className="feed-search-input"
          style={{ flex: '0 0 130px' }}
          value={roleFilter}
          onChange={(e) => { setPage(0); setRoleFilter(e.target.value) }}
        >
          <option value="">All roles</option>
          <option value="MENTOR">Mentor</option>
          <option value="MENTEE">Mentee</option>
          <option value="ADMIN">Admin</option>
        </select>
        <select
          className="feed-search-input"
          style={{ flex: '0 0 140px' }}
          value={banFilter}
          onChange={(e) => { setPage(0); setBanFilter(e.target.value) }}
        >
          <option value="">Any status</option>
          <option value="ACTIVE">Currently banned</option>
          <option value="NONE">Not banned</option>
        </select>
        <button type="submit" className="action-btn">Search</button>
      </form>

      {error && (
        <div className="md-error-card" style={{ marginBottom: '12px' }}>
          <div className="md-error-title">Couldn’t load users</div>
          <div className="md-error-sub">{error}</div>
        </div>
      )}

      {loading ? (
        <div className="md-loading">Loading users…</div>
      ) : (data.content || []).length === 0 ? (
        <div className="empty-state">No users match these filters.</div>
      ) : (
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.content.map(u => {
              const fullName = [u.firstName, u.lastName].filter(Boolean).join(' ') || '—'
              const busy = busyUserId === u.id
              const banned = u.banStatus === 'ACTIVE'
              const isBot = u.isSuspectedBot
              return (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{fullName}</td>
                  <td>{u.email}</td>
                  <td>{u.role}</td>
                  <td>
                    {banned && <span className="admin-pill admin-pill--danger">Banned</span>}
                    {isBot && <span className="admin-pill admin-pill--warn">Bot flagged</span>}
                    {!banned && !isBot && <span className="admin-pill admin-pill--ok">Active</span>}
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {banned ? (
                      <button
                        type="button"
                        className="action-btn"
                        onClick={() => handleUnban(u)}
                        disabled={busy}
                      >Unban</button>
                    ) : u.role !== 'ADMIN' ? (
                      <button
                        type="button"
                        className="action-btn action-btn--danger"
                        onClick={() => openBanModal(u)}
                        disabled={busy}
                      >Ban</button>
                    ) : null}
                    {isBot && (
                      <button
                        type="button"
                        className="action-btn"
                        style={{ marginLeft: '6px' }}
                        onClick={() => handleClearBot(u)}
                        disabled={busy}
                      >Clear bot flag</button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {!loading && !error && data.totalPages > 1 && (
        <div className="follow-pagination" style={{ marginTop: '16px' }}>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
          >Previous</button>
          <div className="follow-page-indicator">Page {page + 1} of {data.totalPages}</div>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
          >Next</button>
        </div>
      )}

      {banTarget && (
        <div className="modal-overlay" onMouseDown={e => { if (e.target === e.currentTarget && !busyUserId) setBanTarget(null) }}>
          <div className="modal-card" role="dialog" aria-modal="true">
            <div className="modal-header">
              <h2>Ban {banTarget.name}</h2>
              <button type="button" className="modal-close" onClick={() => setBanTarget(null)} aria-label="Close">×</button>
            </div>
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Reason (required)</label>
            <textarea
              className="modal-textarea"
              rows={4}
              maxLength={1000}
              value={banReason}
              onChange={e => setBanReason(e.target.value)}
              placeholder="Explain why this ban is being imposed."
            />
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Duration (hours)</label>
            <input
              type="number"
              className="form-input"
              min="1"
              step="1"
              value={banHours}
              onChange={e => setBanHours(e.target.value)}
            />
            <div className="modal-actions" style={{ marginTop: '16px' }}>
              <button type="button" className="modal-btn-secondary" onClick={() => setBanTarget(null)}>Cancel</button>
              <button
                type="button"
                className="modal-btn-primary md-danger-btn"
                onClick={confirmBan}
                disabled={!banReason.trim() || busyUserId === banTarget.id}
              >
                {busyUserId === banTarget.id ? 'Banning…' : 'Ban user'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Reports tab ───────────────────────────────────────────────────────

const REPORT_STATUSES = [
  { value: '', label: 'All statuses' },
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_REVIEW', label: 'Under review' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'DISMISSED', label: 'Dismissed' },
]
const REPORT_TARGET_TYPES = [
  { value: '', label: 'All targets' },
  { value: 'POST', label: 'Post' },
  { value: 'MENTORSHIP', label: 'Mentorship' },
  { value: 'USER', label: 'User' },
]

function ReportsTab() {
  const [status, setStatus] = useState('OPEN')
  const [targetType, setTargetType] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], totalPages: 1 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(null) // report id being transitioned

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await listAdminReports({
        status: status || undefined,
        targetType: targetType || undefined,
        page,
        size: 20,
      })
      setData(res || { content: [], totalPages: 1 })
    } catch (err) {
      setError(err?.message || 'Failed to load reports.')
      setData({ content: [], totalPages: 1 })
    } finally {
      setLoading(false)
    }
  }, [status, targetType, page])

  useEffect(() => { load() }, [load])

  async function transition(report, nextStatus) {
    setBusy(report.id)
    try {
      await updateAdminReportStatus(report.id, nextStatus)
      showTransientToast(`Report ${report.id} → ${nextStatus.replace('_', ' ').toLowerCase()}.`)
      load()
    } catch (err) {
      showTransientToast(err?.message || 'Failed to update report.')
    } finally {
      setBusy(null)
    }
  }

  function nextActions(report) {
    switch (report.status) {
      case 'OPEN':
        return ['UNDER_REVIEW', 'RESOLVED', 'DISMISSED']
      case 'UNDER_REVIEW':
        return ['RESOLVED', 'DISMISSED']
      default:
        return []
    }
  }

  return (
    <div data-testid="admin-reports-tab">
      <div className="feed-search" style={{ marginBottom: '12px' }}>
        <select
          className="feed-search-input"
          value={status}
          onChange={(e) => { setPage(0); setStatus(e.target.value) }}
        >
          {REPORT_STATUSES.map(s => (
            <option key={s.value || 'any'} value={s.value}>{s.label}</option>
          ))}
        </select>
        <select
          className="feed-search-input"
          value={targetType}
          onChange={(e) => { setPage(0); setTargetType(e.target.value) }}
        >
          {REPORT_TARGET_TYPES.map(t => (
            <option key={t.value || 'any'} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      {error && (
        <div className="md-error-card" style={{ marginBottom: '12px' }}>
          <div className="md-error-title">Couldn’t load reports</div>
          <div className="md-error-sub">{error}</div>
        </div>
      )}

      {loading ? (
        <div className="md-loading">Loading reports…</div>
      ) : (data.content || []).length === 0 ? (
        <div className="empty-state">No reports match these filters.</div>
      ) : (
        <ul className="admin-report-list">
          {data.content.map(r => (
            <li key={r.id} className="admin-report-card">
              <div className="admin-report-head">
                <span className="admin-report-id">#{r.id}</span>
                <span className={`admin-pill admin-pill--${pillForStatus(r.status)}`}>{r.status}</span>
                <span className="admin-pill admin-pill--neutral">{r.targetType}</span>
                <span className="admin-pill admin-pill--neutral">{r.problemType}</span>
              </div>
              <div className="admin-report-summary">
                <strong>{r.reporterFirstName || `User #${r.reporterId}`}</strong> reported{' '}
                <strong>{r.targetSummary || `${r.targetType.toLowerCase()} #${r.targetId}`}</strong>
              </div>
              <p className="admin-report-desc">{r.description}</p>
              {nextActions(r).length > 0 && (
                <div className="admin-report-actions">
                  {nextActions(r).map(s => (
                    <button
                      key={s}
                      type="button"
                      className="action-btn"
                      onClick={() => transition(r, s)}
                      disabled={busy === r.id}
                    >
                      {s === 'UNDER_REVIEW' ? 'Mark Under Review' : s === 'RESOLVED' ? 'Resolve' : 'Dismiss'}
                    </button>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {!loading && !error && data.totalPages > 1 && (
        <div className="follow-pagination" style={{ marginTop: '16px' }}>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
          >Previous</button>
          <div className="follow-page-indicator">Page {page + 1} of {data.totalPages}</div>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
          >Next</button>
        </div>
      )}
    </div>
  )
}

function pillForStatus(s) {
  switch (s) {
    case 'OPEN': return 'danger'
    case 'UNDER_REVIEW': return 'warn'
    case 'RESOLVED': return 'ok'
    case 'DISMISSED': return 'neutral'
    default: return 'neutral'
  }
}

// ── Messages tab ──────────────────────────────────────────────────────
// Direct-message composer originally shipped in PR #573. Preserved here
// unchanged so its existing testids continue to satisfy
// AdminConsolePage.test.jsx.

function MessagesTab() {
  // Three sub-panes: direct composer + inbox, plus broadcast composer + history.
  // Direct composer keeps the original testids from #573 so existing
  // AdminConsolePage tests still pass against this restructure.
  const [view, setView] = useState('direct')

  return (
    <div data-testid="admin-messages-tab">
      <div className="admin-subnav">
        <button
          type="button"
          className={`feed-tab${view === 'direct' ? ' feed-tab--active' : ''}`}
          onClick={() => setView('direct')}
        >Direct</button>
        <button
          type="button"
          className={`feed-tab${view === 'broadcast' ? ' feed-tab--active' : ''}`}
          onClick={() => setView('broadcast')}
        >Broadcast</button>
      </div>

      {view === 'direct' && <DirectMessagePane />}
      {view === 'broadcast' && <BroadcastPane />}
    </div>
  )
}

function DirectMessagePane() {
  const [userId, setUserId] = useState('')
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')

  // #410: list the admin's existing admin-direct conversations so they can
  // see who they've DMed before without typing the user id from memory.
  const [inbox, setInbox] = useState([])
  const [inboxLoading, setInboxLoading] = useState(true)

  const loadInbox = useCallback(async () => {
    setInboxLoading(true)
    try {
      const page = await getAdminDirectInbox(0, 50)
      setInbox(page?.content || [])
    } catch {
      setInbox([])
    } finally {
      setInboxLoading(false)
    }
  }, [])
  useEffect(() => { loadInbox() }, [loadInbox])

  async function handleSend(e) {
    e.preventDefault()
    setError('')
    const numericId = Number(userId)
    if (!Number.isFinite(numericId) || numericId <= 0 || !Number.isInteger(numericId)) {
      setError('Enter a valid positive numeric user id.')
      return
    }
    if (!content.trim()) {
      setError('Message body cannot be empty.')
      return
    }
    setSending(true)
    try {
      await sendAdminDirectMessage(numericId, content)
      setContent('')
      setUserId('')
      showTransientToast('Direct message delivered.')
      loadInbox()
    } catch (err) {
      const msg = err?.message || 'Failed to send direct message.'
      setError(msg)
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="admin-direct-pane">
      <form
        className="admin-console-form"
        onSubmit={handleSend}
        data-testid="admin-console-form"
        style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '540px' }}
      >
        <label htmlFor="admin-console-recipient" style={{ fontWeight: 600 }}>Recipient user id</label>
        <input
          id="admin-console-recipient"
          type="number"
          min="1"
          step="1"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          placeholder="e.g. 42"
          data-testid="admin-console-recipient"
          disabled={sending}
          required
        />

        <label htmlFor="admin-console-body" style={{ fontWeight: 600 }}>Message</label>
        <textarea
          id="admin-console-body"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Write your message…"
          rows={6}
          data-testid="admin-console-body"
          disabled={sending}
          required
        />

        {error && (
          <div className="md-error-card" data-testid="admin-console-error">
            <div className="md-error-title">Send failed</div>
            <div className="md-error-sub">{error}</div>
          </div>
        )}

        <div>
          <button
            type="submit"
            className="action-btn"
            disabled={sending}
            data-testid="admin-console-send"
          >
            {sending ? 'Sending…' : 'Send message'}
          </button>
        </div>
      </form>

      <div style={{ marginTop: '24px' }}>
        <div className="section-label" style={{ marginBottom: '8px' }}>
          Your direct conversations
        </div>
        {inboxLoading ? (
          <div className="md-loading">Loading…</div>
        ) : inbox.length === 0 ? (
          <div className="empty-state">No direct messages yet. Send one above to start a conversation.</div>
        ) : (
          <ul className="admin-inbox-list">
            {inbox.map(c => (
              <li key={c.conversationId} className="admin-inbox-row">
                <span className="admin-inbox-peer">{c.peerFirstName || `User #${c.peerId}`}</span>
                <span className="admin-inbox-preview">{c.lastMessageContent || '—'}</span>
                <span className="admin-inbox-time">
                  {c.lastMessageSentAt
                    ? new Date(c.lastMessageSentAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
                    : ''}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function BroadcastPane() {
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(true)

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true)
    try {
      const page = await listAdminBroadcasts(0, 50)
      setHistory(page?.content || [])
    } catch {
      setHistory([])
    } finally {
      setHistoryLoading(false)
    }
  }, [])
  useEffect(() => { loadHistory() }, [loadHistory])

  async function handleSend(e) {
    e.preventDefault()
    setError('')
    if (!content.trim()) {
      setError('Broadcast body cannot be empty.')
      return
    }
    setSending(true)
    try {
      await broadcastAdminMessage(content)
      setContent('')
      showTransientToast('Broadcast sent to all admins.')
      loadHistory()
    } catch (err) {
      setError(err?.message || 'Failed to broadcast.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="admin-broadcast-pane">
      <form
        onSubmit={handleSend}
        style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '540px' }}
      >
        <label htmlFor="admin-broadcast-body" style={{ fontWeight: 600 }}>Broadcast to all admins</label>
        <textarea
          id="admin-broadcast-body"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Note for the moderation team…"
          rows={5}
          disabled={sending}
          required
        />
        {error && (
          <div className="md-error-card">
            <div className="md-error-title">Broadcast failed</div>
            <div className="md-error-sub">{error}</div>
          </div>
        )}
        <div>
          <button type="submit" className="action-btn" disabled={sending}>
            {sending ? 'Sending…' : 'Broadcast'}
          </button>
        </div>
      </form>

      <div style={{ marginTop: '24px' }}>
        <div className="section-label" style={{ marginBottom: '8px' }}>
          Recent broadcasts
        </div>
        {historyLoading ? (
          <div className="md-loading">Loading…</div>
        ) : history.length === 0 ? (
          <div className="empty-state">No broadcasts yet.</div>
        ) : (
          <ul className="admin-broadcast-list">
            {history.map(m => (
              <li key={m.id} className="admin-broadcast-row">
                <div className="admin-broadcast-head">
                  <strong>{m.senderFirstName || `Admin #${m.senderId}`}</strong>
                  <span className="admin-broadcast-time">
                    {m.sentAt
                      ? new Date(m.sentAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
                      : ''}
                  </span>
                </div>
                <div className="admin-broadcast-body">{m.content}</div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
