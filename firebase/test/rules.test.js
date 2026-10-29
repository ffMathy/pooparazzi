// Tests of firestore.rules on the Firestore emulator: `npm test` in firebase/ (docs/BACKEND.md).
import { readFileSync } from 'node:fs'
import { after, beforeEach, describe, test } from 'node:test'
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing'
import {
  Timestamp, collection, deleteDoc, doc, documentId, getDoc, getDocs, limit, orderBy, query, serverTimestamp, setDoc,
  startAfter, writeBatch,
} from 'firebase/firestore'

const env = await initializeTestEnvironment({
  projectId: 'demo-pooparazzi',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
})

const ANNA = { uid: 'anna', email: 'anna@example.com' }
const BO = { uid: 'bo', email: 'bo@example.com' }

/** A Firestore client signed in as [user]. */
const as = (user) => env.authenticatedContext(user.uid, { email: user.email }).firestore()
const signedOut = () => env.unauthenticatedContext().firestore()

const entryJson = (id) => JSON.stringify({ id, kind: 'FEED', startMillis: 1, updatedAtMillis: 1, babyId: 'default' })
const entry = (id) => ({ json: entryJson(id), syncedAt: serverTimestamp() })
const entryDoc = (db, uid, id) => doc(db, 'users', uid, 'entries', id)

async function seed(uid, id) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(entryDoc(ctx.firestore(), uid, id), entry(id)))
}

beforeEach(async () => {
  await env.clearFirestore()
})

after(async () => {
  await env.cleanup()
})

describe('writing entries', () => {
  test('a signed-in account writes its own entries', async () => {
    const db = as(ANNA)
    await assertSucceeds(setDoc(entryDoc(db, ANNA.uid, 'e1'), entry('e1')))
    await assertSucceeds(setDoc(entryDoc(db, ANNA.uid, 'e1'), entry('e1'))) // update
  })

  test("nobody writes another account's log, and nobody signed out writes at all", async () => {
    await assertFails(setDoc(entryDoc(as(BO), ANNA.uid, 'e1'), entry('e1')))
    await assertFails(setDoc(entryDoc(signedOut(), ANNA.uid, 'e1'), entry('e1')))
  })

  test('only the JSON and the server time of the write are accepted', async () => {
    const ref = entryDoc(as(ANNA), ANNA.uid, 'e1')
    await assertFails(setDoc(ref, { ...entry('e1'), babyName: 'Emma' }))
    await assertFails(setDoc(ref, { json: entryJson('e1') }))
    await assertFails(setDoc(ref, { syncedAt: serverTimestamp() }))
    await assertFails(setDoc(ref, { json: entryJson('e1'), syncedAt: Timestamp.fromMillis(1) }))
    await assertFails(setDoc(ref, { json: { id: 'e1' }, syncedAt: serverTimestamp() }))
    await assertFails(setDoc(ref, { json: 'x'.repeat(2049), syncedAt: serverTimestamp() }))
    await assertSucceeds(setDoc(ref, { json: 'x'.repeat(2048), syncedAt: serverTimestamp() }))
  })

  test('a full batch of 400 entries is accepted (the first upload after signing up)', async () => {
    const db = as(ANNA)
    const batch = writeBatch(db)
    for (let i = 0; i < 400; i++) batch.set(entryDoc(db, ANNA.uid, `e${i}`), entry(`e${i}`))
    await assertSucceeds(batch.commit())
  })
})

describe('reading and deleting entries', () => {
  test('the owner reads, pages and deletes its own log', async () => {
    await seed(ANNA.uid, 'e1')
    await seed(ANNA.uid, 'e2')
    const db = as(ANNA)
    await assertSucceeds(getDoc(entryDoc(db, ANNA.uid, 'e1')))
    const entries = collection(db, 'users', ANNA.uid, 'entries')
    const first = await assertSucceeds(getDocs(query(entries, orderBy('syncedAt'), orderBy(documentId()), limit(1))))
    const last = first.docs[0]
    await assertSucceeds(getDocs(query(entries, orderBy('syncedAt'), orderBy(documentId()),
      startAfter(last.get('syncedAt'), last.id), limit(500))))
    await assertSucceeds(deleteDoc(entryDoc(db, ANNA.uid, 'e1')))
  })

  test("nobody reads or deletes another account's log", async () => {
    await seed(ANNA.uid, 'e1')
    const bo = as(BO)
    await assertFails(getDoc(entryDoc(bo, ANNA.uid, 'e1')))
    await assertFails(getDocs(collection(bo, 'users', ANNA.uid, 'entries')))
    await assertFails(deleteDoc(entryDoc(bo, ANNA.uid, 'e1')))
    await assertFails(getDoc(entryDoc(signedOut(), ANNA.uid, 'e1')))
    await assertFails(getDocs(collection(signedOut(), 'users', ANNA.uid, 'entries')))
  })
})

test('everything else is denied', async () => {
  const db = as(ANNA)
  await assertFails(setDoc(doc(db, 'users', ANNA.uid), { babies: ['Emma'] }))
  await assertFails(getDoc(doc(db, 'users', ANNA.uid)))
  await assertFails(setDoc(doc(db, 'users', ANNA.uid, 'babies', 'b1'), { name: 'Emma' }))
  await assertFails(getDocs(collection(db, 'users')))
  await assertFails(setDoc(doc(db, 'anything', 'x'), {}))
})
