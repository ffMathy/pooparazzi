# Data protection impact assessment (GDPR art. 35)

**Scope:** Pooparazzi's accounts, backup and family sharing (Firebase Authentication, Cloud Firestore, Firebase Hosting), as designed in [BABIES_AND_BACKUP.md](../design/BABIES_AND_BACKUP.md) and set up in [BACKEND.md](../BACKEND.md).
**Assessed:** 30 October 2026 by the controller (Mathias Lorenzen). **Review:** when the data, the backend or a processor changes, and at least every two years.

## Why an assessment

The backend stores health data (feeds, sleep, diapers) about children, together with their names, and shares it among family members. Two of the European Data Protection Board's criteria for a DPIA apply: special category data, and vulnerable data subjects. The scale is small (families on the free Spark plan), but the assessment documents the choices.

## The processing

| | |
|---|---|
| What | The log (kind, times, feed type and side, formula amount, diaper details), the babies (id, name), the family (members' emails, join times, creator, invites), the account (email, user id, sign-in metadata). |
| What not | Location, contacts, photos, device or advertising ids, analytics, free text beyond baby names. The rules refuse any other field or document. |
| Who | The parent who signs in; the members they invite; their babies. |
| Why | So the log survives a lost or reset phone, and so the people who care for the same babies see the same log. |
| How long | Until deleted by the parent; unused accounts after 2 years; deleted entries kept only as content-free tombstones. |
| Where | Firestore in the EU (`eur3`); Firebase Authentication possibly in the US. |

## Necessity and proportionality

- **Optional.** The app works fully without an account; the Welcome screen says the log then stays on the phone.
- **Consent first.** A separate consent screen names the data (the log, the babies' names, the email), who sees it, where it is kept and how to withdraw, with an explicit "I agree". Nothing is uploaded before it; signing in again asks again.
- **Minimisation.** Only what a restore and the sharing need. A deleted entry keeps only what the family's other phones need to learn of the deletion.
- **Rights built in.** Download my data (a ZIP file of JSON files, which Import data reads back), edit and delete entries, rename babies, leave the family, delete the account; email for the rest.
- **Storage limitation.** Delete account and leave family in the app; the daily retention job, which also deletes expired invites.

## Risks and measures

| Risk to the parents and children | Before measures | Measures | Residual |
|---|---|---|---|
| Someone outside the family reads a family's log | Possible · significant | Rules: membership checked on every request; joining only with a single-use, 6-day invite from a current member; tested in CI. | Low |
| An invite link reaches the wrong person | Possible · significant | The link is a 128-bit secret, works once, for 6 days, only while its maker is a member; every member sees who is in the family; the creator can remove members. | Low |
| A former member keeps data | Likely · limited | What reached their phone stays there (stated in the policy); they see nothing new. Unavoidable for an offline-first app. | Accepted |
| The log is linked to a named child | Certain within the family · significant | Names are shared only with the family the parent chose; nothing else about the child is stored. | Low |
| A leaked service account key | Unlikely · significant | Only in a GitHub Actions secret, used only by the retention workflow; rotate on exposure (BREACH_RESPONSE.md). | Low |
| Data kept after it is no longer wanted | Likely without measures · limited | Deletion in the app, tombstones without content, 2-year inactivity deletion, deletion of orphaned data and of expired invites, daily. | Low |
| Account data processed in the US | Certain · limited | Only account data, not the log; SCCs and the Data Privacy Framework; disclosed in the policy. | Low |
| Consent not informed | Possible · limited | Plain words on a separate screen, the policy one tap away, asked again on every new sign-in. | Low |

## Outcome

The residual risk is low, so prior consultation with Datatilsynet (art. 36) is not needed. The processing can go ahead with the measures above.
