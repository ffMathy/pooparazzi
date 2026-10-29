// What Pooparazzi's backup keeps, and for how long (docs/PRIVACY.md §6). Run by the Retention workflow
// (.github/workflows/retention.yml) with a service account, or locally against the emulators (test/retention.test.js).
//
// It takes accounts out of their family exactly as the app does when an account is deleted (FamilyExit in the app):
// with others left, only the member document goes (the family handed over to the member who joined first when the
// account created it); as the last member, the family's entries and babies go first. The family document stays as a
// tombstone.

/** An account nobody has used for this long is deleted, with its part of the backup. */
export const INACTIVE_DAYS = 730

const DAY_MILLIS = 24 * 60 * 60 * 1000

/** When the account was last used: its last token refresh (the app in use), sign-in, or creation, in epoch ms. */
export function lastActiveMillis(user) {
  const times = [user.metadata.lastRefreshTime, user.metadata.lastSignInTime, user.metadata.creationTime]
    .filter(Boolean)
    .map((time) => Date.parse(time))
    .filter((millis) => !Number.isNaN(millis))
  return times.length ? Math.max(...times) : 0
}

/** Every Authentication account, a page at a time. */
async function* allUsers(auth) {
  let pageToken
  do {
    const page = await auth.listUsers(1000, pageToken)
    yield* page.users
    pageToken = page.pageToken
  } while (pageToken)
}

/** Who takes a family over from [uid]: the member who joined first, as the app's FamilyExit.successor picks. */
export function successor(uid, members) {
  const others = members.filter((m) => m.uid !== uid)
  others.sort((a, b) => a.joinedAtMillis - b.joinedAtMillis || (a.uid < b.uid ? -1 : a.uid > b.uid ? 1 : 0))
  return others[0]?.uid ?? null
}

/** Takes [uid] out of family [fid]; the last member's leaving deletes the family's entries and babies. */
export async function leaveFamily(db, fid, uid) {
  const family = db.collection('families').doc(fid)
  const members = (await family.collection('members').get()).docs.map((doc) => ({
    uid: doc.id,
    joinedAtMillis: doc.get('joinedAt')?.toMillis?.() ?? 0,
  }))
  if (!members.some((m) => m.uid === uid)) return
  const batch = db.batch()
  if (members.some((m) => m.uid !== uid)) {
    const familyDoc = await family.get()
    if (familyDoc.exists && familyDoc.get('createdBy') === uid) batch.update(family, { createdBy: successor(uid, members) })
  } else {
    await db.recursiveDelete(family.collection('entries'))
    await db.recursiveDelete(family.collection('babies'))
  }
  batch.delete(family.collection('members').doc(uid))
  await batch.commit()
}

/** Deletes everything of account [uid] in the database: its family membership, its invites, its pointer and old backup. */
export async function removeAccountData(db, uid) {
  const user = db.collection('users').doc(uid)
  const familyId = (await user.get()).get('familyId')
  if (typeof familyId === 'string') await leaveFamily(db, familyId, uid)
  // A membership the pointer does not name (a family left halfway, say).
  for (const member of (await db.collectionGroup('members').get()).docs) {
    if (member.id === uid) await leaveFamily(db, member.ref.parent.parent.id, uid)
  }
  const invites = await db.collection('invites').where('createdBy', '==', uid).get()
  await Promise.all(invites.docs.map((doc) => doc.ref.delete()))
  // users/{uid} with the backup from before families (users/{uid}/entries).
  await db.recursiveDelete(user)
}

/**
 * Deletes every account unused for [inactiveDays] (its data first, then the account), the data of accounts that no
 * longer exist (deleted in the console), and expired invites. Returns what it deleted, or would delete with [dryRun].
 */
export async function purgeInactive({ auth, db, now = Date.now(), inactiveDays = INACTIVE_DAYS, dryRun = false, log = console.log }) {
  const cutoff = now - inactiveDays * DAY_MILLIS
  const known = new Set()
  const inactive = []
  for await (const user of allUsers(auth)) {
    known.add(user.uid)
    if (lastActiveMillis(user) < cutoff) inactive.push(user.uid)
  }
  // listDocuments also returns users/{uid} documents that only have an old backup under them.
  const referenced = new Set((await db.collection('users').listDocuments()).map((ref) => ref.id))
  for (const member of (await db.collectionGroup('members').get()).docs) referenced.add(member.id)
  const orphaned = [...referenced].filter((uid) => !known.has(uid)).sort()
  const expired = (await db.collection('invites').where('expiresAt', '<', new Date(now)).get()).docs

  const verb = dryRun ? 'Would delete' : 'Deleting'
  for (const uid of inactive) {
    log(`${verb} inactive account ${uid}`)
    if (dryRun) continue
    await removeAccountData(db, uid)
    await auth.deleteUser(uid)
  }
  for (const uid of orphaned) {
    log(`${verb} the data of deleted account ${uid}`)
    if (!dryRun) await removeAccountData(db, uid)
  }
  log(`${verb} ${expired.length} expired invites`)
  if (!dryRun) await Promise.all(expired.map((doc) => doc.ref.delete()))
  return { inactive, orphaned, expiredInvites: expired.length }
}

/** True when a deleted entry still carries what was logged (written by an app from before tombstones were cleared). */
export function carriesContent(entry) {
  if (!entry.deleted) return false
  return (entry.startMillis ?? 0) !== 0 || entry.endMillis != null || entry.feedType != null || entry.breastSide != null ||
    Boolean(entry.pee) || Boolean(entry.poop) || entry.poopColor != null || entry.poopTexture != null || entry.amountMl != null
}

/** A deleted entry as the app uploads it (Entry.forCloud in the app): what was logged cleared. */
export function redacted(entry) {
  const tombstone = { id: entry.id, kind: entry.kind, startMillis: 0, updatedAtMillis: entry.updatedAtMillis, deleted: true }
  if (entry.babyId != null) tombstone.babyId = entry.babyId
  return tombstone
}

/**
 * Clears what was logged from deleted entries backed up by app versions that uploaded it, in every family and in the
 * old per-account backups (one-off: it reads every entry, so mind the day's read quota). Returns how many it rewrote,
 * or would rewrite with [dryRun].
 */
export async function redactTombstones({ db, dryRun = false, FieldValue, log = console.log }) {
  let rewritten = 0
  let batch = db.batch()
  let inBatch = 0
  for (const doc of (await db.collectionGroup('entries').get()).docs) {
    let entry
    try {
      entry = JSON.parse(doc.get('json'))
    } catch {
      continue
    }
    if (!carriesContent(entry)) continue
    rewritten++
    if (dryRun) continue
    batch.set(doc.ref, { json: JSON.stringify(redacted(entry)), syncedAt: FieldValue.serverTimestamp() })
    if (++inBatch === 400) {
      await batch.commit()
      batch = db.batch()
      inBatch = 0
    }
  }
  if (inBatch > 0) await batch.commit()
  log(`${dryRun ? 'Would clear' : 'Cleared'} ${rewritten} deleted entries`)
  return rewritten
}
