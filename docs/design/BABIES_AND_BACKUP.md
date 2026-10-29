# Several babies and the optional backup

This document adds two things to [DESIGN_SPEC.md](DESIGN_SPEC.md) and [V1_ADDITIONS.md](V1_ADDITIONS.md), and wins where they differ:

1. **Several babies** (twins, siblings): the log is kept per baby, each device has a *current* baby that it shows and logs for, and babies can be added and renamed.
2. **An optional account** on the phone that backs the log up to a small Firebase backend, restores it on a new phone and shares it with a **family**: the people (two parents, a grandparent) who log for the same babies. Without an account everything works as before, on the phone and the watch only.

The backend setup (Firebase project, rules, website, cost, GDPR) is in [../BACKEND.md](../BACKEND.md); the privacy policy is [../PRIVACY.md](../PRIVACY.md).

## 1. Principles

- **Names are shared only with the family.** A baby's name is stored on the phone and synced to the paired watch over the Wearable Data Layer, as before. With an account it is also stored in the family's backup, where only the family's members can read it (BACKEND.md › The rules), so every member's phone shows the same babies. Without an account it never leaves the phone and the watch. Entries carry a baby id (a UUID; the first baby's id is `default`).
- **No account is needed.** The parent can use the app for ever without one. They are told, once and then in Settings, that the log then lives only on this phone (and the watch) and is lost with the phone, and that they can create an account at any time, which backs up everything logged so far.
- **A family is the unit of sharing.** Every account is in exactly one family (at first, a family of one: a plain backup). Members are added only by a single-use invite link that a member shares; every member sees the family's whole log and its babies, and who the other members are.
- **No passwords.** Sign-in is Google or a link sent by email (Firebase email link sign-in); there is nothing to remember or leak.
- **Private by design.** No ads, no analytics, no tracking. The privacy policy ([../PRIVACY.md](../PRIVACY.md), also at `https://<project-id>.web.app/privacy`) is linked from Settings › About and next to sign-in.
- **Cheap by construction.** The backend is Firebase Authentication, Cloud Firestore and Hosting on the free Spark plan, which has no billing account and so cannot charge, and no Cloud Functions: the security rules do all the checking. The app reads only what changed since its last sync and never keeps a listener open over the whole log (§4.4).
- **The phone is the hub.** Only the phone talks to the backend. The watch keeps syncing with the phone over the Data Layer; entries logged on the watch reach the backend through the phone, and entries restored from the backend reach the watch through the phone.

## 2. Babies

### 2.1 Model

```
Baby(id: String, name: String = "", updatedAtMillis: Long = 0)  // synced with the family (§4.1), last writer wins per baby
Settings(
    babies: List<Baby> = [Baby("default")],  // synced phone → watch inside /settings, in order; phone ↔ family backup
    appearance, language, updatedAtMillis,   // as before
    selectedBabyId: String = "default",      // this device's current baby: @Transient, never synced
)
Settings.selectedBaby  = the baby with selectedBabyId, else the first
Settings.babyName      = selectedBaby.name   // computed: every existing reader keeps working
Entry.babyId: String = "default"             // new field; missing in older JSON → "default"
```

- There is always at least one baby. Ids are unique; the order is the order they were added. At most **8** babies.
- **Migration.** An install from before this change has one baby, `default`, with the stored name. Its entries get `babyId = "default"`. Settings JSON from an older device (`babyName`, no `babies`) is read as one `default` baby with that name. Both devices derive the same id, so nothing diverges.
- **The current baby is per device.** The phone and the watch each choose theirs (Home's switcher, the watch's picker). It is never synced, neither to the watch nor to the family. On the phone ↔ watch side the babies list stays single-writer (only the phone edits it), so the settings' last-writer-wins merge cannot lose a rename.
- **Babies sync through the family.** Each baby carries its own `updatedAtMillis`, bumped when it is added or renamed on this phone, which marks it pending for the backup. Pulled babies merge into the phone's settings per baby (the newer `updatedAtMillis` wins, the name with it; babies the phone lacks are added in the family's order) and reach the watch through the existing `/settings` item. So two parents renaming the same baby end with the later name on both phones; two different babies are never merged by name, only by id.
- **Unknown babies are adopted (phone only).** When entries arrive with a baby id that the phone's settings do not know (before that baby's document arrives, an Android restore of the database), the phone adds an unnamed baby with that id, in order of its first entry; its name follows once the family's baby document is pulled. The watch never adds babies: it learns them from the phone's settings.

