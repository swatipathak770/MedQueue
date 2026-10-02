import { describe, expect, it, vi } from 'vitest'
import { createQueueSubscriptionManager, mergeQueueSnapshot, queueTopicDestinations, summarizeQueueSnapshot } from './adminQueue'

describe('admin live queue helpers', () => {
  it('creates one subscription destination per visible doctor', () => {
    expect(queueTopicDestinations([4, '4', 9])).toEqual(['/topic/queue/4', '/topic/queue/9'])
  })

  it('subscribes once per visible doctor, routes messages to that doctor, and resubscribes after reconnect', () => {
    const callbacks = new Map()
    const destinations = []
    const current = {}
    const refreshBaseline = vi.fn()
    const client = { subscribe: (destination, callback) => {
      destinations.push(destination)
      callbacks.set(destination, callback)
      return { unsubscribe: vi.fn() }
    } }
    const manager = createQueueSubscriptionManager(client, (snapshot) => {
      Object.assign(current, mergeQueueSnapshot(current, snapshot))
    }, refreshBaseline)
    manager.setDoctorIds([4, 9, 4])
    manager.onConnect()
    expect(destinations).toEqual(['/topic/queue/4', '/topic/queue/9'])

    callbacks.get('/topic/queue/4')({ body: JSON.stringify({
      doctorId: 4, date: '2026-10-01', available: false, open: true, currentTokenNumber: 3,
      waitingCount: 2, updatedAt: '2026-10-01T10:00:00Z', queue: [],
    }) })
    expect(current['4']).toMatchObject({ doctorId: '4', waitingCount: 2, available: false })
    expect(current).not.toHaveProperty('9')

    manager.onConnect()
    expect(destinations).toHaveLength(2)
    expect(refreshBaseline).toHaveBeenCalledOnce()
    manager.onDisconnect()
    manager.onConnect()
    expect(destinations).toEqual([
      '/topic/queue/4', '/topic/queue/9',
      '/topic/queue/4', '/topic/queue/9',
    ])
    expect(refreshBaseline).toHaveBeenCalledTimes(2)
    manager.dispose()
  })

  it('unsubscribes removed doctors and adds newly visible doctors', () => {
    const callbacks = new Map()
    const handles = new Map()
    const client = { subscribe: (destination, callback) => {
      callbacks.set(destination, callback)
      const handle = { unsubscribe: vi.fn() }
      handles.set(destination, handle)
      return handle
    } }
    const manager = createQueueSubscriptionManager(client, () => {})
    manager.setDoctorIds([4, 9])
    manager.onConnect()
    manager.setDoctorIds([9, 12])
    expect(handles.get('/topic/queue/4').unsubscribe).toHaveBeenCalledOnce()
    expect(handles.has('/topic/queue/12')).toBe(true)
    expect(handles.has('/topic/queue/9')).toBe(true)
    manager.dispose()
  })

  it('summarizes an active token without carrying patient identifiers', () => {
    const summary = summarizeQueueSnapshot({
      doctorId: 4, date: '2026-10-01', available: true, open: true,
      currentTokenNumber: 8, waitingCount: 2, updatedAt: '2026-10-01T10:00:00Z',
      queue: [
        { appointmentId: 99, tokenNumber: 7, status: 'DONE' },
        { appointmentId: 100, tokenNumber: 8, status: 'IN_PROGRESS' },
      ],
    })
    expect(summary).toMatchObject({ doctorId: '4', activeTokenNumber: 8, activeStatus: 'IN_PROGRESS', waitingCount: 2 })
    expect(summary).not.toHaveProperty('appointmentId')
    expect(summary).not.toHaveProperty('queue')
  })

  it('updates only the matching doctor and ignores an older snapshot', () => {
    const initial = {
      4: { doctorId: 4, doctorName: 'Dr A', department: 'General', updatedAt: '2026-10-01T10:00:00Z' },
      9: { doctorId: 9, doctorName: 'Dr B', waitingCount: 3 },
    }
    const next = mergeQueueSnapshot(initial, {
      doctorId: 4, date: '2026-10-01', available: false, open: true,
      currentTokenNumber: 2, waitingCount: 1, updatedAt: '2026-10-01T10:01:00Z', queue: [],
    })
    expect(next[4]).toMatchObject({ doctorName: 'Dr A', department: 'General', waitingCount: 1, available: false })
    expect(next[9]).toBe(initial[9])
    expect(mergeQueueSnapshot(next, { doctorId: 4, updatedAt: '2026-10-01T10:00:30Z', queue: [] })).toBe(next)
  })
})
