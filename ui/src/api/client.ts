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

export type MessageSummary = Present<Schema['MessageSummary']>
export type MessageDetail = Present<Schema['MessageDetail']>

/**
 * The page metadata still comes from the generated document; only the element type is
 * spelled out, because {@link Present} does not recurse into a list's contents and the
 * content is normalised on the way out.
 */
export type MessagePage = Present<Omit<Schema['PageResponseMessageSummary'], 'content'>> & {
  content: MessageSummary[]
}

export interface MessagePageRequest {
  page: number
  size: number
  sort?: string
}

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
    throw new ApiError(`${init?.method ?? 'GET'} ${path} failed with ${response.status}`, response.status)
  }

  return (await response.json()) as T
}

function normaliseSummary(summary: Schema['MessageSummary']): MessageSummary {
  return {
    id: summary.id ?? 0,
    from: summary.from ?? '',
    to: summary.to ?? '',
    receivedTimestamp: summary.receivedTimestamp ?? '',
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

  const page = await request<Schema['PageResponseMessageSummary']>(`/messages/list?${query}`)
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

export async function releaseMessages(release: ReleaseRequest): Promise<MessageResponse> {
  const response = await request<Schema['MessageResponse']>('/messages/release', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(release),
  })
  return { successful: response.successful ?? false, errorMessage: response.errorMessage ?? null }
}