### 2.2 Labels

A baby's label is its name; an unnamed baby is "Baby" when it is the only one and "Baby 2", "Baby 3" … (its position) when there are several (DA "Baby", "Baby 2"). Labels are shown in sentence position as-is. With **one baby** every surface looks exactly as before; the switcher and the extra name lines below appear only with **two or more**.

### 2.3 What is per baby

Everything in the log: Home, History, Statistics, the edit sheet, the widgets, the tile and the complications show and log for the device's current baby. The rules of V1_ADDITIONS §1 apply **per baby**:

- one running sleep **per baby** (twins can sleep at the same time);
- the 5 s dedupe and the cross-device duplicate rule compare entries of the **same baby** only (two diapers changed within seconds of each other on twins are two entries);
- an edit never moves an entry to another baby; Add past entry adds to the current baby.

Two things span all babies, because a parent must not lose sight of a sleeping baby when switching to the other:

- **The phone's running-sleep notification** shows every baby that is asleep, one notification each ("Noa is asleep", "Ida is asleep"), each with its own "Woke up". Each keeps the content of DESIGN_SPEC §3.3.7 for its own baby (how long the sleep has run, that baby's last feed and diaper and today's counts), and "Woke up" leaves that baby's own woke-up notification with its Earlier offsets; other babies' notifications stay.
- **Auto appearance** dims for naps while *any* baby is asleep.

### 2.4 Phone UI

- **Settings › Babies** (replaces Settings › Baby). One name field per baby (label "Name", placeholder its label), saved as typed as before (500 ms after the last keystroke, at once on Done). Under them an "Add a baby" pill (hidden at 8), and a hint about where the names go: without an account "Names stay on this phone and your watch."; signed in "Names are shared with your family and kept in your backup." Adding does not switch the current baby. While a paired watch runs a Pooparazzi from before several babies, the pill is disabled with "Update Pooparazzi on your watch first." under it: such a watch ignores baby ids and resolves conflicts across all babies (it would soft-delete one twin's running sleep, or a diaper changed seconds after the other twin's, as a duplicate). Both apps advertise the Wearable capability `pooparazzi_babies` beside `pooparazzi_tracker`; a node with the second but not the first is such a watch. With one baby nothing else changes.
- **Home's top bar** (two or more babies). The name becomes a 48 dp button with a 16 dp `chevronDown`; it opens the **Switch baby** sheet: one 56 dp radio row per baby (its label), the current one selected. A tap selects that baby, plays SegmentTick and closes the sheet. Switching closes any open card (its Undo belongs to the other baby) and resets the double-toggle guard. In lights-down the button follows the name's colours.
- **History** shows the current baby's label under its title when there are two or more.
- **Statistics** (reached from History) averages the current baby's log only, and shows the same label under its title when there are two or more.
- **Widgets.** They log for the phone's current baby, and their Feed and Diaper buttons open the app's sheets for that baby. The 4×2 widget's status row starts with the baby's label when there are two or more; every widget (the 4×2, the 3×1 and the cover-screen widget) names the baby in its TalkBack descriptions. A receipt belongs to the baby it was made for and is dropped when the current baby changes.

### 2.5 Watch UI

- With two or more babies the curved time text reads "Noa · 14:32" on every screen, so the parent always sees which baby a tap logs for.
- The main screen has a **baby** compact button (after the hero) that opens the **baby picker**: one button per baby, the current one marked; a tap selects it and returns to the main screen. Selecting clears any open draft or receipt and resets the double-toggle guard.
- The tile and the complications follow the watch's current baby; the tile's idle title shows its label.

## 3. Account and family

### 3.1 First run

The first time the phone app opens with accounts available and nobody signed in (also after updating to this version), a **Welcome** screen explains the choice before Home:

