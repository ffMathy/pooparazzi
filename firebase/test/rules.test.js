// Tests of firestore.rules on the Firestore emulator: `npm test` in firebase/ (docs/BACKEND.md).
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { after, beforeEach, describe, test } from 'node:test'
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing'
import {
  Timestamp, collection, deleteDoc, doc, documentId, getDoc, getDocs, limit, orderBy, query, serverTimestamp, setDoc,
  startAfter, updateDoc, writeBatch,
} from 'firebase/firestore'

const env = await initializeTestEnvironment({
  projectId: 'demo-pooparazzi',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
})

const ANNA = { uid: 'anna', email: 'anna@example.com' }
const BO = { uid: 'bo', email: 'bo@example.com' }
const CAI = { uid: 'cai', email: 'cai@example.com' }

const FAMILY = 'fam-anna'
const OTHER_FAMILY = 'fam-cai'
const CODE = 'AbCdEfGhIjKlMnOpQrSt_-' // 22 characters: 128 bits in URL-safe base64
const OTHER_CODE = 'ZyXwVuTsRqPoNmLkJiHg-_'

const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

/** A Firestore client signed in as [user]. */
const as = (user) => env.authenticatedContext(user.uid, { email: user.email }).firestore()
const signedOut = () => env.unauthenticatedContext().firestore()
const admin = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()))

const familyDoc = (db, fid) => doc(db, 'families', fid)
const memberDoc = (db, fid, uid) => doc(db, 'families', fid, 'members', uid)
const userDoc = (db, uid) => doc(db, 'users', uid)
const inviteDoc = (db, code) => doc(db, 'invites', code)
const syncedDoc = (db, fid, kind, id) => doc(db, 'families', fid, kind, id)
const legacyDoc = (db, uid, id) => doc(db, 'users', uid, 'entries', id)

const entryJson = (id) => JSON.stringify({ id, kind: 'FEED', startMillis: 1, updatedAtMillis: 1, babyId: 'default' })
const babyJson = (id) => JSON.stringify({ id, name: 'Noa', updatedAtMillis: 1 })
const synced = (kind, id) => ({ json: kind === 'babies' ? babyJson(id) : entryJson(id), syncedAt: serverTimestamp() })

const member = (user, extra = {}) => ({ email: user.email, joinedAt: serverTimestamp(), ...extra })
const inviteData = (user, fid, expiresInMillis = DAY, extra = {}) =>
  ({ familyId: fid, createdBy: user.uid, expiresAt: Timestamp.fromMillis(Date.now() + expiresInMillis), ...extra })

/** The batch the app sends on first sign-in: the family, its creator's member document and the users document. */
function createFamilyBatch(db, user, fid) {
  const batch = writeBatch(db)
  batch.set(familyDoc(db, fid), { createdBy: user.uid, createdAt: serverTimestamp() })
  batch.set(memberDoc(db, fid, user.uid), member(user))
  batch.set(userDoc(db, user.uid), { familyId: fid })
  return batch
}

/** The batch the app sends to join with an invite: the member document, the users document, the invite deleted. */
function joinBatch(db, user, fid, code) {
  const batch = writeBatch(db)
  batch.set(memberDoc(db, fid, user.uid), member(user, { invite: code }))
  batch.set(userDoc(db, user.uid), { familyId: fid })
  batch.delete(inviteDoc(db, code))
  return batch
}

/** [creator]'s family [fid] with [others] as further members, written past the rules. */
async function seedFamily(fid, creator, ...others) {
  await admin(async (db) => {
    await setDoc(familyDoc(db, fid), { createdBy: creator.uid, createdAt: Timestamp.now() })
    for (const user of [creator, ...others]) {
      await setDoc(memberDoc(db, fid, user.uid), { email: user.email, joinedAt: Timestamp.now() })
      await setDoc(userDoc(db, user.uid), { familyId: fid })
    }
  })
}

