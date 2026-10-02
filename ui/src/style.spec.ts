// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * The two palettes live in style.css as custom properties, and a dark theme is the kind of
 * change that passes review and fails in the dark: a hover fill that glares, a border that
 * vanishes, or white text on a light accent fill. None of that is obvious from reading the
 * numbers, so the bars are asserted here instead.
 *
 * The values are read straight out of the source rather than computed, because jsdom does
 * not resolve a media query, so the dark block is not reachable through getComputedStyle.
 * Reading the file also means this fails if a variable is renamed or dropped, which is the
 * failure that would otherwise leave the dark theme half-painted.
 *
 * Note that Vitest sets `css: false`, so importing this file as a module yields an empty
 * string. Hence readFileSync, and the node environment above since nothing here needs a DOM.
 */

const css = readFileSync(fileURLToPath(new URL('./style.css', import.meta.url)), 'utf8')

function block(selector: string, body: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(body)
  expect(match, `no "${selector}" block found in style.css`).not.toBeNull()
  return match![1]
}

function vars(body: string): Record<string, string> {
  const found: Record<string, string> = {}
  for (const [, name, value] of body.matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    found[name] = value.trim()
  }
  return found
}

const root = vars(block(':root', css))

/** The dark block is nested inside the media query, so take the last :root it contains. */
const darkRoot = vars(block(':root', css.slice(css.indexOf('@media (prefers-color-scheme: dark)'))))

const themes = { light: root, dark: darkRoot }

function channel(hex: string, offset: number): number {
  return parseInt(hex.slice(offset, offset + 2), 16) / 255
}

function relativeLuminance(value: string): number {
  expect(value, `expected a hex colour, got "${value}"`).toMatch(/^#[0-9a-f]{6}$/i)
  // Drop the leading # before slicing, or the first pair parses as "#1" and the whole
  // calculation comes back NaN.
  const hex = value.slice(1)
  const [r, g, b] = [0, 2, 4].map((offset) => channel(hex, offset))
  const [lr, lg, lb] = [r, g, b].map((c) =>
    c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4,
  )
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe.each(Object.entries(themes))('%s theme', (_name, palette) => {
  const ratio = (fg: string, bg: string) => contrast(palette[fg], palette[bg])

  it('defines every colour the components reference', () => {
    // --radius and --mono are not colours, so they are excluded on purpose.
    for (const name of [
      '--border',
      '--border-strong',
      '--text',
      '--text-muted',
      '--surface',
      '--surface-sunken',
      '--accent',
      '--accent-soft',
      '--accent-contrast',
      '--danger',
      '--danger-soft',
    ]) {
      expect(palette[name], `${name} missing`).toBeTruthy()
    }
  })

  /**
   * 4.5:1 is the WCAG AA bar for body text. The muted pair carries real content: the size
   * and timestamp columns, and the empty state.
   */
  it.each([
    ['--text', '--surface', 'body text on a card'],
    ['--text', '--surface-sunken', 'body text on the page'],
    ['--text-muted', '--surface', 'size and timestamp columns'],
    ['--text-muted', '--surface-sunken', 'empty state on the page'],
    ['--danger', '--surface', 'danger button label'],
  ])('%s on %s is readable (%s)', (fg, bg) => {
    expect(ratio(fg, bg)).toBeGreaterThanOrEqual(4.5)
  })

  /**
   * The one that a dark theme silently gets wrong: the accent has to lighten, which makes
   * white on it unusable. This is why --accent-contrast exists.
   */
  it('--accent-contrast on --accent is readable', () => {
    expect(ratio('--accent-contrast', '--accent')).toBeGreaterThanOrEqual(4.5)
  })

  /**
   * The accent carries the active-row bar and the spinner, which are the only things
   * marking the open message. 3:1 is the bar for non-text meaning.
   */
  it('--accent against --surface is visible as a non-text cue', () => {
    expect(ratio('--accent', '--surface')).toBeGreaterThanOrEqual(3)
  })

  it('separates the table rows from the surface', () => {
    expect(ratio('--border', '--surface')).toBeGreaterThan(1.15)
  })

  it('lifts the cards off the page', () => {
    expect(ratio('--surface', '--surface-sunken')).toBeGreaterThan(1.04)
  })

  it('has a scrim that dims the page behind the dialog', () => {
    expect(palette['--scrim'], '--scrim missing').toMatch(/^rgba\(\s*\d+,\s*\d+,\s*\d+,\s*0?\.\d+\s*\)$/i)
    const alpha = Number(/,\s*(0?\.\d+)\s*\)/.exec(palette['--scrim'])![1])
    expect(alpha).toBeGreaterThanOrEqual(0.4)
  })
})

/**
 * The subtle fills are not held to a fixed number, because the light theme's existing values
 * are what set the bar. What matters is that dark is not *less* visible than the design it
 * replaces: an under-done dark palette shows up as hover states that feel broken.
 */
describe('dark against light', () => {
  const cases: Array<[string, string]> = [
    ['--border-strong', '--surface'],
    ['--border', '--surface'],
    ['--accent-soft', '--surface'],
    ['--accent-soft', '--surface-sunken'],
    ['--danger-soft', '--surface'],
  ]

  it.each(cases)('%s on %s is at least as visible as in the light theme', (fg, bg) => {
    expect(contrast(darkRoot[fg], darkRoot[bg])).toBeGreaterThanOrEqual(
      contrast(root[fg], root[bg]),
    )
  })

  it('uses a darker scrim than the light theme, not a lighter one', () => {
    const alphaOf = (v: string) => Number(/,\s*(0?\.\d+)\s*\)/.exec(v)![1])
    expect(alphaOf(darkRoot['--scrim'])).toBeGreaterThanOrEqual(alphaOf(root['--scrim']))
  })

  /**
   * Guards the specific trap: if someone "simplifies" --accent-contrast back to #fff in both
   * themes, everything still reads well on a light background and only the dark theme breaks.
   */
  it('does not use white text on the dark accent fill', () => {
    expect(darkRoot['--accent-contrast'].toLowerCase()).not.toBe('#ffffff')
  })
})