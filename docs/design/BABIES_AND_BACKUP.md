# Several babies and the optional backup

This document adds two things to [DESIGN_SPEC.md](DESIGN_SPEC.md) and [V1_ADDITIONS.md](V1_ADDITIONS.md), and wins where they differ:

1. **Several babies** (twins, siblings): the log is kept per baby, each device has a *current* baby that it shows and logs for, and babies can be added and renamed.
2. **An optional account** on the phone that backs the log up to a small Firebase backend and restores it on a new phone. Without an account everything works as before, on the phone and the watch only.

The backend setup (Firebase project, rules, cost) is in [../BACKEND.md](../BACKEND.md).

## 1. Principles

- **Names never leave the parent's own devices.** A baby's name is stored on the phone and synced to the paired watch over the Wearable Data Layer, as before. It is never uploaded to the backend, and the settings file that holds it is excluded from Android's cloud backup. Entries carry an opaque baby id (a UUID; the first baby's id is `default`).
- **No account is needed.** The parent can use the app for ever without one. They are told, once and then in Settings, that the log then lives only on this phone (and the watch) and is lost with the phone, and that they can create an account at any time, which backs up everything logged so far.
- **Cheap by construction.** The backend is Firebase Authentication (Google sign-in, or email and password) and Cloud Firestore on the free Spark plan, which has no billing account and so cannot charge. The app reads only what changed since its last sync and never keeps a listener open over the whole log (§4.4).
- **The phone is the hub.** Only the phone talks to the backend. The watch keeps syncing with the phone over the Data Layer; entries logged on the watch reach the backend through the phone, and entries restored from the backend reach the watch through the phone.

## 2. Babies

### 2.1 Model

```
Baby(id: String, name: String = "")          // name: local only, never uploaded
Settings(
    babies: List<Baby> = [Baby("default")],  // synced phone → watch inside /settings, in order
    appearance, language, updatedAtMillis,   // as before
    selectedBabyId: String = "default",      // this device's current baby: @Transient, never synced
)
Settings.selectedBaby  = the baby with selectedBabyId, else the first
Settings.babyName      = selectedBaby.name   // computed: every existing reader keeps working
Entry.babyId: String = "default"             // new field; missing in older JSON → "default"
```

- There is always at least one baby. Ids are unique; the order is the order they were added. At most **8** babies.
- **Migration.** An install from before this change has one baby, `default`, with the stored name. Its entries get `babyId = "default"`. Settings JSON from an older device (`babyName`, no `babies`) is read as one `default` baby with that name. Both devices derive the same id, so nothing diverges.
- **The current baby is per device.** The phone and the watch each choose theirs (Home's switcher, the watch's picker). It is never synced. This keeps the babies list single-writer (only the phone edits it), so the settings' last-writer-wins merge cannot lose a rename.
- **Unknown babies are adopted (phone only).** When entries arrive with a baby id that the phone's settings do not know (a restore from the backend, an Android restore of the database, another phone on the account), the phone adds an unnamed baby with that id, in order of its first entry. The watch never adds babies: it learns them from the phone's settings.

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

- **Settings › Babies** (replaces Settings › Baby). One name field per baby (label "Name", placeholder its label), saved as typed as before (500 ms after the last keystroke, at once on Done). Under them an "Add a baby" pill (hidden at 8), and the hint "Names stay on this phone and your watch. They're never uploaded." Adding does not switch the current baby. While a paired watch runs a Pooparazzi from before several babies, the pill is disabled with "Update Pooparazzi on your watch first." under it: such a watch ignores baby ids and resolves conflicts across all babies (it would soft-delete one twin's running sleep, or a diaper changed seconds after the other twin's, as a duplicate). Both apps advertise the Wearable capability `pooparazzi_babies` beside `pooparazzi_tracker`; a node with the second but not the first is such a watch. With one baby nothing else changes.
- **Home's top bar** (two or more babies). The name becomes a 48 dp button with a 16 dp `chevronDown`; it opens the **Switch baby** sheet: one 56 dp radio row per baby (its label), the current one selected. A tap selects that baby, plays SegmentTick and closes the sheet. Switching closes any open card (its Undo belongs to the other baby) and resets the double-toggle guard. In lights-down the button follows the name's colours.
- **History** shows the current baby's label under its title when there are two or more.
- **Statistics** (reached from History) averages the current baby's log only, and shows the same label under its title when there are two or more.
- **Widgets.** They log for the phone's current baby, and their Feed and Diaper buttons open the app's sheets for that baby. The 4×2 widget's status row starts with the baby's label when there are two or more; every widget (the 4×2, the 3×1 and the cover-screen widget) names the baby in its TalkBack descriptions. A receipt belongs to the baby it was made for and is dropped when the current baby changes.

### 2.5 Watch UI

- With two or more babies the curved time text reads "Noa · 14:32" on every screen, so the parent always sees which baby a tap logs for.
- The main screen has a **baby** compact button (after the hero) that opens the **baby picker**: one button per baby, the current one marked; a tap selects it and returns to the main screen. Selecting clears any open draft or receipt and resets the double-toggle guard.
- The tile and the complications follow the watch's current baby; the tile's idle title shows its label.

## 3. Account

### 3.1 First run

The first time the phone app opens with accounts available and nobody signed in (also after updating to this version), a **Welcome** screen explains the choice before Home:

> **Your log stays on this phone**
> Without an account, everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it.
> You can create an account at any time in Settings. Everything logged until then is backed up as soon as you do.
> [Continue with Google]
> — or use email — [Sign in | Create account] · Email · Password · [Sign in] · Forgot password?
> [Continue without an account]

