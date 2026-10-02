import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MessageTable from './MessageTable.vue'
import type { MessageSummary } from '../api/client'

function summary(overrides: Partial<MessageSummary> = {}): MessageSummary {
  return {
    id: 1,
    from: 'from@test.com',
    to: 'to@test.com',
    receivedTimestamp: '2024-03-01T09:30:00Z',
    sizeBytes: 1024,
    subject: 'Test subject',
    ...overrides,
  }
}

function mountTable(props: Record<string, unknown> = {}) {
  return mount(MessageTable, {
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