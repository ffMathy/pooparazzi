# Records of processing activities (GDPR art. 30)

The controller's own record of what Pooparazzi's backend processes. The privacy policy ([../PRIVACY.md](../PRIVACY.md), and the hosted `/privacy` page) is the public side of it. Keep them in step, and review this record whenever the backend, the data or a processor changes.

Last reviewed: 29 October 2026.

| | |
|---|---|
| **Controller** | Mathias Lorenzen, Denmark · [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com) |
| **Data protection officer** | None appointed. Not required: no large-scale processing, and not a public authority (art. 37). |
| **EU representative** | Not needed: the controller is in the EU. |

## Processing: accounts, the backup and family sharing

| Item | Record |
|---|---|
| Purposes | Sign the parent in; back up the log and the babies and restore them on another phone; share them with the members of the parent's family; keep the service secure; delete what is no longer used. |
| Data subjects | Parents and carers with an account (and the members of their family); the babies whose log they keep. |
| Personal data | **Account** (Firebase Authentication): email address, user id, creation and last sign-in and token refresh times, sign-in IP addresses; for Google sign-in also the Google account's name and photo link (unused). **Family** (`families/{fid}`): the members' email addresses and join times, the creator's user id, invites (family, maker, maker's email, expiry). **Babies**: id and name. **Log**: per entry, kind, times, feed type and breast side, formula amount, diaper details, baby id, change time; a deleted entry only as a tombstone (id, kind, baby id, change time). **Pointer** `users/{uid}.familyId`. The consent is recorded on the phone. |
| Special category (art. 9) | The log is health data about children. |
| Legal basis | Consent (art. 6(1)(a)) for the account, backup and sharing; explicit consent (art. 9(2)(a)) for the baby's log, both on the app's consent screen before anything is uploaded. Security and retention: legitimate interest (art. 6(1)(f)). |
| Recipients | The other members of the parent's family, at the parent's choice. Google Ireland Limited / Google LLC (Firebase) as processor. No one else. |
| Transfers outside the EU/EEA | Firebase Authentication may process account data in the US: Standard Contractual Clauses in the Firebase data processing terms, and the EU–US Data Privacy Framework (Google LLC is certified). Firestore is in the EU (`eur3`). |
| Retention | Until the parent deletes it (entries, babies, leaving the family, deleting the account). Deleted entries: tombstones without content. Accounts unused for 2 years: deleted with their data, weekly (`firebase/retention/`). Data of accounts deleted otherwise: deleted weekly. Invites: deleted when used, by the TTL policy after expiry, and by the weekly job. A family everyone left: an empty tombstone document (id, last creator's uid, creation time). |
| Security (art. 32) | TLS in transit, encryption at rest (Google). Firestore rules: only members reach a family's data; joining needs a valid invite from a current member; verified emails for families and invites; fixed document shapes; tested in CI (`firebase/test/rules.test.js`). No passwords (Google or email link). No analytics or third-party SDKs. The retention job's service account key only as a GitHub Actions secret. |
| Impact assessment | [DPIA.md](DPIA.md). |
| Breaches | [BREACH_RESPONSE.md](BREACH_RESPONSE.md), with its log. |

## On the parent's own devices

The log, the babies and the settings on the phone and the watch, the Wearable Data Layer sync between them, the export file ("Download my data") and Android's own backup to the parent's Google account all stay under the parent's control and never reach the controller. They are listed for completeness and are not processing by the controller.
