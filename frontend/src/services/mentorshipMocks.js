// Mock data for mentorship-scoped features whose backend endpoints don't exist yet.
// Each function matches the signature we expect the real endpoint wrapper to have,
// so swapping in the real call is a single-line import change per consumer.

function seededRng(seed) {
  let x = Number(seed) >>> 0 || 1
  return () => {
    x = (x * 1664525 + 1013904223) >>> 0
    return x / 0x100000000
  }
}

function addDays(base, days) {
  const d = new Date(base)
  d.setDate(d.getDate() + days)
  return d
}

function fakeDelay(ms = 180) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

export async function getMeetings(mentorshipId) {
  await fakeDelay()
  const rng = seededRng(mentorshipId)
  const now = new Date()
  const topics = [
    'Weekly check-in',
    'Portfolio review',
    'Career planning session',
    'Technical interview prep',
    'Goal setting for next month',
  ]
  return topics.map((title, i) => {
    const offsetDays = (i - 1) * 7 + Math.floor(rng() * 3)
    const date = addDays(now, offsetDays)
    date.setHours(14 + Math.floor(rng() * 4), 0, 0, 0)
    return {
      id: `mtg-${mentorshipId}-${i}`,
      title,
      date: date.toISOString(),
      durationMin: 45,
      status: offsetDays < 0 ? 'COMPLETED' : (rng() > 0.3 ? 'CONFIRMED' : 'PENDING'),
    }
  })
}

export async function getNextUpcomingMeeting(mentorshipId) {
  const meetings = await getMeetings(mentorshipId)
  const now = Date.now()
  return meetings
    .filter(m => new Date(m.date).getTime() > now && m.status !== 'COMPLETED')
    .sort((a, b) => new Date(a.date) - new Date(b.date))[0] || null
}

export async function getTasks(mentorshipId) {
  await fakeDelay()
  const rng = seededRng(Number(mentorshipId) + 7)
  const titles = [
    'Share portfolio draft for review',
    'Read recommended article on system design',
    'Prepare 3 questions for next meeting',
    'Update LinkedIn headline',
    'Book practice interview slot',
  ]
  return titles.map((title, i) => ({
    id: `task-${mentorshipId}-${i}`,
    title,
    status: rng() > 0.6 ? 'DONE' : 'TODO',
    dueDate: addDays(new Date(), i * 3 + 2).toISOString(),
  }))
}

export async function getMessagesThread(mentorshipId) {
  await fakeDelay()
  const now = Date.now()
  return [
    {
      id: `msg-${mentorshipId}-1`,
      senderRole: 'MENTOR',
      text: 'Welcome! Looking forward to working with you over the coming months.',
      createdAt: new Date(now - 1000 * 60 * 60 * 48).toISOString(),
    },
    {
      id: `msg-${mentorshipId}-2`,
      senderRole: 'MENTEE',
      text: 'Thanks — excited to get started. I drafted a few goals for our first meeting.',
      createdAt: new Date(now - 1000 * 60 * 60 * 47).toISOString(),
    },
    {
      id: `msg-${mentorshipId}-3`,
      senderRole: 'MENTOR',
      text: 'Perfect, send them over and we\'ll refine together.',
      createdAt: new Date(now - 1000 * 60 * 60 * 2).toISOString(),
    },
  ]
}

export async function endMentorship(id) {
  await fakeDelay(500)
  return { id, status: 'COMPLETED' }
}
