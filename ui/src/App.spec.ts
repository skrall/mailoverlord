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

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as Response
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