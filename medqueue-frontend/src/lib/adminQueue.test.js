import { describe, expect, it } from 'vitest'
import { mergeQueueSnapshot, queueTopicDestinations, subscribeToQueueTopics, summarizeQueueSnapshot } from './adminQueue'

describe('admin live queue helpers', () => {
  it('creates one subscription destination per visible doctor', () => {
    expect(queueTopicDestinations([4, '4', 9])).toEqual(['/topic/queue/4', '/topic/queue/9'])
  })

  it('subscribes to every visible doctor again when a connection is re-established', () => {
    const destinations = []
    const client = { subscribe: (destination) => { destinations.push(destination) } }
    const onSnapshot = () => {}
    subscribeToQueueTopics(client, [4, 9], onSnapshot)
    subscribeToQueueTopics(client, [4, 9], onSnapshot)
    expect(destinations).toEqual([
      '/topic/queue/4', '/topic/queue/9',
      '/topic/queue/4', '/topic/queue/9',
    ])
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