async function seedInvite(code, data) {
  await admin((db) => setDoc(inviteDoc(db, code), data))
}

async function seedSynced(fid, kind, id) {
  await admin((db) => setDoc(syncedDoc(db, fid, kind, id), { json: '{}', syncedAt: Timestamp.now() }))
}

beforeEach(async () => {
  await env.clearFirestore()
})

after(async () => {
  await env.cleanup()
})

describe('creating a family', () => {
  test('a new account creates its own family, member document and users document in one batch', async () => {
    const db = as(ANNA)
    await assertSucceeds(createFamilyBatch(db, ANNA, FAMILY).commit())
    await assertSucceeds(getDoc(familyDoc(db, FAMILY)))
    await assertSucceeds(getDocs(collection(db, 'families', FAMILY, 'members')))
    await assertSucceeds(getDoc(userDoc(db, ANNA.uid)))
  })

  test('the family needs its creator as a member in the same batch, and the member needs the family', async () => {
    const db = as(ANNA)
    await assertFails(setDoc(familyDoc(db, FAMILY), { createdBy: ANNA.uid, createdAt: serverTimestamp() }))
    await assertFails(setDoc(memberDoc(db, FAMILY, ANNA.uid), member(ANNA)))
  })

  test('the family names its creator and the server time, and nothing else', async () => {
    const db = as(ANNA)
    const attempt = (family) => {
      const batch = writeBatch(db)
      batch.set(familyDoc(db, FAMILY), family)
      batch.set(memberDoc(db, FAMILY, ANNA.uid), member(ANNA))
      return batch.commit()
    }
    await assertFails(attempt({ createdBy: BO.uid, createdAt: serverTimestamp() }))
    await assertFails(attempt({ createdBy: ANNA.uid, createdAt: Timestamp.fromMillis(1) }))
    await assertFails(attempt({ createdBy: ANNA.uid }))
    await assertFails(attempt({ createdBy: ANNA.uid, createdAt: serverTimestamp(), name: 'Hansen' }))
    await assertSucceeds(attempt({ createdBy: ANNA.uid, createdAt: serverTimestamp() }))
  })

  test("nobody becomes a member as the creator of another account's family", async () => {
    await seedFamily(FAMILY, ANNA)
    const bo = as(BO)
    const batch = writeBatch(bo)
    batch.set(memberDoc(bo, FAMILY, BO.uid), member(BO))
    await assertFails(batch.commit())
  })

  test('a creator who left cannot come back without an invite', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await admin((db) => deleteDoc(memberDoc(db, FAMILY, ANNA.uid)))
    await assertFails(setDoc(memberDoc(as(ANNA), FAMILY, ANNA.uid), member(ANNA)))
  })

  test('a family is never changed, and only its members read it', async () => {
    await seedFamily(FAMILY, ANNA)
    await assertFails(updateDoc(familyDoc(as(ANNA), FAMILY), { createdBy: BO.uid }))
    await assertFails(setDoc(familyDoc(as(ANNA), FAMILY), { createdBy: ANNA.uid, createdAt: serverTimestamp() }))
    await assertFails(getDoc(familyDoc(as(BO), FAMILY)))
    await assertFails(getDoc(familyDoc(signedOut(), FAMILY)))
    await assertFails(getDocs(collection(as(ANNA), 'families')))
  })
})

