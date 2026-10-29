# Releasing Pooparazzi

Three GitHub Actions workflows build, ship and check the apps:

| Workflow | When | What it does |
|---|---|---|
| [`ci.yml`](../.github/workflows/ci.yml) | Every pull request (and by hand) | Unit tests of `:core`, `:app` and `:wear`, and debug builds of both apps. Needs no secrets. |
| [`release.yml`](../.github/workflows/release.yml) | Every push to `main` (a merged pull request), and by hand | Unit tests, signed release **app bundles** (AAB) and **APKs** of the phone and the watch app, a **GitHub Release** with both APKs for side-loading, and an upload of both bundles to Google Play. The bundles and R8 mapping files are also kept as a workflow artifact. |
| [`rules.yml`](../.github/workflows/rules.yml) | Pull requests that change `firebase/` (and by hand) | The Firestore security rules' tests on the Firestore emulator ([BACKEND.md](BACKEND.md#testing-the-rules)). Needs no secrets. |

The phone app and the Wear OS app are one Play listing: both use the applicationId **`com.ffmathy.pooparazzi`** (the Wearable Data Layer only pairs apps with the same applicationId and signing key). The phone bundle goes to the phone track (`internal` by default) and the watch bundle to the matching **Wear OS form-factor track** (`wear:internal`), which is where Play expects Wear OS releases.

## Version codes

Every release run uses one version **base**, `github.run_number + VERSION_CODE_OFFSET` (offset 100 unless the repository variable says otherwise), and passes it to Gradle as `-Ppooparazzi.versionCode=<base>`:

- phone: `versionCode = base * 10`
- watch: `versionCode = base * 10 + 1`

so the codes rise with every run and the two bundles under the one package never clash. Local builds without the properties use the defaults in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) (base 1: phone 10, watch 11, named `pooparazziVersionName`).

Re-running a workflow run reuses its run number, so Play rejects the second upload of the same codes; push a new commit or start the workflow by hand instead. If the run numbers ever go backwards (for example after the workflow is renamed), raise `VERSION_CODE_OFFSET` above the highest base already on Play.

## Version names

Every release run bumps the version name and tags its GitHub Release `v<versionName>` (for example `v0.2.0`), so the tags are the record of what was released. [`scripts/next_version.py`](../scripts/next_version.py) picks the name from the highest release tag on the repository (older `v<name>-<base>` tags such as `v0.1.0-116` count too) and passes it to Gradle as `-Ppooparazzi.versionName=<name>`:

| Bump | When | Example |
|---|---|---|
| patch | Every merge to `main`, unless its pull request has a label below | 0.2.3 → 0.2.4 |
| minor | The merged pull request has the label `release: minor` | 0.2.3 → 0.3.0 |
| major | The merged pull request has the label `release: major` | 0.2.3 → 1.0.0 |

A run started by hand (Actions › Release › Run workflow) bumps the part chosen in its "Which part of the version to bump" input instead. Create the two labels once (Issues › Labels › New label, or `gh label create "release: minor"` and `gh label create "release: major"`).

`pooparazziVersionName` in `libs.versions.toml` is a floor: when it is higher than every tag, the next release uses it exactly, without a bump. Raise it in a pull request to jump to a version by hand (say `1.0.0` for the first production release). With no release tags yet, the first release is named after it.

## Signing

Release builds read the upload key from Gradle properties or environment variables:

| Gradle property | Environment variable | Value |
|---|---|---|
| `pooparazzi.upload.storeFile` | `POOPARAZZI_UPLOAD_STORE_FILE` | Path to the keystore (`.jks`), absolute or relative to the repository root |
| `pooparazzi.upload.storePassword` | `POOPARAZZI_UPLOAD_STORE_PASSWORD` | Keystore password |
| `pooparazzi.upload.keyAlias` | `POOPARAZZI_UPLOAD_KEY_ALIAS` | Key alias |
| `pooparazzi.upload.keyPassword` | `POOPARAZZI_UPLOAD_KEY_PASSWORD` | Key password |

