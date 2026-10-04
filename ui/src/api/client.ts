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
export type MessageDetail = Omit<Present<Schema['MessageDetail']>, 'releasedTimestamp'> & {
  releasedTimestamp: NonNullable<Schema['MessageDetail']['releasedTimestamp']> | null
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
  const response = await fetch(path, {
    headers: { Accept: 'application/json', ...init?.headers },
    ...init,
  })

  if (!response.ok) {
    throw new ApiError(
      (await problemDetail(response)) ?? `${init?.method ?? 'GET'} ${path} failed with ${response.status}`,
      response.status,
    )
  }

  return (await response.json()) as T
}

/**
 * What the server said was wrong, if it said anything.
 *
 * <p>A rejected request comes back as RFC 9457 problem details, and its `detail` is the only part
 * a person can act on: "overrideToAddresses must name at least one address when overrideTo is
 * true" says what to change, where "POST /messages/release failed with 400" says only that
 * something happened. Prefers `detail`, falling back to `title` for a problem that has one.
 */
async function problemDetail(response: Response): Promise<string | null> {
  try {
    const problem = (await response.json()) as { detail?: unknown; title?: unknown }
    if (typeof problem.detail === 'string' && problem.detail !== '') {
      return problem.detail
    }
    return typeof problem.title === 'string' && problem.title !== '' ? problem.title : null
  } catch {
    // A body is not guaranteed: a proxy can fail, a crash writes HTML, and an empty body is not
    // JSON. None of that is worth reporting over the failure itself.
    return null
  }
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
