/**
 * Typed access to the mailoverlord JSON API.
 *
 * The field names and types in this file all come from the generated OpenAPI document, so
 * changing a DTO on the server and re-running `npm run generate:types` is enough to keep
 * the UI compiling against the real API.
 */

import type { components } from './schema'

type Schema = components['schemas']

/**
 * Spring reports every property of a record as both optional and nullable, which is
 * accurate but leaves every call site guarding against undefined. Tighten the generated
 * types in one place instead, so the components can rely on the fields being present.
 */
type Present<T> = { [K in keyof T]-?: NonNullable<T[K]> }

export type MessageSummary = Omit<Present<Schema['MessageSummary']>, 'releasedTimestamp'> & {
  releasedTimestamp: NonNullable<Schema['MessageSummary']['releasedTimestamp']> | null
}
/**
 * One leaf of a message's MIME structure, as {@link MessageDetail} reports it.
 *
 * <p>A part is named by its filename when the sender gave one and by its content type
 * otherwise. The size is the decoded length, which the sender controls and which is
 * therefore absent rather than zero when it is not known.
 */
type MessagePartSchema = Schema['MessagePart']
export type MessagePart = Omit<
  Present<MessagePartSchema>,
  'filename' | 'sizeBytes' | 'disposition'
> & {
  filename: NonNullable<MessagePartSchema['filename']> | null
  sizeBytes: NonNullable<MessagePartSchema['sizeBytes']> | null
  disposition: NonNullable<MessagePartSchema['disposition']> | null
}

export type MessageDetail = Omit<Present<Schema['MessageDetail']>, 'releasedTimestamp' | 'parts'> & {
  releasedTimestamp: NonNullable<Schema['MessageDetail']['releasedTimestamp']> | null
  parts: MessagePart[]
}

/**
 * The page metadata still comes from the generated document; only the element type is
 * spelled out, because {@link Present} does not recurse into a list's contents and the
 * content is normalised on the way out.
 */
export type MessagePage = Present<Omit<Schema['PageResponse'], 'content'>> & {
  content: MessageSummary[]
}

/**
 * The criteria narrowing a listing, as the server understands them.
 *
 * <p>Every field is optional and an absent one means "do not filter on this". Empty strings are
 * dropped when the request is built rather than sent as `?subject=`: the server treats blank as
 * absent, but not sending them at all keeps the unfiltered URL identical to the one the app used
 * before search existed, which is easier to read in a log.
 */
export interface MessageFilterRequest {
  subject?: string
  from?: string
  to?: string
  receivedFrom?: string
  receivedTo?: string
}

export interface MessagePageRequest extends MessageFilterRequest {
  page: number
  size: number
  sort?: string
}

/** The filter fields, in the order {@link listMessages} writes them. */
const FILTER_FIELDS: (keyof MessageFilterRequest)[] = ['subject', 'from', 'to', 'receivedFrom', 'receivedTo']

export type ReleaseRequest = {
  messageIds: number[]
  overrideTo?: boolean
  overrideToAddresses?: string
  overrideFrom?: boolean
  overrideFromAddress?: string
}

export interface MessageResponse {
  successful: boolean
  errorMessage: string | null
}

/**
 * What happened to one message in a release batch. Delivery reaches a real inbox and cannot be
 * undone, so this is what a caller consults before retrying: retrying the whole batch re-sends
 * whatever already went out.
 */
export interface MessageReleaseOutcome {
  id: number
  released: boolean
  errorMessage: string | null
}

export interface MessageReleaseResponse extends MessageResponse {
  outcomes: MessageReleaseOutcome[]
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? 'GET'
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...csrfHeaders(method), ...init?.headers },
  })

  if (!response.ok) {
    if (response.status === 401) {
      return unauthorized(response)
    }
    const failure = `${method} ${path} failed with ${response.status}`
    const reason = await explanation(response)
    // A view-only user reaching release or delete is a permission problem, not a request
    // problem; name it as one so the banner reads as "ask for more access", not "fix a query".
    const message = reason ? `${failure}: ${reason}` : failure
    throw new ApiError(
      response.status === 403 && reason ? `Not allowed: ${reason}` : message,
      response.status,
    )
  }

  return (await response.json()) as T
}

