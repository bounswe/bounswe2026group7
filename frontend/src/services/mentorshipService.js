import { listMentorshipTasks, listMentorshipMeetings } from './api'

/**
 * Aggregates non-completed tasks across all provided mentorships.
 * Uses Promise.all to fetch tasks in parallel as suggested.
 */
export async function getTasksAcrossMentorships(mentorships) {
  if (!mentorships || mentorships.length === 0) return []

  const results = await Promise.allSettled(
    mentorships.map(m => listMentorshipTasks(m.id))
  )

  const allTasks = results
    .filter(r => r.status === 'fulfilled' && r.value)
    .flatMap((r, i) => {
      const mentorship = mentorships[i]
      const tasks = Array.isArray(r.value) ? r.value : (r.value.content || [])
      
      return tasks
        .filter(t => t.status !== 'COMPLETED') // Only active tasks
        .map(t => ({
          ...t,
          mentorship: {
            id: mentorship.id,
            mentorFirstName: mentorship.mentorFirstName,
            menteeFirstName: mentorship.menteeFirstName
          }
        }))
    })

  return allTasks
}

/**
 * Aggregates active (upcoming or pending) meetings across all provided mentorships.
 * Uses Promise.all to fetch meetings in parallel.
 */
export async function getMeetingsAcrossMentorships(mentorships) {
  if (!mentorships || mentorships.length === 0) return []

  const results = await Promise.allSettled(
    mentorships.map(m => listMentorshipMeetings(m.id))
  )

  const allMeetings = results
    .filter(r => r.status === 'fulfilled' && r.value)
    .flatMap((r, i) => {
      const mentorship = mentorships[i]
      const meetings = Array.isArray(r.value) ? r.value : (r.value.content || [])
      
      const activeStatuses = ['PENDING_CONFIRMATION', 'CONFIRMED']
      
      return meetings
        .filter(mt => activeStatuses.includes(mt.status)) // Only upcoming/confirmed meetings
        .map(mt => ({
          ...mt,
          mentorship: {
            id: mentorship.id,
            mentorFirstName: mentorship.mentorFirstName,
            menteeFirstName: mentorship.menteeFirstName
          }
        }))
    })

  return allMeetings
}
