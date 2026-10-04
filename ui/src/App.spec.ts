import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

/**
 * The poll is the awkward part of adding a filter, and it is why the filter reports two events.
 *
 * <p>The table reloads every ten seconds so that new mail appears. Once there is a search box,
 * that poll can fire while someone is halfway through typing a word, and re-filtering the list
 * by the half-typed fragment both wastes a request and replaces the rows under the cursor. These
 * tests cover that interaction, and the other race the filter introduces: a request already on
 * the wire when the criteria change.
 */

const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    // The client reads an error body as text and parses it, so a mock answering only json()
    // would fail for the wrong reason.
    text: async () => JSON.stringify(body),
  } as unknown as Response
}

function pageOf(subject: string, totalPages = 1): unknown {
  return {
    content: [{ id: 1, from: 'a@test.com', to: 'b@test.com', receivedTimestamp: '', sizeBytes: 1, subject }],
    number: 0,
    size: 25,
    totalElements: totalPages * 25,
    totalPages,
    first: true,
    last: totalPages === 1,
  }
}

let mounted: VueWrapper | undefined

beforeEach(() => {
  // The split pane remembers its divider and the filter pane remembers whether it was open, so
  // without this a test that moves either one decides what the next test starts with.
  localStorage.clear()
  vi.useFakeTimers()
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(jsonResponse(pageOf('Invoice')))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  // Left mounted, a previous table keeps its ten second interval alive and keeps calling
  // fetch, which makes every later count wrong.
  mounted?.unmount()
  mounted = undefined
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

function mountApp(): VueWrapper {
  mounted = mount(App, { attachTo: document.body })
  return mounted
}

function subjectInput(wrapper: VueWrapper) {
  return wrapper.find('input[type="search"]')
}

function lastRequest(): string {
  return fetchMock.mock.calls.at(-1)![0] as string
}



/** Selects the first row the way a user would, since selection is not a prop of App. */
async function selectFirst(wrapper: VueWrapper): Promise<void> {
  await wrapper.find('#message-row-1 input[type="checkbox"]').setValue(true)
  await flushPromises()
}

/** Fails every release, which is how the server explains a rejected override. */
function failReleases(detail: string): void {
  fetchMock.mockImplementation((url: string) =>
    url.includes('/messages/release')
      ? Promise.resolve(jsonResponse({ detail }, 400))
      : Promise.resolve(jsonResponse(pageOf('Invoice'))),
  )
}

/** Releases the selection, whatever it takes to get there. */
async function releaseSelection(wrapper: VueWrapper): Promise<void> {
  await wrapper.findAll('button').find((b) => b.text() === 'Release')!.trigger('click')
  await flushPromises()
  await wrapper.find('[role="dialog"]').trigger('submit')
  await flushPromises()
}

function banner(wrapper: VueWrapper): string {
  return wrapper.find('[role="alert"]').exists() ? wrapper.find('[role="alert"]').text() : ''
}

function buttonLabelled(wrapper: VueWrapper, label: string) {
  const button = wrapper
    .findAll('button')
    .find((candidate: { text: () => string }) => candidate.text() === label)
  if (!button) {
    throw new Error(`No button labelled ${label}`)
  }
  return button
}

describe('App polling', () => {
  it('loads once on mount and then polls', async () => {
    mountApp()
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(10_000)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('carries the filter into the request', async () => {
    const wrapper = mountApp()
    await flushPromises()

    await subjectInput(wrapper).setValue('invoice')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(lastRequest()).toContain('subject=invoice')
  })

  /**
   * The reason the filter reports `editing` separately.
   *
   * <p>Timed so the poll boundary falls inside the debounce window, which is the only way it
   * can: the poll is due at ten seconds and the debounce clears after 300ms, so the only way to
   * be mid-word when the poll fires is to start typing just before it is due. Reloading then
   * would re-filter the list by a fragment and swap the rows out from under the cursor.
   */
  it('holds the poll off while a filter edit is waiting on its debounce', async () => {
    const wrapper = mountApp()
    await flushPromises()
    fetchMock.mockClear()

    // Stop just short of the first poll, then start typing.
    await vi.advanceTimersByTimeAsync(9_990)
    await subjectInput(wrapper).setValue('inv')
    // Cross the ten second mark with the debounce still pending.
    await vi.advanceTimersByTimeAsync(50)
    await flushPromises()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('applies the filter once the debounce clears', async () => {
    const wrapper = mountApp()
    await flushPromises()
    fetchMock.mockClear()

    await vi.advanceTimersByTimeAsync(9_990)
    await subjectInput(wrapper).setValue('inv')
    await vi.advanceTimersByTimeAsync(50)
    await flushPromises()
    expect(fetchMock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(lastRequest()).toContain('subject=inv')
  })

  it('polls again once the filter has settled', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await subjectInput(wrapper).setValue('invoice')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    fetchMock.mockClear()

    await vi.advanceTimersByTimeAsync(10_000)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(lastRequest()).toContain('subject=invoice')
  })

  /**
   * A filter change alters what page 5 means, and it may not exist any more. Staying put would
   * show an empty table that reads as "your search matched nothing".
   */
  it('returns to the first page when the filter changes', async () => {
    fetchMock.mockResolvedValue(jsonResponse(pageOf('Invoice', 3)))
    const wrapper = mountApp()
    await flushPromises()

    await buttonLabelled(wrapper, 'Next').trigger('click')
    await flushPromises()
    expect(lastRequest()).toContain('page=1')

    await subjectInput(wrapper).setValue('invoice')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(lastRequest()).toContain('page=0')
    expect(lastRequest()).toContain('subject=invoice')
  })

  /**
   * Two requests can be in flight together: the poll that was already on the wire when the
   * filter changed, and the debounced request that replaced it. If the slower, older one answers
   * last then its rows win, and the list shows results for criteria the user has moved on from.
   */
  it('ignores a superseded response', async () => {
    const wrapper = mountApp()
    await flushPromises()

    const deferred: { resolve: (value: Response) => void }[] = []
    fetchMock.mockImplementation(() => new Promise<Response>((resolve) => deferred.push({ resolve })))

    // Start a poll, then change the filter while it is still in flight.
    await vi.advanceTimersByTimeAsync(10_000)
    await subjectInput(wrapper).setValue('invoice')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(deferred).toHaveLength(2)

    // The newer request answers first, then the older one arrives late.
    deferred[1].resolve(jsonResponse(pageOf('Filtered result')))
    await flushPromises()
    deferred[0].resolve(jsonResponse(pageOf('Stale result')))
    await flushPromises()

    expect(wrapper.text()).toContain('Filtered result')
    expect(wrapper.text()).not.toContain('Stale result')
  })
})
describe('app shortcuts', () => {
  /**
   * Dispatches on the focused element rather than on window, because that is what the browser
   * does: a keydown targets whatever has focus and bubbles up. Dispatching on window directly
   * would hand the handler a target of `window` and quietly skip the typing check it is
   * supposed to make, so the test would pass for the wrong reason.
   */
  function press(key: string, init: KeyboardEventInit = {}): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init })
    const target = document.activeElement ?? window
    target.dispatchEvent(event)
    return event
  }

  it('focuses the search box on /', async () => {
    const wrapper = mountApp()
    await flushPromises()

    press('/')
    // The pane starts closed, and opening it is a render, so the caret arrives on the next tick
    // rather than in the same task as the keypress.
    await flushPromises()

    expect(document.activeElement).toBe(wrapper.find('input[type="search"]').element)
  })

  /**
   * The point of `/` is to get to the box, not to be a slash key. Once someone is typing in it
   * — or in any other field — `/` belongs to them.
   */
  it('leaves / alone while a field has focus', async () => {
    const wrapper = mountApp()
    await flushPromises()
    const from = wrapper.findAll<HTMLInputElement>('input[type="search"]')[1]
    from.element.focus()

    const event = press('/')

    expect(document.activeElement).toBe(from.element)
    expect(event.defaultPrevented).toBe(false)
  })

  it('leaves / alone while the date fields have focus', async () => {
    const wrapper = mountApp()
    await flushPromises()
    const date = wrapper.find<HTMLInputElement>('input[type="datetime-local"]')
    date.element.focus()

    const event = press('/')

    expect(document.activeElement).toBe(date.element)
    expect(event.defaultPrevented).toBe(false)
  })

  it('deletes the selection on Delete', async () => {
    fetchMock.mockImplementation((url: string) =>
      url.includes('/messages/delete')
        ? Promise.resolve(jsonResponse({ successful: true }))
        : Promise.resolve(jsonResponse(pageOf('Invoice'))),
    )
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)

    press('Delete')
    await flushPromises()

    const deleteCall = fetchMock.mock.calls.find(([url]) => (url as string).includes('/messages/delete'))
    expect(deleteCall).toBeDefined()
    expect(deleteCall![1].body).toContain('1')
  })

  it('does not send a delete when nothing is selected', async () => {
    mountApp()
    await flushPromises()

    press('Delete')
    await flushPromises()

    expect(fetchMock.mock.calls.some(([url]) => (url as string).includes('/messages/delete'))).toBe(false)
  })

  /**
   * Delete is destructive and the dialog is modal. Acting on the selection behind an overlay the
   * user cannot see is precisely the thing a modal is supposed to prevent.
   */
  it('does not delete behind the release dialog', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    await wrapper.findAll('button').find((b) => b.text() === 'Release')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    press('Delete')
    await flushPromises()

    expect(fetchMock.mock.calls.some(([url]) => (url as string).includes('/messages/delete'))).toBe(false)
  })

  /**
   * The whole point of #14: the server knows what was wrong with the request, and that sentence
   * has to reach the screen. Asserted here rather than in the client because the client test only
   * proves the error object is composed, not that anyone is shown it.
   */
  it('shows what the server said was wrong when a release is rejected', async () => {
    fetchMock.mockImplementation((url: string) =>
      url.includes('/messages/release')
        ? Promise.resolve(
            jsonResponse(
              {
                detail: 'overrideToAddresses must name at least one address when overrideTo is true.',
                status: 400,
                title: 'Bad Request',
              },
              400,
            ),
          )
        : Promise.resolve(jsonResponse(pageOf('Invoice'))),
    )
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)

    await wrapper.findAll('button').find((b) => b.text() === 'Release')!.trigger('click')
    await flushPromises()
    await wrapper.find('[role="dialog"]').trigger('submit')
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain(
      'overrideToAddresses must name at least one address when overrideTo is true.',
    )
  })

  it('does not delete while a request is in flight', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    let finish: (value: Response) => void = () => {}
    fetchMock.mockImplementation(() => new Promise<Response>((resolve) => (finish = resolve)))
    // Re-sorting is the shortest path to a request that stays in flight.
    await wrapper.findAll('thead th button')[1].trigger('click')
    await flushPromises()

    press('Delete')
    await flushPromises()
    finish(jsonResponse(pageOf('Invoice')))
    await flushPromises()

    expect(fetchMock.mock.calls.some(([url]) => (url as string).includes('/messages/delete'))).toBe(false)
  })
})