describe('joining with an invite', () => {
  test('a valid invite lets an account in, and it then shares the log and the babies', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedSynced(FAMILY, 'entries', 'e1')
    await seedSynced(FAMILY, 'babies', 'default')
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    const db = as(BO)
    await assertSucceeds(getDoc(inviteDoc(db, CODE)))
    await assertSucceeds(joinBatch(db, BO, FAMILY, CODE).commit())
    await assertSucceeds(getDocs(collection(db, 'families', FAMILY, 'entries')))
    await assertSucceeds(getDocs(collection(db, 'families', FAMILY, 'babies')))
    await assertSucceeds(getDocs(collection(db, 'families', FAMILY, 'members')))
  })

  test('an expired invite is refused', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedInvite(CODE, inviteData(ANNA, FAMILY, -HOUR))
    await assertFails(joinBatch(as(BO), BO, FAMILY, CODE).commit())
  })

  test("another family's invite is refused", async () => {
    await seedFamily(FAMILY, ANNA)
    await seedFamily(OTHER_FAMILY, CAI)
    await seedInvite(CODE, inviteData(CAI, OTHER_FAMILY))
    await assertFails(joinBatch(as(BO), BO, FAMILY, CODE).commit())
  })

  test('a missing invite is refused, and so is a member document without one', async () => {
    await seedFamily(FAMILY, ANNA)
    await assertFails(joinBatch(as(BO), BO, FAMILY, CODE).commit())
    const bo = as(BO)
    const batch = writeBatch(bo)
    batch.set(memberDoc(bo, FAMILY, BO.uid), member(BO))
    batch.set(userDoc(bo, BO.uid), { familyId: FAMILY })
    await assertFails(batch.commit())
  })

  test('an invite is used once: joining must delete it, and a used one lets nobody else in', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    const bo = as(BO)
    const keep = writeBatch(bo)
    keep.set(memberDoc(bo, FAMILY, BO.uid), member(BO, { invite: CODE }))
    await assertFails(keep.commit())
    await assertSucceeds(joinBatch(bo, BO, FAMILY, CODE).commit())
    const used = await assertSucceeds(getDoc(inviteDoc(as(CAI), CODE)))
    assert.equal(used.exists(), false)
    await assertFails(joinBatch(as(CAI), CAI, FAMILY, CODE).commit())
  })

  test('an account adds only itself, with its own email, the server time and nothing else', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    const bo = as(BO)
    const attempt = (uid, data) => {
      const batch = writeBatch(bo)
      batch.set(memberDoc(bo, FAMILY, uid), data)
      batch.delete(inviteDoc(bo, CODE))
      return batch.commit()
    }
    await assertFails(attempt(CAI.uid, member(CAI, { invite: CODE })))
    await assertFails(attempt(BO.uid, member(BO, { invite: CODE, email: 'someone@example.com' })))
    await assertFails(attempt(BO.uid, member(BO, { invite: CODE, joinedAt: Timestamp.fromMillis(1) })))
    await assertFails(attempt(BO.uid, member(BO, { invite: CODE, role: 'admin' })))
    await assertFails(attempt(BO.uid, { email: BO.email, invite: CODE }))
    await seedInvite('42', inviteData(ANNA, FAMILY))
    const numeric = writeBatch(bo)
    numeric.set(memberDoc(bo, FAMILY, BO.uid), member(BO, { invite: 42 }))
    numeric.delete(inviteDoc(bo, '42'))
    await assertFails(numeric.commit())
    await assertSucceeds(attempt(BO.uid, member(BO, { invite: CODE })))
  })

  test('signed out, nobody joins', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    const db = signedOut()
    const batch = writeBatch(db)
    batch.set(memberDoc(db, FAMILY, BO.uid), member(BO, { invite: CODE }))
    batch.delete(inviteDoc(db, CODE))
    await assertFails(batch.commit())
  })
})

