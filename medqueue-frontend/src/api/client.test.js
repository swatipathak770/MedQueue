import { describe, expect, it } from 'vitest'
import { errorMessage } from './client'

describe('API error messages', () => {
  it('uses a safe message returned by the API', () => {
    expect(errorMessage({ response: { status: 409, data: { message: 'Slot is full' } } })).toBe('Slot is full')
  })

  it.each([
    [400, 'Please check the information and try again.'],
    [401, 'Your session has expired. Please sign in again.'],
    [403, 'You do not have permission to do that.'],
    [404, 'The requested item could not be found.'],
    [409, 'This request conflicts with the current state. Refresh and try again.'],
    [500, 'The server could not complete the request. Please try again later.'],
  ])('provides a useful fallback for HTTP %s', (status, message) => {
    expect(errorMessage({ response: { status, data: {} } })).toBe(message)
  })

  it('explains network failures without exposing raw transport details', () => {
    expect(errorMessage({ message: 'Network Error' })).toBe('Cannot reach the server. Check your connection and try again.')
  })
})