> **Your log stays on this phone**
> Without an account, everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it.
> You can create an account at any time in Settings. Everything logged until then is backed up as soon as you do, and you can share it with your family.
> [Continue with Google]
> — or — Email · [Email me a sign-in link]
> By continuing you agree to the privacy policy. (a link)
> [Continue without an account]

The sign-in choices are the Account screen's (§3.3). Signing in, or "Continue without an account" (Back counts as that), answers the Welcome, and a widget, notification or invite link that opened the app is followed after it; the answer is remembered on this phone only (excluded from backups and device transfers), and a phone that is already signed in never shows it. Builds without a Firebase configuration never show it, and neither do the unit tests (Firebase never starts in them).

### 3.2 Settings › Account

The first Settings section. One row that opens the **Account** screen, whose summary says where the log is:

| State | Row |
|---|---|
| Not signed in | "Not signed in" · "Your log is only on this phone. Sign in to back it up." |
| Backing up | email · "Backing up…" |
| Backed up | email · "Backed up · 14:31" |
| Offline | email · "Saved on phone · will back up" |
| Error | email · "Couldn't back up · Tap to retry" (a tap also backs up again) |
| Backup deleted, account not (§3.3) | email · "Backup deleted · Back up now to start again" (a tap opens the Account screen only) |
| No Firebase configuration in this build | (not tappable) "Backup isn't set up in this build. Everything is saved on this phone." |

While signed in, Home's and History's top bars also show the backup as a small **cloud icon** next to the watch icon, with no text (DESIGN_SPEC §4.5): a tick when backed up, an arrow while backing up, a slash offline, "!" in error colour for an error (a 48 dp button that backs up again) and for a deleted backup (not a button). TalkBack reads the row's status line ("Backed up · 14:31"). Signed out, or in a build without Firebase, there is no cloud icon.

Settings › About gains a **Privacy policy** row that opens `https://<project-id>.web.app/privacy` in the browser, and under it **Download my data** · "The whole log and the babies' names as a JSON file" (GDPR art. 15, 20). It asks where to save `pooparazzi-log-<date>.json` (the system's file picker) and then says "Saved 214 entries" or "Couldn't save the file. Try again." in a toast. The file (`LogExport`) holds `format: "pooparazzi-log"`, `version: 1`, `exportedAtMillis`, the babies with their names, and every entry on the phone that is not deleted, of every baby, oldest first, in the backup's JSON shape. It works with or without an account. What only the backend holds (the account, the family's member list) is given by email on request (PRIVACY.md §8).

### 3.3 Account screen and signing in

- **Signed out:** the explanation of §3.1, "Continue with Google" first (one tap), then "or": an email field and **"Email me a sign-in link"**. There are no passwords: no password field, no "Forgot password?", no Create account (the first sign-in creates the account). Under the buttons: "By continuing you agree to the privacy policy", with the link.
- **The email link.** The app sends Firebase's sign-in email (`sendSignInLinkToEmail`, in the app's language) and remembers the address on this phone; the screen says "Check your email" and "We sent a sign-in link to <address>. Tap the link in it on this phone." with "Send again" and "Use another email". Tapping the link opens the app through an App Link (`https://<project-id>.web.app/__/auth/links…`, or `/signin…`; BACKEND.md › App Links), which completes the sign-in (`isSignInWithEmailLink` → `signInWithEmailLink`). A link opened on a phone that did not ask for it (no remembered address) first asks for the email address. Without the app, the link lands on the website's `/signin` page, which says to open it on the phone. Errors in words: "That link has expired or was already used. Send a new one.", "That email address doesn't look right", "Too many sign-in emails today. Try again tomorrow, or use Google." (Firebase's QUOTA_EXCEEDED, or "too many requests", for sending the link) (the free plan sends few a day: BACKEND.md › Cost), "No connection. Try again." A call without an answer within 30 s says "No answer yet. It may still go through in a moment.": Firebase keeps trying, and should the sign-in go through after all, the screen follows the account.
- **After signing in** comes the consent screen (§3.6); once the account has agreed on this phone, its first sign-in creates its family (§4.1) and the backup starts. There is no invitation and no email confirmation step beyond the link itself. When an email link signs in to an account that existed before (Firebase reports it is not new), the app replaces any password on the account with a random one (which also ends every other session): the app has no passwords, so any password on it was set by someone who registered the address before its owner (BACKEND.md › The rules).
- **Signed in:** the address, the backup status line, "Back up now", "Sign out", and "Delete account…". That opens a "Delete account?" confirmation in place on the screen (Back cancels it): it says what is deleted (the account, your membership, and, if you are the family's only member, the family's backup) and that the log on this phone stays, then asks to sign in again — for a Google account "Delete", which opens Google's account picker; for an email account "Email me a link", whose link then completes the deletion while the confirmation is open. Cancelling forgets the link, so tapping it later deletes nothing; a deletion link that opens the app while the confirmation is closed (the app was restarted, say) opens it again with "This link deletes your account. Tap Delete to go ahead." and "Delete", which uses that link. Nothing is ever deleted without the confirmation on screen. It leaves the family (deleting its entries and babies first when no one else is in it, and handing the family over to the member who joined first when you started it and others remain; the family document itself always stays, BACKEND.md › The model), deletes the invites it made on this phone that are still outstanding, the backup from before families and the users document, and then the account. Partial failures are said honestly: if the backup cannot be deleted, the account is kept and it says so (the next sync completes the backup again); if the backup was deleted but the account was not, it says exactly that, and trying again finishes the job. Until then nothing is uploaded for the account, also after the app restarts (the phone remembers it), and the status line says "Backup deleted · Back up now to start again"; "Back up now", or signing out or in to another account, ends that. Closing Google's picker is not an error and says nothing. Account deletion inside the app is a Google Play requirement.
- Signing out keeps the log on the phone, and withdraws the consent on this phone (§3.6). Signing in to an account merges the phone's log and babies with the family's (nothing is lost on either side) and uploads the phone's.

