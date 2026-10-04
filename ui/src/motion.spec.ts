// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Motion has to be gated behind `prefers-reduced-motion: no-preference` rather than switched off
 * inside a `reduce` query, so that a browser which does not support the query gets none of it.
 *
 * <p>jsdom does not evaluate media queries and computes no transitions, so the assertions read
 * the source, for the same reason the palette tests do. What is being guarded here is quiet in
 * review and loud on someone else's machine: a pane that slides for a reader who has asked their
 * operating system for less movement.
 *
 * <p>Note that Vitest sets `css: false`, so importing the component as a module yields no
 * styles at all, and this file needs the node environment above to have a filesystem.
 */

const source = readFileSync(fileURLToPath(new URL('./App.vue', import.meta.url)), 'utf8')
const style = source.slice(source.indexOf('<style scoped>'))
const query = style.indexOf('@media (prefers-reduced-motion: no-preference)')

describe('filter pane motion', () => {
  it('declares its transitions inside a no-preference query', () => {
    expect(query, 'no prefers-reduced-motion query in App.vue').toBeGreaterThan(-1)
    // Not just "a query is there": the transitions have to open under no-preference rather than
    // under reduce, so that motion is the thing that needs asking for.
    expect(style.slice(query)).toMatch(/^@media \(prefers-reduced-motion: no-preference\)/)
    expect(style.slice(query)).toContain('grid-template-rows 180ms ease')
  })

  /**
   * The collapsed track has to apply unconditionally. Left inside the query, the pane would stay
   * open for exactly the people who cannot watch it animate, which is the one group the query
   * exists to serve.
   */
  it('collapses whether or not motion is allowed', () => {
    const unconditional = style.slice(0, query)
    expect(unconditional).toContain('grid-template-rows: 0fr')
    expect(unconditional).not.toContain('transition:')
  })
})
