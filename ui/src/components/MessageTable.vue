<script setup lang="ts">
import { computed, nextTick } from 'vue'
import type { MessageSummary } from '../api/client'
import { formatBytes, formatTimestamp, summariseAddresses } from '../format'

const props = defineProps<{
  messages: MessageSummary[]
  selectedIds: number[]
  activeId: number | null
  sortField: string
  sortDirection: 'asc' | 'desc'
  busy: boolean
}>()

const emit = defineEmits<{
  (event: 'toggle', id: number): void
  (event: 'toggle-all'): void
  (event: 'open', id: number): void
  (event: 'sort', field: string): void
}>()

const columns: { field: string; label: string }[] = [
  { field: 'receivedTimestamp', label: 'Received' },
  { field: 'from', label: 'From' },
  { field: 'to', label: 'To' },
  { field: 'subject', label: 'Subject' },
]

function ariaSort(field: string, sortField: string, direction: 'asc' | 'desc'): 'ascending' | 'descending' | 'none' {
  if (field !== sortField) {
    return 'none'
  }
  return direction === 'asc' ? 'ascending' : 'descending'
}

/**
 * The one row that is reachable by Tab.
 *
 * <p>This is the roving tabindex: the table is a single tab stop, and the arrow keys move within
 * it. The alternative, a tab stop per row, means tabbing through a page of 25 messages takes 25
 * presses to get anywhere, and almost nobody does it, so the table stays unreachable in practice.
 *
 * <p>It has to be exactly one row even when nothing is open yet, or the table would be skipped
 * entirely, so it falls back to the first row until something has been opened.
 */
const tabStopIndex = computed(() => {
  const active = props.messages.findIndex((message) => message.id === props.activeId)
  return active === -1 ? 0 : active
})

function rowAt(index: number): MessageSummary | undefined {
  return props.messages[index]
}

/**
 * Moves the cursor and puts DOM focus on the row it lands on.
 *
 * <p>The focus call is not decoration. Emitting `open` alone would move the detail panel and the
 * `active` highlight, but the browser's focus would still be on the row the user started from,
 * so the next keypress would move relative to the wrong place and a screen reader would announce
 * nothing at all. Moving focus is what makes the arrow keys behave the way they look.
 *
 * <p>Deferred to `nextTick` because the row is not focusable until the re-render that the new
 * `activeId` causes has landed. Focusing before that would be a no-op on `tabindex="-1"`.
 */
async function moveTo(index: number): Promise<void> {
  const target = rowAt(index)
  if (!target) {
    return
  }
  emit('open', target.id)
  await nextTick()
  focusRow(target.id)
}

function focusRow(id: number): void {
  const element = document.querySelector<HTMLTableRowElement>(`#message-row-${id}`)
  element?.focus()
}

/**
 * The row a keypress belongs to, read from the event rather than tracked in state.
 *
 * <p>The handler sits on the tbody, so the target is the focused row or something inside it. The
 * alternative is a ref holding the focused id, which would be a second source of truth for
 * something the DOM already knows and could disagree with after a poll replaced the rows.
 */
function rowFrom(event: KeyboardEvent): HTMLElement | null {
  return (event.target as HTMLElement).closest('tr')
}

function messageIdIn(row: HTMLElement): number | null {
  const id = row.dataset.messageId
  if (id === undefined) {
    return null
  }
  const parsed = Number(id)
  return Number.isNaN(parsed) ? null : parsed
}

function indexOf(id: number): number {
  return props.messages.findIndex((message) => message.id === id)
}

function onKeydown(event: KeyboardEvent): void {
  const row = rowFrom(event)
  if (!row) {
    return
  }
  const id = messageIdIn(row)
  if (id === null) {
    return
  }

  // Falls back to the tab stop when the focused row is not on this page, which happens if a
  // poll replaced the list while focus was elsewhere.
  const found = indexOf(id)
  const index = found === -1 ? tabStopIndex.value : found
  const last = props.messages.length - 1

  switch (event.key) {
    case 'j':
    case 'ArrowDown':
      if (index < last) {
        void moveTo(index + 1)
      }
      break
    case 'k':
    case 'ArrowUp':
      if (index > 0) {
        void moveTo(index - 1)
      }
      break
    case 'Home':
      void moveTo(0)
      break
    case 'End':
      void moveTo(last)
      break
    case ' ':
      // A checkbox in the row handles Space itself, and letting both act would toggle it twice
      // and leave the selection exactly as it started.
      if ((event.target as HTMLElement).tagName !== 'INPUT') {
        emit('toggle', id)
      }
      break
    default:
      return
  }

  // Anything handled above is ours; without this the page would also scroll on Space or an
  // arrow key, which reads as the list jumping while you are trying to move the cursor.
  event.preventDefault()
}
</script>

