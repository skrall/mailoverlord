<script setup lang="ts">
import { computed } from 'vue'
import type { MessageDetail } from '../api/client'
import { formatTimestamp } from '../format'

const props = defineProps<{
  message: MessageDetail | null
  loading: boolean
}>()

const emit = defineEmits<{ (event: 'close'): void }>()

const recipients = computed(() => {
  if (!props.message) {
    return []
  }
  return props.message.to.split(',').map((address) => address.trim()).filter(Boolean)
})
</script>

<template>
  <aside class="detail" aria-label="Message detail">
    <p v-if="loading" class="placeholder">Loading…</p>
    <p v-else-if="!message" class="placeholder">Select a message to read it.</p>

    <template v-else>
      <header>
        <h2>{{ message.subject || '(no subject)' }}</h2>
        <button type="button" aria-label="Close the message" @click="emit('close')">Close</button>
      </header>

      <dl>
        <dt>From</dt>
        <dd>{{ message.from }}</dd>
        <dt>To</dt>
        <dd>
          <span v-for="address in recipients" :key="address" class="recipient">{{ address }}</span>
        </dd>
        <dt>Received</dt>
        <dd>{{ formatTimestamp(message.receivedTimestamp) }}</dd>
      </dl>

      <pre class="body">{{ message.body }}</pre>
    </template>
  </aside>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--surface);
}

header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

h2 {
  margin: 0;
  font-size: 15px;
  line-height: 1.4;
  flex: 1;
  word-break: break-word;
}

.placeholder {
  margin: 0;
  padding: 32px 16px;
  color: var(--text-muted);
  text-align: center;
}

dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 4px 12px;
  margin: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

dt {
  color: var(--text-muted);
}

dd {
  margin: 0;
  word-break: break-word;
}

.recipient {
  display: block;
}

.body {
  margin: 0;
  padding: 16px;
  overflow: auto;
  flex: 1;
  min-height: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font: 13px/1.55 var(--mono);
}
</style>
