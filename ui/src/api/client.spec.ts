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
    // The error path reads the body as text and parses it, so a mock that only answers json()
    // would fail for the wrong reason.
    text: async () => JSON.stringify(body),
  } as unknown as Response
}

/** An error body that is not JSON at all, which is what a proxy in front of the app sends. */
function textResponse(body: string, status: number): Response {
  return {
    ok: false,
    status,
    json: async () => JSON.parse(body),
    text: async () => body,
  } as unknown as Response
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
   * The filter parameters have to reach the server as query parameters, because the filtering
   * happens there. Filtering the returned page instead would hide matches on later pages while
   * looking like a complete answer.
   */
  it('sends every filter criterion as a query parameter', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({
      page: 0,
      size: 25,
      subject: 'invoice',
      from: 'alice',
      to: 'bob',
      receivedFrom: '2024-03-01T00:00:00.000Z',
      receivedTo: '2024-03-02T00:00:00.000Z',
    })

    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('subject=invoice')
    expect(url).toContain('from=alice')
    expect(url).toContain('to=bob')
    expect(url).toContain('receivedFrom=2024-03-01T00%3A00%3A00.000Z')
    expect(url).toContain('receivedTo=2024-03-02T00%3A00%3A00.000Z')
  })

  /**
   * An empty box means "no filter". Sending `?subject=` anyway would produce a URL that reads as
   * filtered, and it would not match the request the app made before search existed.
   */
  it('omits blank and whitespace-only criteria', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 0, size: 25, subject: '', from: '   ', to: undefined })

    expect(fetchMock).toHaveBeenCalledWith(
      '/messages/list?page=0&size=25',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('trims a criterion before sending it', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 0, size: 25, subject: '  invoice  ' })

    expect(fetchMock.mock.calls[0][0]).toContain('subject=invoice')
    expect(fetchMock.mock.calls[0][0]).not.toContain('%20invoice')
  })

  it('combines the filter with paging and sorting', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 1, size: 10, sort: 'subject,asc', subject: 'invoice' })

    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('page=1')
    expect(url).toContain('size=10')
    expect(url).toContain('sort=subject%2Casc')
    expect(url).toContain('subject=invoice')
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

  /**
   * The server reports an attachment with no filename, an unknown size and no disposition as
   * nulls, so they have to survive the trip as nulls. Coerced to 0 or undefined they would
   * read as an empty unnamed file instead of absent facts.
   */
  it('keeps an unknown filename and size as null rather than inventing values', async () => {
    fetchMock.mockResolvedValue(jsonResponse({
      id: 1,
      parts: [{ filename: null, contentType: 'text/plain', sizeBytes: null, disposition: null }]
    }))

    await expect(getMessage(1)).resolves.toMatchObject({
      parts: [{ filename: null, contentType: 'text/plain', sizeBytes: null, disposition: null }]
    })
  })

  it('reports no parts when the response omits them', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 1 }))

    await expect(getMessage(1)).resolves.toMatchObject({ parts: [] })
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
      outcomes: [],
    })
  })

  /**
   * A batch can partly succeed, and delivery cannot be undone, so the per-id outcomes are what
   * says which messages already reached an inbox.
   */
  it('reports what happened to each message separately', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        successful: false,
        errorMessage: 'Released 2 of 3 messages; 1 failed.',
        outcomes: [
          { id: 1, released: true },
          { id: 2, released: false, errorMessage: 'smtp refused' },
          { id: 3, released: true },
        ],
      }),
    )

    const response = await releaseMessages({ messageIds: [1, 2, 3] })

    expect(response.successful).toBe(false)
    expect(response.outcomes).toEqual([
      { id: 1, released: true, errorMessage: null },
      { id: 2, released: false, errorMessage: 'smtp refused' },
      { id: 3, released: true, errorMessage: null },
    ])
  })
})