describe('members', () => {
  test('members read the member list, nobody else does', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await assertSucceeds(getDocs(collection(as(BO), 'families', FAMILY, 'members')))
    await assertSucceeds(getDoc(memberDoc(as(BO), FAMILY, ANNA.uid)))
    await assertFails(getDocs(collection(as(CAI), 'families', FAMILY, 'members')))
    await assertFails(getDoc(memberDoc(as(CAI), FAMILY, ANNA.uid)))
    await assertFails(getDocs(collection(signedOut(), 'families', FAMILY, 'members')))
  })

  test('a member document is never changed', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await assertFails(updateDoc(memberDoc(as(BO), FAMILY, BO.uid), { email: 'new@example.com' }))
    await assertFails(setDoc(memberDoc(as(BO), FAMILY, BO.uid), member(BO)))
  })

  test('a member leaves, and then reads nothing of the family', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await seedSynced(FAMILY, 'entries', 'e1')
    const bo = as(BO)
    await assertSucceeds(deleteDoc(memberDoc(bo, FAMILY, BO.uid)))
    await assertFails(getDocs(collection(bo, 'families', FAMILY, 'entries')))
    await assertFails(getDocs(collection(bo, 'families', FAMILY, 'members')))
    await assertFails(setDoc(syncedDoc(bo, FAMILY, 'entries', 'e2'), synced('entries', 'e2')))
  })

  test('leaving and starting a new family of your own is one batch', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    const bo = as(BO)
    const batch = writeBatch(bo)
    batch.delete(memberDoc(bo, FAMILY, BO.uid))
    batch.set(familyDoc(bo, 'fam-bo'), { createdBy: BO.uid, createdAt: serverTimestamp() })
    batch.set(memberDoc(bo, 'fam-bo', BO.uid), member(BO))
    batch.set(userDoc(bo, BO.uid), { familyId: 'fam-bo' })
    await assertSucceeds(batch.commit())
  })

  test('the creator removes a member; other members remove nobody but themselves', async () => {
    await seedFamily(FAMILY, ANNA, BO, CAI)
    await assertFails(deleteDoc(memberDoc(as(BO), FAMILY, CAI.uid)))
    await assertFails(deleteDoc(memberDoc(as(BO), FAMILY, ANNA.uid)))
    await assertFails(deleteDoc(memberDoc(signedOut(), FAMILY, CAI.uid)))
    await assertSucceeds(deleteDoc(memberDoc(as(ANNA), FAMILY, CAI.uid)))
  })

  test('a creator who left removes nobody, and neither does the creator of another family', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await seedFamily(OTHER_FAMILY, CAI)
    await assertFails(deleteDoc(memberDoc(as(CAI), FAMILY, BO.uid)))
    await admin((db) => deleteDoc(memberDoc(db, FAMILY, ANNA.uid)))
    await assertFails(deleteDoc(memberDoc(as(ANNA), FAMILY, BO.uid)))
  })
})

describe('the users document', () => {
  test('an account reads, points and deletes its own', async () => {
    await seedFamily(FAMILY, ANNA)
    const db = as(ANNA)
    await assertSucceeds(getDoc(userDoc(db, ANNA.uid)))
    await assertSucceeds(setDoc(userDoc(db, ANNA.uid), { familyId: FAMILY }))
    await assertSucceeds(deleteDoc(userDoc(db, ANNA.uid)))
    await assertSucceeds(setDoc(userDoc(db, ANNA.uid), { familyId: FAMILY })) // create
  })

  test('it names only a family the account is in, and holds nothing else', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedFamily(OTHER_FAMILY, CAI)
    const db = as(ANNA)
    await assertFails(setDoc(userDoc(db, ANNA.uid), { familyId: OTHER_FAMILY }))
    await assertFails(setDoc(userDoc(db, ANNA.uid), { familyId: 'nowhere' }))
    await assertFails(setDoc(userDoc(db, ANNA.uid), { familyId: FAMILY, babies: ['Emma'] }))
    await assertFails(setDoc(userDoc(db, ANNA.uid), {}))
    await seedFamily('7', ANNA)
    await assertFails(setDoc(userDoc(db, ANNA.uid), { familyId: 7 }))
    await assertSucceeds(setDoc(userDoc(db, ANNA.uid), { familyId: '7' }))
  })

  test("nobody reads or writes another account's", async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await assertFails(getDoc(userDoc(as(BO), ANNA.uid)))
    await assertFails(setDoc(userDoc(as(BO), ANNA.uid), { familyId: FAMILY }))
    await assertFails(deleteDoc(userDoc(as(BO), ANNA.uid)))
    await assertFails(getDoc(userDoc(signedOut(), ANNA.uid)))
    await assertFails(getDocs(collection(as(ANNA), 'users')))
  })
})

