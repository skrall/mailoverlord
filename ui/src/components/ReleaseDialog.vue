<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
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

const dialog = ref<HTMLElement | null>(null)
let previouslyFocused: HTMLElement | null = null

function submit(): void {
  emit('release', {
    overrideTo: overrideTo.value ? toAddresses.value : '',
    overrideFrom: overrideFrom.value ? fromAddress.value : '',
  })
}

/**
 * The controls Tab is allowed to reach, worked out at the moment Tab is pressed.
 *
 * <p>Not cached: the address fields only exist once their checkbox is ticked, so the list changes
 * while the dialog is open and a cached copy would trap focus on an element that has gone.
 */
function focusable(): HTMLElement[] {
  if (!dialog.value) {
    return []
  }
  const candidates = dialog.value.querySelectorAll<HTMLElement>(
    'button, input, select, textarea, [href], [tabindex]:not([tabindex="-1"])',
  )
  return Array.from(candidates).filter((element) => !element.hasAttribute('disabled'))
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    // Marked so the dialog's own Escape is the one that counts. A document-level listener
    // would otherwise see it too, and something that treats Escape as "clear the filter" would
    // fire while a modal is up.
    event.stopPropagation()
    emit('cancel')
    return
  }
  if (event.key !== 'Tab') {
    return
  }

  const items = focusable()
  if (items.length === 0) {
    event.preventDefault()
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  const inside = dialog.value?.contains(active) ?? false

  if (event.shiftKey && (!inside || active === first)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (!inside || active === last)) {
    event.preventDefault()
    first.focus()
  }
}

/**
 * Moves focus into the dialog and hands it back on the way out.
 *
 * <p>`aria-modal="true"` promises the rest of the page is unavailable, but a promise the DOM does
 * not keep is worse than none: a keyboard user who tabs from here lands on the table behind the
 * dialog and starts deleting messages through an overlay they cannot see. Focus goes to the
 * dialog itself rather than the first checkbox, so the title is what gets announced.
 *
 * <p>Focus returns to whatever opened the dialog. Without that, dismissing a modal drops the
 * user at the top of the document and the button they pressed is lost, which on a long table
 * means tabbing back to find it.
 */
onMounted(() => {
  previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.focus()
})

onUnmounted(() => previouslyFocused?.focus())
</script>

<template>
  <div class="backdrop" @click.self="emit('cancel')">
    <form
      ref="dialog"
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="release-heading"
      tabindex="-1"
      @submit.prevent="submit"
      @keydown="onKeydown"
    >
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
  background: var(--scrim);
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

/*
 * The dialog takes focus on open so that it gets announced, and it is not itself interactive, so
 * it must not draw a focus ring the way a control the user can act on would.
 */
.dialog:focus {
  outline: none;
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
