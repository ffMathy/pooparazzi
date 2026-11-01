// bun retention/run.js [--dry-run] [--redact-tombstones]
// Credentials: GOOGLE_APPLICATION_CREDENTIALS (a service account key file), as the Retention workflow sets it.
import { initializeApp, applicationDefault } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { FieldValue, getFirestore } from 'firebase-admin/firestore'
import { purgeInactive, redactTombstones } from './retention.js'

const dryRun = process.argv.includes('--dry-run')
const app = initializeApp({ credential: applicationDefault(), projectId: process.env.FIREBASE_PROJECT_ID })
const auth = getAuth(app)
const db = getFirestore(app)

const { inactive, orphaned, expiredInvites } = await purgeInactive({ auth, db, dryRun })
console.log(`${inactive.length} inactive accounts, ${orphaned.length} deleted accounts with data left, ${expiredInvites} expired invites`)
if (process.argv.includes('--redact-tombstones')) await redactTombstones({ db, dryRun, FieldValue })