describe('invites', () => {
  test('a member invites to their own family, for up to 7 days, optionally with their own email', async () => {
    await seedFamily(FAMILY, ANNA)
    const db = as(ANNA)
    await assertSucceeds(setDoc(inviteDoc(db, CODE), inviteData(ANNA, FAMILY, 7 * DAY - 60 * 1000)))
    await assertSucceeds(setDoc(inviteDoc(db, OTHER_CODE), inviteData(ANNA, FAMILY, DAY, { email: ANNA.email })))
  })

  test('the expiry is in the future and at most 7 days away', async () => {
    await seedFamily(FAMILY, ANNA)
    const ref = inviteDoc(as(ANNA), CODE)
    await assertFails(setDoc(ref, inviteData(ANNA, FAMILY, 7 * DAY + HOUR)))
    await assertFails(setDoc(ref, inviteData(ANNA, FAMILY, -HOUR)))
    await assertFails(setDoc(ref, { ...inviteData(ANNA, FAMILY), expiresAt: Date.now() + DAY }))
    await assertFails(setDoc(ref, { familyId: FAMILY, createdBy: ANNA.uid }))
  })

  test('only for your own family, in your own name, with nothing else', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedFamily(OTHER_FAMILY, CAI)
    await assertFails(setDoc(inviteDoc(as(ANNA), CODE), inviteData(ANNA, OTHER_FAMILY)))
    await assertFails(setDoc(inviteDoc(as(BO), CODE), inviteData(BO, FAMILY)))
    await assertFails(setDoc(inviteDoc(as(ANNA), CODE), inviteData(BO, FAMILY)))
    await assertFails(setDoc(inviteDoc(as(ANNA), CODE), inviteData(ANNA, FAMILY, DAY, { email: BO.email })))
    await assertFails(setDoc(inviteDoc(as(ANNA), CODE), inviteData(ANNA, FAMILY, DAY, { uses: 5 })))
    await seedFamily('3', ANNA)
    await assertFails(setDoc(inviteDoc(as(ANNA), CODE), { ...inviteData(ANNA, FAMILY), familyId: 3 }))
    await assertFails(setDoc(inviteDoc(signedOut(), CODE), inviteData(ANNA, FAMILY)))
  })

  test('the code is long and URL-safe', async () => {
    await seedFamily(FAMILY, ANNA)
    const db = as(ANNA)
    await assertFails(setDoc(inviteDoc(db, 'short'), inviteData(ANNA, FAMILY)))
    await assertFails(setDoc(inviteDoc(db, 'x'.repeat(65)), inviteData(ANNA, FAMILY)))
    await assertFails(setDoc(inviteDoc(db, 'AbCdEfGhIjKlMnOpQrSt+='), inviteData(ANNA, FAMILY)))
    await assertSucceeds(setDoc(inviteDoc(db, 'x'.repeat(64)), inviteData(ANNA, FAMILY)))
  })

  test('any signed-in account with the code reads it; nobody lists them or changes one', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    await assertSucceeds(getDoc(inviteDoc(as(CAI), CODE)))
    await assertFails(getDoc(inviteDoc(signedOut(), CODE)))
    await assertFails(getDocs(collection(as(ANNA), 'invites')))
    await assertFails(getDocs(query(collection(as(ANNA), 'invites'), limit(1))))
    await assertFails(updateDoc(inviteDoc(as(ANNA), CODE), { expiresAt: Timestamp.fromMillis(Date.now() + DAY) }))
  })

  test('deleted by whoever made it or by a member of its family, by nobody else', async () => {
    await seedFamily(FAMILY, ANNA, BO)
    await seedInvite(CODE, inviteData(ANNA, FAMILY))
    await seedInvite(OTHER_CODE, inviteData(ANNA, FAMILY))
    await assertFails(deleteDoc(inviteDoc(as(CAI), CODE)))
    await assertFails(deleteDoc(inviteDoc(signedOut(), CODE)))
    await assertSucceeds(deleteDoc(inviteDoc(as(BO), CODE)))
    await admin((db) => deleteDoc(memberDoc(db, FAMILY, ANNA.uid)))
    await assertSucceeds(deleteDoc(inviteDoc(as(ANNA), OTHER_CODE)))
  })
})

