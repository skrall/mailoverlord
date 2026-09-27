<script setup lang="ts">
import { ref } from 'vue'
import type { MessageSummary } from '../api/client'

defineProps<{
  messages: MessageSummary[]
}>()

const emit = defineEmits<{
  (event: 'release', options: { overrideTo: string; overrideFrom: string }): void
  (event: 'cancel'): void
}>()

const overrideTo = ref(false)
const toAddresses = ref('')
const overrideFrom = ref(false)
const fromAddress = ref('')

function submit(): void {
  emit('release', {
    overrideTo: overrideTo.value ? toAddresses.value : '',
    overrideFrom: overrideFrom.value ? fromAddress.value : '',
  })
}
</script>

<template>
  <div class="backdrop" @click.self="emit('cancel')">
    <form class="dialog" role="dialog" aria-modal="true" aria-labelledby="release-heading" @submit.prevent="submit">
      <h2 id="release-heading">Release {{ messages.length }} message{{ messages.length === 1 ? '' : 's' }}</h2>
      <p class="note">The selected messages are forwarded to the SMTP server configured for this instance.</p>

      <label class="toggle">
        <input v-model="overrideTo" type="checkbox" />
        Override recipients
      </label>
      <input
        v-if="overrideTo"
        v-model="toAddresses"
        type="text"
        placeholder="someone@example.com, another@example.com"
        aria-label="Replacement recipients"
        required
      />

      <label class="toggle">
        <input v-model="overrideFrom" type="checkbox" />
        Override sender
      </label>
      <input
        v-if="overrideFrom"
        v-model="fromAddress"
        type="email"
        placeholder="sender@example.com"
        aria-label="Replacement sender"
        required
      />

      <footer>
        <button type="button" @click="emit('cancel')">Cancel</button>
        <button type="submit" class="primary">Release</button>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(28, 36, 48, 0.45);
}

.dialog {
  width: min(30rem, 100%);
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  background: var(--surface);
  border-radius: var(--radius);
  border: 1px solid var(--border-strong);
}

h2 {
  margin: 0;
  font-size: 15px;
}

.note {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle input {
  width: auto;
}

footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
