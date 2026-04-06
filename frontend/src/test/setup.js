import '@testing-library/jest-dom'
import { vi, beforeEach } from 'vitest'

beforeEach(() => {
  global.fetch = vi.fn()
})