/**
 * What a 401 does for the user. Two of the modes hand the browser over to a sign-in flow the
 * SPA cannot run from a fetch: Basic answers 401 with a login-url of /login, whose prompt only
 * shows on a top-level navigation, and OIDC answers with the token endpoint, which starts the
 * authorization-code dance when navigated to. A bodyless or non-JSON 401 — what a proxy in front
 * of the app tends to produce — goes to /login on the same reasoning.
 *
 * <p>Header mode is the exception that must not redirect at all. The reverse proxy owns sign-in,
 * so its 401 is a problem detail with no login-url; there is no page to send the browser to, and
 * posing the question again is what restores the identity header — redirecting would only reload
 * the same page over a request that is still unauthorized. The proxy's detail is shown instead.
 */
async function unauthorized(response: Response): Promise<never> {
  const problem = await unauthorizedProblem(response)
  if (problem === null) {
    window.location.assign('/login')
    throw new ApiError('Sign in required.', 401)
  }
  const { loginUrl, detail } = problem
  if (loginUrl !== null) {
    window.location.assign(loginUrl)
    throw new ApiError('Sign in required.', 401)
  }
  throw new ApiError(detail ?? 'Sign in required.', 401)
}

/**
 * The shape of a 401 the app itself wrote: an RFC 9457 problem detail naming a login-url (Basic
 * and OIDC) or carrying only a detail because the app keeps no login flow of its own (header
 * mode). Anything unrecognisable — an empty body or a proxy's HTML — comes back as null and is
 * handled as "send the browser to /login".
 */
async function unauthorizedProblem(
  response: Response,
): Promise<{ loginUrl: string | null; detail: string | null } | null> {
  const body = await response.text()
  if (body.trim() === '') {
    return null
  }
  try {
    const problem = JSON.parse(body) as { 'login-url'?: unknown; detail?: unknown }
    if (typeof problem !== 'object' || problem === null) {
      return null
    }
    const loginUrl =
      typeof problem['login-url'] === 'string' && problem['login-url'] !== ''
        ? problem['login-url']
        : null
    const detail = typeof problem.detail === 'string' && problem.detail !== '' ? problem.detail : null
    return { loginUrl, detail }
  } catch {
    return null
  }
}

/**
 * The OIDC session is a cookie, so the browser's identity rides along on every request and the
 * SPA holds no token of its own. That same quiet cookie is what makes state-changing requests
 * cross-site in origin terms, which is why Spring Security asks for a CSRF token on them. The
 * token arrives as a readable cookie (the server deliberately leaves it readable) and goes back
 * in X-XSRF-TOKEN, which is the header CookieCsrfTokenRepository expects. Basic mode has no such
 * cookie and simply stays header-free.
 */
function csrfHeaders(method: string): Record<string, string> {
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    return {}
  }
  const token = csrfToken()
  return token === null ? {} : { 'X-XSRF-TOKEN': token }
}

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match === null ? null : decodeURIComponent(match[1])
}

/** Longer than this in a one-line banner is noise rather than an explanation. */
const MAX_EXPLANATION = 200

/**
 * What the server said was wrong, if it said anything.
 *
 * <p>A rejected request comes back as RFC 9457 problem details, and its `detail` is the only part
 * a person can act on: "overrideToAddresses must name at least one address when overrideTo is
 * true" says what to change, where "failed with 400" says only that something happened.
 *
 * <p>The body is read as text and parsed rather than read as JSON, because a body is not
 * guaranteed to be JSON. This is the one place an error body is consumed, so reading it twice
 * would not work; reading it once as text and trying to parse handles both shapes.
 */
