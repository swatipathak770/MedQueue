import { describe, expect, it } from 'vitest'
import { formatDoctorName } from './doctorDisplay'

describe('doctor name formatting', () => {
  it('adds one Dr. prefix when the stored name has none', () => {
    expect(formatDoctorName('Shikha Mishra')).toBe('Dr. Shikha Mishra')
  })

  it('preserves a single existing prefix and collapses repeated prefixes', () => {
    expect(formatDoctorName('Dr. Shikha Mishra')).toBe('Dr. Shikha Mishra')
    expect(formatDoctorName('Dr. Dr. Amit Sharma')).toBe('Dr. Amit Sharma')
  })
})