With none of them set, release builds are signed with the **debug key**, so `./gradlew :app:bundleRelease :wear:bundleRelease` (or `assembleRelease`) still works on any machine and installs for testing. With only some of them set the build stops with an error rather than produce a bundle Play would refuse. CI sets all four from the secrets below. For a signed build on your own machine, put the four properties in `~/.gradle/gradle.properties` (never in the repository).

## One-time setup

### 1. Create the upload key

The upload key signs what you upload; Google re-signs the apps for users with the app signing key it keeps (Play App Signing, step 3). Keep a backup of the keystore and its passwords outside the repository.

```sh
keytool -genkeypair -v \
  -keystore pooparazzi-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Pooparazzi upload key"

# One line of base64 for the UPLOAD_KEYSTORE_BASE64 secret
base64 -w0 pooparazzi-upload.jks > pooparazzi-upload.jks.b64      # macOS: base64 -i pooparazzi-upload.jks
```

### 2. Add the GitHub secrets and variables

Repository › Settings › Secrets and variables › Actions.

**Secrets** (the four `UPLOAD_*` secrets go together: with only some of them the run stops early with a list of the missing ones; with none of them the run falls back to the [public side-load key](#without-the-upload-key); `GOOGLE_SERVICES_JSON` is optional on its own):

| Secret | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | The whole JSON key file of the Play service account (step 5) |
| `UPLOAD_KEYSTORE_BASE64` | The keystore as one line of base64 (step 1) |
| `UPLOAD_KEYSTORE_PASSWORD` | The keystore password |
| `UPLOAD_KEY_ALIAS` | The key alias (`upload` above) |
| `UPLOAD_KEY_PASSWORD` | The key password (the same as the keystore password if `keytool` did not ask for a separate one) |
| `GOOGLE_SERVICES_JSON` | Optional: the whole `google-services.json` of the Firebase backend ([BACKEND.md](BACKEND.md#6-connect-the-app)). Without it the release builds with accounts and the backup turned off, and the run warns |

**Variables** (all optional):

| Variable | Default | Meaning |
|---|---|---|
| `PLAY_TRACK` | `internal` | Play track for the phone bundle: `internal`, `alpha` (closed testing), `beta` (open testing) or `production` |
| `PLAY_WEAR_TRACK` | `wear:<PLAY_TRACK>` | Play track for the watch bundle |
| `PLAY_RELEASE_STATUS` | `completed` | `completed` rolls the release out; use `draft` while the app has never been published (see below) |
| `VERSION_CODE_OFFSET` | `100` | Added to the run number to make the version base |

### 3. Create the app in Play Console and enable Play App Signing

In [Play Console](https://play.google.com/console): **Create app**, name it Pooparazzi, and answer the declarations. The Google Play Developer API cannot create an app, and it cannot upload the **first** bundle of a new app either: that one has to be uploaded by hand (step 4).

Play App Signing is on by default for new apps (app bundles require it). When you upload the first bundle, keep **"Let Google manage and protect your app signing key"**; the key that signed that first bundle becomes the registered **upload key**, so the first bundle must be signed with the key from step 1. You can check it later under Test and release › Setup › App signing.

### 4. Enable Wear OS and upload the first bundles by hand

1. **Enable the Wear OS form factor** for the listing: Test and release › Advanced settings › Form factors › **Add form factor › Wear OS**, and opt in. Without it Play has no Wear OS tracks and the watch upload fails. Wear OS also needs its own screenshots in the store listing and goes through a separate Wear OS review.
2. Build signed bundles without publishing: Actions › **Release** › **Run workflow**, with "Upload the bundles to Google Play" switched **off**. (This needs the four upload-key secrets; `PLAY_SERVICE_ACCOUNT_JSON` can come later.) Download the `bundles-<base>` artifact.
3. In Play Console, create a release on **Internal testing** with `app-release.aab`, and one on the **Wear OS › Internal testing** track with `wear-release.aab`. Save them (rolling them out is optional).

Later runs number their builds above this one, so the automatic uploads continue from there.

### 5. Create the service account for the API

1. In [Google Cloud console](https://console.cloud.google.com/), pick or create a project and enable the **Google Play Android Developer API** for it.
2. IAM & Admin › Service accounts › **Create service account** (no Cloud roles needed). Open it › Keys › Add key › **JSON**, and save the downloaded file's whole content as the `PLAY_SERVICE_ACCOUNT_JSON` secret.
3. In Play Console › **Users and permissions** › Invite new users: enter the service account's email address, add the Pooparazzi app, and grant at least **Release apps to testing tracks** (and **Release to production, exclude devices, and use Play App Signing** if `PLAY_TRACK` will be `production`). Invitations to service accounts are accepted automatically; permissions can take a while (up to a day) to take effect.

### Draft apps

Until the app's first release has been reviewed and published, Play only accepts **draft** releases through the API ("Only releases with status draft may be created on draft app"). Set the repository variable `PLAY_RELEASE_STATUS` to `draft` until then, and roll the drafts out from Play Console; afterwards remove the variable (or set it to `completed`).

## Side-loading the APKs

Every run of `release.yml` creates a GitHub Release tagged `v<versionName>` (for example `v0.2.0`; see [Version names](#version-names)) with two files: `pooparazzi-phone-….apk` and `pooparazzi-watch-….apk`. They need no secrets: without `PLAY_SERVICE_ACCOUNT_JSON` the Play upload is skipped with a warning, and without the upload-key secrets the APKs are signed with the [public side-load key](#without-the-upload-key). Either way the GitHub Release is still made.

- **Same release on both devices.** Install the phone and watch APK from the same release so they sync.
- **Updates.** All releases are signed with the same key (your upload key, or the side-load key until you add one), so a newer release installs over an older one and keeps the data. Adding the upload-key secrets later changes the key: uninstall the side-loaded apps once (which deletes their data) before installing the first upload-key release.
- **Not interchangeable with Play installs.** Play re-signs its downloads with the Play App Signing key, so a Play install can't update a side-loaded one or the other way round: uninstall first (which deletes the app's data).
- **Phone:** open the APK on the phone and allow installs from that app, or `adb install pooparazzi-phone-….apk`.
- **Watch:** enable Developer options (Settings › System › About › Versions, tap Build number 7 times), turn on ADB debugging and Wireless debugging, pair from a computer on the same Wi-Fi and install:

  ```sh
  adb pair <ip>:<pairing-port>      # enter the pairing code shown on the watch
  adb connect <ip>:<port>
  adb -s <ip>:<port> install pooparazzi-watch-….apk
  ```

### Without the upload key

Until the four `UPLOAD_*` secrets are set, `release.yml` signs the APKs (and the bundles in the workflow artifact) with [`keystores/sideload.jks`](../keystores/sideload.jks), alias `sideload`, store and key password `pooparazzi-sideload`, and skips the Play upload. The key is in the repository on purpose, so every merge produces installable releases with no setup and each one updates the last. Because it is public, anyone can sign an APK that installs over these: use it only for testing builds, and never upload a bundle signed with it to Play (it would become the registered upload key). Add the upload-key secrets before relying on the side-loaded apps.

## Checklist

- [ ] Upload keystore created and backed up (step 1); until then every merge publishes APKs signed with the public side-load key to GitHub Releases
- [ ] Secrets `PLAY_SERVICE_ACCOUNT_JSON`, `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` added (step 2)
- [ ] App `com.ffmathy.pooparazzi` created in Play Console, Play App Signing on (step 3)
- [ ] Wear OS form factor enabled for the listing (step 4)
- [ ] First phone and watch bundles uploaded by hand (step 4)
- [ ] Service account invited in Play Console with release permissions (step 5)
- [ ] `PLAY_RELEASE_STATUS=draft` while the app is still a draft; optional `PLAY_TRACK`
- [ ] Optional backup: Firebase project set up and `GOOGLE_SERVICES_JSON` added ([BACKEND.md](BACKEND.md)); Play Console › App content › Data safety declares the account email and names a way to request account deletion
