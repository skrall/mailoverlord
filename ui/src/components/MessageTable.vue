<script setup lang="ts">
import type { MessageSummary } from '../api/client'
import { formatBytes, formatTimestamp, summariseAddresses } from '../format'

defineProps<{
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
]

function ariaSort(field: string, sortField: string, direction: 'asc' | 'desc'): 'ascending' | 'descending' | 'none' {
  if (field !== sortField) {
    return 'none'
  }
  return direction === 'asc' ? 'ascending' : 'descending'
}
</script>

<template>
  <table class="messages">
    <thead>
      <tr>
        <th class="pick">
          <input
            type="checkbox"
            :checked="selectedIds.length > 0 && selectedIds.length === messages.length"
            :disabled="messages.length === 0 || busy"
            aria-label="Select all messages on this page"
            @change="emit('toggle-all')"
          />
        </th>
        <th v-for="column in columns" :key="column.field" :aria-sort="ariaSort(column.field, sortField, sortDirection)">
          <button class="sort" type="button" @click="emit('sort', column.field)">
            {{ column.label }}
            <span v-if="sortField === column.field" aria-hidden="true">{{ sortDirection === 'asc' ? '▲' : '▼' }}</span>
          </button>
        </th>
        <th>Subject</th>
        <th class="size">Size</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="messages.length === 0 && busy">
        <td colspan="5" class="empty">
          <span class="spinner" aria-hidden="true"></span>
          Loading messages…
        </td>
      </tr>
      <tr v-else-if="messages.length === 0">
        <td colspan="5" class="empty">No mail captured yet. Send something to the SMTP port to see it here.</td>
      </tr>
      <template v-else>
        <tr
          v-for="message in messages"
          :key="message.id"
          :class="{ active: message.id === activeId }"
          @click="emit('open', message.id)"
        >
          <td class="pick" @click.stop>
            <input
              type="checkbox"
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
