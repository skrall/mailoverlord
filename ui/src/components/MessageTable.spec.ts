import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MessageTable from './MessageTable.vue'
import type { MessageSummary } from '../api/client'

function summary(overrides: Partial<MessageSummary> = {}): MessageSummary {
  return {
    id: 1,
    from: 'from@test.com',
    to: 'to@test.com',
    receivedTimestamp: '2024-03-01T09:30:00Z',
    releasedTimestamp: null,
    sizeBytes: 1024,
    subject: 'Test subject',
    ...overrides,
  }
}

let mounted: VueWrapper | undefined

beforeEach(() => {
  // Attached, because moving the cursor puts real DOM focus on a row and looks it up by id.
  document.body.innerHTML = ''
})

afterEach(() => {
  mounted?.unmount()
  mounted = undefined
})

function mountTable(props: Record<string, unknown> = {}) {
  mounted = mount(MessageTable, {
    attachTo: document.body,
    props: {
      messages: [],
      selectedIds: [],
      activeId: null,
      sortField: 'receivedTimestamp',
      sortDirection: 'desc',
      busy: false,
      ...props,
    },
  })
  return mounted
}

describe('sortable column headers', () => {
  /**
   * The sortable set is what the API allows, so a column added to the table without a sort
   * field behind it would come back 400. Asserting both ends keeps the two lists in step.
   */
  const columns = [
    { label: 'Received', field: 'receivedTimestamp' },
    { label: 'From', field: 'from' },
    { label: 'To', field: 'to' },
    { label: 'Subject', field: 'subject' },
  ]

  it('offers exactly the sortable columns, in order', () => {
    const table = mountTable()

    // The active column also renders its direction arrow. That glyph is aria-hidden
    // presentation, so strip it here and let the aria-sort test cover the indicator.
    const labels = table
      .findAll('thead th button')
      .map((button) => button.text().replace(/[▲▼]/g, '').trim())

    expect(labels).toEqual(columns.map((column) => column.label))
  })

  it.each(columns)('sorts by $field when the $label header is clicked', async ({ field }) => {
    const table = mountTable()
    const index = columns.findIndex((column) => column.field === field)

    await table.findAll('thead th button')[index].trigger('click')

    expect(table.emitted('sort')).toEqual([[field]])
  })

  it('marks the active column with its direction for screen readers', () => {
    const table = mountTable({ sortField: 'subject', sortDirection: 'asc' })

    const headers = table.findAll('thead th')
    expect(headers[1].attributes('aria-sort')).toBe('none')
    expect(headers[4].attributes('aria-sort')).toBe('ascending')
  })
})

describe('rows', () => {
  it('shows a placeholder when a message has no subject', () => {
    const table = mountTable({ messages: [summary({ subject: '' })] })

    expect(table.find('tbody td.subject').text()).toBe('(no subject)')
  })

  it('shows the subject when there is one', () => {
    const table = mountTable({ messages: [summary({ subject: 'Hello world' })] })

    expect(table.find('tbody td.subject').text()).toBe('Hello world')
  })

  it('gives the full subject as a title so a truncated cell can be read', () => {
    const table = mountTable({ messages: [summary({ subject: 'A rather long subject' })] })

    expect(table.find('tbody td.subject').attributes('title')).toBe('A rather long subject')
  })

  it('marks the open message as active', () => {
    const table = mountTable({ messages: [summary({ id: 5 })], activeId: 5 })

    expect(table.find('tbody tr').classes()).toContain('active')
  })
})

