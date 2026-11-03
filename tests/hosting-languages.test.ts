// The website (firebase/hosting) shows each page in one language, English or Danish, picked from the browser's
// languages by a small inline script (English without it). The Content-Security-Policy allows only that script, by its
// hash, so a page whose script differs would silently stay in English: every page must carry exactly the allowed one,
// and every Danish part must have its English twin. `bun test ./tests` from the repository root.
import { describe, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(import.meta.dir, '..')
const read = (path: string) => readFileSync(join(root, path), 'utf8')

const pages = ['index.html', '404.html', 'join/index.html', 'signin/index.html', 'privacy/index.html']
const config = JSON.parse(read('firebase/firebase.json'))
const csp: string = config.hosting.headers
  .flatMap((rule: { headers: { key: string; value: string }[] }) => rule.headers)
  .find((header: { key: string }) => header.key === 'Content-Security-Policy').value

describe('the website picks its language from the browser', () => {
  for (const page of pages) {
    const html = read(join('firebase/hosting', page))

    test(`${page} runs only the script the CSP allows`, () => {
      const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1])
      expect(scripts.length).toBe(1)
      const hash = createHash('sha256').update(scripts[0]).digest('base64')
      expect(csp).toContain(`'sha256-${hash}'`)
      expect(html).not.toMatch(/<script\s+src=/)
    })

    test(`${page} has English and Danish, English shown by default`, () => {
      expect(html).toContain('<html lang="en" data-title-da="')
      expect(html).toContain('[data-lang="da"] { display: none; }')
      const english = (html.match(/data-lang="en"/g) ?? []).length
      const danish = (html.match(/data-lang="da"/g) ?? []).length
      expect(english).toBeGreaterThan(0)
      // The CSS names data-lang="da" twice and data-lang="en" once besides the page's own parts.
      expect(danish - 2).toBe(english - 1)
    })
  }
})