The sign-in choices are the Account screen's (§3.3). Signing in, or "Continue without an account" (Back counts as that), answers the Welcome, and a widget or notification link that opened the app is followed after it; the answer is remembered on this phone only (excluded from backups and device transfers), and a phone that is already signed in never shows it. Builds without a Firebase configuration never show it, and neither do the unit tests (Firebase never starts in them).

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

### 3.3 Account screen

- **Signed out:** the explanation of §3.1, "Continue with Google" first (one tap, no password), then "or use email": a Sign in / Create account choice, email and password fields, the main button, "Forgot password?" (sign in; sends a reset link to the typed address), and errors in words ("Wrong email or password", "That email already has an account", "Use at least 6 characters", "No connection. Try again."). A call without an answer within 30 s says "No answer yet. It may still go through in a moment.": Firebase keeps trying, and should the sign-in go through after all, the screen follows the account (signed in, the error gone).
- **Create account** creates the account and signs in; the backup starts at once. There is no invitation and no email confirmation step.
- **Signed in:** the address, the backup status line, "Back up now", "Sign out", and "Delete account…". That opens a "Delete account?" confirmation in place on the screen (not a separate dialog window, so the password field behaves like the rest of the screen; Back cancels it): it says what is deleted and that the log on this phone stays, then asks to sign in again — the password, or for a Google account just "Delete", which opens Google's account picker. It then deletes the backup and the account. If the backup cannot be deleted, the account is kept and it says so (the next sync completes the backup again); if the backup was deleted but the account was not, it says exactly that, and trying again finishes the job. Until then nothing is uploaded for the account, also after the app restarts (the phone remembers it), and the status line says "Backup deleted · Back up now to start again"; "Back up now", or signing out or in to another account, ends that. Closing Google's picker is not an error and says nothing. Account deletion inside the app is a Google Play requirement.
- Signing out keeps the log on the phone. Signing in to an account merges the phone's log with the account's backup (nothing is lost on either side) and uploads the phone's entries.

## 4. Sync with the backend

### 4.1 Data

```
users/{uid}/entries/{entryId}           { json: EntryJson of the entry (≤ 2 KB), syncedAt: server time of the write }
```

Nothing else is stored: no settings, no babies, no names. The rules (`firebase/firestore.rules`) let a signed-in account read, write and delete only its own entries, in exactly that shape.

### 4.2 Pending uploads

Each local row has a second pending flag beside the Data Layer's `pendingSync`: **`cloudPending`**, true while the backend lacks the row's current version.

| Write | `pendingSync` (watch) | `cloudPending` (backend) |
|---|---|---|
| Local write on this device | true | true while an account is signed in, else false |
| Merged from the other device (Data Layer) | false | true while an account is signed in, else false |
| Merged from the backend | true | false |
| A row identical to the backend's version | unchanged | cleared |

A soft-deleted row is purged only when both flags are clear. Without an account nothing is flagged, so deletes are purged as before; signing out clears every flag. The first time a device syncs for an account (for a different account than before, or after signing out), every row is marked `cloudPending`: that is the "everything logged so far is backed up". The download that runs first starts at the beginning of the backup and clears the flag on rows the backend already holds unchanged, so signing in again on the same phone uploads only differences.

### 4.3 A sync

One at a time, never throwing, and always a pull before a push:

1. Signed out → status Off.
2. **Pull:** page through `users/{uid}/entries` ordered by (`syncedAt`, document id), starting after the stored cursor, 500 per page. Merge each page into the database (last-writer-wins, per-baby conflict rules, forwarded to the watch), then store the cursor of its last document. A document that does not decode (or whose entry id is not its document id) is skipped; one without a server time yet (this phone's own write still in flight) is skipped too, and the cursor stops before it so it is read once it has one.
3. **Push:** upload the current version of `cloudPending` rows in batches of up to 400 writes, each with `syncedAt` set by the server; clear the flag of each row that was not written again meanwhile. An upload overwrites the document, so pulling first matters: a newer version another phone backed up is merged (and wins) before this phone sends its own.
4. Status Backed up (time), or Offline / Error. The app stops waiting for a backend call after 30 s and counts it as Offline, so a phone without a connection never waits on a sync. Stopping to wait does not cancel a write: Firestore keeps the batch queued in memory and sends it once it can (offline, or with the day's write quota used up, that can take hours). So at most one batch is ever queued: the next upload, and deleting the backup, first wait for it (again at most 30 s, else Offline) instead of queueing the same rows again. The rows stay pending meanwhile, so a batch that lands late is sent once more at the next sync (one extra write per entry).

### 4.4 When it runs (and what it costs)

- At app start, when an account signs in, when the phone app comes to the foreground (at most once a minute), and from "Back up now".
- A sync about 2 s after any local write or watch merge leaves rows pending. It pulls first like every sync: one extra read per push when nothing else changed.
- No snapshot listener: Firestore bills a re-attached listener as a full re-read after 30 minutes away. A pull with nothing new costs one read; each changed entry costs one read; each uploaded entry one write.

For a family logging about 40 entries a day this is roughly 40 writes and well under 200 reads a day, against Spark's free 20 000 writes and 50 000 reads a day. Fifty such accounts use about 10 % of the free quota. A first sign-in with a year of history (about 15 000 entries) uploads in one go and may hit that day's write quota; the rest then follows the next day, automatically.
