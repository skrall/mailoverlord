<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/** Neither pane may be squeezed below this, in pixels. */
const MIN_PANE = 200
/** ...unless the viewport is too small for that, in which case leave each a third. */
const MIN_PANE_FLOOR = 96
/** How far the divider moves per arrow key press. */
const STEP = 24
const STORAGE_KEY = 'mailoverlord:detail-size'

const root = ref<HTMLElement | null>(null)
const secondary = ref<HTMLElement | null>(null)
/** Detail pane size in pixels, or 0 to leave it to the stylesheet default. */
const size = ref(0)
const dragging = ref(false)
/** True when the panes are stacked, so the divider runs along the other axis. */
const stacked = ref(false)

/** The last chosen size, so a window resize can re-clamp it without discarding it. */
let preferred: number | null = null
let pointerStart = 0
let sizeStart = 0

function total(): number {
  const el = root.value
  if (el === null) {
    return MIN_PANE
  }
  return stacked.value ? el.clientHeight : el.clientWidth
}

/**
 * A fixed 200px floor would push the panes past the edges of a short window, so scale
 * the minimum down to a third of the available space and never go below the floor.
 */
function minPane(): number {
  return Math.max(MIN_PANE_FLOOR, Math.min(MIN_PANE, total() / 3))
}

function limit(): number {
  return Math.max(minPane(), total() - minPane())
}

function clamp(value: number): number {
  return Math.min(Math.max(value, minPane()), limit())
}

/** The size the stylesheet asks for, which the caller then clamps. */
function stylesheetSize(): number {
  const pane = secondary.value
  if (pane === null) {
    return 0
  }
  return stacked.value ? pane.clientHeight : pane.clientWidth
}

function measure(): void {
  const el = root.value
  if (el === null) {
    return
  }
  // The stylesheet owns the breakpoint, so read the resulting direction back rather than
  // repeating it here. The divider then always drags along the same axis as the panes.
  stacked.value = getComputedStyle(el).flexDirection === 'column'
  if (preferred !== null) {
    size.value = clamp(preferred)
  }
}

function apply(value: number): void {
  preferred = value
  size.value = clamp(value)
}

function remember(): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(Math.round(size.value)))
  } catch {
    // Storage can be blocked, in which case the divider still works, just not remembered.
  }
}

function onPointerDown(event: PointerEvent): void {
  if (event.button !== 0) {
    return
  }
  const pane = secondary.value
  if (size.value === 0 && pane !== null) {
    // Adopt the stylesheet default as the starting point, so the first drag does not jump.
    apply(stacked.value ? pane.clientHeight : pane.clientWidth)
  }
  dragging.value = true
  pointerStart = stacked.value ? event.clientY : event.clientX
  sizeStart = size.value
  const handle = event.currentTarget as HTMLElement
  handle.setPointerCapture(event.pointerId)
  event.preventDefault()
}

function onPointerMove(event: PointerEvent): void {
  if (!dragging.value) {
    return
  }
  const pointer = stacked.value ? event.clientY : event.clientX
  // The detail pane sits after the divider, so it grows as the divider moves away from it.
  apply(sizeStart - (pointer - pointerStart))
}

function onPointerUp(event: PointerEvent): void {
  if (!dragging.value) {
    return
  }
  dragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
  remember()
}

function onKeydown(event: KeyboardEvent): void {
  const step = STEP * (event.shiftKey ? 5 : 1)
  const shrink = stacked.value ? 'ArrowDown' : 'ArrowRight'
  const grow = stacked.value ? 'ArrowUp' : 'ArrowLeft'
  if (event.key === shrink) {
    apply(size.value - step)
  } else if (event.key === grow) {
    apply(size.value + step)
  } else if (event.key === 'Home') {
    apply(limit())
  } else if (event.key === 'End') {
    apply(minPane())
  } else {
    return
  }
  remember()
  event.preventDefault()
}

function reset(): void {
  // Go back to the stylesheet's proportion, but clamped, so a short window cannot leave
  // the list with no height at all.
  apply(stylesheetSize())
  remember()
}

onMounted(() => {
  measure()
  let saved = 0
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY))
    if (Number.isFinite(value) && value > 0) {
      saved = value
    }
  } catch {
    // Fall back to the stylesheet default when the saved size cannot be read.
  }
  // Always take an explicit size, otherwise a short window would leave the stylesheet's
  // fixed default to squeeze the list down to nothing.
  apply(saved > 0 ? saved : stylesheetSize())
  window.addEventListener('resize', measure)
})

onBeforeUnmount(() => window.removeEventListener('resize', measure))
</script>

<template>
  <div ref="root" class="split" :class="{ dragging, stacked }">
    <div class="primary">
      <slot name="primary" />
    </div>

    <div
      class="handle"
      role="separator"
      tabindex="0"
      :aria-orientation="stacked ? 'horizontal' : 'vertical'"
      :aria-valuenow="size > 0 ? Math.round(size) : undefined"
      aria-label="Resize the message detail pane. Double click to reset."
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @keydown="onKeydown"
      @dblclick="reset"
    />

    <div ref="secondary" class="secondary" :style="size > 0 ? { flexBasis: `${size}px` } : undefined">
      <slot name="secondary" />
    </div>
  </div>
</template>

<style scoped>
.split {
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

/*
 * The primary pane carries the only pane that scrolls, so it has to be allowed to
 * shrink below its content height. Without min-height: 0 the default min-height: auto
 * makes the table grow to its full height and push the detail pane off screen.
 */
.primary {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
}

.secondary {
  display: flex;
  flex-direction: column;
  flex: 0 0 min(30rem, 40%);
  min-height: 0;
  min-width: 0;
}

.handle {
  position: relative;
  flex: 0 0 6px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: col-resize;
  touch-action: none;
}

.handle::after {
  content: '';
  position: absolute;
  background: var(--border);
  transition: background-color 120ms ease;
}

.split:not(.stacked) .handle::after {
  inset-block: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
}

.stacked .handle::after {
  inset-inline: 0;
  top: 50%;
  height: 1px;
  transform: translateY(-50%);
}

.handle:hover::after,
.handle:focus-visible::after,
.dragging .handle::after {
  background: var(--accent);
}

.handle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.dragging {
  user-select: none;
}

.stacked .handle {
  cursor: row-resize;
}

.stacked .secondary {
  /* Capped as a fraction of the height so a short window still leaves the list room. */
  flex-basis: min(20rem, 45%);
}

@media (max-width: 60rem) {
  .split {
    flex-direction: column;
  }
}
</style>