describe('error banner', () => {
  /**
   * The sequence #21 is about: a release is rejected, the banner says why, and the user clicks
   * a message to check whether it was one of the released ones. Reading a message cannot fix a
   * release, so the explanation has to still be there afterwards.
   */
  it('keeps a failed release explained while the user reads a message', async () => {
    failReleases('Message 1 could not be released: relay refused.')
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    await releaseSelection(wrapper)
    expect(banner(wrapper)).toContain('relay refused')

    await wrapper.find('#message-row-1').trigger('click')
    await flushPromises()

    expect(banner(wrapper)).toContain('relay refused')
  })

  /** Retrying the action that failed is how the user clears it, so that has to work. */
  it('clears the banner when the failed action is retried successfully', async () => {
    failReleases('relay refused')
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    await releaseSelection(wrapper)
    expect(banner(wrapper)).not.toBe('')

    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse({ successful: true })))
    await releaseSelection(wrapper)

    expect(banner(wrapper)).toBe('')
  })

  /**
   * The list reloads every ten seconds on its own. Nobody asked for that, so it cannot take the
   * banner down with it.
   */
  it('does not let the background poll clear the banner', async () => {
    failReleases('relay refused')
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    await releaseSelection(wrapper)
    expect(banner(wrapper)).toContain('relay refused')

    await vi.advanceTimersByTimeAsync(10_000)
    await flushPromises()

    expect(banner(wrapper)).toContain('relay refused')
  })

  it('replaces an older error when a later action fails too', async () => {
    failReleases('relay refused')
    const wrapper = mountApp()
    await flushPromises()
    await selectFirst(wrapper)
    await releaseSelection(wrapper)

    fetchMock.mockImplementation((url: string) =>
      url.includes('/messages/1')
        ? Promise.resolve(jsonResponse({ detail: 'Message 1 is gone.' }, 404))
        : Promise.resolve(jsonResponse(pageOf('Invoice'))),
    )
    await wrapper.find('#message-row-1').trigger('click')
    await flushPromises()

    expect(banner(wrapper)).toContain('Message 1 is gone.')
  })
})

