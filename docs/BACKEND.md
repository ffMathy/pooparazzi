# The backup backend

The phone app can back the log up to a small Firebase backend. Accounts are optional: without one, the app keeps everything on the phone and the watch, as before. How the app behaves is in [design/BABIES_AND_BACKUP.md](design/BABIES_AND_BACKUP.md); this page is the setup and the running of the backend.

| Part | Firebase product | What it holds |
|---|---|---|
| Accounts | Authentication: Google sign-in, and email and password | Email address (and the password hash for email accounts) |
| The backup | Cloud Firestore, `users/{uid}/entries/{entryId}` | Each log entry as JSON (times, kind, feed type and side, diaper details, an opaque baby id) and the server time it was written |

Baby names are **never uploaded**: they stay on the phone and the watch. The security rules ([`firebase/firestore.rules`](../firebase/firestore.rules)) refuse every other document, so there is nowhere for a name to go.

## Cost

Use the free **Spark** plan and do not add a billing account. A Spark project cannot be charged anything: when a daily quota runs out, requests fail until the quota resets at midnight Pacific time, and the app keeps the entries pending and sends them later.

| Spark quota (per project) | Free | Pooparazzi's use |
|---|---|---|
| Firestore document reads | 50 000 a day | about 1 per sync with nothing new, plus 1 per changed entry |
| Firestore document writes | 20 000 a day | 1 per logged, edited or deleted entry |
| Firestore deletes | 20 000 a day | only when an account is deleted |
| Firestore storage | 1 GiB | about 0.5 KB per entry: a year of logging is about 7 MB |
| Authentication (Google, email and password) | no charge | – |

A family logging about 40 entries a day uses about 40 writes and well under 200 reads a day, so 50 accounts stay around a tenth of the free quota. The app never keeps a live listener on the backup, because Firestore bills a listener that reconnects after 30 minutes as a full re-read. It asks only for what changed since its last sync, when it starts, when it comes to the foreground (at most once a minute) and a couple of seconds after something was logged.

The one heavy moment is the **first sign-in on a phone with a long history**: every entry is uploaded once (a year is about 15 000 writes). If that passes the day's write quota, the rest uploads by itself the next day.

## One-time setup

### 1. Create the project

1. Go to the [Firebase console](https://console.firebase.google.com/), click **Create a project** and name it, for example, Pooparazzi. Google Analytics is not needed.
2. Keep the project on the **Spark** plan (the default). Do not upgrade it to Blaze.
3. Under Project settings › General, set the **public-facing name** (Pooparazzi). It appears in the password reset email.

### 2. Turn on sign-in

1. Build › **Authentication** › Get started › Sign-in method:
   - **Google** › Enable, and choose a support email. This is the main way in: one tap with the Google account already on the phone, no password.
   - **Email/Password** › Enable, for people who prefer an email address. Leave "Email link (passwordless sign-in)" off.
2. Optional: Authentication › Templates. The app sends the **password reset** email from there. Choose its language (Danish is available) and wording.

### 3. Create the database and its rules

1. Build › **Firestore Database** › Create database, in **production mode**. Pick a location near the users, for example `eur3` (Europe). The location cannot be changed later.
2. Deploy the rules from this repository (Node.js 22 and Java 21 needed):

   ```sh
   cd firebase
   npm install
   npx firebase login
   npx firebase deploy --only firestore --project <your-project-id>
   ```

   Or open Firestore › **Rules** in the console, paste the whole of [`firebase/firestore.rules`](../firebase/firestore.rules) and click Publish.

### 4. Connect the app

1. Project settings › General › Your apps › **Add app** › Android. Package name **`com.ffmathy.pooparazzi`**. Nickname: Pooparazzi.
2. Google sign-in only works for apps whose signing certificate is registered. Under the Android app › **Add fingerprint**, add the SHA-1 of:
   - every debug key you build with: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`;
   - the upload key, which signs the side-loadable APKs: `keytool -list -v -keystore pooparazzi-upload.jks -alias upload`;
   - Google Play's app signing key, which signs Play installs: Play Console › Test and release › Setup › App signing.

   Without a matching fingerprint, the Google button fails and email and password still work.
3. Download **`google-services.json`** after enabling Google sign-in, because the file then carries the web client id the app needs. Download it again whenever you add fingerprints.
4. Local builds: put it at **`app/google-services.json`** (git ignores it). The phone build applies the Google services Gradle plugin only when that file exists. Without it, the app builds and runs as before, with accounts turned off and no Firebase code running: Settings says "Backup isn't set up in this build". With it but without the web client id (Google sign-in not enabled when the file was downloaded), only email and password are offered. The unit tests never start Firebase, with or without the file.
5. Release builds: add the whole file's content as the repository secret **`GOOGLE_SERVICES_JSON`** (Settings › Secrets and variables › Actions). [`release.yml`](../.github/workflows/release.yml) writes it to `app/google-services.json` before building ([RELEASING.md](RELEASING.md)). Pull request builds ([`ci.yml`](../.github/workflows/ci.yml)) do not need it.

The file is not a secret in the strict sense: its API key only identifies the project, and the rules decide what anyone can do. Keeping it out of the repository just keeps it out of search results.

Only the phone app uses Firebase. The watch app shares the applicationId but has no Firebase code or configuration.

## Who can sign up

Anyone with the app can create an account; there are no invitations. That is fine for a small group: on the Spark plan nothing can cost money, and the worst a misbehaving client could do is use up the day's free quota. Firebase also limits account creation per IP address.

**Removing someone:** delete their user under Authentication › Users, and their data under Firestore › `users` › their user id (shown in Authentication › Users). People can also delete their own account and backup in the app.

## What the app does

- **Signing up or in** backs up everything the phone has: the whole log, including what came from the watch, with the other device's entries merged in. Entries already in the backup are not uploaded again.
- **Restoring** is signing in on the new phone. The log comes back and the watch gets it from the phone. Baby names do not come back, because they were never uploaded. The babies appear as "Baby 1", "Baby 2" and can be renamed in Settings › Babies.
- **Signing out** keeps the log on the phone. Nothing more is backed up until someone signs in again.
- **Deleting the account** (Settings › Account › Delete account) deletes the backup and the account after the person signs in again: their password, or Google. The log on the phone stays.

## Google Play

An app that lets people create an account must also let them delete it (in the app: Settings › Account › Delete account) and must name a way to request deletion outside the app, for example an email address, in Play Console › App content › **Data safety** › Data deletion. The Data safety form should declare the **email address** (account management) and the logged entries (app functionality), both stored by the developer and encrypted in transit. A privacy policy URL is needed as soon as accounts collect an email address.

## Testing the rules

The rules have tests that run on the Firestore emulator (Node.js 22 and Java 21 needed):

```sh
cd firebase
npm install
npm test
```

They check that nobody reads or writes another account's log (or anything while signed out), the shape of an entry document (JSON of at most 2 KB, the server's write time), and that nothing else can be stored. The [`Firestore rules`](../.github/workflows/rules.yml) workflow runs them on every pull request that changes `firebase/`.