### 3.4 Settings › Family

A Settings section under Account, shown while signed in:

- **The members**, one row each (you first, then in the order they joined): their email, and under it "You" on your own row and "Started the family" on the creator's ("You · Started the family" when both). With one member the section says "Only you so far. Invite someone to share the log and the babies."
- **"Invite someone"** creates an invite (single use, valid 6 days, a day short of the rules' 7-day limit so a phone clock running ahead of the server's never breaks it) and opens the Android share sheet with the link `https://<project-id>.web.app/join/<code>` and a short text: "Join my family on Pooparazzi to share our baby log: <link> (works once, for 6 days)" (DA "Bliv en del af min familie i Pooparazzi, så vi deler babyloggen: <link> (virker én gang og i 6 dage)"). The phone remembers the code, so leaving the family, joining another or deleting the account deletes the invites still outstanding (invites cannot be listed). An invite works only while its maker is still a member. A refused invite (removed meanwhile, or an unverified email address) says "Couldn't create an invite. You may have been removed from the family, or your email address isn't verified yet."
- **"Leave family"** (with two or more members) asks first: "Leave the family? You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own." On yes, your membership is deleted, a new empty family of your own is created (one batch, which also hands the old family over to the member who joined first when you started it) and the phone uploads its log and babies there.
- **"Remove"** on each other member's row, for the family's creator only, asks first ("Remove <email>? They'll stop seeing the family's log from now on. What's already on their phone stays there.") and deletes their membership. Their invites stop working with it. A removed member's phone finds itself outside the family when it next reads its family (at start, whenever the app comes to the foreground, and when a family action is refused) and starts a family of its own, like leaving.

### 3.5 Opening an invite

An invite link opens the app (App Link). Signed out, the app first asks to sign in (the Welcome or Account screen, with a line saying "Sign in to join the family."), then for the consent (§3.6) if the account has not agreed on this phone, then continues. The app reads the invite and asks, on a screen of its own: **"Join <inviter's email>'s family?** Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too." and "Your first baby and the family's first baby become one baby." [Join] [Not now]. After joining: "You're in the family. Your log and babies are being shared." [Done].

