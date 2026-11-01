// Play Services reads the Wear capabilities (core's res/values/wear.xml) by name, never through R, so release builds'
// resource shrinking drops them unless a res/raw keep file names them. Without them neither app advertises
// pooparazzi_tracker, and the phone says "No watch connected" next to a watch with the app (data items still sync).
// `bun test ./tests` from the repository root.
import { describe, expect, test } from 'bun:test'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

const root = join(import.meta.dir, '..')
const read = (path: string) => readFileSync(join(root, path), 'utf8')

/** The resources named in tools:keep of every res/raw XML file of [module]'s main source set. */
function kept(module: string): Set<string> {
  const dir = join(module, 'src/main/res/raw')
  const names = new Set<string>()
  for (const file of readdirSync(join(root, dir)).filter((f) => f.endsWith('.xml'))) {
    for (const match of read(join(dir, file)).matchAll(/tools:keep="([^"]*)"/g)) {
      for (const name of match[1].split(',')) names.add(name.trim())
    }
  }
  return names
}

const capabilities = read('core/src/main/res/values/wear.xml')

describe('the Wear capabilities', () => {
  test('core declares both', () => {
    expect(capabilities).toContain('name="android_wear_capabilities"')
    expect(capabilities).toContain('<item>pooparazzi_tracker</item>')
    expect(capabilities).toContain('<item>pooparazzi_babies</item>')
  })

  for (const module of ['app', 'wear']) {
    test(`${module} keeps them through resource shrinking`, () => {
      expect(kept(module)).toContain('@array/android_wear_capabilities')
    })
  }

  test('the Kotlin constants match', () => {
    const sync = read('core/src/main/kotlin/com/ffmathy/pooparazzi/core/sync/EntrySync.kt')
    expect(sync).toContain('const val CAPABILITY = "pooparazzi_tracker"')
    expect(sync).toContain('const val BABIES_CAPABILITY = "pooparazzi_babies"')
  })
})
