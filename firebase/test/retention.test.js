// Tests of retention/retention.js on the Authentication and Firestore emulators: `npm test` in firebase/.
import assert from 'node:assert/strict'
import { after, beforeEach, test } from 'node:test'
import { deleteApp, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { FieldValue, getFirestore } from 'firebase-admin/firestore'
import { INACTIVE_DAYS, carriesContent, purgeInactive, redactTombstones, redacted } from '../retention/retention.js'

const PROJECT = 'demo-pooparazzi'
const app = initializeApp({ projectId: PROJECT }, 'retention-test')
const auth = getAuth(app)
const db = getFirestore(app)
const DAY = 24 * 60 * 60 * 1000
const NOW = Date.parse('2026-10-29T12:00:00Z')
const quiet = () => {}

const feed = (id, extra = {}) => ({ id, kind: 'FEED', startMillis: 1_700_000_000_000, feedType: 'FORMULA', updatedAtMillis: 1_700_000_000_000, ...extra })

async function seedEntry(uid, entry) {
  await db.collection('users').doc(uid).collection('entries').doc(entry.id).set({ json: JSON.stringify(entry), syncedAt: FieldValue.serverTimestamp() })
}

async function clear() {
  await db.recursiveDelete(db.collection('users'))
  const { users } = await auth.listUsers(1000)
  if (users.length) await auth.deleteUsers(users.map((user) => user.uid))
}

beforeEach(clear)
after(() => deleteApp(app))

test('accounts unused for two years are deleted with their backup, and orphaned backups go too', async () => {
  const at = (daysAgo) => new Date(NOW - daysAgo * DAY).toUTCString()
  await auth.importUsers([
    { uid: 'active', email: 'a@example.com', metadata: { creationTime: at(900), lastSignInTime: at(10) } },
    { uid: 'idle', email: 'i@example.com', metadata: { creationTime: at(900), lastSignInTime: at(INACTIVE_DAYS + 1) } },
  ])
  await seedEntry('active', feed('e1'))
  await seedEntry('idle', feed('e2'))
  await db.collection('users').doc('idle').set({ consentVersion: 1, consentedAt: FieldValue.serverTimestamp() })
  await seedEntry('gone', feed('e3'))

  const dry = await purgeInactive({ auth, db, now: NOW, dryRun: true, log: quiet })
  assert.deepEqual(dry, { inactive: ['idle'], orphaned: ['gone'] })
  assert.equal((await db.collection('users').doc('idle').collection('entries').get()).size, 1, 'a dry run deletes nothing')

  await purgeInactive({ auth, db, now: NOW, log: quiet })
  assert.equal((await db.collection('users').doc('idle').collection('entries').get()).size, 0)
  assert.equal((await db.collection('users').doc('idle').get()).exists, false, 'the consent record goes too')
  assert.equal((await db.collection('users').doc('gone').collection('entries').get()).size, 0)
  await assert.rejects(auth.getUser('idle'))
  assert.equal((await auth.getUser('active')).uid, 'active')
  assert.equal((await db.collection('users').doc('active').collection('entries').get()).size, 1)
})

test('deleted entries lose what was logged; everything else stays as it is', async () => {
  const old = feed('old', { deleted: true, babyId: 'baby-2', breastSide: null })
  const cleared = redacted(feed('new', { deleted: true }))
  const kept = feed('kept')
  await seedEntry('anna', old)
  await seedEntry('anna', cleared)
  await seedEntry('anna', kept)
  assert.equal(carriesContent(cleared), false)

  assert.equal(await redactTombstones({ db, dryRun: true, FieldValue, log: quiet }), 1)
  assert.equal(await redactTombstones({ db, FieldValue, log: quiet }), 1)
  const json = async (id) => JSON.parse((await db.collection('users').doc('anna').collection('entries').doc(id).get()).get('json'))
  assert.deepEqual(await json('old'), { id: 'old', kind: 'FEED', startMillis: 0, updatedAtMillis: old.updatedAtMillis, deleted: true, babyId: 'baby-2' })
  assert.deepEqual(await json('kept'), kept)
  assert.equal(await redactTombstones({ db, FieldValue, log: quiet }), 0, 'nothing left to clear')
})
