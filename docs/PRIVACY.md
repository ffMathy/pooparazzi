# Pooparazzi privacy policy

**Version 1 · last updated 29 October 2026** · [På dansk](PRIVACY.da.md)

Pooparazzi is a baby tracker for an Android phone and a Wear OS watch. It logs feeds, sleep and diapers. This policy says what the apps store, where, why, for how long, and what your rights are. It covers the phone app, the watch app and the optional backup.

**In short:** without an account, everything stays on your phone and your watch, and nothing is sent to us. With the optional backup, your email address and your baby's log (but never the baby's name) are stored on Google's Firebase servers so you can restore the log on a new phone. We store the log only with your explicit consent. You can download all of it, and you can delete it at any time. There are no ads, no analytics and no tracking, and nothing is sold or shared for marketing.

## 1. Who is responsible

The data controller is **Mathias Lorenzen**, Denmark, the developer of Pooparazzi.

Email: **[mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)**. Write to this address for any question about your data or to use your rights (section 8), also if you no longer have the app or the phone.

## 2. What stays on your devices

The phone app and the watch app store the log, the babies' names and your settings on your phone and your watch. They sync with each other directly, over Google's Wearable Data Layer between your own paired devices. None of this reaches us unless you turn on the backup (section 3).

- **Android's own backup.** If your phone's Android backup is on, Android may copy the app's log and settings to your Google account's backup (Google Drive), under your Google account's terms, not ours. The babies' names are left out of that cloud backup. A direct transfer from an old phone to a new one may carry them. The watch app backs up nothing.
- **Notifications and widgets** show the log on your own phone and watch only.
- **Download my data** (section 8) writes a file wherever you choose to save it. What happens to that file is up to you.

## 3. The optional backup: what we store

The backup is off until you sign in or create an account. Without an account, nothing below applies.

| What | Details | Where |
|---|---|---|
| Your account | Email address; for an email account, a password hash (we never see the password); for Google sign-in, your Google account's id and email address. Firebase Authentication also records when the account was created and last used, and the IP address of sign-ins, to protect against abuse. | Firebase Authentication |
| Your baby's log | Each entry: its kind (feed, sleep, diaper), times, feed type and breast side, diaper details (pee, poop, colour, texture), an internal random baby id, and when it was last changed. **Never the baby's name.** | Cloud Firestore, Europe (`eur3`) |
| Your consent | The version of the consent you agreed to, and when (section 4). | Cloud Firestore, Europe (`eur3`) |

A deleted entry is kept in the backup only as a marker that tells your other phones it was deleted: its random id, kind, baby id and change time. What was logged (times and details) is removed from the backup. The phone you deleted it on keeps its own copy for 7 days, then purges it.

The log of feeds, sleep and diapers is **health information about a child**. That is a special category of personal data (GDPR art. 9), and it gets extra protection.

## 4. Why, and on what legal basis

| Purpose | Data | Legal basis (GDPR) |
|---|---|---|
| Back up the log and restore it on a new phone | The log, the baby id | Your **explicit consent**, art. 9(2)(a) and art. 6(1)(a). You give it by ticking the box before you sign in. |
| Run your account: sign-in, password reset, account deletion | Email, password hash or Google id | **Contract**, art. 6(1)(b): it is the service you ask for. |
| Keep the service secure and within its free quota, and delete unused accounts | Sign-in records, account activity times | **Legitimate interest**, art. 6(1)(f): security, and not keeping data longer than needed. |
| Prove that you consented | The consent version and time | **Legal obligation**, art. 6(1)(c) with art. 7(1). |

You give the consent as the parent or the person with parental responsibility, for your child's data. You can **withdraw it at any time** by deleting the account (Settings › Account › Delete account…) or by emailing us. Withdrawing deletes the backup. It does not affect what was done with your consent before. If you signed in before this consent existed, the app pauses the backup until you agree, and nothing more is uploaded in the meantime.

The backup is optional. If you do not consent, the app works fully without it, on your phone and watch only.

## 5. Who processes the data for us

- **Google Ireland Limited and Google LLC (Firebase)** host the backup as our data processor, under the Firebase data processing terms. Cloud Firestore keeps the log in Europe (multi-region `eur3`). Firebase Authentication may process account data in the United States. Such transfers rely on Google's certification under the **EU–US Data Privacy Framework** and on the **EU Standard Contractual Clauses** in Google's data processing terms.
- **Google Play** distributes the app under Google's own terms.

No one else receives the data. We do not sell it or use it for advertising or profiling, and we use no analytics or crash-reporting services.

## 6. How long we keep it

| Data | Kept until |
|---|---|
| The log in the backup | You delete entries (only the deletion marker described in section 3 stays) or you delete the account, which deletes the whole backup and the consent record at once. |
| The account | You delete it. An account **not used for 2 years** (no sign-in and no app use) is deleted automatically with its backup. A weekly job checks this. |
| A backup whose account was removed | Deleted by the same weekly job. |
| Firebase's own sign-in and security logs | The periods set by Google for Firebase Authentication. |
| Data on your phone and watch | You delete it in the app, or uninstall the app. Signing out or deleting the account keeps the log on the phone. |

## 7. Security

All traffic to the backup is encrypted (TLS), and Google encrypts the data at rest. Security rules let each account read and write only its own log, in a fixed shape: there is nowhere to store a name or anything else. Passwords are handled by Firebase Authentication and are never visible to us. If a breach ever puts your data at risk, we will tell the Danish Data Protection Agency within 72 hours and tell you without undue delay where the law requires it.

## 8. Your rights

| Right | How |
|---|---|
| **Access** and **portability** (art. 15, 20) | Settings › Privacy › **Download my data** saves the whole log and the babies' names as a JSON file. The backup holds nothing more than your phone does. You can also email us for a copy of what the backup and your account hold. |
| **Rectification** (art. 16) | Edit any entry in the app; the backup follows. |
| **Erasure** (art. 17) | Delete an entry in the app, or delete the whole account and backup: Settings › Account › **Delete account…**. Without the phone, email us from the account's address and we delete it within one month. |
| **Withdraw consent** (art. 7(3)) | Delete the account, or email us. |
| **Restriction** and **objection** (art. 18, 21) | Email us. |
| **Complain** (art. 77) | To the Danish Data Protection Agency (Datatilsynet), Carl Jacobsens Vej 35, 2500 Valby, Denmark, [www.datatilsynet.dk](https://www.datatilsynet.dk), or to the authority where you live. |

We answer requests within one month, free of charge. We may ask you to confirm that you own the account, for example by replying from its email address.

## 9. Children

The app is used by parents and carers, not by children. The data about the child is entered by the parent, who also gives the consent for it (section 4).

## 10. Changes

When this policy changes, the new version is published here with a new date. When a change affects what the backup stores or why, the app asks for your consent again before it backs up anything more.
