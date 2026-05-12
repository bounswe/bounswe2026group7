import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'

vi.mock('../../services/stompClient', () => ({
  getStompClient: vi.fn(),
}))

import useFeedSubscription from '../useFeedSubscription'
import { getStompClient } from '../../services/stompClient'

describe('useFeedSubscription', () => {
  let subscribe
  let unsubscribe
  let client

  beforeEach(() => {
    unsubscribe = vi.fn()
    subscribe = vi.fn().mockReturnValue({ unsubscribe })
    client = { connected: true, subscribe }
    getStompClient.mockReturnValue(client)
  })

  it('subscribes to /topic/feed.{userId} and routes post frames to onPost', () => {
    const onPost = vi.fn()
    const onShare = vi.fn()
    renderHook(() => useFeedSubscription('42', { onPost, onShare }))

    expect(subscribe).toHaveBeenCalledTimes(1)
    const [destination, handler] = subscribe.mock.calls[0]
    expect(destination).toBe('/topic/feed.42')
    expect(typeof handler).toBe('function')

    const postPayload = {
      postId: 1,
      authorId: 2,
      authorFirstName: 'A',
      createdAt: '2026-05-12T00:00:00Z',
    }
    handler({ body: JSON.stringify(postPayload) })
    expect(onPost).toHaveBeenCalledWith(postPayload)
    expect(onShare).not.toHaveBeenCalled()
  })

  it('routes share frames (sharerId present) to onShare', () => {
    const onPost = vi.fn()
    const onShare = vi.fn()
    renderHook(() => useFeedSubscription('42', { onPost, onShare }))
    const handler = subscribe.mock.calls[0][1]

    const sharePayload = {
      shareId: 5,
      postId: 1,
      sharerId: 7,
      sharerFirstName: 'B',
      commentary: '',
      sharedAt: '2026-05-12T00:00:00Z',
    }
    handler({ body: JSON.stringify(sharePayload) })
    expect(onShare).toHaveBeenCalledWith(sharePayload)
    expect(onPost).not.toHaveBeenCalled()
  })

  it('unsubscribes on unmount', () => {
    const { unmount } = renderHook(() => useFeedSubscription('42', { onPost: vi.fn() }))
    unmount()
    expect(unsubscribe).toHaveBeenCalledTimes(1)
  })

  it('does nothing when userId is null', () => {
    renderHook(() => useFeedSubscription(null, { onPost: vi.fn() }))
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('swallows malformed JSON frames without throwing', () => {
    const onPost = vi.fn()
    renderHook(() => useFeedSubscription('7', { onPost }))
    const handler = subscribe.mock.calls[0][1]
    expect(() => handler({ body: 'not-json' })).not.toThrow()
    expect(onPost).not.toHaveBeenCalled()
  })

  it('defers subscribe until onConnect when client is not yet connected', () => {
    const deferredClient = { connected: false, subscribe, onConnect: null }
    getStompClient.mockReturnValue(deferredClient)
    renderHook(() => useFeedSubscription('99', { onPost: vi.fn() }))

    expect(subscribe).not.toHaveBeenCalled()
    expect(typeof deferredClient.onConnect).toBe('function')

    deferredClient.onConnect({})
    expect(subscribe).toHaveBeenCalledTimes(1)
    expect(subscribe.mock.calls[0][0]).toBe('/topic/feed.99')
  })

  it('skips deferred attach when the hook unmounts before CONNECT', () => {
    const deferredClient = { connected: false, subscribe, onConnect: null }
    getStompClient.mockReturnValue(deferredClient)
    const { unmount } = renderHook(() => useFeedSubscription('100', { onPost: vi.fn() }))

    unmount()
    deferredClient.onConnect({})

    expect(subscribe).not.toHaveBeenCalled()
    expect(unsubscribe).not.toHaveBeenCalled()
  })

  it('chains multiple deferred subscriptions so both attach on CONNECT', () => {
    const deferredClient = { connected: false, subscribe, onConnect: null }
    getStompClient.mockReturnValue(deferredClient)

    renderHook(() => useFeedSubscription('1', { onPost: vi.fn() }))
    renderHook(() => useFeedSubscription('2', { onPost: vi.fn() }))

    expect(subscribe).not.toHaveBeenCalled()
    deferredClient.onConnect({})

    expect(subscribe).toHaveBeenCalledTimes(2)
    const destinations = subscribe.mock.calls.map((c) => c[0]).sort()
    expect(destinations).toEqual(['/topic/feed.1', '/topic/feed.2'])
  })
})
