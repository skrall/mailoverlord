<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  deleteMessages,
  getMessage,
  listMessages,
  releaseMessages,
  type MessageDetail,
  type MessageFilterRequest,
  type MessagePage,
  type MessageSummary,
} from './api/client'
import MessageDetailPanel from './components/MessageDetail.vue'
import FilterBar from './components/FilterBar.vue'
import MessageTable from './components/MessageTable.vue'
import PaginationBar from './components/PaginationBar.vue'
import ReleaseDialog from './components/ReleaseDialog.vue'
import SplitPane from './components/SplitPane.vue'

const page = ref(0)
const size = ref(25)
const sortField = ref('receivedTimestamp')
const sortDirection = ref<'asc' | 'desc'>('desc')

const messages = ref<MessageSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const filter = ref<MessageFilterRequest>({})
/**
 * True between a keystroke in the filter box and the debounced request it triggers.
 *
 * <p>The poll is held off while this is set. Reloading mid-word would re-filter the list by a
 * half-typed subject, which both wastes a request and replaces the rows under the cursor.
 */
const filterSettling = ref(false)

const selectedIds = ref<number[]>([])
const activeId = ref<number | null>(null)
const detail = ref<MessageDetail | null>(null)
const detailLoading = ref(false)
const busy = ref(false)
const showReleaseDialog = ref(false)
const error = ref<string | null>(null)
let pollTimer = 0
/**
 * Identifies the most recent list request, so a slower earlier one cannot overwrite it.
 *
 * <p>Two requests can be in flight at once: the debounced filter request and the poll that was
 * already on the wire when the filter changed. Whichever answers last used to win, so the list
 * could settle on results for a filter the user had already moved on from.
 */
let latestLoad = 0

function closeDetail(): void {
  activeId.value = null
  detail.value = null
}

const selectedMessages = computed(() =>
  messages.value.filter((message) => selectedIds.value.includes(message.id)),
)

const allSelected = computed(
  () => messages.value.length > 0 && selectedIds.value.length === messages.value.length,
)

async function load(): Promise<void> {
  const request = ++latestLoad
  busy.value = true
  error.value = null
  try {
    const result: MessagePage = await listMessages({
      page: page.value,
      size: size.value,
      sort: `${sortField.value},${sortDirection.value}`,
      ...filter.value,
    })
    // A newer request has already been issued, so this answer is stale; applying it would
    // show rows for criteria the user has since changed.
    if (request !== latestLoad) {
      return
    }
    messages.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages

    // Drop anything that is no longer on this page, and close a message that was deleted.
    const visible = new Set(result.content.map((message) => message.id))
    selectedIds.value = selectedIds.value.filter((id) => visible.has(id))
    if (activeId.value !== null && !visible.has(activeId.value)) {
      activeId.value = null
      detail.value = null
    }
  } catch (cause) {
    // A superseded request has nothing useful to say: its failure belongs to a filter the
    // user has already moved on from, and reporting it would blame the current one.
    if (request === latestLoad) {
      error.value = cause instanceof Error ? cause.message : String(cause)
    }
  } finally {
    // Only the newest request owns the busy flag, otherwise the first one to finish would
    // clear it while the second is still in flight.
    if (request === latestLoad) {
      busy.value = false
    }
  }
}

/**
 * Applies a settled filter and reloads.
 *
 * <p>Returns to the first page, because the page someone was on may not exist under the new
 * criteria: page 5 of a list that now has two pages is an empty table that looks like the
 * filter found nothing.
 */
function applyFilter(next: MessageFilterRequest): void {
  filter.value = next
  filterSettling.value = false
  page.value = 0
  void load()
}

async function open(id: number): Promise<void> {
  activeId.value = id
  detailLoading.value = true
  error.value = null
  try {
    detail.value = await getMessage(id)
  } catch (cause) {
    detail.value = null
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    detailLoading.value = false
  }
}

function toggle(id: number): void {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((selected) => selected !== id)
    : [...selectedIds.value, id]
}

function toggleAll(): void {
  selectedIds.value = allSelected.value ? [] : messages.value.map((message) => message.id)
}

