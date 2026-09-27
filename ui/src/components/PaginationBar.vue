<script setup lang="ts">
const props = defineProps<{
  page: number
  size: number
  totalElements: number
  totalPages: number
  busy: boolean
}>()

const emit = defineEmits<{
  (event: 'page', page: number): void
  (event: 'size', size: number): void
  (event: 'refresh'): void
}>()

const pageSizes = [10, 25, 50, 100]

function previous(): void {
  if (props.page > 0) {
    emit('page', props.page - 1)
  }
}

function next(): void {
  if (props.page + 1 < props.totalPages) {
    emit('page', props.page + 1)
  }
}
</script>

<template>
  <div class="pagination">
    <span class="count">
      {{ totalElements }} message{{ totalElements === 1 ? '' : 's' }}
      <template v-if="totalPages > 1"> &middot; page {{ page + 1 }} of {{ totalPages }}</template>
    </span>

    <span class="controls">
      <label>
        Per page
        <select :value="size" :disabled="busy" @change="emit('size', Number(($event.target as HTMLSelectElement).value))">
          <option v-for="option in pageSizes" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
      <button type="button" :disabled="busy || page <= 0" @click="previous">Previous</button>
      <button type="button" :disabled="busy || page + 1 >= totalPages" @click="next">Next</button>
      <button type="button" :disabled="busy" @click="emit('refresh')">Refresh</button>
    </span>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: var(--surface-sunken);
  border-top: 1px solid var(--border);
}

.count {
  color: var(--text-muted);
}

.controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}

select {
  font: inherit;
  padding: 5px 6px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
}
</style>
