# Pooparazzi privacy policy

Effective 29 October 2026. The same policy is published at `https://<project>.web.app/privacy` (the page is [`firebase/hosting/privacy/index.html`](../firebase/hosting/privacy/index.html); keep the two in step).

Pooparazzi is an app for parents: it logs a baby's feeds, diapers and sleep on an Android phone and a Wear OS watch. This policy explains what happens to that information. In short: **without an account nothing is sent to us; with an account, your log and your babies are stored in Google Firebase so that you and the members of your family can see them, and nobody else. There are no ads, no analytics and no tracking, and nothing is ever sold.**

## 1. Who is responsible

The data controller is **Mathias Lorenzen**, Denmark, the developer of Pooparazzi. Contact: **[mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)**. Write to this address with any question about your data or to use any of your rights (section 8).

## 2. What is collected

**Without an account** (the default), the app sends nothing to the developer. Everything you log is stored on your phone and your paired watch. The phone and the watch exchange it through Wear OS (Google Play services' Wearable Data Layer), which is how every Wear OS app talks to its phone app. If Android backup is switched on for your Google account, Android may include the app's log in that backup, under Google's own terms; the developer cannot see it.

**With an account** (optional, in Settings › Account), the following is stored in Google Firebase:

| What | Details | Why |
|---|---|---|
| Your account | Your email address, a user id, when the account was created and last signed in. If you sign in with Google, Firebase Authentication also keeps the name and profile picture link of your Google account (the app does not use them). There is no password: you sign in with Google or with a link sent to your email. | To sign you in and keep your data yours |
| Your log | Every entry: its times, its kind (feed, diaper, sleep), the feed type and breast side, the diaper's pee and poop with optional colour and texture, which baby it belongs to, and when it was last changed or deleted | To back it up, restore it on a new phone and share it with your family |
| Your babies | Each baby's name (whatever you type; a nickname is fine) and an id | To show the same babies, with their names, on every family member's phone |
| Your family | The email address of every member, when they joined and which invite they used; invites (the family, who made it, the inviter's email, when it expires) | To let the members see who is in the family, and to let an invited person join |

The app does not collect your location, contacts, photos, device identifiers or advertising id, and contains no analytics, advertising or crash-reporting code. Google's servers, like any web server, see the IP address of each request; Google uses it to run and protect the service (for example to limit how many accounts can be created from one address).

The website (the invite page, the sign-in page and this policy) sets no cookies and loads no scripts.

## 3. Why, and on what legal basis (GDPR)

- **To provide the service you ask for** (the backup, the restore on a new phone, the family sharing and your account): GDPR Article 6(1)(b), performance of a contract with you.
- **Information about your baby** (feeding, diapers and sleep can say something about a child's health): your **explicit consent** as the baby's parent or guardian, GDPR Articles 6(1)(a) and 9(2)(a). You give it by creating an account or joining a family and choosing to back up the log. You can withdraw it at any time by leaving the family or deleting your account (section 6); this does not affect what was done before.
- **Security and abuse protection** (for example Google's limits on sign-up attempts): GDPR Article 6(1)(f), the legitimate interest in keeping the service safe.

The information is used for nothing else: no profiling, no marketing, no ads, no analytics.

## 4. Where it is stored, and who processes it

The backend is **Google Firebase** (Firebase Authentication, Cloud Firestore and Firebase Hosting), provided by Google Ireland Limited and Google LLC. Google acts as the developer's **data processor** under the [Firebase and Google Cloud data processing terms](https://firebase.google.com/terms/data-processing-terms), which include the EU Standard Contractual Clauses for any transfer outside the EU/EEA.

The database (your log, babies and family) is stored in Google's **Europe multi-region (`eur3`: Belgium and the Netherlands)**. Firebase Authentication is a global Google service, so your account data may be processed outside the EU, including in the United States, under those terms (and the EU–US Data Privacy Framework, to which Google is certified).

## 5. Who can see it

- **You**, on every phone where you sign in to your account.
- **The members of your family.** When you are in a family, every member sees the family's log, babies (with their names) and the email addresses of the other members. A family is only the people who were invited with a link that one of its members shared, and every member can see who they are. You can leave at any time.
- **Nobody else.** The database's security rules let only the members of a family read or change its data. The developer can technically access the database to run the service and answer your requests, but does not look at or use the content otherwise. Nothing is shared with or sold to anyone, and Google uses it only to provide Firebase to the developer.

An **invite link** is a secret: anyone who has it can use it to join your family. It works once, and only for 7 days. Share it only with the person you want to invite.

## 6. How long it is kept, and how to delete it

Your information is kept until you delete it, as follows:

- **Delete your account** in the app: Settings › Account › Delete account. This deletes your account, your membership of your family and the pointer to it. If you were the **last member** of your family, it also deletes the family's whole log and babies. The log on your phone stays on your phone.
- **Leave a family** in Settings › Family. Your membership (and your email address in the member list) is removed. Entries already logged into the family stay in the family's shared log for its other members, because they are part of the log they share; your phone keeps its own copy and backs it up into a new, empty family of your own.
- **When the last member leaves or deletes their account**, the family's data is deleted with it.
- An **invite** is deleted when it is used. An unused invite stops working after 7 days and is then deleted.
- **Signing out** keeps everything as it is; nothing more is uploaded from that phone.
- You can also ask for deletion **by email** to [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com), for example if you no longer have the app. Please write from the address of the account.

Deleted data is removed from Google's systems as described in Google's data processing terms (backups can take a little longer to expire).

## 7. Children

Pooparazzi is made for parents and caregivers. Accounts are for adults; the app is not meant to be used by children. The information about a baby (its name and its log) is entered by its parent or guardian, who decides whether to back it up and with whom to share it, and who can exercise the baby's rights on the baby's behalf.

## 8. Your rights

Under the GDPR you have the right to:

- **access** your data and get a copy of it;
- **rectify** it (you can rename babies and edit or delete entries in the app at any time);
- **erase** it (section 6);
- **restrict** or **object** to its processing;
- **data portability**: receive it in a structured, machine-readable format (JSON);
- **withdraw your consent** at any time, without affecting what was done before.

Write to [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com); you will get an answer within one month. You can also complain to the Danish Data Protection Agency, **Datatilsynet** (Carl Jacobsens Vej 35, 2500 Valby, Denmark, [www.datatilsynet.dk](https://www.datatilsynet.dk)), or to the data protection authority where you live.

## 9. Security

All traffic between the app and Firebase is encrypted (TLS), and Google encrypts the stored data. Firestore security rules allow each account to reach only its own family's data, and a family can only be joined with a valid invite. There are no passwords to leak: sign-in is with Google or with a one-time link sent to your email. On your phone and watch, the app's data is kept in the app's private storage.

## 10. Changes

If this policy changes, the new version is published on its web page (linked from the app's Settings › About), with a new date. Significant changes are also shown in the app before they take effect.

## 11. Contact

Mathias Lorenzen · [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)

---

## Kort fortalt på dansk

- **Dataansvarlig:** Mathias Lorenzen, Danmark, [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com).
- **Uden konto** sendes intet til os: loggen er kun på din telefon og dit ur (og i din egen Android-backup, hvis du har slået den til).
- **Med konto** gemmes din e-mail, din log, dine børns navne og din families medlemmer (deres e-mails) i Google Firebase, så du og din familie kan se dem. Databasen ligger i EU (`eur3`). Google er databehandler.
- **Hvorfor:** for at levere den backup og deling, du beder om (GDPR art. 6(1)(b)), og med dit udtrykkelige samtykke som forælder til oplysningerne om dit barn (art. 6(1)(a) og 9(2)(a)).
- **Hvem ser det:** kun du og medlemmerne af din familie. Ingen reklamer, ingen analyse, intet sælges.
- **Sletning:** slet kontoen i appen (Indstillinger › Konto), forlad familien (Indstillinger › Familie), eller skriv til os. Når det sidste medlem forlader familien, slettes familiens data.
- **Dine rettigheder:** indsigt, berigtigelse, sletning, begrænsning, indsigelse, dataportabilitet og tilbagetrækning af samtykke. Du kan klage til **Datatilsynet** (www.datatilsynet.dk).
- **Børn:** appen er til forældre; oplysningerne om barnet indtastes af forælderen.

Den fulde politik ovenfor (på engelsk) gælder.
