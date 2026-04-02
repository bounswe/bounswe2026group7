import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const FILTERS = ['All', 'Backend', 'Mobile', 'AI/ML', 'DevOps', 'Frontend', 'Data']

const MENTORS = [
  {
    id: 1,
    initials: 'BA',
    name: 'Burak Afşar',
    sub: 'Senior iOS Dev · Apple',
    tags: ['Swift', 'Mobile'],
    rating: '4.9',
    reviews: 24,
    stars: 5,
    available: true,
    avatarStyle: { background: '#dce9fc', color: '#2563eb' },
  },
  {
    id: 2,
    initials: 'AY',
    name: 'Ayşe Yıldız',
    sub: 'ML Engineer · Google',
    tags: ['Python', 'AI/ML'],
    rating: '4.7',
    reviews: 18,
    stars: 5,
    available: true,
    avatarStyle: { background: '#ece8f8', color: '#5b4c8a' },
  },
  {
    id: 3,
    initials: 'MK',
    name: 'Mehmet Kaya',
    sub: 'Backend Lead · Trendyol',
    tags: ['Node.js', 'AWS'],
    rating: '4.5',
    reviews: 31,
    stars: 4,
    available: false,
    avatarStyle: { background: '#f5ead8', color: '#8a6a20' },
  },
  {
    id: 4,
    initials: 'DÇ',
    name: 'Deniz Çelik',
    sub: 'Frontend Lead · Getir',
    tags: ['React', 'TypeScript'],
    rating: '4.8',
    reviews: 12,
    stars: 5,
    available: true,
    avatarStyle: { background: '#e8f5ea', color: '#2d7a3a' },
  },
  {
    id: 5,
    initials: 'SA',
    name: 'Selin Arslan',
    sub: 'Data Scientist · Insider',
    tags: ['Python', 'SQL', 'Data'],
    rating: '4.6',
    reviews: 9,
    stars: 4,
    available: true,
    avatarStyle: { background: '#fce8e8', color: '#c0392b' },
  },
  {
    id: 6,
    initials: 'EY',
    name: 'Emre Yılmaz',
    sub: 'DevOps Engineer · Hepsiburada',
    tags: ['Kubernetes', 'Docker'],
    rating: '4.9',
    reviews: 7,
    stars: 5,
    available: true,
    avatarStyle: { background: '#e0f4f8', color: '#0e7490' },
  },
]

function StarRow({ count }) {
  return (
    <span className="stars">
      {[1, 2, 3, 4, 5].map(i => (
        <span key={i}>{i <= count ? '★' : '☆'}</span>
      ))}
    </span>
  )
}

export default function ExplorePage() {
  const [activeFilter, setActiveFilter] = useState('All')
  const [search, setSearch] = useState('')

  const filtered = MENTORS.filter(m => {
    const matchesFilter = activeFilter === 'All' || m.tags.some(t => t === activeFilter) || m.sub.toLowerCase().includes(activeFilter.toLowerCase())
    const matchesSearch = search === '' || m.name.toLowerCase().includes(search.toLowerCase()) || m.tags.some(t => t.toLowerCase().includes(search.toLowerCase()))
    return matchesFilter && matchesSearch
  })

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Find a Mentor</div></div>
        <button className="action-btn">Filter</button>
      </div>

      <div className="explore-header">
        <div className="search-box">
          <span style={{ color: 'var(--text-muted)' }}>🔍</span>
          <input
            type="text"
            placeholder="Search topic or mentor..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="chips">
        {FILTERS.map(f => (
          <div
            key={f}
            className={`chip${activeFilter === f ? ' active' : ''}`}
            onClick={() => setActiveFilter(f)}
          >
            {f}
          </div>
        ))}
      </div>

      <div className="mentors-grid">
        {filtered.map(m => (
          <div className="mentor-card" key={m.id}>
            <div className="mc-header">
              <div className="mc-info">
                <div className="mc-avatar" style={m.avatarStyle}>{m.initials}</div>
                <div>
                  <div className="mc-name">{m.name}</div>
                  <div className="mc-sub">{m.sub}</div>
                </div>
              </div>
              {m.available
                ? <span className="badge-avail">Available</span>
                : <span className="badge-full">Full</span>
              }
            </div>
            <div className="mc-tags">
              {m.tags.map(tag => <span className="tag" key={tag}>{tag}</span>)}
            </div>
            <div className="mc-footer">
              <div>
                <StarRow count={m.stars} />
                <span className="rating-text">{m.rating} ({m.reviews})</span>
              </div>
              <button
                className="view-btn"
                style={!m.available ? { opacity: 0.5, cursor: 'default' } : {}}
                disabled={!m.available}
              >
                View
              </button>
            </div>
          </div>
        ))}
      </div>
    </MainLayout>
  )
}
