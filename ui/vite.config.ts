import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// During development the UI runs on its own dev server and proxies API calls to the
// Spring Boot app, so the browser still sees a single origin and CORS never comes up.
// In a built app both are served by the same process, so no proxy is involved.
const apiTarget = process.env.MAILOVERLORD_API ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/messages': apiTarget,
      '/v3': apiTarget,
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  test: {
    // jsdom rather than node: component tests need a document, and the alternative is a
    // per-file environment comment on every one of them.
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    // With the default isolating pool a fresh jsdom is built per spec file, which is most
    // of the runtime of this suite. Sharing one environment per worker measured ~20% faster
    // (`vitest doctor`: 5.97s -> ~4.8s). The suite is safe to share: no spec uses `vi.mock`
    // or `vi.resetModules`, nothing attaches to `document.body`, and `client.spec.ts`
    // unstubs its globals in `afterEach`. Re-check with `vitest doctor` after adding a spec
    // that leans on isolation.
    pool: 'threads',
    isolate: false,
  },
})