describe('CSRF handling', () => {
  /**
   * The OIDC session is a cookie, so state-changing requests must prove they came from the
   * page. The server hands the token over as a readable cookie and wants it back as
   * X-XSRF-TOKEN; both names are Spring Security's CookieCsrfTokenRepository defaults.
   */
  it('echoes the XSRF-TOKEN cookie back on a POST', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=cookie-value' })
    fetchMock.mockResolvedValue(jsonResponse({ successful: true }))

    await deleteMessages([1])

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('cookie-value')
  })

  it('sends no CSRF header when the server set no cookie', async () => {
    vi.stubGlobal('document', { cookie: '' })
    fetchMock.mockResolvedValue(jsonResponse({ successful: true }))

    await deleteMessages([1])

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('does not add a CSRF header to a state-less GET', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=still-there' })
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }))

    await listMessages({ page: 0, size: 25 })

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined()
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

  /**
   * A rejected request comes back as problem details, and the detail is the only part of it a
   * person can act on. Reporting the status alone is what made a missing override address look
   * like an unexplained failure. See #14.
   */
  it('uses the explanation the server sent rather than the status alone', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ detail: 'overrideToAddresses must name at least one address when overrideTo is true.' }, 400),
    )

    await expect(releaseMessages({ messageIds: [1], overrideTo: true })).rejects.toMatchObject({
      message:
        'POST /messages/release failed with 400: overrideToAddresses must name at least one address when overrideTo is true.',
      status: 400,
    })
  })

  /**
   * Basic authentication does not pop its dialog for fetch, so a 401 cannot be answered in
   * place: the browser has to be sent to /login for the native prompt to appear. Surfacing the
   * 401 as a banner would just tell the user about an error none of the buttons they can see
   * fixes.
   */
  it('sends the browser to /login on a bodyless 401 instead of reporting it', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    fetchMock.mockResolvedValue({ ok: false, status: 401, text: async () => '' } as unknown as Response)

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({ status: 401 })
    expect(assign).toHaveBeenCalledWith('/login')
  })

  /**
   * Under OIDC the API answers 401 with a problem detail naming the provider endpoint, so the
   * browser is sent straight back into the login flow rather than to /login (which under OIDC
   * is only the page that happens to list the providers).
   */
  it('uses the login-url the server names in the 401 problem detail', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    fetchMock.mockResolvedValue(
      jsonResponse({ 'login-url': '/oauth2/authorization/okta' }, 401),
    )

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({ status: 401 })
    expect(assign).toHaveBeenCalledWith('/oauth2/authorization/okta')
  })

  it('still reaches /login when the 401 body is not JSON', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    fetchMock.mockResolvedValue(textResponse('gateway says no', 401))

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({ status: 401 })
    expect(assign).toHaveBeenCalledWith('/login')
  })

  /**
   * Header mode has no login flow of its own — the reverse proxy owns sign-in — so a 401 is a
   * problem detail that names no login-url and there is nothing to navigate to. Redirecting
   * would only reload the page over a request that is still unauthorized, so the browser stays
   * put and the proxy's detail becomes the banner. See #58.
   */
  it('stays put when the 401 problem detail names no login-url', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    fetchMock.mockResolvedValue(
      jsonResponse(
        { detail: 'Authentication is required. The reverse proxy must set the X-Remote-User header.' },
        401,
      ),
    )

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({
      message:
        'Authentication is required. The reverse proxy must set the X-Remote-User header.',
      status: 401,
    })
    expect(assign).not.toHaveBeenCalled()
  })

  it('falls back to a fixed message when a JSON 401 explains nothing in the detail', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    fetchMock.mockResolvedValue(jsonResponse({}, 401))

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({
      message: 'Sign in required.',
      status: 401,
    })
    expect(assign).not.toHaveBeenCalled()
  })

  /**
   * A view-only user reaching release or delete gets a 403, and the banner names it as a
   * permission problem rather than the request being wrong. The status alone reads like a
   * transient failure; the point is that nothing the user retries will fix it.
   */
  it('names a 403 as a permission problem', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ detail: 'You are not allowed to do that.' }, 403))

    await expect(releaseMessages({ messageIds: [1], overrideTo: true })).rejects.toMatchObject({
      message: 'Not allowed: You are not allowed to do that.',
      status: 403,
    })
  })

  /**
   * The status and the request say the call failed, which is useful and worth keeping whatever
   * else the body turned out to contain.
   */
  it('keeps the status in the message alongside the explanation', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ title: 'Not Found' }, 404))

    await expect(getMessage(99)).rejects.toMatchObject({
      message: 'GET /messages/99 failed with 404: Not Found',
      status: 404,
    })
  })

  it('reports a plain-text error body from a proxy', async () => {
    fetchMock.mockResolvedValue(textResponse('upstream connect error', 502))

    await expect(listMessages({ page: 0, size: 25 })).rejects.toMatchObject({
      message: 'GET /messages/list?page=0&size=25 failed with 502: upstream connect error',
      status: 502,
    })
  })

  /**
   * A gateway error page is not an explanation, and it would otherwise become the text of a
   * one-line banner.
   */
  it('leaves an html error page out of the message', async () => {
    fetchMock.mockResolvedValue(textResponse('<html><body>502 Bad Gateway</body></html>', 502))

    await expect(getMessage(99)).rejects.toMatchObject({
      message: 'GET /messages/99 failed with 502',
      status: 502,
    })
  })

  it('falls back to the status when the failure carries no detail', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 400))

    await expect(getMessage(99)).rejects.toMatchObject({
      message: 'GET /messages/99 failed with 400',
      status: 400,
    })
  })

  /**
   * A body is not guaranteed. A proxy can answer with HTML and a crash can write nothing, so
   * reading the detail must not turn one failure into another.
   */
  it('still reports the status when the failure has no body at all', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 502, text: async () => '' } as unknown as Response)

    await expect(getMessage(99)).rejects.toMatchObject({
      message: 'GET /messages/99 failed with 502',
      status: 502,
    })
  })
})