function sortBy(field: string): void {
  if (sortField.value === field) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortDirection.value = 'asc'
  }
  page.value = 0
  void load()
}

function goToPage(next: number): void {
  page.value = next
  void load()
}

function changeSize(next: number): void {
  size.value = next
  page.value = 0
  void load()
}

async function removeSelected(): Promise<void> {
  if (selectedIds.value.length === 0) {
    return
  }
  busy.value = true
  error.value = null
  try {
    const response = await deleteMessages(selectedIds.value)
    if (!response.successful) {
      error.value = response.errorMessage ?? 'The server could not delete those messages.'
    } else {
      selectedIds.value = []
      await load()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    busy.value = false
  }
}

async function release(options: { overrideTo: string; overrideFrom: string }): Promise<void> {
  showReleaseDialog.value = false
  busy.value = true
  error.value = null
  try {
    const response = await releaseMessages({
      messageIds: selectedIds.value,
      overrideTo: options.overrideTo.length > 0,
      overrideToAddresses: options.overrideTo || undefined,
      overrideFrom: options.overrideFrom.length > 0,
      overrideFromAddress: options.overrideFrom || undefined,
    })
    if (!response.successful) {
      error.value = response.errorMessage ?? 'The server could not release those messages.'
    } else {
      selectedIds.value = []
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
  // Mail arrives over SMTP at any time, so poll to keep the table current. This is a tool for
  // watching a test environment, and a push channel is not worth the complexity here. The
  // interval is cleared in onUnmounted.
  pollTimer = window.setInterval(() => {
    // filterSettling holds the poll off while a filter edit is waiting on its debounce, so
    // the list cannot be swapped out from under someone who is still typing.
    if (!busy.value && !showReleaseDialog.value && !filterSettling.value) {
      void load()
    }
  }, 10_000)
})

onUnmounted(() => window.clearInterval(pollTimer))
</script>

<template>
  <div class="shell">
    <header class="toolbar">
      <h1>Mailoverlord</h1>
      <div class="actions">
        <span class="selected">{{ selectedIds.length }} selected</span>
        <button
          type="button"
          :disabled="busy || selectedIds.length === 0"
          @click="showReleaseDialog = true"
        >
          Release
        </button>
        <button
          type="button"
          class="danger"
          :disabled="busy || selectedIds.length === 0"
          @click="removeSelected"
        >
          Delete
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <SplitPane>
      <template #primary>
        <section class="list">
          <FilterBar :filter="filter" @editing="filterSettling = true" @change="applyFilter" />
          <div class="scroll">
            <MessageTable
              :messages="messages"
              :selected-ids="selectedIds"
              :active-id="activeId"
              :sort-field="sortField"
              :sort-direction="sortDirection"
              :busy="busy"
              @toggle="toggle"
              @toggle-all="toggleAll"
              @open="open"
              @sort="sortBy"
            />
          </div>
          <PaginationBar
            :page="page"
            :size="size"
            :total-elements="totalElements"
            :total-pages="totalPages"
            :busy="busy"
            @page="goToPage"
            @size="changeSize"
            @refresh="load"
          />
        </section>
      </template>

      <template #secondary>
        <MessageDetailPanel
          class="panel"
          :message="detail"
          :loading="detailLoading"
          @close="closeDetail"
        />
      </template>
    </SplitPane>

    <ReleaseDialog
      v-if="showReleaseDialog"
      :messages="selectedMessages"
      @release="release"
      @cancel="showReleaseDialog = false"
    />
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

h1 {
  margin: 0;
  font-size: 15px;
  letter-spacing: 0.01em;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected {
  color: var(--text-muted);
}

.error {
  margin: 0;
  padding: 8px 14px;
  background: var(--danger-soft);
  color: var(--danger);
  border-bottom: 1px solid var(--border);
}

.list {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  /* Without this the list cannot shrink below its rows, so .scroll never scrolls. */
  min-height: 0;
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* Fills the slot the split pane gives it; the split itself owns the width. */
.panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
