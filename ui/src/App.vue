<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
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

/** The filter bar, so the `/` shortcut can reach its subject box without a selector. */
const filterBar = ref<InstanceType<typeof FilterBar> | null>(null)

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

const FILTERS_KEY = 'mailoverlord:filters-open'

/**
 * Whether the filter pane is showing.
 *
 * <p>Collapsed on a first visit. Most of the time this tool is opened to see what arrived, and the
 * filter row is the one thing standing between the table and the height it needs. Someone who
 * lives in the filter pane should not have to reopen it after every reload, so the choice is
 * remembered.
 */
const filtersVisible = ref(readStoredFilters())

function readStoredFilters(): boolean {
  try {
    return localStorage.getItem(FILTERS_KEY) === 'open'
  } catch {
    // Storage can be blocked, in which case the pane simply starts collapsed.
    return false
  }
}

function setFiltersVisible(next: boolean): void {
  filtersVisible.value = next
  try {
    localStorage.setItem(FILTERS_KEY, next ? 'open' : 'closed')
  } catch {
    // The pane still toggles, it just will not be remembered.
  }
}

/**
 * Whether a filter is currently narrowing the list.
 *
 * <p>Needed because the pane can be shut with a filter still applied, and a list that is silently
 * filtered is indistinguishable from one that happens to have nothing else in it.
 */
const filterActive = computed(() =>
  Object.values(filter.value).some((value) => value !== undefined && value !== ''),
)

function toggleFilters(): void {
  setFiltersVisible(!filtersVisible.value)
}

const selectedIds = ref<number[]>([])
const activeId = ref<number | null>(null)
const detail = ref<MessageDetail | null>(null)
const detailLoading = ref(false)
const busy = ref(false)
const showReleaseDialog = ref(false)
const error = ref<string | null>(null)

/** Which action the banner is reporting on. */
type FailingAction = 'load' | 'open' | 'delete' | 'release'

const failedAction = ref<FailingAction | null>(null)

/**
 * Puts a failure on the banner and records which action it came from.
 *
 * <p>The owner is what lets a later success clear the banner without wiping an unrelated error.
 * The two-argument alternative — clearing whenever anything succeeds — is what made the banner
 * useless: a failed release explained itself, the user clicked a row to check whether that message
 * was one of the released ones, and the explanation was gone. Reading a message cannot fix a
 * release, so it has no business clearing a release's error.
 */
function reportError(action: FailingAction, cause: unknown): void {
  error.value = cause instanceof Error ? cause.message : String(cause)
  failedAction.value = action
}

/**
 * Clears the banner, but only if it is reporting on this action.
 *
 * <p>A success elsewhere is not news: the ten second poll reloads the list every tick, and would
 * otherwise clear whatever someone was reading without anyone having asked for it to go away.
 */