<template>
  <table class="messages" role="grid" aria-multiselectable="true" aria-label="Captured messages">
    <thead>
      <tr>
        <th class="pick" scope="col">
          <input
            type="checkbox"
            :checked="selectedIds.length > 0 && selectedIds.length === messages.length"
            :disabled="messages.length === 0 || busy"
            aria-label="Select all messages on this page"
            @change="emit('toggle-all')"
          />
        </th>
        <th v-for="column in columns" :key="column.field" scope="col" :aria-sort="ariaSort(column.field, sortField, sortDirection)">
          <button class="sort" type="button" @click="emit('sort', column.field)">
            {{ column.label }}
            <span v-if="sortField === column.field" aria-hidden="true">{{ sortDirection === 'asc' ? '▲' : '▼' }}</span>
          </button>
        </th>
        <th class="size" scope="col">Size</th>
      </tr>
    </thead>
    <tbody @keydown="onKeydown">
      <tr v-if="messages.length === 0 && busy">
        <td colspan="6" class="empty">
          <span class="spinner" aria-hidden="true"></span>
          Loading messages…
        </td>
      </tr>
      <tr v-else-if="messages.length === 0">
        <td colspan="6" class="empty">No mail captured yet. Send something to the SMTP port to see it here.</td>
      </tr>
      <template v-else>
        <tr
          v-for="(message, index) in messages"
          :id="`message-row-${message.id}`"
          :key="message.id"
          :data-message-id="message.id"
          :class="{ active: message.id === activeId }"
          :tabindex="index === tabStopIndex ? 0 : -1"
          :aria-selected="selectedIds.includes(message.id)"
          @click="emit('open', message.id)"
        >
          <td class="pick" @click.stop>
            <input
              type="checkbox"
              tabindex="-1"
              :checked="selectedIds.includes(message.id)"
              :aria-label="`Select message ${message.id}`"
              @change="emit('toggle', message.id)"
            />
          </td>
          <td class="when">{{ formatTimestamp(message.receivedTimestamp) }}</td>
          <td class="address" :title="message.from">{{ summariseAddresses(message.from) }}</td>
          <td class="address" :title="message.to">{{ summariseAddresses(message.to) }}</td>
          <td class="subject" :title="message.subject">{{ message.subject || '(no subject)' }}</td>
          <td class="size">{{ formatBytes(message.sizeBytes) }}</td>
        </tr>
      </template>
    </tbody>
  </table>
</template>

<style scoped>
.messages {
  width: 100%;
  border-collapse: collapse;
  background: var(--surface);
}

th,
td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

thead th {
  position: sticky;
  top: 0;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border-strong);
  padding: 0;
  white-space: nowrap;
}

.sort {
  width: 100%;
  border: 0;
  border-radius: 0;
  background: transparent;
  padding: 8px 10px;
  text-align: left;
  font-weight: 600;
}

.sort:hover:not(:disabled) {
  background: var(--accent-soft);
}

tbody tr {
  cursor: pointer;
}

tbody tr:hover {
  background: var(--accent-soft);
}

tbody tr.active {
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

/*
 * The focus ring has to be drawn by the row, because a focusable row gets no outline from the
 * user agent the way a button does. Without this the row holds focus invisibly and there is no
 * way to tell by looking where j and k have got to.
 */
tbody tr:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.pick {
  width: 32px;
  text-align: center;
}

.size {
  white-space: nowrap;
  text-align: right;
  color: var(--text-muted);
}

.when {
  white-space: nowrap;
  color: var(--text-muted);
}

.address {
  max-width: 16rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subject {
  max-width: 24rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  padding: 32px 10px;
  text-align: center;
  color: var(--text-muted);
}

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 8px;
  vertical-align: -2px;
  border: 2px solid var(--border-strong);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>