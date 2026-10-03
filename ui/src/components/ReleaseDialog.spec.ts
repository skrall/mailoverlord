import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseDialog from './ReleaseDialog.vue'
import type { MessageSummary } from '../api/client'

function summary(overrides: Partial<MessageSummary> = {}): MessageSummary {
  return {
    id: 1,
    from: 'from@test.com',
    to: 'to@test.com',
    receivedTimestamp: '2024-03-01T09:30:00Z',
    releasedTimestamp: null,
    sizeBytes: 1024,
    subject: 'Test subject',
    ...overrides,
  }
}

let mounted: VueWrapper | undefined

beforeEach(() => {
  document.body.innerHTML = ''
})

afterEach(() => {
  mounted?.unmount()
  mounted = undefined
})

function mountDialog(messages: MessageSummary[] = [summary()]): VueWrapper {
  mounted = mount(ReleaseDialog, { attachTo: document.body, props: { messages } })
  return mounted
}

/** The controls in the order Tab reaches them, as the dialog computes them. */
function focusableIn(dialog: VueWrapper): HTMLElement[] {
  return dialog
    .findAll('button, input, select, textarea, [href], [tabindex]:not([tabindex="-1"])')
    .map((wrapper) => wrapper.element as HTMLElement)
    .filter((element) => !element.hasAttribute('disabled'))
}

describe('escaping the release dialog', () => {
  /**
   * The issue called this out specifically: a modal that traps focus but not Escape is a real
   * accessibility failure, and the dialog declares `aria-modal="true"`, so it had better.
   */
  it('cancels on Escape', async () => {
    const dialog = mountDialog()

    await dialog.find('form').trigger('keydown', { key: 'Escape' })

    expect(dialog.emitted('cancel')).toHaveLength(1)
  })

  it('cancels on Escape from inside a text field, not only from the dialog itself', async () => {
    const dialog = mountDialog()
    await dialog.find('input[aria-label="Replacement recipients"]').exists()
    await dialog.findAll('input[type="checkbox"]')[0].setValue(true)

    await dialog.find('input[aria-label="Replacement recipients"]').trigger('keydown', { key: 'Escape' })

    expect(dialog.emitted('cancel')).toHaveLength(1)
  })

  /**
   * A shortcut bound at the document level sees every Escape, so one treating it as "clear the
   * filter" would fire behind a modal. The dialog marks the event so only it acts on it.
   */
  it('does not let the Escape reach the rest of the page', async () => {
    const behind = vi.fn()
    document.addEventListener('keydown', behind)
    const dialog = mountDialog()

    await dialog.find('form').trigger('keydown', { key: 'Escape' })

    document.removeEventListener('keydown', behind)
    expect(behind).not.toHaveBeenCalled()
  })
})

describe('focus inside the release dialog', () => {
  /**
   * `aria-modal="true"` claims the page behind is unavailable. Without moving focus in, a
   * keyboard user tabs straight out of the dialog and starts acting on the table they cannot
   * see, which is the opposite of what the attribute promised.
   */
  it('takes focus when it opens', () => {
    const dialog = mountDialog()

    expect(document.activeElement).toBe(dialog.find('form').element)
  })

  it('announces itself by focusing the dialog rather than a control', () => {
    const dialog = mountDialog()

    // Focusing the first checkbox would skip the title; the dialog container is what makes
    // the screen reader say "Release 1 message, dialog".
    expect(dialog.find('form').attributes('tabindex')).toBe('-1')
    expect(document.activeElement?.tagName).toBe('FORM')
  })

  /**
   * Otherwise dismissing a modal drops the user at the top of the document, and on a long table
   * finding the Release button again is a chore.
   */
  it('hands focus back to whatever opened it', () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()

    const dialog = mountDialog()
    expect(document.activeElement).not.toBe(opener)

    dialog.unmount()
    mounted = undefined

    expect(document.activeElement).toBe(opener)
  })

  it('wraps Tab from the last control back to the first', async () => {
    const dialog = mountDialog()
    const items = focusableIn(dialog)
    items.at(-1)!.focus()

    await dialog.find('form').trigger('keydown', { key: 'Tab' })

    expect(document.activeElement).toBe(items[0])
  })

  it('wraps Shift+Tab from the first control to the last', async () => {
    const dialog = mountDialog()
    const items = focusableIn(dialog)
    items[0].focus()

    await dialog.find('form').trigger('keydown', { key: 'Tab', shiftKey: true })

    expect(document.activeElement).toBe(items.at(-1))
  })

  /**
   * The recipient field only exists once its checkbox is ticked, so the list of controls grows
   * and shrinks while the dialog is open. A copy taken when it opened would send focus to a
   * detached node, where `.focus()` does nothing and focus would escape to nowhere.
   */
  it('recomputes its controls after a field is revealed and then removed', async () => {
    const dialog = mountDialog()
    await dialog.findAll('input[type="checkbox"]')[0].setValue(true)
    const revealed = dialog.find('input[aria-label="Replacement recipients"]')
    expect(revealed.exists()).toBe(true)

    await dialog.findAll('input[type="checkbox"]')[0].setValue(false)
    expect(dialog.find('input[aria-label="Replacement recipients"]').exists()).toBe(false)

    // Focus was on the field that has just been removed, so it is on nothing now.
    await dialog.find('form').trigger('keydown', { key: 'Tab' })

    expect(dialog.find('form').element.contains(document.activeElement)).toBe(true)
  })

  it('does not let Tab out of the dialog when focus has fallen outside it', async () => {
    const dialog = mountDialog()
    const outside = document.createElement('button')
    document.body.append(outside)
    outside.focus()

    await dialog.find('form').trigger('keydown', { key: 'Tab' })

    expect(document.activeElement).not.toBe(outside)
    expect(dialog.find('form').element.contains(document.activeElement)).toBe(true)
  })
})