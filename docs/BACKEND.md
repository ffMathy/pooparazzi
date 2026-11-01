# The backup backend

The phone app can back the log up to a small Firebase backend and share it with a **family**: the people who log for the same babies. Accounts are optional: without one, the app keeps everything on the phone and the watch, as before. How the app behaves is in [design/BABIES_AND_BACKUP.md](design/BABIES_AND_BACKUP.md); the privacy policy is [PRIVACY.md](PRIVACY.md); this page is the setup and the running of the backend.

| Part | Firebase product | What it holds |
|---|---|---|
| Accounts | Authentication: Google sign-in and **email link** (passwordless) sign-in. No passwords. | Email address (and, for Google accounts, the Google profile's name and picture link, unused) |
| Families | Cloud Firestore, `users/{uid}`, `families/{fid}` and its `members` | Which family an account is in; each member's email and join time |
| The shared log and babies | Cloud Firestore, `families/{fid}/entries` and `families/{fid}/babies` | Each log entry as JSON (times, kind, feed type and side, diaper details, the baby id) and each baby as JSON (id, **name**, last change), with the server time of the write |
| Invites | Cloud Firestore, `invites/{code}` | An unused invite to a family, for 6 days (the rules allow at most 7) |
| The website | Hosting, `https://<project-id>.web.app` (also served at `<project-id>.firebaseapp.com`) | The App Links file, the invite and sign-in landing pages, the privacy policy |

Baby **names are uploaded** now: a family shares its babies, names included, so every member's phone shows the same babies. Only the members of the family can read them (the rules below), and the privacy policy says so. Each device's *current* baby is still its own and never uploaded.

## The model

```
users/{uid}                         { familyId }                                the account's family
families/{fid}                      { createdBy, createdAt }                    created once, never deleted; createdBy handed over
families/{fid}/members/{uid}        { email, joinedAt, invite? }                who is in it
families/{fid}/entries/{entryId}    { json, syncedAt }                          the shared log (EntryJson, ≤ 2 KB)
families/{fid}/babies/{babyId}      { json, syncedAt }                          the shared babies: {id, name, updatedAtMillis}
invites/{code}                      { familyId, createdBy, expiresAt, email? }  code: 128 random bits, URL-safe base64 (22 chars)
users/{uid}/entries/{entryId}       { json, syncedAt }                          the backup from before families: read once, then deleted
```

- **Every account is in exactly one family.** The first sign-in of an account without `users/{uid}.familyId` creates its own family in one batch: the family, the creator's member document and the users document. A family of one is simply a backup.
- **Consent first.** Nothing is written to Firestore for an account (no family created or joined, no log, babies or email) until it has tapped "I agree" on the app's consent screen on that phone (BABIES_AND_BACKUP §3.6). Signing out withdraws it there.
- **Inviting.** A member creates an invite (expiring in 6 days, with their email so the invitee sees who asked) and shares `https://<project-id>.web.app/join/<code>`. The invitee opens it on a phone with the app (App Links, below), signs in if needed, confirms, and joins in one batch: their member document (naming the invite), their users document, and the invite deleted. An invite therefore lets exactly one account in, once.
- **Joining merges.** The joiner's phone keeps its log and babies and uploads them into the family; the family's log and babies come down. Every phone's first baby has the id `default`, so two members' first babies become one baby (usually the same child; rename or keep apart in the app).
- **Leaving** deletes your member document and starts a new, empty family of your own in the same batch; the phone uploads its log there. The family's **creator** may remove any member; a creator who leaves (or deletes the account) while others remain hands the family over to the member who joined first, in the same batch (`createdBy` is the only thing that ever changes in a family document). Entries a member logged stay in the family, and a member who leaves or is removed keeps what is already on their phone. Leaving (and joining another family, and deleting the account) also deletes the invites the account made on that phone: invites cannot be listed, so the phone remembers their codes.
- **The family document is never deleted.** The **last member** to leave (or delete the account) deletes the family's entries and babies, then their member document; the family document stays as a tombstone (`createdBy`, `createdAt`, nothing else), so its id can never be created again. Without that, anyone who knew the id (a removed member, say) could create the family again as its creator and take over whatever still pointed at it. Since the rules cannot count members and someone may join while the data is being deleted, the app reads the members again before every page it deletes and before its member document goes, and stops (just leaving, and handing over) as soon as anyone else is in.
- **Deleting the account** deletes the member document (as above), the outstanding invites made on that phone, the backup from before families (`users/{uid}/entries`) if any is left, and the users document, in that order, then the account.
- **Accounts from the first backup** (entries under `users/{uid}/entries`): the phone still has the whole log, so the app creates the family, uploads everything into it and then deletes the old documents.

## The rules

[`firebase/firestore.rules`](../firebase/firestore.rules), without Cloud Functions:

- **Membership** is the existence of `families/{fid}/members/{uid}`. Only members read the family, its members, entries and babies; only members write and delete entries and babies, and only in the shape `{ json: string ≤ 2 KB, syncedAt: the server time }`.
- **Verified email addresses only.** Creating a family, adding a member document and creating an invite all need `request.auth.token.email_verified == true` (Google, or an email link). The member list and invites show email addresses to others; an unverified one could be anybody's, since anyone can register any address with a password through Firebase's public API (the Email/Password provider is on for email links). And when an email link signs in to an account that existed before (perhaps registered that way by someone else), the app replaces any password on it with a random one nobody knows: the address is now verified, and that password must not sign in to it. A password change also makes Firebase revoke every other session's refresh token, so a session opened with that password ends too. (The provider itself stays linked, since email links use it.)
- **Families** are created only together with the creator's member document (and with `createdBy` = the caller, `createdAt` = the server time), only where no family document exists, and are **never deleted**, so a family id is used once. The only change is the hand-over: the current creator, while a member, sets `createdBy` (and nothing else) to someone who is a member once the batch is done.
- **Member documents** are created only by the account itself, with its own verified sign-in email and the server time, either as the creator of a family created in the same batch, or with an unexpired invite to that family that the same batch deletes, made by someone who is still a member of a family that exists. So the invites of a member who left or was removed stop working (a removed member cannot come back with an invite they made themselves), and so do those of a family everyone left. They are never changed; a member deletes their own (leave), the creator, while a member, deletes anyone's (remove).
- **`users/{uid}`** is read and written only by its account, holds only `familyId`, and may only name a family whose member document for that account exists after the write.
- **Invites** are created by a member of the family with a verified email, as themselves (`createdBy`, and `email` if given, must be the caller's), with `expiresAt` in the future and at most 7 days away, and a code of 22 to 64 URL-safe characters. Any signed-in account may read one by its code (the code is the secret); nobody can list them or change one. Deleted by their creator, by a member of their family, or by the invitee as they join.
- **The old backup** (`users/{uid}/entries`) is read and deleted only by its owner, and never written.
- Everything else is denied.

What the rules cannot enforce, the app does: deleting the family's entries and babies only as the last member (checking again before each step), handing the family over when its creator leaves, taking back its own invites, and asking for consent before anything is uploaded. Expired invites are deleted by the daily retention job (Retention, below, required).

Families deleted by the earlier version of these rules (before the tombstones) have no family document left; their ids are 120 random bits that nobody outside the family knew, their data was deleted with them, and their old invites no longer work (the inviter is no longer a member).

## Cost

Use the free **Spark** plan and do not add a billing account. A Spark project cannot be charged anything: when a daily quota runs out, it stops serving until the quota resets at midnight Pacific time. Reads are refused (the app says "Couldn't back up"); writes are not refused but held: Firestore on the phone keeps the batch and retries it. The app stops waiting after 30 seconds ("Saved on phone · will back up"), keeps the entries pending, and queues no second batch while that one is outstanding, so a used-up quota does not multiply the writes. The batch lands once the quota is back (or, if the app was closed meanwhile, the next sync sends the entries again).

| Spark quota (per project) | Free | Pooparazzi's use |
|---|---|---|
| Firestore document reads | 50 000 a day | about 2 per sync with nothing new (entries and babies; every push pulls first), plus 1 per changed entry or baby, **plus 1 per request for the membership check**; the daily retention job about 2 per account (its member and users documents) plus 1 per expired invite |
| Firestore document writes | 20 000 a day | 1 per logged, edited or deleted entry or changed baby; a few to create a family, invite or join |
| Firestore deletes | 20 000 a day | joining, leaving, account deletion |
| Firestore storage | 1 GiB | about 0.5 KB per entry: a year of logging is about 7 MB |
| Authentication: Google | no charge | – |
| Authentication: **email link sign-in emails** | **5 a day** for the whole project | 1 per "Email me a sign-in link" |
| Hosting | 10 GB stored, 360 MB a day transferred | a few KB per page view |

**The membership check costs one read per request.** Every rule that asks "is this account a member?" reads the member document (`exists()`); Firestore bills that read once per request, so a sync's page query or a batch of 400 uploads costs one extra read, not one per document. Joining reads the invite and the family once more.

**Email link sign-in is limited to 5 emails a day on Spark** (Firebase's limit since 2025; the Blaze plan allows 25 000). That is enough for a few families signing in on a new phone now and then, but it is shared by everyone: once used, "Email me a sign-in link" fails until the next day (the app says so), and Google sign-in still works. If more is needed, the project has to move to Blaze (with a budget alert), which this repository does not assume.

A family logging about 40 entries a day uses about 40 writes and well under 300 reads a day, so 50 families stay around a tenth of the free quota. The app never keeps a live listener on the backup, because Firestore bills a listener that reconnects after 30 minutes as a full re-read. It asks only for what changed since its last sync, when it starts, when it comes to the foreground (at most once a minute, but every time while the last sync failed) and a couple of seconds after something was logged (before uploading it, so it never overwrites a newer edit from another phone).

The one heavy moment is the **first sign-in, or joining a family, on a phone with a long history**: every entry is uploaded once (a year is about 15 000 writes). If that passes the day's write quota, the rest uploads by itself the next day.

## One-time setup

### 1. Create the project

1. Go to the [Firebase console](https://console.firebase.google.com/), click **Create a project** and name it, for example, Pooparazzi. Google Analytics is not needed (and the privacy policy promises none).
2. Keep the project on the **Spark** plan (the default). Do not upgrade it to Blaze.
3. Under Project settings › General, set the **public-facing name** (Pooparazzi). It appears in the sign-in email.
4. Under Project settings › **Privacy** (Data privacy), fill in the contact details and **accept the Firebase data processing terms** (the Data Processing and Security Terms). Google is then the processor of the data under the GDPR (GDPR checklist, below).

### 2. Turn on sign-in

1. Build › **Authentication** › Get started › Sign-in method:
   - **Google** › Enable, and choose a support email. One tap with the Google account already on the phone.
   - **Email/Password** › Enable, then turn on **Email link (passwordless sign-in)** in the same dialog and Save. Firebase needs the Email/Password provider on for email links; the app offers no password field, no password reset and no password sign-up. (If you set up an earlier version with passwords: existing password accounts keep working with an email link to the same address.)
2. Authentication › Settings › **Authorised domains**: `<project-id>.web.app` and `<project-id>.firebaseapp.com` must be listed (they are by default). Add any custom Hosting domain you put in front. `localhost` is not needed.
3. Authentication › Settings › User account management: keep **email enumeration protection** on (the default).
4. Optional: Authentication › Templates › Email address sign-in: the sender name and reply-to. The app sets the language of the email to the app's language.

The app sends the link with `ActionCodeSettings`: `url = https://<project-id>.web.app/signin`, `handleCodeInApp = true`, Android package `com.ffmathy.pooparazzi` (install if not available: true, so the link offers Google Play on a phone without the app), and `linkDomain = <project-id>.web.app`. The email then links to `https://<project-id>.web.app/__/auth/links?…`, a page Firebase Hosting serves by itself, which opens the app (App Links, below) or, without the app, the `/signin` page of this site.

### 3. Create the database and its rules

1. Build › **Firestore Database** › Create database, in **production mode**. Choose the location **`eur3` (Europe)**: the privacy policy says the data is stored in the EU. The location cannot be changed later; if your project uses another one, change [PRIVACY.md](PRIVACY.md) and [`firebase/hosting/privacy/index.html`](../firebase/hosting/privacy/index.html).
2. Deploy the rules and the website from this repository (next section).

Expired invites (which hold the inviter's email address) are deleted by the daily [Retention](#retention) job, within a day after they expire, as the privacy policy promises: invites cannot be listed by the app, and the phone that made one deletes it only when its maker leaves or deletes the account there. Firestore's own TTL policies would do it without the job, but they need billing enabled ("403: Project … has billing disabled" on Spark), so this project does not use them.

### 4. Deploy the rules and the website

Bun, Node.js 22 (for the Firebase CLI) and Java 21 needed:

```sh
cd firebase
bun install
bunx firebase login
bun run deploy -- --project <your-project-id>
```

That deploys the Firestore rules (and indexes) and the website in [`firebase/hosting/`](../firebase/hosting). Or, for the rules only, open Firestore › **Rules** in the console, paste the whole of [`firebase/firestore.rules`](../firebase/firestore.rules) and click Publish.

After that, the [`Deploy backend`](../.github/workflows/deploy.yml) workflow deploys both again on every merge to `main` that changes them, once the rules' tests pass. It needs a service account of its own, kept apart from the retention job's so neither can do the other's work:

1. Google Cloud console of the Firebase project › IAM & Admin › Service accounts › Create service account `deploy`, with the roles **Firebase Rules Admin**, **Firebase Hosting Admin**, **Cloud Datastore Index Admin**, **Firebase Viewer** and **Service Usage Consumer**. Keys › Add key › JSON.
2. Add the whole key file's content as the repository secret **`FIREBASE_DEPLOY_SERVICE_ACCOUNT`**, then delete the file. Without the secret the workflow does nothing and says so, and you deploy by hand as above.
3. Run it once by hand (Actions › Deploy backend › Run workflow) to check that the account may deploy: a missing role fails the run with the permission it lacked.

The website ([`firebase.json`](../firebase/firebase.json) › `hosting`), served at `https://<project-id>.web.app` and `https://<project-id>.firebaseapp.com`, is a few static pages with no scripts, no cookies and no external resources (`Content-Security-Policy: default-src 'none'`, `Referrer-Policy: no-referrer`, so an invite code never leaks to Google Play through the referrer). Those headers apply to the site's own paths only (`firebase.json` matches every path except those starting with `/__/`), so Firebase's reserved pages under `/__/` (the email link handler `/__/auth/links`, which runs scripts) keep their own:

| Path | File | What it is |
|---|---|---|
| `/.well-known/assetlinks.json` | [`hosting/.well-known/assetlinks.json`](../firebase/hosting/.well-known/assetlinks.json) | The App Links statement (next section) |
| `/join/<code>` | [`hosting/join/index.html`](../firebase/hosting/join/index.html) (every `/join/…` path) | Shown only where the app does not open the link: "open this on your Android phone with Pooparazzi installed", with a Google Play link (EN + DA) |
| `/signin` | [`hosting/signin/index.html`](../firebase/hosting/signin/index.html) (also `/signin/…`) | Where an email link lands without the app: the same advice for the sign-in link (EN + DA) |
| `/privacy` | [`hosting/privacy/index.html`](../firebase/hosting/privacy/index.html) | The privacy policy (in English), the same content as [PRIVACY.md](PRIVACY.md) |
| `/` | [`hosting/index.html`](../firebase/hosting/index.html) | A one-line description and the Play link |
| `/__/auth/links` | served by Firebase itself | The email sign-in link's handler |

### 5. App Links: fill in assetlinks.json

The invite links (`/join/<code>`) and the email sign-in links (`/__/auth/links`, `/signin`) open the app directly only once Android has **verified** that the site belongs to the app. Android checks `https://<host>/.well-known/assetlinks.json` for the package name and the **SHA-256** fingerprint of the certificate that signed the installed app. The file in this repository lists Pooparazzi's own two: the Play app signing key and the upload key. A copy of the project on another Play listing replaces them with its own (uppercase hex pairs separated by colons, `AB:CD:…`, 32 pairs) and deploys. A local debug key is not in the file, so locally built apps open the links through a chooser or the browser.

| Key | Which certificate | How to get its SHA-256 |
|---|---|---|
| Play app signing key | Google Play's **app signing key**, which signs every install from Play | Play Console › Test and release › **App integrity** › Play app signing › Settings › App signing key certificate › SHA-256 (or search Play Console for "app signing"). (The same page offers a ready "Digital Asset Links JSON" snippet.) |
| Upload key | The **upload key**, which signs the side-loadable APKs on GitHub Releases ([RELEASING.md](RELEASING.md)) | `keytool -list -v -keystore pooparazzi-upload.jks -alias upload` (the line "SHA256:"), or Play Console › App signing › Upload key certificate |
| (optional, add a line) | The public **side-load test key** that signs releases until the upload key is set up ([RELEASING.md](RELEASING.md)); anyone can sign with it, so leave it out once the upload key is in use | `keytool -list -v -keystore keystores/sideload.jks -alias sideload -storepass pooparazzi-sideload` |
| (optional, add a line) | Your **debug key**, for testing locally built apps (one per machine; leave it out of the deployed file) | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` |

Add the same **SHA-256** fingerprints (and the SHA-1s, for Google sign-in, section 6) to the Android app in Firebase Project settings: email link sign-in needs them too.

Check it after deploying:

- `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<project-id>.web.app&relation=delegate_permission/common.handle_all_urls` lists the app;
- on a phone: `adb shell pm verify-app-links --re-verify com.ffmathy.pooparazzi`, then `adb shell pm get-app-links com.ffmathy.pooparazzi` shows `verified` for both hosts.

The app's manifest declares `android:autoVerify="true"` intent filters for `https` on `<project-id>.web.app` and `<project-id>.firebaseapp.com`, paths `/join/` (prefix), `/__/auth/links` and `/signin`. Until verification succeeds, Android shows a chooser or opens the browser, which shows the landing page.

### 6. Connect the app

1. Project settings › General › Your apps › **Add app** › Android. Package name **`com.ffmathy.pooparazzi`**. Nickname: Pooparazzi.
2. Google sign-in only works for apps whose signing certificate is registered. Under the Android app › **Add fingerprint**, add the SHA-1 **and** the SHA-256 of:
   - every debug key you build with: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`;
   - the upload key, which signs the side-loadable APKs: `keytool -list -v -keystore pooparazzi-upload.jks -alias upload`;
   - Google Play's app signing key, which signs Play installs: Play Console › Test and release › App integrity › Play app signing › Settings.

   Without a matching fingerprint, the Google button fails and the email link still works.
3. Download **`google-services.json`** after enabling Google sign-in, because the file then carries the web client id the app needs. Download it again whenever you add fingerprints.
4. Local builds: put it at **`app/google-services.json`** (git ignores it). The phone build applies the Google services Gradle plugin only when that file exists, and takes the Hosting domain (`<project_id>.web.app`, for the invite and sign-in links) from its `project_id`. Without it, the app builds and runs as before, with accounts turned off and no Firebase code running: Settings says "Backup isn't set up in this build". With it but without the web client id (Google sign-in not enabled when the file was downloaded), only the email link is offered. The unit tests never start Firebase, with or without the file.
5. Release builds: add the whole file's content as the repository secret **`GOOGLE_SERVICES_JSON`** (Settings › Secrets and variables › Actions). [`release.yml`](../.github/workflows/release.yml) writes it to `app/google-services.json` before building ([RELEASING.md](RELEASING.md)). Pull request builds ([`ci.yml`](../.github/workflows/ci.yml)) do not need it.

The file is not a secret in the strict sense: its API key only identifies the project, and the rules decide what anyone can do. Keeping it out of the repository just keeps it out of search results.

Only the phone app uses Firebase. The watch app shares the applicationId but has no Firebase code or configuration.

## Who can sign up

Anyone with the app can create an account; a family is joined only with an invite from one of its members. That is fine for a small group: on the Spark plan nothing can cost money, and the worst a misbehaving client could do is use up the day's free quota. Firebase also limits account creation per IP address.

**Removing someone** (or answering an emailed deletion request): find their user id under Authentication › Users; Firestore › `users` › that id shows their `familyId`. Delete `families/<familyId>/members/<uid>` and `users/<uid>` (and `users/<uid>/entries` if it is still there); if they were the family's only member, delete its `entries` and `babies` but keep the `families/<familyId>` document itself (the tombstone: deleting it would let the id be created again); if they were its creator and others remain, set `createdBy` to the member who joined first. Then delete the user under Authentication › Users. People can also delete their own account in the app.

## What the app does

- **Signing in** (Google, or a link sent by email), then **"I agree"** on the consent screen, puts the account in its family (creating one the first time) and backs up everything the phone has: the whole log and the babies, including what came from the watch, merged with what the family already holds. Entries already in the backup are not uploaded again.
- **Restoring** is signing in on the new phone. The log and the babies, names included, come back, and the watch gets them from the phone. Each phone chooses its own current baby.
- **Families**: Settings › Family lists the members, invites someone (the share sheet with the link), leaves the family and, for the creator, removes members. Changing family (joining or leaving) uploads the whole phone's log and babies to the new family, like signing in to another account.
- **Signing out** keeps the log on the phone, and withdraws the consent on that phone. Nothing more is backed up until someone signs in (and agrees) again.
- **Deleting the account** (Settings › Account › Delete account) signs in again (Google, or a fresh email link opened while the confirmation is on screen: a cancelled confirmation forgets its link, and a deletion link that arrives without it asks again before deleting), leaves the family (deleting the family's entries and babies if no one else is in it, handing it over if others are), deletes its outstanding invites, the backup from before families and the users document, and then the account. The log on the phone stays.

## Retention

[`firebase/retention/`](../firebase/retention) deletes what the privacy policy (§6) says is not kept, and the [`Retention`](../.github/workflows/retention.yml) workflow runs it every day at 03:37 UTC:

- **Accounts unused for 2 years** (no sign-in and no token refresh, that is, the app not opened): each leaves its family exactly as "Delete account" does in the app (`FamilyExit`): with others left only its member document goes, and the family is handed over to the member who joined first if it was the creator; as the last member, the family's entries and babies go first and the family document stays as a tombstone. Then its invites, `users/{uid}` with the old backup under it, and the account itself.
- **Data of accounts that no longer exist** (deleted under Authentication › Users, say): the same, without the account.
- **Expired invites**: the only thing that removes the invites nobody used, which is why the job runs daily (the policy promises "within a day after it expires").

The privacy, compliance and breach documents are in [privacy/](privacy): the records of processing (art. 30), the impact assessment and the breach plan with its log.

One-time setup:

1. Google Cloud console of the Firebase project › IAM & Admin › Service accounts › Create service account `retention`, with the roles **Cloud Datastore User** and **Firebase Authentication Admin**. Keys › Add key › JSON.
2. Add the whole key file's content as the repository secret **`FIREBASE_SERVICE_ACCOUNT`**, then delete the file. Without the secret the workflow does nothing and says so, and expired invites stay.
3. Run the workflow by hand (Actions › Retention › Run workflow) with **dry run** on, and check the list it prints.
4. Once, after the first release that uploads deleted entries as tombstones without their content, run it with **dry run** off and **clear deleted entries** on: it rewrites the deleted entries that older versions backed up in full (in every family and in the old per-account backups). It reads every entry, so on the Spark plan keep it to a day with read quota to spare.

Run it locally with `GOOGLE_APPLICATION_CREDENTIALS=key.json FIREBASE_PROJECT_ID=<id> bun run retention -- --dry-run` in `firebase/`.

GitHub turns off a scheduled workflow once the repository has had no commits for 60 days (it emails the owner first). Re-enable it under Actions › Retention, or the deletions the privacy policy promises stop.

## GDPR and Google Play checklist

- [ ] Firestore location **`eur3`** (or the privacy policy changed to say where).
- [ ] Project settings › Privacy: **Firebase data processing terms accepted** and the controller's contact details filled in.
- [ ] No Google Analytics, Crashlytics or other SDKs added to the project or the app (the policy promises none).
- [ ] Hosting deployed, so **`https://<project-id>.web.app/privacy`** is live; the same URL in Play Console › App content › **Privacy policy** and in the store listing.
- [ ] Play Console › App content › **Data safety**: collected and stored by the developer, encrypted in transit, deletable by the user: **email address** (account management, app functionality); **app activity / other user-generated content**: the log entries and the babies' names (app functionality), **shared with other users** of the family at the user's request (not "shared with third parties" in Play's sense, since that is a user-initiated transfer); not used for ads or analytics. Health-related? The log is feeding, diaper and sleep data about a baby: declare it as **Health info** if Play asks.
- [ ] Data safety › Data deletion: in the app (Settings › Account › Delete account) and by email to **mathias.lorenzen@live.com** (the web address of the privacy policy explains both).
- [ ] Requests to the contact address answered within one month (access: export the family's documents from the console; erasure: "Removing someone" above).
- [ ] **Required: Retention**: the `FIREBASE_SERVICE_ACCOUNT` secret added, a dry run of the Retention workflow checked, and the workflow enabled (Retention, above); the privacy policy promises the daily deletion of expired invites and the 2-year deletion.
- [ ] When the data handled changes, update [PRIVACY.md](PRIVACY.md) and the `/privacy` page together, with a new date, and the records in [privacy/](privacy).

## Testing the rules

The rules have tests that run on the Firestore emulator, and the retention job on the Firestore and Authentication emulators (Bun, Node.js 22 for the Firebase CLI, and Java 21 needed):

```sh
cd firebase
bun install
bun run test
```

They cover every rule: creating a family (only atomically with the creator's member document, only as yourself, only with a verified email, never taking over another's, never again once everyone left), joining (a valid invite; refused when expired, of another family, missing, kept instead of deleted, used before, made by someone no longer a member, of a family everyone left, or without a verified email), handing the family over (only by its current creator, only to a member, only `createdBy`), the family document never being deleted, the probes of the security review (taking over a family by deleting and creating it again, a removed member rejoining with their own invite, stale invites, unverified addresses posing as someone else), member documents (own uid and email, server time, nothing else, never changed), leaving, the creator removing and others not, `users/{uid}` (own only, only `familyId`, only a family you are in), invites (members with a verified email only, at most 7 days, own name and email, code length and characters, get but no list, who may delete), the shape of entries and babies documents, non-members and signed-out clients, the old backup (read and delete only), the last member deleting the family's data but not the family, and that nothing else can be stored. Every guard was checked by weakening it and seeing a test fail; keep doing that when changing the rules. The retention tests (`test/retention.test.js`) check that an unused account leaves a shared family as the app does (handing it over) and wipes a family it was alone in, that the data of accounts deleted in the console and expired invites go, that a dry run deletes nothing, and that old tombstones lose their content. The [`Firestore rules`](../.github/workflows/rules.yml) workflow runs them on every pull request that changes `firebase/`.

To look at the website locally: `bunx firebase emulators:start --only hosting --project demo-pooparazzi` and open <http://127.0.0.1:5000/privacy>.