- **Join** creates your membership with the invite, points your account at the family and deletes the invite, in one batch; then the sync engine switches family (§4.5). You then leave your old family as when deleting the account: its data is deleted if you were its last member, and it is handed over if you started it and others remain.
- **Joining merges.** Babies match by id, and every phone's first baby has the id `default`, so the joiner's first baby and the family's first baby become **one baby** (usually the same child, which is the point). Its name is the one changed last. A family with a different first child (two sets of parents sharing a grandparent's phone, say) should give the second child a new baby (Settings › Babies › Add a baby) before joining; the entries of a merged first baby cannot be told apart afterwards.
- **Refusals in words:** expired or already used: "This invite has expired or was already used. Ask for a new one."; not found: "This invite link isn't valid."; already a member: "You're already in this family."; offline: "No connection. Try again."
- Without the app, the link opens the website's `/join/` page (EN + DA): "Open this link on your Android phone with Pooparazzi installed", with a Google Play link.

### 3.6 Consent before the first upload

Signing in only creates the account. Before anything is uploaded to the backend (a family created or joined, the log, the babies' names, the email address in the member list), a signed-in account that has not agreed on this phone sees a full screen of its own, before everything else (after the Welcome, before Home, the Account screen or a join confirmation; links wait behind it):

- **"Back up to Google Firebase?"** "You're signed in as <email>. Nothing has been uploaded yet. Before anything is, please agree to this:"
- **What is uploaded:** "Your log (feeds, diapers and sleep), your babies' names and your email address. A log like this can say something about your baby's health."
- **Who sees it:** "You and the members of your family: everyone in it sees the family's log, the babies' names and the members' email addresses."
- **Where:** "Google Firebase. The database is in the EU; Google processes it for us and uses it for nothing else."
- **Changing your mind:** "Sign out to upload nothing more, leave the family, or delete your account (Settings › Account) to delete what is stored."
- "Details are in the privacy policy." (a link), then **"I agree"** (filled) and **"Not now, sign out"** (text; Back does the same).

The answer is kept per account on this phone (device-local, not in Android backup), so a new phone asks again; signing out (or deleting the account) withdraws it there. Until then nothing is read from or written to Firestore for the account and the backup is off. This is the explicit consent of GDPR Art. 6(1)(a) and 9(2)(a) the privacy policy rests on (PRIVACY.md §3).

## 4. Sync with the backend

### 4.1 Data

```
users/{uid}                         { familyId }                                the account's family
families/{fid}                      { createdBy, createdAt }                    never deleted (a tombstone once everyone left)
families/{fid}/members/{uid}        { email, joinedAt, invite? }
families/{fid}/entries/{entryId}    { json: EntryJson of the entry (≤ 2 KB), syncedAt: server time of the write }
families/{fid}/babies/{babyId}      { json: {id, name, updatedAtMillis} (≤ 2 KB), syncedAt: server time of the write }
invites/{code}                      { familyId, createdBy, expiresAt, email? }
users/{uid}/entries/{entryId}       the backup from before families: read once to migrate, then deleted
```

A **deleted** entry is uploaded as a tombstone (`Entry.forCloud`): its id, kind, baby id, `updatedAtMillis` and `deleted: true`, with `startMillis` 0 and every detail (amount included) cleared, so what was logged does not stay in the backup once it is deleted (PRIVACY.md §2, §6). The phone that deleted it keeps its own copy until it is purged (7 days); the family's other phones merge the tombstone. Accounts unused for 2 years, the data of accounts deleted otherwise, and expired invites are deleted by the weekly retention job (BACKEND.md › Retention).

No settings and no current baby are stored. The rules ([`firebase/firestore.rules`](../../firebase/firestore.rules), summarised in BACKEND.md) let only a family's members read and write its entries and babies, in exactly that shape, and let accounts with a verified email join only with a valid invite from a current member. A family id is a random id, used once (the family document is never deleted); an invite code is 128 random bits in URL-safe base64 without padding (22 characters).

- **First sign-in** of an account without `users/{uid}.familyId`: one batch creates `families/{fid}` (`createdBy`, `createdAt` = server time), the creator's member document (`email` = the account's email, `joinedAt` = server time) and `users/{uid}`.
- **An account from the first backup** (documents under `users/{uid}/entries`): the first time a phone resolves the account's family, it first pulls those documents into its log (merged like any pull, so a new phone restores them too), and only then hands the family to the backup, whose first sync for the family flags every row and baby and uploads them. Once a sync of the family has finished after that, the old documents are deleted. The family is cached per account on the phone, so a restart shows it at once instead of "Backing up…" until the server answers.

