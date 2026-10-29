# Pooparazzi privacy policy

Effective 29 October 2026. The same policy is published at `https://<project>.web.app/privacy` (the page is [`firebase/hosting/privacy/index.html`](../firebase/hosting/privacy/index.html); keep the two in step).

Pooparazzi is an app for parents: it logs a baby's feeds, diapers and sleep on an Android phone and a Wear OS watch. This policy explains what happens to that information. In short: **without an account nothing is sent to us; with an account, your log and your babies are stored in Google Firebase so that you and the members of your family can see them, and nobody else. There are no ads, no analytics and no tracking, and nothing is ever sold.**

## 1. Who is responsible

The data controller is **Mathias Lorenzen**, Denmark, the developer of Pooparazzi. Contact: **[mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)**. Write to this address with any question about your data or to use any of your rights (section 8).

## 2. What is collected

**Without an account** (the default), the app sends nothing to the developer. Everything you log is stored on your phone and your paired watch. The phone and the watch exchange it through Wear OS (Google Play services' Wearable Data Layer), which is how every Wear OS app talks to its phone app. If Android backup is switched on for your Google account, Android may include the app's log in that backup, under Google's own terms; the developer cannot see it.

**With an account** (optional, in Settings › Account), the following is stored in Google Firebase. Signing in creates your account (the first row); everything else is uploaded only after you have agreed to it on the app's consent screen (section 3):

| What | Details | Why |
|---|---|---|
| Your account | Your email address, a user id, when the account was created and last signed in. If you sign in with Google, Firebase Authentication also keeps the name and profile picture link of your Google account (the app does not use them). There is no password: you sign in with Google or with a link sent to your email. | To sign you in and keep your data yours |
| Your log | Every entry: its times, its kind (feed, diaper, sleep), the feed type and breast side, the diaper's pee and poop with optional colour and texture, which baby it belongs to, and when it was last changed. A deleted entry stays only as a marker that tells your family's other phones it is gone (its id, kind, baby and when it was deleted), without its times or details | To back it up, restore it on a new phone and share it with your family |
| Your babies | Each baby's name (whatever you type; a nickname is fine) and an id | To show the same babies, with their names, on every family member's phone |
| Your family | The email address of every member, when they joined and which invite they used; who runs the family (the user id of its creator); invites (the family, who made it, the inviter's email, when it expires) | To let the members see who is in the family, to let its creator remove members, and to let an invited person join |

The app does not collect your location, contacts, photos, device identifiers or advertising id, and contains no analytics, advertising or crash-reporting code. Google's servers, like any web server, see the IP address of each request; Google uses it to run and protect the service (for example to limit how many accounts can be created from one address).

The website (the invite page, the sign-in page and this policy) sets no cookies and loads no scripts.

## 3. Why, and on what legal basis (GDPR)

- **Your account, the backup, the restore on a new phone and the family sharing**: your **consent**, GDPR Article 6(1)(a). Signing in is your choice and creates the account (your email address). Before the app uploads anything else, it asks on a **separate consent screen**, the first time you are signed in on a phone (and so also before you can join a family): it says what is uploaded (your log, your babies' names and your email address), who sees it (you and the members of your family), where it is kept (Google Firebase, with the database in the EU) and how to withdraw, with an explicit **"I agree"** button. Nothing is uploaded until you tap it; "Not now, sign out" signs you out instead. The app remembers your answer on that phone, for that account.
- **Information about your baby** (feeding, diapers and sleep can say something about a child's health): your **explicit consent** as the baby's parent or guardian, GDPR Articles 6(1)(a) and 9(2)(a), given on the same consent screen, which says so.
- **Withdrawing your consent**, at any time: **sign out** (nothing more is uploaded from that phone, and the app asks again when you sign in), **leave the family** (Settings › Family), or **delete your account** (Settings › Account), which deletes what is stored as section 6 describes. Withdrawing does not affect what was done before.
- **Security and abuse protection** (for example Google's limits on sign-up attempts): GDPR Article 6(1)(f), the legitimate interest in keeping the service safe.

The information is used for nothing else: no profiling, no marketing, no ads, no analytics.

## 4. Where it is stored, and who processes it

The backend is **Google Firebase** (Firebase Authentication, Cloud Firestore and Firebase Hosting), provided by Google Ireland Limited and Google LLC. Google acts as the developer's **data processor** under the [Firebase and Google Cloud data processing terms](https://firebase.google.com/terms/data-processing-terms), which include the EU Standard Contractual Clauses for any transfer outside the EU/EEA.

The database (your log, babies and family) is stored in Google's **Europe multi-region (`eur3`: Belgium and the Netherlands)**. Firebase Authentication is a global Google service, so your account data may be processed outside the EU, including in the United States, under those terms (and the EU–US Data Privacy Framework, to which Google is certified).

## 5. Who can see it

- **You**, on every phone where you sign in to your account.
- **The members of your family.** When you are in a family, every member sees the family's log, babies (with their names) and the email addresses of the other members. A family is only the people who were invited with a link that one of its members shared, and every member can see who they are. You can leave at any time. The family's creator can remove members; if the creator leaves or deletes their account while others remain, the member who joined first takes over.
- **What a member already has stays with them.** When someone leaves or is removed, they stop seeing the family's data from then on, but what had already reached their phone (the family's log and babies' names, as last synced) stays on their phone, as on yours: the app cannot take it back.
- **Nobody else.** The database's security rules let only the members of a family read or change its data. The developer can technically access the database to run the service and answer your requests, but does not look at or use the content otherwise. Nothing is shared with or sold to anyone, and Google uses it only to provide Firebase to the developer.

An **invite link** is a secret: anyone who has it can use it to join your family, and it shows its maker's email address to whoever opens it. It works once, for 6 days, and only while the member who made it is still in the family. Share it only with the person you want to invite.

## 6. How long it is kept, and how to delete it

Your information is kept until you delete it, as follows:

- **Delete your account** in the app: Settings › Account › Delete account. This deletes your account, your membership of your family, the pointer to it, the backup an earlier version of the app may have kept for your account, and the unused invites you made on that phone. If you were the **last member** of your family, it also deletes the family's whole log and babies. If you started the family and others remain, the member who joined first takes it over. The log on your phone stays on your phone.
- **Leave a family** in Settings › Family. Your membership (and your email address in the member list) is removed, and so are the unused invites you made on that phone. Entries already logged into the family stay in the family's shared log for its other members, because they are part of the log they share; your phone keeps its own copy and backs it up into a new, empty family of your own.
- **If you are removed** from a family, your membership is deleted; your phone keeps its log and backs it up into a new family of your own.
- **When the last member leaves or deletes their account**, the family's log and babies are deleted with it. What stays is an empty record of the family: its random id, the user id of whoever last ran it, and when it was created, so that no one can ever create a family with the same id and take over what still refers to it (an old invite, say). It holds no email address, log or names.
- An **invite** is deleted when it is used, or when its maker leaves the family or deletes their account on the phone it was made on. It stops working after 6 days, or as soon as its maker is no longer in the family, and the database deletes expired invites automatically (usually within a day after they expire); a weekly clean-up removes any that remain.
- **Signing out** keeps what is stored as it is; nothing more is uploaded from that phone, and signing in again asks for your consent again.
- **Deleting an entry** removes its times and details from the backup at once; only the marker described in section 2 stays, until the family's log is deleted.
- **Accounts nobody uses** are deleted automatically: an account with no sign-in and no use of the app for **2 years** is deleted with everything "Delete account" deletes, by a weekly clean-up. The same clean-up deletes what is left in the database of accounts removed in other ways.
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
- **data portability**: receive it in a structured, machine-readable format (JSON). Settings › About › **Download my data** saves the log and the babies (with their names) that are on your phone as a ZIP file of JSON files (and **Import data** reads it back in); write to us for what only the backup holds, such as your account and your family's member list;
- **withdraw your consent** at any time, without affecting what was done before.

Write to [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com); you will get an answer within one month. You can also complain to the Danish Data Protection Agency, **Datatilsynet** (Carl Jacobsens Vej 35, 2500 Valby, Denmark, [www.datatilsynet.dk](https://www.datatilsynet.dk)), or to the data protection authority where you live.

## 9. Security

All traffic between the app and Firebase is encrypted (TLS), and Google encrypts the stored data. Firestore security rules allow each account to reach only its own family's data, and a family can only be joined with a valid invite from someone still in it. Creating or joining a family and inviting need a verified email address (Google, or the sign-in link), so nobody can appear in a family under someone else's address. There are no passwords to leak: sign-in is with Google or with a one-time link sent to your email. Should someone have registered your address with a password before you first signed in with a link, the app removes that password when you do. On your phone and watch, the app's data is kept in the app's private storage.

## 10. Changes

If this policy changes, the new version is published on its web page (linked from the app's Settings › About), with a new date. Significant changes are also shown in the app before they take effect.

## 11. Contact

Mathias Lorenzen · [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)

---

## Kort fortalt på dansk

- **Dataansvarlig:** Mathias Lorenzen, Danmark, [mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com).
- **Uden konto** sendes intet til os: loggen er kun på din telefon og dit ur (og i din egen Android-backup, hvis du har slået den til).
- **Med konto** gemmes din e-mail, din log, dine børns navne og din families medlemmer (deres e-mails) i Google Firebase, så du og din familie kan se dem. Databasen ligger i EU (`eur3`). Google er databehandler.
- **Hvorfor:** med dit samtykke (GDPR art. 6(1)(a)) og, for oplysningerne om dit barn, dit udtrykkelige samtykke som forælder (art. 9(2)(a)). Appen beder om det på en særskilt skærm, før noget uploades (også før du kan blive medlem af en familie), og intet uploades, før du trykker "Jeg giver samtykke". Du trækker det tilbage ved at logge ud, forlade familien eller slette kontoen.
- **Hvem ser det:** kun du og medlemmerne af din familie. Et medlem, der forlader familien eller fjernes, beholder det, der allerede er på deres telefon. Ingen reklamer, ingen analyse, intet sælges.
- **Sletning:** slet kontoen i appen (Indstillinger › Konto), forlad familien (Indstillinger › Familie), eller skriv til os. Når det sidste medlem forlader familien, slettes familiens log og børn; kun en tom post om familien (dens id, hvem der sidst stod for den, og hvornår den blev oprettet) bliver, så ingen kan overtage den. Invitationer virker i 6 dage og slettes automatisk, når de er udløbet. En slettet registrering bliver kun som en markering uden tidspunkter og detaljer, og en konto, der ikke er brugt i 2 år, slettes automatisk.
- **Dine rettigheder:** indsigt, berigtigelse, sletning, begrænsning, indsigelse, dataportabilitet og tilbagetrækning af samtykke. Indstillinger › Om › **Download mine data** gemmer loggen og børnene som en JSON-fil. Du kan klage til **Datatilsynet** (www.datatilsynet.dk).
- **Børn:** appen er til forældre; oplysningerne om barnet indtastes af forælderen.

Den fulde politik ovenfor (på engelsk) gælder.