function clearError(action: FailingAction): void {
  if (failedAction.value === action) {
    error.value = null
    failedAction.value = null
  }
}
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
    clearError('load')
  } catch (cause) {
    // A superseded request has nothing useful to say: its failure belongs to a filter the
    // user has already moved on from, and reporting it would blame the current one.
    if (request === latestLoad) {
      reportError('load', cause)
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
  try {
    detail.value = await getMessage(id)
    clearError('open')
  } catch (cause) {
    detail.value = null
    reportError('open', cause)
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
  try {
    const response = await deleteMessages(selectedIds.value)
    if (!response.successful) {
      reportError('delete', response.errorMessage ?? 'The server could not delete those messages.')
    } else {
      selectedIds.value = []
      await load()
      clearError('delete')
    }
  } catch (cause) {
    reportError('delete', cause)
  } finally {
    busy.value = false
  }
}

async function release(options: { overrideTo: string; overrideFrom: string }): Promise<void> {
  showReleaseDialog.value = false
  busy.value = true
  try {
    const response = await releaseMessages({
      messageIds: selectedIds.value,
      overrideTo: options.overrideTo.length > 0,
      overrideToAddresses: options.overrideTo || undefined,
      overrideFrom: options.overrideFrom.length > 0,
      overrideFromAddress: options.overrideFrom || undefined,
    })
    if (!response.successful) {
      reportError('release', response.errorMessage ?? 'The server could not release those messages.')
    } else {
      selectedIds.value = []
      clearError('release')
    }
  } catch (cause) {
    reportError('release', cause)
  } finally {
    busy.value = false
  }
}

/**
 * Whether the keypress is destined for somewhere the user is typing.
 *
 * <p>A shortcut has to keep its hands off text entry. Pressing `/` to jump to the search box is
 * the point; pressing `/` while typing a date into the filter is a slash the user wants, and
 * Delete while a filter box has a selection in it is a character they want gone. Without this
 * the shortcuts would quietly eat input, which is worse than not having them.
 */
function isTyping(target: EventTarget | null): boolean {
  const element = target
  if (!(element instanceof HTMLElement)) {
    return false
  }
  if (element.isContentEditable) {
    return true
  }
  return ['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName)
}

/**
 * The app-wide shortcuts.
 *
 * <p>Deliberately not bound with Vue's `@keydown` on a wrapper: those only fire when something
 * inside is focused, and `/` has to work from a freshly loaded page where focus is still on the
 * document body. A window listener is the only thing that covers every case.
 */
function onShortcut(event: KeyboardEvent): void {
  if (event.metaKey || event.ctrlKey || event.altKey) {
    return
  }
  // The dialog is modal. Anything the document would do behind it is exactly what it exists to
  // prevent, and Delete is destructive.
  if (showReleaseDialog.value) {
    return
  }
  if (isTyping(event.target)) {
    return
  }

  if (event.key === '/') {
    event.preventDefault()
    // A collapsed pane is inert, so its subject box cannot take focus until the pane is open.
    // That attribute is not lifted until the next render, hence the tick before focusing:
    // opening and focusing in one go silently leaves the caret nowhere.
    if (!filtersVisible.value) {
      setFiltersVisible(true)
    }
    void nextTick(() => filterBar.value?.focusSubject())
    return
  }

  if (event.key === 'Delete' && !busy.value && selectedIds.value.length > 0) {
    event.preventDefault()
    void removeSelected()
  }
}

onMounted(() => {
  void load()
  window.addEventListener('keydown', onShortcut)
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

onUnmounted(() => {
  window.removeEventListener('keydown', onShortcut)
  window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="shell">
    <header class="toolbar">
      <h1>Mailoverlord</h1>
      <div class="actions">
        <button
          type="button"
          class="filters-toggle"
          :aria-expanded="filtersVisible"
          :aria-label="filterActive ? 'Filters (a filter is applied)' : undefined"
          aria-controls="filter-pane"
          @click="toggleFilters"
        >
          <span class="chevron" :class="{ open: filtersVisible }" aria-hidden="true" />
          Filters
          <span v-if="filterActive" class="applied" aria-hidden="true" />
        </button>
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
          <!--
            `inert` is spelled out rather than bound as a plain boolean: Vue does not drop the
            attribute when it is bound to false, it would render inert="false", and an inert
            attribute that is present is inert whatever it says. That would leave the pane
            permanently untabbable.
          -->
          <div
            id="filter-pane"
            class="filter-pane"
            :class="{ collapsed: !filtersVisible }"
            :inert="filtersVisible ? undefined : true"
          >
            <FilterBar
              ref="filterBar"
              :filter="filter"
              @editing="filterSettling = true"
              @change="applyFilter"
            />
          </div>
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

/**
 * Collapses by animating the track from 1fr to 0fr rather than a height, because a height
 * transition has to be told how tall the content is and the filter fields wrap onto a second row
 * depending on the window. The track takes the content's own height and needs no measuring.
 */
.filter-pane {
  display: grid;
  grid-template-rows: 1fr;
  border-bottom: 1px solid var(--border);
}

.filter-pane > * {
  min-height: 0;
  overflow: hidden;
}

.filter-pane.collapsed {
  grid-template-rows: 0fr;
  /* The rule under the pane would otherwise be left behind as a stray line under the table. */
  border-bottom: 0;
}

.filter-pane.collapsed > * {
  opacity: 0;
}

.applied {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.chevron {
  display: inline-block;
  margin-right: 6px;
  border: solid currentColor;
  border-width: 0 1.5px 1.5px 0;
  padding: 3px;
  transform: rotate(-45deg);
}

.chevron.open {
  transform: rotate(45deg);
}

/*
 * Motion lives entirely inside a no-preference query rather than being switched off in a
 * reduce query. A browser that does not support the query then gets no animation at all, which is
 * the right way round: someone who has asked for less movement should never be the one to find
 * out that a transition was left unguarded.
 */
@media (prefers-reduced-motion: no-preference) {
  .filter-pane,
  .filter-pane > * {
    transition:
      grid-template-rows 180ms ease,
      opacity 140ms ease;
  }

  .chevron {
    transition: transform 180ms ease;
  }
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
