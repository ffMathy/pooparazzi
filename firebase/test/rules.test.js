// Tests of firestore.rules on the Firestore emulator: `npm test` in firebase/ (docs/BACKEND.md).
import { readFileSync } from 'node:fs'
import { after, beforeEach, describe, test } from 'node:test'
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing'
import {
  Timestamp, collection, deleteDoc, doc, getDoc, getDocs, limit, orderBy, query, serverTimestamp, setDoc, startAfter,
  documentId, writeBatch,
} from 'firebase/firestore'

const env = await initializeTestEnvironment({
  projectId: 'demo-pooparazzi',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
})

const ANNA = { uid: 'anna', email: 'anna@example.com' }
const BO = { uid: 'bo', email: 'bo@example.com' }

/** A Firestore client signed in as [user], with a confirmed email unless [verified] is false. */
function as(user, { verified = true, email = user.email } = {}) {
  return env.authenticatedContext(user.uid, { email, email_verified: verified }).firestore()
}

const entryJson = (id) => JSON.stringify({ id, kind: 'FEED', startMillis: 1, updatedAtMillis: 1, babyId: 'default' })
const entry = (id) => ({ json: entryJson(id), syncedAt: serverTimestamp() })
const entryDoc = (db, uid, id) => doc(db, 'users', uid, 'entries', id)

async function invite(address) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'invites', address), {}))
}

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
  test('an invited account with a confirmed email writes its own entries', async () => {
    await invite(ANNA.email)
    const db = as(ANNA)
    await assertSucceeds(setDoc(entryDoc(db, ANNA.uid, 'e1'), entry('e1')))
    await assertSucceeds(setDoc(entryDoc(db, ANNA.uid, 'e1'), entry('e1'))) // update
  })

  test('the invitation matches the address in any letter case', async () => {
    await invite('anna@example.com')
    const db = as(ANNA, { email: 'Anna@Example.COM' })
    await assertSucceeds(setDoc(entryDoc(db, ANNA.uid, 'e1'), entry('e1')))
  })

  test('an account whose email is not confirmed yet cannot write', async () => {
    await invite(ANNA.email)
    await assertFails(setDoc(entryDoc(as(ANNA, { verified: false }), ANNA.uid, 'e1'), entry('e1')))
  })

  test('an account that is not invited cannot write', async () => {
    await invite(BO.email)
    await assertFails(setDoc(entryDoc(as(ANNA), ANNA.uid, 'e1'), entry('e1')))
  })

  test("nobody writes another account's log, invited or not", async () => {
    await invite(ANNA.email)
    await invite(BO.email)
    await assertFails(setDoc(entryDoc(as(BO), ANNA.uid, 'e1'), entry('e1')))
    await assertFails(setDoc(entryDoc(env.unauthenticatedContext().firestore(), ANNA.uid, 'e1'), entry('e1')))
  })

  test('only the JSON and the server time of the write are accepted', async () => {
    await invite(ANNA.email)
    const db = as(ANNA)
    const ref = entryDoc(db, ANNA.uid, 'e1')
    await assertFails(setDoc(ref, { ...entry('e1'), babyName: 'Emma' }))
    await assertFails(setDoc(ref, { json: entryJson('e1') }))
    await assertFails(setDoc(ref, { syncedAt: serverTimestamp() }))
    await assertFails(setDoc(ref, { json: entryJson('e1'), syncedAt: Timestamp.fromMillis(1) }))
    await assertFails(setDoc(ref, { json: { id: 'e1' }, syncedAt: serverTimestamp() }))
    await assertFails(setDoc(ref, { json: 'x'.repeat(2049), syncedAt: serverTimestamp() }))
    await assertSucceeds(setDoc(ref, { json: 'x'.repeat(2048), syncedAt: serverTimestamp() }))
  })

  test('a full batch of 400 entries is accepted (the sign-up upload)', async () => {
    await invite(ANNA.email)
    const db = as(ANNA)
    const batch = writeBatch(db)
    for (let i = 0; i < 400; i++) batch.set(entryDoc(db, ANNA.uid, `e${i}`), entry(`e${i}`))
    await assertSucceeds(batch.commit())
  })
})

describe('reading and deleting entries', () => {
  test('the owner reads, pages and deletes its own log, even without an invitation', async () => {
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
    await invite(BO.email)
    const bo = as(BO)
    await assertFails(getDoc(entryDoc(bo, ANNA.uid, 'e1')))
    await assertFails(getDocs(collection(bo, 'users', ANNA.uid, 'entries')))
    await assertFails(deleteDoc(entryDoc(bo, ANNA.uid, 'e1')))
    await assertFails(getDoc(entryDoc(env.unauthenticatedContext().firestore(), ANNA.uid, 'e1')))
  })
})

describe('invitations', () => {
  test('an account may ask whether its own address is invited', async () => {
    await invite(ANNA.email)
    const unverified = as(ANNA, { verified: false })
    const snapshot = await assertSucceeds(getDoc(doc(unverified, 'invites', ANNA.email)))
    if (!snapshot.exists()) throw new Error('the invitation should be visible to its owner')
    const missing = await assertSucceeds(getDoc(doc(as(BO), 'invites', BO.email)))
    if (missing.exists()) throw new Error('Bo was never invited')
  })

  test('nobody lists, reads other or writes invitations', async () => {
    await invite(ANNA.email)
    const bo = as(BO)
    await assertFails(getDoc(doc(bo, 'invites', ANNA.email)))
    await assertFails(getDocs(collection(bo, 'invites')))
    await assertFails(setDoc(doc(bo, 'invites', BO.email), {}))
    await assertFails(setDoc(doc(as(ANNA), 'invites', ANNA.email), {}))
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'invites', ANNA.email)))
  })
})

test('everything else is denied', async () => {
  await invite(ANNA.email)
  const db = as(ANNA)
  await assertFails(setDoc(doc(db, 'users', ANNA.uid), { babies: ['Emma'] }))
  await assertFails(getDoc(doc(db, 'users', ANNA.uid)))
  await assertFails(setDoc(doc(db, 'users', ANNA.uid, 'babies', 'b1'), { name: 'Emma' }))
  await assertFails(setDoc(doc(db, 'anything', 'x'), {}))
})
