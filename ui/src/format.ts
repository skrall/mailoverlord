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
