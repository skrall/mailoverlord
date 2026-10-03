<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import type { MessageFilterRequest } from '../api/client'
import { toUtcIso } from '../format'

/**
 * The search controls above the message table.
 *
 * <p>Two events rather than one, and the difference matters to the table. `editing` fires on
 * every keystroke, which is the table's signal to hold off its ten second poll: reloading the
 * list mid-word would replace the results with ones filtered by a half-typed term and move the
 * page out from under the cursor. `change` fires once the typing has settled, and is the only
 * one that costs a request.
 *
 * <p>The debounce lives here rather than in the table so that this component owns its own
 * timing, and so the delay can be tested without a running poll. Clearing is deliberately not
 * debounced: it is a deliberate act with an obvious result, and waiting reads as a broken button.
 *
 * <p>Nothing here is disabled while a request is in flight, unlike the pagination controls.
 * A request is in flight for a moment every ten seconds, and a disabled input swallows
 * whatever was typed into it at that moment. Overlapping requests are harmless because the
 * table discards a response that has been superseded, so there is nothing to protect against by
 * locking the user out of their own search box.
 */

const props = defineProps<{
  filter: MessageFilterRequest
}>()

const emit = defineEmits<{
  (event: 'editing'): void
  (event: 'change', filter: MessageFilterRequest): void
}>()

const DEBOUNCE_MS = 300

const subject = ref(props.filter.subject ?? '')
const from = ref(props.filter.from ?? '')
const to = ref(props.filter.to ?? '')
/** Kept as the raw `datetime-local` value; converted to an instant only when emitting. */
const receivedFrom = ref('')
const receivedTo = ref('')

let timer = 0

function currentFilter(): MessageFilterRequest {
  return {
    subject: subject.value.trim(),
    from: from.value.trim(),
    to: to.value.trim(),
    receivedFrom: toUtcIso(receivedFrom.value),
    receivedTo: toUtcIso(receivedTo.value),
  }
}

function schedule(): void {
  emit('editing')
  window.clearTimeout(timer)
  timer = window.setTimeout(() => emit('change', currentFilter()), DEBOUNCE_MS)
}

function clear(): void {
  window.clearTimeout(timer)
  subject.value = ''
  from.value = ''
  to.value = ''
  receivedFrom.value = ''
  receivedTo.value = ''
  emit('change', currentFilter())
}

const hasFilter = (): boolean =>
  subject.value.trim().length > 0 ||
  from.value.trim().length > 0 ||
  to.value.trim().length > 0 ||
  receivedFrom.value.length > 0 ||
  receivedTo.value.length > 0

// A pending debounce would fire after the component went away and emit into nothing.
onUnmounted(() => window.clearTimeout(timer))
</script>

<template>
  <div class="filters">
    <label class="grow">
      Subject
      <input
        v-model="subject"
        type="search"
        placeholder="Search subjects"
        @input="schedule"
      />
    </label>

    <label>
      From
      <input v-model="from" type="search" placeholder="Sender" @input="schedule" />
    </label>

    <label>
      To
      <input v-model="to" type="search" placeholder="Recipient" @input="schedule" />
    </label>

    <label>
      Received from
      <input
        v-model="receivedFrom"
        type="datetime-local"
        @input="schedule"
      />
    </label>

    <label>
      Received to
      <input v-model="receivedTo" type="datetime-local" @input="schedule" />
    </label>

    <button v-if="hasFilter()" type="button" class="clear" @click="clear">
      Clear
    </button>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border);
}

label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--text-muted);
}

/* The subject box is the one people type a phrase into, so it takes the slack. */
.grow {
  flex: 1 1 16rem;
  min-width: 12rem;
}

input {
  font: inherit;
  padding: 5px 6px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
}

.clear {
  font: inherit;
  padding: 5px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  cursor: pointer;
}

.clear:hover {
  border-color: var(--accent);
  color: var(--accent);
}
</style>