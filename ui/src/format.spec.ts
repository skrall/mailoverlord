import { describe, expect, it } from 'vitest'
import { formatBytes, formatTimestamp, summariseAddresses } from './format'

describe('formatBytes', () => {
  it('reports bytes below a kilobyte without a fraction', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1)).toBe('1 B')
    expect(formatBytes(1023)).toBe('1023 B')
  })

  it('moves up to KB at 1024 rather than at 1000', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
  })

  it('keeps one decimal place below ten, and drops it above', () => {
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(9 * 1024)).toBe('9.0 KB')
    expect(formatBytes(10 * 1024)).toBe('10 KB')
  })

  it('scales through each unit', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB')
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.0 GB')
  })

  /**
   * There is no TB entry, so a big enough message has to stay in GB rather than index past
   * the end of the unit list.
   */
  it('stops at GB instead of running off the end of the units', () => {
    expect(formatBytes(1024 ** 4)).toBe('1024 GB')
  })
})

describe('formatTimestamp', () => {
  it('renders nothing for an absent timestamp', () => {
    expect(formatTimestamp('')).toBe('')
  })

  /**
   * An unparseable value is passed through unchanged rather than shown as "Invalid Date",
   * because the raw text is more use to whoever is debugging than that is.
   */
  it('returns the input unchanged when it cannot be parsed', () => {
    expect(formatTimestamp('not a date')).toBe('not a date')
  })

  it('localises a parseable timestamp', () => {
    const iso = '2024-03-01T09:30:00Z'
    const formatted = formatTimestamp(iso)
    expect(formatted).toBe(new Date(iso).toLocaleString())
    expect(formatted).not.toBe(iso)
  })
})

describe('summariseAddresses', () => {
  it('renders nothing when there are no addresses', () => {
    expect(summariseAddresses('')).toBe('')
  })

  it('shows a single address unchanged', () => {
    expect(summariseAddresses('a@test.com')).toBe('a@test.com')
  })

  /**
   * The whole point: a message with 100 recipients must not widen the From or To column.
   */
  it('shows the first address and counts the rest', () => {
    expect(summariseAddresses('a@test.com,b@test.com')).toBe('a@test.com +1 more')
    expect(summariseAddresses('a@test.com,b@test.com,c@test.com')).toBe('a@test.com +2 more')
  })

  it('ignores surrounding whitespace and empty segments', () => {
    expect(summariseAddresses(' a@test.com , b@test.com ')).toBe('a@test.com +1 more')
    expect(summariseAddresses('a@test.com,')).toBe('a@test.com')
    expect(summariseAddresses(',,')).toBe('')
  })
})