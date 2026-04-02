import { useState, useRef, useEffect } from 'react'
import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const CONVERSATIONS = [
  {
    id: 1,
    initials: 'BA',
    name: 'Burak Afşar',
    online: true,
    avatarStyle: { background: '#dce9fc', color: '#2563eb' },
  },
  {
    id: 2,
    initials: 'ZD',
    name: 'Zeynep Demir',
    online: false,
    avatarStyle: { background: '#f5ead8', color: '#8a6a20' },
  },
  {
    id: 3,
    initials: 'AY',
    name: 'Ayşe Yıldız',
    online: false,
    avatarStyle: { background: '#ece8f8', color: '#5b4c8a' },
  },
]

const INITIAL_MESSAGES = {
  1: [
    { id: 1, from: 'them', text: "Hey! I've reviewed your React Native project files.", time: '14:22' },
    { id: 2, from: 'me', text: 'Great, how should we structure the navigation?', time: '14:24' },
    {
      id: 3, from: 'them',
      text: "I'd recommend a Stack + Tab navigator combo. Sharing the file.",
      time: '14:27',
      file: { name: 'navigation-structure.pdf', size: '234 KB · PDF' },
    },
    { id: 4, from: 'me', text: "Thanks, I'll check it out!", time: '14:28' },
  ],
  2: [
    { id: 1, from: 'them', text: 'Thanks for the session today!', time: '11:05' },
    { id: 2, from: 'me', text: 'Glad it was helpful! Keep working on those tasks.', time: '11:07' },
  ],
  3: [
    { id: 1, from: 'them', text: 'Check out this paper on transformers', time: '09:30' },
    { id: 2, from: 'me', text: 'Will do, thanks!', time: '09:45' },
  ],
}

function getTime() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

export default function MessagesPage() {
  const [activeConv, setActiveConv] = useState(1)
  const [messages, setMessages] = useState(INITIAL_MESSAGES)
  const [input, setInput] = useState('')
  const fileInputRef = useRef(null)
  const messagesEndRef = useRef(null)

  const active = CONVERSATIONS.find(c => c.id === activeConv)
  const currentMessages = messages[activeConv] || []

  const lastPreview = conv => {
    const msgs = messages[conv.id]
    if (!msgs?.length) return ''
    const last = msgs[msgs.length - 1]
    return last.file ? `📄 ${last.file.name}` : last.text
  }

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [currentMessages])

  function sendMessage() {
    const text = input.trim()
    if (!text) return
    const newMsg = { id: Date.now(), from: 'me', text, time: getTime() }
    setMessages(prev => ({ ...prev, [activeConv]: [...(prev[activeConv] || []), newMsg] }))
    setInput('')
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  function handleFileChange(e) {
    const file = e.target.files[0]
    if (!file) return
    const newMsg = {
      id: Date.now(),
      from: 'me',
      text: '',
      time: getTime(),
      file: { name: file.name, size: `${(file.size / 1024).toFixed(0)} KB · ${file.type || 'file'}` },
    }
    setMessages(prev => ({ ...prev, [activeConv]: [...(prev[activeConv] || []), newMsg] }))
    e.target.value = ''
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Messages</div></div>
      </div>
      <div className="messages-layout">

        {/* Conversation list — #121 */}
        <div className="msg-list">
          <div className="msg-list-header">Conversations</div>
          {CONVERSATIONS.map(c => (
            <div
              key={c.id}
              className={`msg-item${activeConv === c.id ? ' active' : ''}`}
              onClick={() => setActiveConv(c.id)}
            >
              <div className="msg-item-avatar" style={c.avatarStyle}>
                {c.initials}
                {c.online && <div className="online-dot" />}
              </div>
              <div style={{ minWidth: 0 }}>
                <div className="msg-item-name">{c.name}</div>
                <div className="msg-item-preview">{lastPreview(c)}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Chat area — #120, #121, #122 */}
        <div className="chat-area">
          <div className="chat-header">
            <div className="chat-header-avatar" style={active.avatarStyle}>{active.initials}</div>
            <div>
              <div className="chat-name">{active.name}</div>
              {active.online && <div className="chat-status">● Online</div>}
            </div>
            <div
              style={{ marginLeft: 'auto', fontSize: '22px', cursor: 'pointer', color: 'var(--text-muted)' }}
              onClick={() => fileInputRef.current?.click()}
              title="Attach file"
            >
              📎
            </div>
          </div>

          <div className="chat-messages">
            {currentMessages.map(msg => (
              <div key={msg.id} className={`bubble-wrap ${msg.from}`}>
                <div>
                  {msg.text && <div className={`bubble ${msg.from}`}>{msg.text}</div>}
                  {msg.file && (
                    <div className="file-bubble">
                      <div className="file-icon">📄</div>
                      <div>
                        <div className="file-name">{msg.file.name}</div>
                        <div className="file-size">{msg.file.size}</div>
                      </div>
                    </div>
                  )}
                  <div className="bubble-time">{msg.time}</div>
                </div>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          <div className="chat-input-bar">
            {/* Hidden file input — #122 */}
            <input
              type="file"
              ref={fileInputRef}
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />
            <span
              className="chat-add"
              onClick={() => fileInputRef.current?.click()}
              title="Attach file"
              style={{ cursor: 'pointer' }}
            >
              +
            </span>
            <input
              className="chat-input"
              type="text"
              placeholder="Type a message..."
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
            />
            <button className="chat-send" onClick={sendMessage}>▶</button>
          </div>
        </div>

      </div>
    </MainLayout>
  )
}
