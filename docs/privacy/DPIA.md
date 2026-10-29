# Data protection impact assessment (GDPR art. 35)

**Scope:** Pooparazzi's optional backup (Firebase Authentication and Cloud Firestore), as designed in [BABIES_AND_BACKUP.md](../design/BABIES_AND_BACKUP.md) and set up in [BACKEND.md](../BACKEND.md).
**Assessed:** 29 October 2026, by the controller (Mathias Lorenzen). **Review:** when the data, the backend or a processor changes, and at least every two years.

## Why an assessment

The backup stores health data (feeds, sleep, diapers) about children, who are vulnerable data subjects. Two of the European Data Protection Board's criteria for a DPIA apply: special category data, and vulnerable data subjects. The volume is small (a family's log, a small group of users on the free Spark plan), but an assessment costs little and documents the choices.

## The processing

| | |
|---|---|
| What | An entry per feed, sleep or diaper: kind, times, feed type and breast side, diaper details, a random baby id. Account email, and a password hash or Google id. The consent version and time. |
| What not | Babies' names, settings, free text, location, contacts, device identifiers, analytics. The rules refuse any other field or document. |
| Who | The parent who creates an account, about their own child. |
| Why | So the log survives a lost or reset phone, and can be restored on a new one. |
| How long | Until the parent deletes it; accounts unused for 2 years are deleted automatically. |
| Where | Firestore in the EU (`eur3`). Firebase Authentication, possibly in the US. |

## Necessity and proportionality

- **Optional and off by default.** The app works fully without an account; the Welcome screen says the log then stays on the phone.
- **Explicit consent** before anything is uploaded (the security rules refuse entries until the account's consent record exists): a separate, unticked checkbox naming the data as health information, with a link to the policy (BABIES_AND_BACKUP §3.4). Withdrawing is as easy as deleting the account, which is in the app.
- **Minimisation.** Only what a restore needs. No names: the baby is a random id. A deleted entry keeps only what other phones need to learn of the deletion.
- **Rights built in.** Download my data (JSON), edit, delete entries, delete the account; email for everything else.
- **Storage limitation.** The weekly retention job (`.github/workflows/retention.yml`).

## Risks and measures

| Risk to the parent and child | Likelihood · severity before | Measures | Residual |
|---|---|---|---|
| Someone reads another family's log | Possible · significant | Security rules allow each account only its own documents (tested on every change); Firebase Authentication; no admin UI shared with anyone. | Low |
| The log is linked to a named child | Possible · significant | Names never leave the parent's devices; the rules accept no other field; the backup holds no free text. The email address can still identify the parent. | Low |
| A leaked or abused service account key | Unlikely · significant | Kept only as a GitHub Actions secret, used only by the retention workflow; rotate it if exposed (BREACH_RESPONSE.md). | Low |
| Data kept after it is no longer wanted | Likely without measures · limited | Delete account in the app; tombstones without content; 2-year inactivity deletion; deletion of orphaned backups. | Low |
| Transfer of account data to the US | Certain · limited | Only account data (not the log); EU–US Data Privacy Framework and SCCs; disclosed in the policy. | Low |
| Consent given without understanding | Possible · limited | The words name the data, the place and how to withdraw; the policy is one tap away; a new consent version asks again. | Low |
| Loss of the log (availability) | Possible · limited | The backup itself is the measure; the phone keeps the log when the backup fails. | Low |

## Outcome

The residual risk is low, so prior consultation with Datatilsynet (art. 36) is not needed. The processing can go ahead with the measures above.