for (const kind of ['entries', 'babies']) {
  describe(`the family's ${kind}`, () => {
    test('members write, read, page and delete them', async () => {
      await seedFamily(FAMILY, ANNA, BO)
      const db = as(BO)
      await assertSucceeds(setDoc(syncedDoc(db, FAMILY, kind, 'x1'), synced(kind, 'x1')))
      await assertSucceeds(setDoc(syncedDoc(db, FAMILY, kind, 'x1'), synced(kind, 'x1'))) // update
      await assertSucceeds(setDoc(syncedDoc(db, FAMILY, kind, 'x2'), synced(kind, 'x2')))
      await assertSucceeds(getDoc(syncedDoc(as(ANNA), FAMILY, kind, 'x1')))
      const col = collection(db, 'families', FAMILY, kind)
      const first = await assertSucceeds(getDocs(query(col, orderBy('syncedAt'), orderBy(documentId()), limit(1))))
      const last = first.docs[0]
      await assertSucceeds(getDocs(query(col, orderBy('syncedAt'), orderBy(documentId()),
        startAfter(last.get('syncedAt'), last.id), limit(500))))
      await assertSucceeds(deleteDoc(syncedDoc(db, FAMILY, kind, 'x1')))
    })

    test('non-members and signed-out clients do nothing with them', async () => {
      await seedFamily(FAMILY, ANNA)
      await seedFamily(OTHER_FAMILY, CAI)
      await seedSynced(FAMILY, kind, 'x1')
      for (const db of [as(CAI), as(BO), signedOut()]) {
        await assertFails(getDoc(syncedDoc(db, FAMILY, kind, 'x1')))
        await assertFails(getDocs(collection(db, 'families', FAMILY, kind)))
        await assertFails(setDoc(syncedDoc(db, FAMILY, kind, 'x2'), synced(kind, 'x2')))
        await assertFails(setDoc(syncedDoc(db, FAMILY, kind, 'x1'), synced(kind, 'x1')))
        await assertFails(deleteDoc(syncedDoc(db, FAMILY, kind, 'x1')))
      }
    })

    test('only the JSON (at most 2 KB) and the server time of the write are accepted', async () => {
      await seedFamily(FAMILY, ANNA)
      const ref = syncedDoc(as(ANNA), FAMILY, kind, 'x1')
      await assertFails(setDoc(ref, { ...synced(kind, 'x1'), babyName: 'Emma' }))
      await assertFails(setDoc(ref, { json: '{}' }))
      await assertFails(setDoc(ref, { syncedAt: serverTimestamp() }))
      await assertFails(setDoc(ref, { json: '{}', syncedAt: Timestamp.fromMillis(1) }))
      await assertFails(setDoc(ref, { json: { id: 'x1' }, syncedAt: serverTimestamp() }))
      await assertFails(setDoc(ref, { json: 'x'.repeat(2049), syncedAt: serverTimestamp() }))
      await assertSucceeds(setDoc(ref, { json: 'x'.repeat(2048), syncedAt: serverTimestamp() }))
    })
  })
}

test('a full batch of 400 entries is accepted (the first upload into a family)', async () => {
  await seedFamily(FAMILY, ANNA)
  const db = as(ANNA)
  const batch = writeBatch(db)
  for (let i = 0; i < 400; i++) batch.set(syncedDoc(db, FAMILY, 'entries', `e${i}`), synced('entries', `e${i}`))
  await assertSucceeds(batch.commit())
})