### 4.2 Pending uploads

Each local row has a second pending flag beside the Data Layer's `pendingSync`: **`cloudPending`**, true while the backend lacks the row's current version.

| Write | `pendingSync` (watch) | `cloudPending` (backend) |
|---|---|---|
| Local write on this device | true | true while an account is signed in, else false |
| Merged from the other device (Data Layer) | false | true while an account is signed in, else false |
| Merged from the backend | true | false |
| A row identical to the backend's version | unchanged | cleared |

Babies have the same flag: a baby added or renamed on this phone (its `updatedAtMillis` bumped) is pending until its document is uploaded; a baby merged from the family is not.

A soft-deleted row is purged only when both flags are clear. Without an account nothing is flagged, so deletes are purged as before; signing out clears every flag. The first time a device syncs for a **family** (a different family than before: another account, joining, leaving or being removed, or after signing out), every row and every baby is marked `cloudPending` and the cursors are reset: that is the "everything logged so far is backed up". The bookkeeping (cursors, the first full upload) is keyed by the family id, not the account. The download that runs first starts at the beginning of the backup and clears the flag on rows the backend already holds unchanged, so signing in again on the same phone uploads only differences.

### 4.3 A sync

One at a time, never throwing, and always a pull before a push:

1. Signed out → status Off. Read `users/{uid}.familyId` (creating the family on the first sign-in, migrating the first backup, §4.1); a family whose member document for this account is gone (removed) counts as leaving (§3.4).
2. **Pull:** babies first, then entries, each from its own collection with its own cursor: page through `families/{fid}/babies` and `families/{fid}/entries` ordered by (`syncedAt`, document id), starting after the stored cursor, 500 per page. Merge each page into the database (last-writer-wins, per-baby conflict rules, forwarded to the watch), then store the cursor of its last document. A document that does not decode (or whose entry id is not its document id) is skipped; one without a server time yet (this phone's own write still in flight) is skipped too, and the cursor stops before it so it is read once it has one.
   Pulled babies merge into the settings per baby (§2.1).
3. **Push:** upload pending babies, then the current version of `cloudPending` rows in batches of up to 400 writes, each with `syncedAt` set by the server; clear the flag of each row that was not written again meanwhile. An upload overwrites the document, so pulling first matters: a newer version another phone backed up is merged (and wins) before this phone sends its own.
4. Status Backed up (time), or Offline / Error. The app stops waiting for a backend call after 30 s and counts it as Offline, so a phone without a connection never waits on a sync. Stopping to wait does not cancel a write: Firestore keeps the batch queued in memory and sends it once it can (offline, or with the day's write quota used up, that can take hours). So at most one batch is ever queued: the next upload, and deleting the backup, first wait for it (again at most 30 s, else Offline) instead of queueing the same rows again. The rows stay pending meanwhile, so a batch that lands late is sent once more at the next sync (one extra write per entry).

### 4.4 When it runs (and what it costs)

- At app start, when an account signs in, when the phone app comes to the foreground (at most once a minute), and from "Back up now".
- A sync about 2 s after any local write or watch merge leaves rows pending. It pulls first like every sync: one extra read per push when nothing else changed.
- No snapshot listener: Firestore bills a re-attached listener as a full re-read after 30 minutes away. A pull with nothing new costs one read per collection (babies, entries); each changed entry or baby costs one read; each uploaded entry or baby one write; and every request costs one more read for the rules' membership check.

For a family logging about 40 entries a day this is roughly 40 writes and well under 300 reads a day, against Spark's free 20 000 writes and 50 000 reads a day. Fifty such families use about 10 % of the free quota. A first sign-in with a year of history (about 15 000 entries) uploads in one go and may hit that day's write quota; the rest then follows the next day, automatically.

### 4.5 Changing family

Joining, leaving and being removed change the family the phone syncs, like signing in to another account: the cursors are reset, every local row and baby is marked for upload to the new family, and the next sync pulls the new family's log and babies from the beginning and merges them (nothing on the phone is lost). The old family keeps what was uploaded to it. Each phone keeps its own current baby throughout.
