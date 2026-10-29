# Personal data breach response (GDPR art. 33–34)

What to do if the backup's data may have been read, changed, lost or deleted by someone who should not have been able to. Examples: a security rule that let one account read another's log, a leaked service account key or `google-services.json` with a weakened rule, a compromised Firebase or Google account of the controller.

## 1. Contain (at once)

- Fix or roll back the rules (`firebase/firestore.rules`, `npx firebase deploy --only firestore`), or temporarily deploy a deny-all rule set: the app keeps the log on the phone and backs up again later.
- Rotate what leaked: delete the service account key in Google Cloud › IAM › Service accounts and update the `FIREBASE_SERVICE_ACCOUNT` secret; change the Google account's password and review its sessions; disable affected Authentication users if needed.
- Keep evidence: note the times and save the relevant Cloud Logging and Firebase usage graphs.

## 2. Assess (within 24 hours)

- What data (log entries, email addresses), how many accounts, and over what period.
- The risk to the parents and children: the log is health data about children, so assume a risk unless the data was unreadable or the exposure was shown not to have happened.

## 3. Notify

- **Datatilsynet** within **72 hours** of becoming aware, unless the breach is unlikely to result in a risk: [www.datatilsynet.dk](https://www.datatilsynet.dk) › Anmeld brud på persondatasikkerheden. If not everything is known yet, notify what is known and add to it later.
- **The affected parents**, by email to their account address, without undue delay when the risk is high: what happened, what data, what was done, what they can do (for example delete the account), and the contact address.
- If not notified, write down why.

## 4. Record (always)

Every breach goes in the log below, notified or not (art. 33(5)): date, what happened, data and accounts affected, consequences, measures, and whether and when Datatilsynet and the parents were told.

| Date | What happened | Data and accounts | Measures | Notified |
|---|---|---|---|---|
| — | No breaches so far. | | | |