describe('the backup from before families (users/{uid}/entries)', () => {
  async function seedLegacy(uid, id) {
    await admin((db) => setDoc(legacyDoc(db, uid, id), { json: entryJson(id), syncedAt: Timestamp.now() }))
  }

  test('the owner reads, pages and deletes it', async () => {
    await seedLegacy(ANNA.uid, 'e1')
    await seedLegacy(ANNA.uid, 'e2')
    const db = as(ANNA)
    await assertSucceeds(getDoc(legacyDoc(db, ANNA.uid, 'e1')))
    const entries = collection(db, 'users', ANNA.uid, 'entries')
    await assertSucceeds(getDocs(query(entries, orderBy('syncedAt'), orderBy(documentId()), limit(500))))
    await assertSucceeds(deleteDoc(legacyDoc(db, ANNA.uid, 'e1')))
  })

  test('nobody writes it any more, not even the owner', async () => {
    await seedLegacy(ANNA.uid, 'e1')
    const db = as(ANNA)
    await assertFails(setDoc(legacyDoc(db, ANNA.uid, 'e2'), { json: entryJson('e2'), syncedAt: serverTimestamp() }))
    await assertFails(setDoc(legacyDoc(db, ANNA.uid, 'e1'), { json: entryJson('e1'), syncedAt: serverTimestamp() }))
  })

  test("nobody reads or deletes another account's", async () => {
    await seedLegacy(ANNA.uid, 'e1')
    await assertFails(getDoc(legacyDoc(as(BO), ANNA.uid, 'e1')))
    await assertFails(getDocs(collection(as(BO), 'users', ANNA.uid, 'entries')))
    await assertFails(deleteDoc(legacyDoc(as(BO), ANNA.uid, 'e1')))
    await assertFails(getDoc(legacyDoc(signedOut(), ANNA.uid, 'e1')))
  })
})

describe('deleting a family', () => {
  test("the last member deletes the family's entries, babies and family document as they leave", async () => {
    await seedFamily(FAMILY, ANNA)
    await seedSynced(FAMILY, 'entries', 'e1')
    await seedSynced(FAMILY, 'babies', 'default')
    const db = as(ANNA)
    await assertSucceeds(deleteDoc(syncedDoc(db, FAMILY, 'entries', 'e1')))
    await assertSucceeds(deleteDoc(syncedDoc(db, FAMILY, 'babies', 'default')))
    const batch = writeBatch(db)
    batch.delete(familyDoc(db, FAMILY))
    batch.delete(memberDoc(db, FAMILY, ANNA.uid))
    await assertSucceeds(batch.commit())
    await assertSucceeds(deleteDoc(userDoc(db, ANNA.uid)))
  })

  test('a member who stays deletes no family, and neither does anyone outside it', async () => {
    await seedFamily(FAMILY, ANNA)
    await seedFamily(OTHER_FAMILY, CAI)
    await assertFails(deleteDoc(familyDoc(as(ANNA), FAMILY)))
    await assertFails(deleteDoc(familyDoc(as(CAI), FAMILY)))
    await assertFails(deleteDoc(familyDoc(signedOut(), FAMILY)))
    const cai = as(CAI)
    const batch = writeBatch(cai)
    batch.delete(familyDoc(cai, FAMILY))
    batch.delete(memberDoc(cai, OTHER_FAMILY, CAI.uid))
    await assertFails(batch.commit())
  })
})

test('everything else is denied', async () => {
  const db = as(ANNA)
  await assertFails(setDoc(doc(db, 'users', ANNA.uid, 'babies', 'b1'), { name: 'Emma' }))
  await assertFails(setDoc(doc(db, 'families', FAMILY, 'settings', 'x'), {}))
  await assertFails(setDoc(doc(db, 'anything', 'x'), {}))
  await assertFails(getDocs(collection(db, 'anything')))
})
