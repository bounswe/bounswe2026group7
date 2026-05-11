import '@testing-library/jest-dom'
import { vi, beforeEach } from 'vitest'

// jsdom doesn't implement scrollIntoView; ExplorePage schedules a
// setTimeout(scrollIntoView, 100) on the AI-matches CTA that can fire AFTER
// the test's React-Testing-Library cleanup on slower runners (GitHub
// Actions), leaving the ref still pointing at a live DOM element when the
// timer runs. That throws an uncaught TypeError which vitest 4.x reports
// as suite failure even when every individual test passed. A no-op stub
// on the prototype kills the flake without touching production behaviour.
Element.prototype.scrollIntoView = vi.fn()

beforeEach(() => {
  global.fetch = vi.fn()
})
