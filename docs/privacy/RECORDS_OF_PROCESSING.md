# Records of processing activities (GDPR art. 30)

The controller's own record of what Pooparazzi's backup processes. The public side of it is the privacy policy ([../PRIVACY.md](../PRIVACY.md)). Keep both in step, and review this record whenever the backend, the data or a processor changes.

Last reviewed: 29 October 2026.

| | |
|---|---|
| **Controller** | Mathias Lorenzen, Denmark · [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com) |
| **Data protection officer** | None appointed. Not required: no large-scale processing, and no public authority (art. 37). |
| **Representative in the EU** | Not needed: the controller is in the EU. |

## Processing: the optional backup

| Item | Record |
|---|---|
| Purposes | Back up the log and restore it on another phone; run the account; keep the service secure; delete accounts that are no longer used. |
| Categories of data subjects | Parents and carers who create an account; the babies whose log they keep. |
| Categories of personal data | Account: email address, password hash (email accounts), Google account id (Google sign-in), account creation and last-use times, sign-in IP addresses (Firebase Authentication). Log: entry kind, times, feed type and breast side, diaper details, a random baby id, change times. Consent: version and time. **Special category (art. 9):** the log is health data about a child. Names are never processed by the backend. |
| Legal basis | Log: explicit consent (art. 9(2)(a), 6(1)(a)), recorded on the phone and in `users/{uid}`. Account: contract (art. 6(1)(b)). Security and retention: legitimate interest (art. 6(1)(f)). Consent record: legal obligation (art. 6(1)(c), 7(1)). |
| Recipients | Google Ireland Limited / Google LLC (Firebase) as processor. No other recipients. |
| Transfers to third countries | Firebase Authentication may process account data in the US: EU–US Data Privacy Framework (Google LLC is certified) and the Standard Contractual Clauses in the Firebase data processing terms. Firestore data is stored in the EU (`eur3`). |
| Retention | Entries: until the parent deletes them (then a tombstone without content) or the account. Account and backup: until deleted by the parent, or after 2 years without use (`firebase/retention/`, weekly). Backups without an account: deleted weekly. |
| Security measures (art. 32) | TLS in transit; encryption at rest (Google); Firestore security rules allowing each account only its own documents in a fixed shape, and no entry before its consent record (tested in CI, `firebase/test/rules.test.js`); no names or free text accepted; no analytics or third-party SDKs; the service account key for the retention job only in a GitHub Actions secret; deleted entries uploaded without content. |
| Impact assessment | [DPIA.md](DPIA.md). |

## Processing on the parent's own devices

The log, the names and the settings on the phone and the watch, and the Data Layer sync between them, stay under the parent's control on their own devices, and the controller never receives them. Android's own backup to the parent's Google account runs under the parent's Google account (the names are excluded from it, `app/src/main/res/xml/data_extraction_rules.xml`). This is listed for completeness. It is not processing by the controller.
