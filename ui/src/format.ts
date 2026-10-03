export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`
}

export function formatTimestamp(timestamp: string): string {
  if (!timestamp) {
    return ''
  }
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? timestamp : date.toLocaleString()
}

/**
 * The table has to cope with addresses that are a long comma separated list, so show the
 * first one and say how many others there are.
 */
export function summariseAddresses(addresses: string): string {
  if (!addresses) {
    return ''
  }
  const parts = addresses.split(',').map((part) => part.trim()).filter(Boolean)
  if (parts.length <= 1) {
    return parts[0] ?? ''
  }
  return `${parts[0]} +${parts.length - 1} more`
}

/**
 * Turns the value of a `datetime-local` input into the ISO instant the API expects.
 *
 * <p>A `datetime-local` field has no timezone, and the browser reports it as local time, so
 * someone filtering to "yesterday morning" means their own morning. Sending that string
 * unchanged would have the server read it as UTC and quietly shift the window by the
 * difference, which is the kind of wrong answer that looks like missing mail.
 *
 * <p>An empty field means "no bound" rather than the epoch, so it becomes undefined and the
 * parameter is left off the request entirely. Anything unparseable is treated the same way
 * rather than sent on to fail as a bad request.
 */
export function toUtcIso(localDateTime: string | undefined): string | undefined {
  if (!localDateTime) {
    return undefined
  }
  const parsed = new Date(localDateTime)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}
