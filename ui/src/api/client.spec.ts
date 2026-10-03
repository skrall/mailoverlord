import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  deleteMessages,
  getMessage,
  listMessages,
  releaseMessages,
} from './client'

/**
 * The API is generated from the OpenAPI document, where every property of a record is
 * optional and nullable. These tests pin what the client does with the values that can
 * actually be missing, because a gap here shows up as a blank cell in the table rather than
 * as an error.
 */

const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('listMessages', () => {
  it('asks for the requested page and size', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 2, size: 10 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/messages/list?page=2&size=10',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('includes the sort when there is one and omits it when there is not', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 0, size: 25, sort: 'subject,asc' })
    expect(fetchMock.mock.calls[0][0]).toContain('sort=subject%2Casc')

    fetchMock.mockClear()
    await listMessages({ page: 0, size: 25 })
    expect(fetchMock.mock.calls[0][0]).not.toContain('sort=')
  })

  /**
   * Every property of a record arrives as optional, so the page metadata has to be defaulted
   * too or the pagination bar renders blanks.
   */
  it('defaults every page field that is missing', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}))

    const page = await listMessages({ page: 0, size: 25 })

    expect(page).toEqual({
      content: [],
      number: 0,
      size: 0,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    })
  })

  it('defaults a null content to an empty page', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: null }))

    await expect(listMessages({ page: 0, size: 25 })).resolves.toMatchObject({ content: [] })
  })

  it('fills in a summary that arrived with everything missing', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [{}] }))

    const page = await listMessages({ page: 0, size: 25 })

    expect(page.content[0]).toEqual({
      id: 0,
      from: '',
      to: '',
      receivedTimestamp: '',
      releasedTimestamp: null,
      sizeBytes: 0,
      subject: '',
    })
  })

  it('keeps the values it was given', async () => {
    const summary = {
    id: 7,
    from: 'a@test.com',
    to: 'b@test.com',
    receivedTimestamp: '2024-03-01T09:30:00Z',
    releasedTimestamp: null,
    sizeBytes: 1234,
    subject: 'Hello',
    }
    fetchMock.mockResolvedValue(jsonResponse({ content: [summary] }))

    const page = await listMessages({ page: 0, size: 25 })

    expect(page.content[0]).toEqual(summary)
  })
})

describe('getMessage', () => {
  it('requests the message by id', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}))

    await getMessage(42)

    expect(fetchMock.mock.calls[0][0]).toBe('/messages/42')
  })

  it('falls back to the requested id when the response omits it', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ from: 'a@test.com' }))

    await expect(getMessage(42)).resolves.toMatchObject({ id: 42 })
  })

  it('treats a null body as an empty one', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 1, body: null }))

    await expect(getMessage(1)).resolves.toMatchObject({ body: '' })
  })
})

describe('deleteMessages', () => {
  it('posts the ids as JSON', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ successful: true }))

    await deleteMessages([1, 2])

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual({ messageIds: [1, 2] })
  })

  /**
   * A release or delete can fail without failing the request, so a missing flag has to mean
   * failure rather than undefined.
   */
  it('defaults a missing successful flag to false', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}))

    await expect(deleteMessages([1])).resolves.toEqual({
      successful: false,
      errorMessage: null,
    })
  })
})

describe('releaseMessages', () => {
  it('posts the release request as given', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ successful: true }))

    const release = { messageIds: [1], overrideTo: true, overrideToAddresses: 'c@test.com' }
    await releaseMessages(release)

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual(release)
  })

  it('carries the failure message back when there is one', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ successful: false, errorMessage: 'Boom' }))

    await expect(releaseMessages({ messageIds: [1] })).resolves.toEqual({
      successful: false,
      errorMessage: 'Boom',
    })
  })
})

describe('request failures', () => {
  it('throws an ApiError carrying the status', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 400))

    await expect(listMessages({ page: 0, size: 25 })).rejects.toBeInstanceOf(ApiError)
    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({ status: 400 })
  })

  it('surfaces a 404 as an ApiError rather than an unparsed body', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 404))

    await expect(getMessage(99)).rejects.toMatchObject({ name: 'ApiError', status: 404 })
  })
})