function pressKey(key: string): void {
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
}

describe('filter pane', () => {
  function pane(wrapper: VueWrapper) {
    return wrapper.find('#filter-pane')
  }

  it('starts closed, since the filter is not what most visits are for', async () => {
    const wrapper = mountApp()
    await flushPromises()
    expect(pane(wrapper).classes()).toContain('collapsed')
    expect(buttonLabelled(wrapper, 'Filters').attributes('aria-expanded')).toBe('false')
  })

  it('opens and closes from the toolbar', async () => {
    const wrapper = mountApp()
    await flushPromises()
    const toggle = buttonLabelled(wrapper, 'Filters')

    await toggle.trigger('click')
    expect(pane(wrapper).classes()).not.toContain('collapsed')
    expect(toggle.attributes('aria-expanded')).toBe('true')

    await toggle.trigger('click')
    expect(pane(wrapper).classes()).toContain('collapsed')
    expect(toggle.attributes('aria-expanded')).toBe('false')
  })

  /**
   * Hiding by clipping is not enough on its own: the fields would still take focus, so tabbing
   * through the app would land in a search box nobody can see. `inert` is what takes the whole
   * pane out of the tab order and the accessibility tree, and it has to be asserted because the
   * failure is invisible until someone tabs into a box that is not there.
   */
  it('takes the closed fields out of the tab order', async () => {
    const wrapper = mountApp()
    await flushPromises()
    expect(pane(wrapper).attributes('inert')).toBeDefined()

    await buttonLabelled(wrapper, 'Filters').trigger('click')
    expect(pane(wrapper).attributes('inert')).toBeUndefined()
  })

  /**
   * `/` jumps to the search box, so it has to open the pane on the way.
   *
   * <p>The pane is inert while closed, and that attribute is not lifted until the next render, so
   * opening and focusing in the same step leaves the caret nowhere in a real browser. jsdom does
   * not enforce inert, so this pins the outcome rather than the ordering the tick is there for:
   * it fails if `/` stops opening the pane, and would not fail if the tick were dropped.
   */
  it('opens the pane and puts the caret in the search box on /', async () => {
    const wrapper = mountApp()
    await flushPromises()
    expect(pane(wrapper).classes()).toContain('collapsed')

    pressKey('/')
    await flushPromises()

    expect(pane(wrapper).classes()).not.toContain('collapsed')
    expect(document.activeElement).toBe(subjectInput(wrapper).element)
  })

  /**
   * A pane shut with a filter still applied leaves a list that looks like it simply has nothing
   * else in it, which is not a state anyone can tell apart from an unfiltered one.
   */
  it('marks the toggle while a filter is applied but the pane is closed', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await buttonLabelled(wrapper, 'Filters').trigger('click')
    await subjectInput(wrapper).setValue('invoice')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await buttonLabelled(wrapper, 'Filters').trigger('click')

    expect(pane(wrapper).classes()).toContain('collapsed')
    expect(buttonLabelled(wrapper, 'Filters').attributes('aria-label')).toContain('a filter is applied')
  })

  it('leaves the toggle unmarked when nothing is filtered', async () => {
    const wrapper = mountApp()
    await flushPromises()
    expect(buttonLabelled(wrapper, 'Filters').attributes('aria-label')).toBeUndefined()
  })

  it('remembers the choice, so a filter user does not reopen it every reload', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await buttonLabelled(wrapper, 'Filters').trigger('click')

    mounted?.unmount()
    const second = mountApp()
    await flushPromises()

    expect(pane(second).classes()).not.toContain('collapsed')
  })

  it('remembers having been closed, rather than reopening on the next reload', async () => {
    const wrapper = mountApp()
    await flushPromises()
    await buttonLabelled(wrapper, 'Filters').trigger('click')
    await buttonLabelled(wrapper, 'Filters').trigger('click')

    mounted?.unmount()
    const second = mountApp()
    await flushPromises()

    expect(pane(second).classes()).toContain('collapsed')
  })
})
