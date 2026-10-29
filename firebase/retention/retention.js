// What Pooparazzi's backup keeps, and for how long (docs/PRIVACY.md, "How long"). Run by the Retention workflow
// (.github/workflows/retention.yml) with a service account, or locally against the emulators (test/retention.test.js).

/** An account nobody has used for this long is deleted, with its backup. */
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

/**
 * Deletes every account unused for [inactiveDays] (its backup first, then the account), and every backup whose account
 * no longer exists (deleted in the console). Returns what it deleted, or would delete with [dryRun].
 */
export async function purgeInactive({ auth, db, now = Date.now(), inactiveDays = INACTIVE_DAYS, dryRun = false, log = console.log }) {
  const cutoff = now - inactiveDays * DAY_MILLIS
  const known = new Set()
  const inactive = []
  for await (const user of allUsers(auth)) {
    known.add(user.uid)
    if (lastActiveMillis(user) < cutoff) inactive.push(user.uid)
  }
  // listDocuments also returns the users/{uid} documents that only have entries under them.
  const orphaned = (await db.collection('users').listDocuments()).map((ref) => ref.id).filter((uid) => !known.has(uid))

  for (const uid of inactive) {
    log(`${dryRun ? 'Would delete' : 'Deleting'} inactive account ${uid}`)
    if (dryRun) continue
    await db.recursiveDelete(db.collection('users').doc(uid))
    await auth.deleteUser(uid)
  }
  for (const uid of orphaned) {
    log(`${dryRun ? 'Would delete' : 'Deleting'} the backup of deleted account ${uid}`)
    if (!dryRun) await db.recursiveDelete(db.collection('users').doc(uid))
  }
  return { inactive, orphaned }
}

/** True when a deleted entry still carries what was logged (written by an app from before tombstones were cleared). */
export function carriesContent(entry) {
  if (!entry.deleted) return false
  return (entry.startMillis ?? 0) !== 0 || entry.endMillis != null || entry.feedType != null || entry.breastSide != null ||
    Boolean(entry.pee) || Boolean(entry.poop) || entry.poopColor != null || entry.poopTexture != null
}

/** A deleted entry as the app uploads it (Entry.forCloud in the app): what was logged cleared. */
export function redacted(entry) {
  const tombstone = { id: entry.id, kind: entry.kind, startMillis: 0, updatedAtMillis: entry.updatedAtMillis, deleted: true }
  if (entry.babyId != null) tombstone.babyId = entry.babyId
  return tombstone
}

/**
 * Clears what was logged from deleted entries backed up by app versions that uploaded it (one-off: it reads every
 * entry, so mind the day's read quota). Returns how many it rewrote, or would rewrite with [dryRun].
 */
export async function redactTombstones({ db, dryRun = false, FieldValue, log = console.log }) {
  let rewritten = 0
  for (const user of await db.collection('users').listDocuments()) {
    const entries = await user.collection('entries').get()
    let batch = db.batch()
    let inBatch = 0
    for (const doc of entries.docs) {
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
  }
  log(`${dryRun ? 'Would clear' : 'Cleared'} ${rewritten} deleted entries`)
  return rewritten
}
