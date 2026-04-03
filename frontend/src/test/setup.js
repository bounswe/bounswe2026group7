import { vi, beforeEach } from 'vitest'

beforeEach(() => {
  global.fetch = vi.fn()
})
