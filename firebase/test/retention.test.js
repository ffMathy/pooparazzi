// Tests of retention/retention.js on the Authentication and Firestore emulators: `npm test` in firebase/.
import assert from 'node:assert/strict'
import { after, beforeEach, test } from 'node:test'
import { deleteApp, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { FieldValue, Timestamp, getFirestore } from 'firebase-admin/firestore'
import { INACTIVE_DAYS, carriesContent, purgeInactive, redactTombstones, redacted, successor } from '../retention/retention.js'

const app = initializeApp({ projectId: 'demo-pooparazzi' }, 'retention-test')
const auth = getAuth(app)
const db = getFirestore(app)
const DAY = 24 * 60 * 60 * 1000
const NOW = Date.parse('2026-10-29T12:00:00Z')
const quiet = () => {}
const at = (daysAgo) => new Date(NOW - daysAgo * DAY).toUTCString()

const feed = (id, extra = {}) => ({ id, kind: 'FEED', startMillis: 1_700_000_000_000, feedType: 'FORMULA', amountMl: 120, updatedAtMillis: 1_700_000_000_000, ...extra })
const synced = (json) => ({ json: JSON.stringify(json), syncedAt: FieldValue.serverTimestamp() })

/** A family with [members] (uid → days since joining), created by the first, with one entry and one baby. */
async function seedFamily(fid, members) {
  const family = db.collection('families').doc(fid)
  const uids = Object.keys(members)
  await family.set({ createdBy: uids[0], createdAt: FieldValue.serverTimestamp() })
  for (const [uid, joinedDaysAgo] of Object.entries(members)) {
    await family.collection('members').doc(uid).set({ email: `${uid}@example.com`, joinedAt: Timestamp.fromMillis(NOW - joinedDaysAgo * DAY) })
    await db.collection('users').doc(uid).set({ familyId: fid })
  }
  await family.collection('entries').doc(`${fid}-e`).set(synced(feed(`${fid}-e`)))
  await family.collection('babies').doc('default').set(synced({ id: 'default', name: 'Noa', updatedAtMillis: 1 }))
}

const count = async (path) => (await db.collection(path).get()).size

async function clear() {
  for (const name of ['users', 'families', 'invites']) await db.recursiveDelete(db.collection(name))
  const { users } = await auth.listUsers(1000)
  if (users.length) await auth.deleteUsers(users.map((user) => user.uid))
}

beforeEach(clear)
after(() => deleteApp(app))

test('accounts unused for two years leave their family as the app does, and their data goes', async () => {
  await auth.importUsers([
    { uid: 'anna', email: 'anna@example.com', metadata: { creationTime: at(900), lastSignInTime: at(INACTIVE_DAYS + 1) } },
    { uid: 'bo', email: 'bo@example.com', metadata: { creationTime: at(900), lastSignInTime: at(10) } },
    { uid: 'cai', email: 'cai@example.com', metadata: { creationTime: at(900), lastSignInTime: at(INACTIVE_DAYS + 5) } },
  ])
  // Anna created the family she shares with Bo; Cai is alone in his.
  await seedFamily('shared', { anna: 300, bo: 200 })
  await seedFamily('alone', { cai: 300 })
  await db.collection('users').doc('anna').collection('entries').doc('old').set(synced(feed('old')))
  await db.collection('invites').doc('a'.repeat(22)).set({ familyId: 'shared', createdBy: 'anna', expiresAt: Timestamp.fromMillis(NOW + DAY) })

  const dry = await purgeInactive({ auth, db, now: NOW, dryRun: true, log: quiet })
  assert.deepEqual(dry, { inactive: ['anna', 'cai'], orphaned: [], expiredInvites: 0 })
  assert.equal(await count('families/shared/members'), 2, 'a dry run deletes nothing')

  await purgeInactive({ auth, db, now: NOW, log: quiet })
  await assert.rejects(auth.getUser('anna'))
  await assert.rejects(auth.getUser('cai'))
  assert.equal((await auth.getUser('bo')).uid, 'bo')
  // Bo keeps the shared family, now his to run.
  assert.deepEqual((await db.collection('families/shared/members').get()).docs.map((d) => d.id), ['bo'])
  assert.equal((await db.doc('families/shared').get()).get('createdBy'), 'bo')
  assert.equal(await count('families/shared/entries'), 1)
  assert.equal(await count('families/shared/babies'), 1)
  // Cai was the last member: the family's log and babies go, its document stays as a tombstone.
  assert.equal(await count('families/alone/members'), 0)
  assert.equal(await count('families/alone/entries'), 0)
  assert.equal(await count('families/alone/babies'), 0)
  assert.equal((await db.doc('families/alone').get()).exists, true)
  // Pointers, old backups and invites go.
  assert.equal((await db.doc('users/anna').get()).exists, false)
  assert.equal(await count('users/anna/entries'), 0)
  assert.equal((await db.doc('users/cai').get()).exists, false)
  assert.equal(await count('invites'), 0)
  assert.equal((await db.doc('users/bo').get()).get('familyId'), 'shared')
})

test('data of accounts deleted in the console, and expired invites, go too', async () => {
  await auth.importUsers([{ uid: 'bo', email: 'bo@example.com', metadata: { creationTime: at(30), lastSignInTime: at(1) } }])
  await seedFamily('shared', { gone: 300, bo: 200 })
  await db.collection('invites').doc('b'.repeat(22)).set({ familyId: 'shared', createdBy: 'bo', expiresAt: Timestamp.fromMillis(NOW - DAY) })
  await db.collection('invites').doc('c'.repeat(22)).set({ familyId: 'shared', createdBy: 'bo', expiresAt: Timestamp.fromMillis(NOW + DAY) })

  const result = await purgeInactive({ auth, db, now: NOW, log: quiet })
  assert.deepEqual(result, { inactive: [], orphaned: ['gone'], expiredInvites: 1 })
  assert.deepEqual((await db.collection('families/shared/members').get()).docs.map((d) => d.id), ['bo'])
  assert.equal((await db.doc('families/shared').get()).get('createdBy'), 'bo')
  assert.equal((await db.doc('users/gone').get()).exists, false)
  assert.deepEqual((await db.collection('invites').get()).docs.map((d) => d.id), ['c'.repeat(22)])
})

test('the successor is the member who joined first', () => {
  const members = [{ uid: 'a', joinedAtMillis: 1 }, { uid: 'c', joinedAtMillis: 3 }, { uid: 'b', joinedAtMillis: 3 }]
  assert.equal(successor('a', members), 'b')
  assert.equal(successor('a', [{ uid: 'a', joinedAtMillis: 1 }]), null)
})

test('deleted entries lose what was logged; everything else stays as it is', async () => {
  const old = feed('old', { deleted: true, babyId: 'baby-2' })
  const cleared = redacted(feed('new', { deleted: true }))
  const kept = feed('kept')
  const entries = db.collection('families/f/entries')
  await entries.doc('old').set(synced(old))
  await entries.doc('new').set(synced(cleared))
  await entries.doc('kept').set(synced(kept))
  await db.collection('users/anna/entries').doc('legacy').set(synced(feed('legacy', { deleted: true })))
  assert.equal(carriesContent(cleared), false)

  assert.equal(await redactTombstones({ db, dryRun: true, FieldValue, log: quiet }), 2)
  assert.equal(await redactTombstones({ db, FieldValue, log: quiet }), 2)
  const json = async (path) => JSON.parse((await db.doc(path).get()).get('json'))
  assert.deepEqual(await json('families/f/entries/old'), { id: 'old', kind: 'FEED', startMillis: 0, updatedAtMillis: old.updatedAtMillis, deleted: true, babyId: 'baby-2' })
  assert.equal(carriesContent(await json('users/anna/entries/legacy')), false)
  assert.deepEqual(await json('families/f/entries/kept'), kept)
  assert.equal(await redactTombstones({ db, FieldValue, log: quiet }), 0, 'nothing left to clear')
})
