# On a Danish phone

The setup and Settings as they read on a phone set to Danish: where the copy
matters most, because a Danish parent reads it once and must understand it.

```ail
node DanishPhone : flow   level: experience
  why   Danish parents should meet the app in Danish from its first screen, with
        the same meaning as in English, not a word-for-word translation.   <- Language
  what
    Scenario: your baby on a danish phone
      Given a new install on a phone set to Danish
      When the app opens
      Then the screen reads "Dit barn" over "Begge dele er valgfrie. Du kan springe dem over nu og tilføje dem senere under Indstillinger › Barn."
      And it offers "Navn", "Tilføj fødselsdato", "Fortsæt" and "Spring over for nu"
      When the parent taps "Tilføj fødselsdato", picks today and taps OK
      Then the pill reads "Født" and today's date, as "Født 27. nov. 2026"
    Scenario: the introduction on a danish phone
      Given the introduction on a phone set to Danish
      When the parent taps "Næste" five times
      Then the pages read "Velkommen til Pooparazzi", "Søvn", "Måltider og bleskift", "Ret hvad som helst", "Hvad kommer nu" and "Altid ved hånden", the dots "Side 1 af 6" to "Side 6 af 6"
      And the last page offers "Kom i gang", and "Spring over" is gone
    Scenario: settings on a danish phone
      Given a phone set to Danish, its first run skipped
      When the parent opens "Indstillinger"
      Then its sections read "Konto", "Barn", "Udseende", "Sprog", "Ur", "Notifikationer" and "Om"
      And the account reads "Backup er ikke sat op i denne version. Alt gemmes på denne telefon."
      And the footer reads "Lavet med ❤️ af en far i Aarhus" and "Pooparazzi er open source"
    Scenario: adding and removing a baby in danish
      Given Indstillinger on a phone set to Danish, with one unnamed baby
      When the parent taps "Tilføj et barn", types "Ida" and taps "Tilføj"
      Then the section reads "Børn", with buttons spoken "Fjern Baby 1" and "Fjern Ida"
      When the parent taps "Fjern Ida"
      Then a dialog asks "Fjern Ida?" with "Barnets log bliver slettet fra telefonen og dit ur. Det kan ikke fortrydes.", "Annuller" and "Fjern"
  how
    decide every user-visible text comes from the string resources, English by default and Danish beside it; nothing is written in code
      because the target says so, and the design requires it (V1_ADDITIONS §5)
    decide the Danish footer leaves out "Denmark": "Lavet med ❤️ af en far i Aarhus"
      because a Danish reader knows where Aarhus is (DESIGN_SPEC §3.3.5)
    decide the date picker and its date follow the language: "Født 27. nov. 2026", the picker's own words in Danish ("Fødselsdato", "Annuller")
      because the birth date reads in the app language's medium style (DESIGN_SPEC §3.3.5)
  look
    As in English, screen for screen: Danish words are longer, so labels wrap and nothing is cut off, also at 200 %
      font size
  copy
    yourBaby.title     "Dit barn"
    yourBaby.optional  "Begge dele er valgfrie. Du kan springe dem over nu og tilføje dem senere under Indstillinger › Barn."
    yourBaby.why       "Fødselsdatoen er valgfri, men den gør forudsigelserne og estimaterne bedre: de følger så barnets alder."
    yourBaby.hint      "Navne og fødselsdatoer bliver på telefonen og dit ur."
    intro.welcome      "Velkommen til Pooparazzi"
    intro.welcome.body "Registrér søvn, måltider og bleskift med et eller to tryk, lavet til én hånd klokken 3 om natten. Sådan virker det; det tager et minut."
    intro.sleep        "Søvn"
    intro.sleep.body   "Tryk på Start søvn, når barnet falder i søvn, og på samme knap, nu Vågnede, når det vågner. Mens barnet sover, viser forsiden hvor længe; tryk på ringen for at ændre, hvornår søvnen startede."
    intro.log          "Måltider og bleskift"
    intro.log.body     "Tryk på Måltid eller Ble. Kun tidspunktet er nødvendigt: typen, brystsiden, tis eller bæ og resten er valgfrit, så Kun tidspunktet er fint. Skete det lidt før? −15m, −10m og −5m flytter tidspunktet tilbage."
    intro.fix          "Ret hvad som helst"
    intro.fix.body     "Alt kan ændres. Tryk på Fortryd lige efter en søvn, eller tryk på et tidspunkt på forsiden (Sidste måltid, en siden-linje) eller en linje i Historik for at rette eller slette den."
    intro.patterns     "Hvad kommer nu"
    intro.patterns.body "Under hver knap viser forsiden, hvad de sidste 7 dage forventer, fx næste måltid. Historik viser hver dag på et 24-timers bånd, og Statistik gennemsnittet per dag."
    intro.everywhere   "Altid ved hånden"
    intro.everywhere.body "Tilføj en widget til din startskærm, og installér Pooparazzi på dit Wear OS-ur for at få dets felt og komplikationer. Er du logget ind, kan din familie også registrere. Du kan se denne introduktion igen under Indstillinger › Om."
    account.unavailable "Backup er ikke sat op i denne version. Alt gemmes på denne telefon."
    addBaby            "Tilføj et barn"
    addBaby.confirm    "Tilføj"
    remove.cd          "Fjern {label}"
    remove.title       "Fjern {label}?"
    remove.body        "Barnets log bliver slettet fra telefonen og dit ur. Det kan ikke fortrydes."
    remove.body.family "Barnets log bliver slettet for alle i din familie og fra din backup. Det kan ikke fortrydes."
  sources
    docs/design/V1_ADDITIONS.md §5 (Internationalisation)
    docs/design/BABIES_AND_BACKUP.md §2.4 (the Danish remove dialog)
    driven on Robolectric on a phone set to Danish (qualifier da), 2026-11-27: Dit barn, the picker ("Fødselsdato",
      "I dag, fredag den 27. november 2026"), "Født 27. nov. 2026", the six pages, Hjem, Indstillinger, Tilføj et barn,
      Fjern Ida?
    app/src/main/res/values-da/strings.xml and core/src/main/res/values-da/strings.xml
```
