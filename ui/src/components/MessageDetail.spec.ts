import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { MessageDetail, MessagePart } from '../api/client'
import MessageDetailPanel from './MessageDetail.vue'

/**
 * The parts list exists because an attachment used to be captured, stored and completely
 * invisible, so these check the panel still names it and says what it is.
 */
describe('MessageDetail parts', () => {
  function part(overrides: Partial<MessagePart> = {}): MessagePart {
    return {
      filename: null,
      contentType: 'text/plain',
      sizeBytes: 0,
      disposition: null,
      displayedBody: false,
      ...overrides
    }
  }

  function mountPanel(parts: MessagePart[]) {
    const message: MessageDetail = {
      id: 1,
      from: 'sender@email.com',
      to: 'to@email.com',
      receivedTimestamp: '2026-01-01T00:00:00Z',
      releasedTimestamp: null,
      subject: 'invoice attached',
      body: 'the caption',
      parts
    }
    return mount(MessageDetailPanel, { props: { message, loading: false } })
  }

  it('names an attachment with its type and size', () => {
    const wrapper = mountPanel([
      part({ contentType: 'text/plain', sizeBytes: 17, displayedBody: true }),
      part({ filename: 'invoice.pdf', contentType: 'application/pdf', sizeBytes: 4096, disposition: 'attachment' })
    ])

    const rows = wrapper.findAll('.parts li')
    expect(rows).toHaveLength(2)
    expect(rows[1].text()).toContain('invoice.pdf')
    expect(rows[1].text()).toContain('application/pdf')
    expect(rows[1].text()).toContain('4.0 KB')
    expect(rows[1].text()).toContain('attachment')
  })

  it('marks the part that is shown as the body', () => {
    const wrapper = mountPanel([
      part({ contentType: 'text/plain', sizeBytes: 17, displayedBody: true }),
      part({ filename: 'logo.png', contentType: 'image/png', sizeBytes: 900, disposition: 'inline' })
    ])

    const tagged = wrapper.findAll('.parts .tag')
    expect(tagged).toHaveLength(1)
    expect(tagged[0].text()).toBe('body')
    // The tag sits on the text part, which is the first row.
    expect(wrapper.findAll('.parts li')[0].find('.tag').exists()).toBe(true)
  })

  it('falls back to the content type when a part has no filename', () => {
    const wrapper = mountPanel([part({ contentType: 'text/html', sizeBytes: 120 })])

    const row = wrapper.find('.parts li')
    expect(row.find('.name').text()).toBe('text/html')
    // Naming it by its type and then repeating it would be noise.
    expect(row.text()).not.toContain('text/html · text/html')
  })

  it('says the size is unknown rather than showing zero bytes', () => {
    const wrapper = mountPanel([part({ filename: 'scan.tiff', contentType: 'image/tiff', sizeBytes: null })])

    expect(wrapper.find('.parts .meta').text()).toContain('size unknown')
  })

  it('omits the section when the message reported no parts', () => {
    const wrapper = mountPanel([])

    expect(wrapper.find('.parts').exists()).toBe(false)
  })

  it('leaves the body untouched', () => {
    const wrapper = mountPanel([part({ contentType: 'text/plain', sizeBytes: 12, displayedBody: true })])

    expect(wrapper.find('.body').text()).toBe('the caption')
  })
})