async function explanation(response: Response): Promise<string | null> {
  const body = await response.text()
  if (body.trim() === '') {
    return null
  }
  try {
    const problem = JSON.parse(body) as { detail?: unknown; title?: unknown }
    if (typeof problem.detail === 'string' && problem.detail !== '') {
      return problem.detail
    }
    // A problem detail with no detail of its own still carries a title worth showing.
    return typeof problem.title === 'string' && problem.title !== '' ? problem.title : null
  } catch {
    return proseFrom(body)
  }
}

/**
 * A plain-text error body, which is what a reverse proxy in front of the app tends to produce.
 *
 * <p>Markup is left out rather than shown. A gateway's error page is not an explanation, and
 * dropping one into a single-line banner helps nobody; the status and the request already say the
 * call failed, which is the part a person can act on.
 */
function proseFrom(body: string): string | null {
  const text = body.trim()
  if (text.startsWith('<')) {
    return null
  }
  return text.length > MAX_EXPLANATION ? `${text.slice(0, MAX_EXPLANATION)}...` : text
}

function normaliseSummary(summary: Schema['MessageSummary']): MessageSummary {
  return {
    id: summary.id ?? 0,
    from: summary.from ?? '',
    to: summary.to ?? '',
    receivedTimestamp: summary.receivedTimestamp ?? '',
    releasedTimestamp: summary.releasedTimestamp ?? null,
    sizeBytes: summary.sizeBytes ?? 0,
    // A message is allowed to have no Subject header at all.
    subject: summary.subject ?? '',
  }
}

function normalisePart(part: Schema['MessagePart']): MessagePart {
  return {
    filename: part.filename ?? null,
    contentType: part.contentType ?? '',
    sizeBytes: part.sizeBytes ?? null,
    disposition: part.disposition ?? null,
    displayedBody: part.displayedBody ?? false
  }
}

export async function listMessages(pageRequest: MessagePageRequest): Promise<MessagePage> {
  const query = new URLSearchParams({ page: String(pageRequest.page), size: String(pageRequest.size) })
  if (pageRequest.sort) {
    query.set('sort', pageRequest.sort)
  }
  for (const field of FILTER_FIELDS) {
    const value = pageRequest[field]
    // A whitespace-only box means "no filter" too, so it is dropped here as well as on the
    // server. Sending it would produce a filter that matches everything while looking active.
    if (value && value.trim().length > 0) {
      query.set(field, value.trim())
    }
  }

  const page = await request<Schema['PageResponse']>(`/messages/list?${query}`)
  return {
    content: (page.content ?? []).map(normaliseSummary),
    number: page.number ?? 0,
    size: page.size ?? 0,
    totalElements: page.totalElements ?? 0,
    totalPages: page.totalPages ?? 0,
    first: page.first ?? true,
    last: page.last ?? true,
  }
}

export async function getMessage(id: number): Promise<MessageDetail> {
  const message = await request<Schema['MessageDetail']>(`/messages/${id}`)
  return {
    id: message.id ?? id,
    from: message.from ?? '',
    to: message.to ?? '',
    receivedTimestamp: message.receivedTimestamp ?? '',
    releasedTimestamp: message.releasedTimestamp ?? null,
subject: message.subject ?? '',
    body: message.body ?? '',
    parts: (message.parts ?? []).map(normalisePart)
  }
}

export async function deleteMessages(messageIds: number[]): Promise<MessageResponse> {
  const response = await request<Schema['MessageResponse']>('/messages/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageIds }),
  })
  return { successful: response.successful ?? false, errorMessage: response.errorMessage ?? null }
}

export async function releaseMessages(
  release: ReleaseRequest,
): Promise<MessageReleaseResponse> {
  const response = await request<Schema['MessageReleaseResponse']>('/messages/release', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(release),
  })
  return {
    successful: response.successful ?? false,
    errorMessage: response.errorMessage ?? null,
    outcomes: (response.outcomes ?? []).map(outcome => ({
      id: outcome.id ?? 0,
      released: outcome.released ?? false,
      errorMessage: outcome.errorMessage ?? null,
    })),
  }
}
