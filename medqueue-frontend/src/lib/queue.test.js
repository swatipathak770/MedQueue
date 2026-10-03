import { describe, expect, it } from 'vitest'
import { findActiveQueueAppointment, findLiveQueueEntry, isActiveAppointment, localDateString, nowServingToken, patientVisitConnectionLabel } from './queue'

describe('patient queue helpers', () => {
  it('treats only future or current waiting visits as active', () => {
    expect(isActiveAppointment({ status: 'WAITING', appointmentDate: '2026-09-27' }, '2026-09-26')).toBe(true)
    expect(isActiveAppointment({ status: 'DONE', appointmentDate: '2026-09-26' }, '2026-09-26')).toBe(false)
    expect(isActiveAppointment({ status: 'WAITING', appointmentDate: '2026-09-25' }, '2026-09-26')).toBe(false)
  })
  it('matches the live snapshot entry by appointment id', () => {
    const snapshot = { queue: [{ appointmentId: 3, position: 2 }, { appointmentId: 4, position: 1 }] }
    expect(findLiveQueueEntry(snapshot, 4)).toEqual({ appointmentId: 4, position: 1 })
    expect(findLiveQueueEntry(snapshot, 9)).toBeNull()
  })
  it('formats a local calendar date without UTC day drift', () => {
    expect(localDateString(new Date(2026, 8, 26, 0, 5))).toBe('2026-09-26')
  })
  it('shows visit connection state only when an active appointment exists', () => {
    expect(patientVisitConnectionLabel(null, false)).toBe('Not active')
    expect(patientVisitConnectionLabel(null, true)).toBe('Not active')
    expect(patientVisitConnectionLabel({ id: 1 }, true)).toBe('Live')
    expect(patientVisitConnectionLabel({ id: 1 }, false)).toBe('Connecting')
  })
  it('shows Now serving only for a currently active queue appointment', () => {
    const called = { appointmentId: 1, tokenNumber: 2, status: 'CALLED' }
    expect(findActiveQueueAppointment([called])).toBe(called)
    expect(nowServingToken([called])).toBe('#2')
    expect(nowServingToken([{ tokenNumber: 3, status: 'IN_PROGRESS' }])).toBe('#3')
    expect(nowServingToken([{ tokenNumber: 2, status: 'DONE' }])).toBe('—')
    expect(nowServingToken([{ tokenNumber: 2, status: 'SKIPPED' }])).toBe('—')
    expect(nowServingToken([{ tokenNumber: 2, status: 'WAITING' }])).toBe('—')
  })
})
