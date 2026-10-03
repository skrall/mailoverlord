import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FilterBar from './FilterBar.vue'

/**
 * The filter box owns a debounce, and that is the part with behaviour worth pinning down: a
 * request per keystroke is the thing being avoided, and a debounce that fires after the
 * component has gone away emits into nothing.
 */
describe('FilterBar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function mountBar() {
    return mount(FilterBar, { props: { filter: {} } })
  }

  function inputs(wrapper: ReturnType<typeof mountBar>) {
    return wrapper.findAll('input')
  }

  async function type(wrapper: ReturnType<typeof mountBar>, index: number, value: string) {
    await inputs(wrapper)[index].setValue(value)
  }

  it('waits for typing to settle before emitting a filter', async () => {
    const wrapper = mountBar()

    await type(wrapper, 0, 'inv')
    await type(wrapper, 0, 'invo')
    await type(wrapper, 0, 'invoice')

    // Every keystroke reports that an edit is in progress, so the table can hold off its poll.
    expect(wrapper.emitted('editing')).toHaveLength(3)
    expect(wrapper.emitted('change')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(300)

    expect(wrapper.emitted('change')).toEqual([[{ subject: 'invoice', from: '', to: '', receivedFrom: undefined, receivedTo: undefined }]])
  })

  /**
   * One request for three keystrokes. Without the reset on each keystroke the three would
   * coalesce into one anyway, but the timer restarting is what keeps a slow typist from having
   * their first letter searched for on its own.
   */
  it('restarts the delay on each keystroke', async () => {
    const wrapper = mountBar()

    await type(wrapper, 0, 'a')
    await vi.advanceTimersByTimeAsync(250)
    await type(wrapper, 0, 'ab')

    // Past the first delay, but the second keystroke pushed it out again.
    expect(wrapper.emitted('change')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.emitted('change')).toHaveLength(1)
  })

  it('reports the criterion that was typed into', async () => {
    const wrapper = mountBar()

    await type(wrapper, 1, 'alice')
    await type(wrapper, 2, 'bob')
    await vi.advanceTimersByTimeAsync(300)

    expect(wrapper.emitted('change')).toEqual([
      [{ subject: '', from: 'alice', to: 'bob', receivedFrom: undefined, receivedTo: undefined }],
    ])
  })

  /**
   * A `datetime-local` box has no timezone, so the value is emitted as an instant rather than
   * passed through. Sending it raw would have the server read it as UTC and shift the window.
   */
  it('converts a received bound to an instant', async () => {
    const wrapper = mountBar()

    await type(wrapper, 3, '2024-03-01T12:00')
    await vi.advanceTimersByTimeAsync(300)

    const [filter] = wrapper.emitted('change')![0] as [{ receivedFrom: string | undefined }]
    expect(filter.receivedFrom).toMatch(/Z$/)
    expect(new Date(filter.receivedFrom as string).getHours()).toBe(12)
  })

  it('leaves an empty received bound off entirely', async () => {
    const wrapper = mountBar()

    await type(wrapper, 0, 'invoice')
    await vi.advanceTimersByTimeAsync(300)

    const [filter] = wrapper.emitted('change')![0] as [{ receivedFrom?: string }]
    expect(filter.receivedFrom).toBeUndefined()
  })

  /**
   * Clearing is a deliberate act with an obvious result, so it must not sit behind the debounce.
   */
  it('clears immediately rather than after the delay', async () => {
    const wrapper = mountBar()

    await type(wrapper, 0, 'invoice')
    await vi.advanceTimersByTimeAsync(300)
    await type(wrapper, 1, 'alice')
    await vi.advanceTimersByTimeAsync(300)

    await wrapper.get('button.clear').trigger('click')

    const last = wrapper.emitted('change')!.at(-1)![0] as Record<string, string | undefined>
    expect(last).toEqual({ subject: '', from: '', to: '', receivedFrom: undefined, receivedTo: undefined })
    expect(inputs(wrapper).map((input) => input.element.value)).toEqual(['', '', '', '', ''])
  })

  /**
   * A pending debounce firing after unmount would emit into a component that no longer exists,
   * and the request it triggers would replace whatever the table moved on to showing.
   */
  it('does not emit after it has been unmounted', async () => {
    const wrapper = mountBar()

    await type(wrapper, 0, 'invoice')
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(1000)

    expect(wrapper.emitted('change')).toBeUndefined()
  })

  it('offers no clear button until something is typed', async () => {
    const wrapper = mountBar()

    expect(wrapper.find('button.clear').exists()).toBe(false)

    await type(wrapper, 0, 'invoice')

    expect(wrapper.find('button.clear').exists()).toBe(true)
  })

  /**
   * A request is in flight for a moment every ten seconds, and a disabled input swallows
   * whatever was typed into it at that moment. Locking the box would lose a character at random.
   */
  it('stays usable while a request is in flight', () => {
    const wrapper = mountBar()

    for (const input of inputs(wrapper)) {
      expect(input.attributes('disabled')).toBeUndefined()
    }
  })
})