describe('empty state', () => {
  /**
   * Six columns: the checkbox, four sortable ones, and Size. A short span leaves the last
   * column uncovered, which is easy to miss because the empty row still looks plausible.
   */
  it('spans every column', () => {
    const table = mountTable()

    expect(table.findAll('thead th')).toHaveLength(6)
    expect(table.find('tbody td.empty').attributes('colspan')).toBe('6')
  })

  it('spans every column while loading too', () => {
    const table = mountTable({ busy: true })

    expect(table.find('tbody td.empty').attributes('colspan')).toBe('6')
    expect(table.find('tbody td.empty').text()).toContain('Loading messages')
  })
})
describe('keyboard operability', () => {
  const three = () => [
    summary({ id: 1, subject: 'One' }),
    summary({ id: 2, subject: 'Two' }),
    summary({ id: 3, subject: 'Three' }),
  ]

  /**
   * The issue behind this file: the table was a `<table>` of clickable rows, so it had no
   * focusable element of its own and no state a screen reader could read. Shortcuts layered on
   * top of that would have made it *look* operable without making it so.
   */
  it('is a multi-selectable grid rather than a plain table', () => {
    const table = mountTable({ messages: three() })

    expect(table.attributes('role')).toBe('grid')
    expect(table.attributes('aria-multiselectable')).toBe('true')
    expect(table.attributes('aria-label')).toBe('Captured messages')
  })

  /**
   * One tab stop for the whole table. A tab stop per row means 25 presses of Tab to get past a
   * page of messages, so in practice nobody does it and the table stays unreachable.
   */
  it('makes exactly one row reachable by Tab', () => {
    const table = mountTable({ messages: three() })

    const stops = table.findAll('tbody tr').filter((row) => row.attributes('tabindex') === '0')

    expect(stops).toHaveLength(1)
  })

  it('puts the tab stop on the open message', () => {
    const table = mountTable({ messages: three(), activeId: 2 })

    const stops = table.findAll('tbody tr').filter((row) => row.attributes('tabindex') === '0')

    expect(stops[0].attributes('data-message-id')).toBe('2')
  })

  /**
   * With nothing open there still has to be a tab stop, or Tab would skip the table entirely
   * until the user found a way to open a message with the mouse.
   */
  it('puts the tab stop on the first row when nothing is open', () => {
    const table = mountTable({ messages: three(), activeId: null })

    const stops = table.findAll('tbody tr').filter((row) => row.attributes('tabindex') === '0')

    expect(stops[0].attributes('data-message-id')).toBe('1')
  })

  /**
   * The row checkboxes would otherwise add 25 more tab stops inside the one the roving tabindex
   * just collapsed, which would give back everything the pattern was for. Space toggles instead.
   */
  it('keeps the row checkboxes out of the tab order', () => {
    const table = mountTable({ messages: three() })

    for (const box of table.findAll('tbody input[type="checkbox"]')) {
      expect(box.attributes('tabindex')).toBe('-1')
    }
  })

  it('exposes selection state on the rows themselves', () => {
    const table = mountTable({ messages: three(), selectedIds: [2] })

    const rows = table.findAll('tbody tr')

    expect(rows[0].attributes('aria-selected')).toBe('false')
    expect(rows[1].attributes('aria-selected')).toBe('true')
  })

  it('moves down with j and up with k', async () => {
    const table = mountTable({ messages: three(), activeId: 1 })

    await table.find('#message-row-1').trigger('keydown', { key: 'j' })
    expect(table.emitted('open')?.at(-1)).toEqual([2])

    await table.find('#message-row-2').trigger('keydown', { key: 'k' })
    expect(table.emitted('open')?.at(-1)).toEqual([1])
  })

  it('moves with the arrow keys too', async () => {
    const table = mountTable({ messages: three(), activeId: 2 })

    await table.find('#message-row-2').trigger('keydown', { key: 'ArrowDown' })
    expect(table.emitted('open')?.at(-1)).toEqual([3])

    await table.find('#message-row-3').trigger('keydown', { key: 'ArrowUp' })
    expect(table.emitted('open')?.at(-1)).toEqual([2])
  })

  /**
   * Moving the detail panel without moving focus would leave the arrow keys stepping from
   * wherever focus still was, which is the difference between this working and looking like it
   * works.
   */
  it('puts DOM focus on the row it moves to', async () => {
    const table = mountTable({ messages: three(), activeId: 1 })

    await table.find('#message-row-1').trigger('keydown', { key: 'j' })

    expect(document.activeElement?.id).toBe('message-row-2')
  })

  it('stops at the ends rather than wrapping', async () => {
    const table = mountTable({ messages: three(), activeId: 3 })

    await table.find('#message-row-3').trigger('keydown', { key: 'j' })
    await table.find('#message-row-1').trigger('keydown', { key: 'k' })

    expect(table.emitted('open')).toBeUndefined()
  })

  it('jumps to the first and last row with Home and End', async () => {
    const table = mountTable({ messages: three(), activeId: 2 })

    await table.find('#message-row-2').trigger('keydown', { key: 'End' })
    expect(table.emitted('open')?.at(-1)).toEqual([3])

    await table.find('#message-row-3').trigger('keydown', { key: 'Home' })
    expect(table.emitted('open')?.at(-1)).toEqual([1])
  })

  it('selects the focused row with Space', async () => {
    const table = mountTable({ messages: three(), activeId: 2 })

    await table.find('#message-row-2').trigger('keydown', { key: ' ' })

    expect(table.emitted('toggle')?.at(-1)).toEqual([2])
  })

  /**
   * A checkbox handles Space itself. Handling it on the row as well would toggle twice and leave
   * the selection exactly as it started, which reads as Space being broken.
   */
  it('leaves Space on a checkbox to the checkbox', async () => {
    const table = mountTable({ messages: three(), activeId: 1 })

    await table.find('#message-row-1 input[type="checkbox"]').trigger('keydown', { key: ' ' })

    expect(table.emitted('toggle')).toBeUndefined()
  })

  it('does not swallow keys it has no use for', async () => {
    const table = mountTable({ messages: three(), activeId: 1 })

    const event = new KeyboardEvent('keydown', { key: 'q', bubbles: true, cancelable: true })
    table.find('#message-row-1').element.dispatchEvent(event)

    expect(table.emitted('open')).toBeUndefined()
    expect(event.defaultPrevented).toBe(false)
  })

  /**
   * Otherwise the page scrolls under the table while j is being held down, and the row the user
   * is on slides out from under the cursor they are reading.
   */
  it('keeps the arrow keys and Space from scrolling the page', async () => {
    const table = mountTable({ messages: three(), activeId: 1 })

    for (const key of ['j', 'k', 'ArrowDown', 'ArrowUp', 'Home', 'End', ' ']) {
      const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
      table.find(`#message-row-1`).element.dispatchEvent(event)
      expect(event.defaultPrevented, `${key} should not scroll`).toBe(true)
    }
  })

  it('does nothing on an empty table', async () => {
    const table = mountTable({ messages: [] })

    const event = new KeyboardEvent('keydown', { key: 'j', bubbles: true, cancelable: true })
    table.find('tbody').element.dispatchEvent(event)

    expect(table.emitted('open')).toBeUndefined()
  })
})
