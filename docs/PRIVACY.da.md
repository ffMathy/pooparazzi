# Pooparazzis privatlivspolitik

**Version 1 · senest opdateret 29. oktober 2026** · [In English](PRIVACY.md)

Pooparazzi er en babylog til en Android-telefon og et Wear OS-ur. Den registrerer måltider, søvn og bleer. Denne politik beskriver, hvad apps gemmer, hvor, hvorfor, hvor længe, og hvilke rettigheder du har. Den gælder telefonappen, urappen og den valgfri backup.

**Kort fortalt:** uden en konto bliver alt på din telefon og dit ur, og intet sendes til os. Med den valgfri backup gemmes din e-mailadresse og dit barns log (men aldrig barnets navn) på Googles Firebase-servere, så du kan gendanne loggen på en ny telefon. Vi gemmer kun loggen med dit udtrykkelige samtykke. Du kan downloade det hele og til enhver tid slette det. Der er ingen reklamer, ingen analyse og ingen sporing, og intet sælges eller deles til markedsføring.

## 1. Hvem er ansvarlig

Den dataansvarlige er **Mathias Lorenzen**, Danmark, som udvikler Pooparazzi.

E-mail: **[mathias.lorenzen@live.com](mailto:mathias.lorenzen@live.com)**. Skriv til denne adresse med alle spørgsmål om dine data, eller for at bruge dine rettigheder (afsnit 8), også hvis du ikke længere har appen eller telefonen.

## 2. Det, der bliver på dine enheder

Telefonappen og urappen gemmer loggen, børnenes navne og dine indstillinger på din telefon og dit ur. De synkroniserer direkte med hinanden over Googles Wearable Data Layer mellem dine egne parrede enheder. Intet af det når os, medmindre du slår backuppen til (afsnit 3).

- **Androids egen backup.** Hvis telefonens Android-backup er slået til, kan Android kopiere appens log og indstillinger til din Google-kontos backup (Google Drev) på din Google-kontos vilkår, ikke vores. Børnenes navne holdes ude af den cloud-backup. En direkte overførsel fra en gammel telefon til en ny kan tage dem med. Urappen tager ikke backup af noget.
- **Notifikationer og widgets** viser kun loggen på din egen telefon og dit eget ur.
- **Download mine data** (afsnit 8) skriver en fil, hvor du vælger at gemme den. Hvad der sker med filen, bestemmer du.

## 3. Den valgfri backup: det gemmer vi

Backuppen er slået fra, indtil du logger ind eller opretter en konto. Uden en konto gælder intet af det nedenfor.

| Hvad | Detaljer | Hvor |
|---|---|---|
| Din konto | E-mailadresse; for en e-mailkonto en hash af adgangskoden (vi ser aldrig selve adgangskoden); for Google-login din Google-kontos id og e-mailadresse. Firebase Authentication registrerer også, hvornår kontoen blev oprettet og sidst brugt, og IP-adressen ved login, for at beskytte mod misbrug. | Firebase Authentication |
| Dit barns log | Hver registrering: dens art (måltid, søvn, ble), tidspunkter, type måltid og brystside, bledetaljer (tis, afføring, farve, konsistens), et internt tilfældigt barne-id, og hvornår den sidst blev ændret. **Aldrig barnets navn.** | Cloud Firestore, Europa (`eur3`) |
| Dit samtykke | Versionen af det samtykke, du gav, og hvornår (afsnit 4). | Cloud Firestore, Europa (`eur3`) |

En slettet registrering bliver kun i backuppen som en markering, der fortæller dine andre telefoner, at den er slettet: dens tilfældige id, art, barne-id og ændringstidspunkt. Det, der blev registreret (tidspunkter og detaljer), fjernes fra backuppen. Telefonen, du slettede den på, beholder sin egen kopi i 7 dage og fjerner den så.

Loggen over måltider, søvn og bleer er **helbredsoplysninger om et barn**. Det er en særlig kategori af personoplysninger (databeskyttelsesforordningens art. 9), og de får ekstra beskyttelse.

## 4. Hvorfor, og på hvilket retsgrundlag

| Formål | Data | Retsgrundlag (GDPR) |
|---|---|---|
| Tage backup af loggen og gendanne den på en ny telefon | Loggen, barne-id'et | Dit **udtrykkelige samtykke**, art. 9, stk. 2, litra a, og art. 6, stk. 1, litra a. Du giver det ved at sætte kryds, før du logger ind. |
| Drive din konto: login, nulstilling af adgangskode, sletning af kontoen | E-mail, adgangskode-hash eller Google-id | **Aftale**, art. 6, stk. 1, litra b: det er den tjeneste, du beder om. |
| Holde tjenesten sikker og inden for dens gratis kvote, og slette ubrugte konti | Loginregistreringer, tidspunkter for brug af kontoen | **Legitim interesse**, art. 6, stk. 1, litra f: sikkerhed, og ikke at gemme data længere end nødvendigt. |
| Kunne påvise, at du har givet samtykke | Samtykkets version og tidspunkt | **Retlig forpligtelse**, art. 6, stk. 1, litra c, sammen med art. 7, stk. 1. |

Du giver samtykket som forælder eller indehaver af forældremyndigheden, for dit barns data. Du kan **til enhver tid trække det tilbage** ved at slette kontoen (Indstillinger › Konto › Slet konto…) eller ved at skrive til os. At trække samtykket tilbage sletter backuppen. Det påvirker ikke det, der skete med dit samtykke før. Hvis du loggede ind, før dette samtykke fandtes, sætter appen backuppen på pause, indtil du giver samtykke, og der uploades ikke mere i mellemtiden.

Backuppen er valgfri. Uden samtykke virker appen fuldt ud uden den, kun på din telefon og dit ur.

## 5. Hvem behandler data for os

- **Google Ireland Limited og Google LLC (Firebase)** driver backuppen som vores databehandler efter Firebases databehandlervilkår. Cloud Firestore gemmer loggen i Europa (multiregion `eur3`). Firebase Authentication kan behandle kontodata i USA. Sådanne overførsler bygger på Googles certificering under **EU-US Data Privacy Framework** og på **EU's standardkontraktbestemmelser** i Googles databehandlervilkår.
- **Google Play** distribuerer appen på Googles egne vilkår.

Ingen andre får data. Vi sælger dem ikke og bruger dem ikke til reklamer eller profilering, og vi bruger ingen analyse- eller nedbrudsrapporteringstjenester.

## 6. Hvor længe vi gemmer data

| Data | Gemmes, indtil |
|---|---|
| Loggen i backuppen | Du sletter registreringer (kun sletningsmarkeringen fra afsnit 3 bliver), eller du sletter kontoen, hvilket sletter hele backuppen og samtykket på én gang. |
| Kontoen | Du sletter den. En konto, der **ikke er brugt i 2 år** (intet login og ingen brug af appen), slettes automatisk med sin backup. Et ugentligt job tjekker det. |
| En backup, hvis konto er fjernet | Slettes af samme ugentlige job. |
| Firebases egne login- og sikkerhedslogs | De perioder, Google har fastsat for Firebase Authentication. |
| Data på din telefon og dit ur | Du sletter dem i appen eller afinstallerer appen. At logge ud eller slette kontoen beholder loggen på telefonen. |

## 7. Sikkerhed

Al trafik til backuppen er krypteret (TLS), og Google krypterer data, hvor de er gemt. Sikkerhedsregler lader hver konto læse og skrive kun sin egen log, i en fast form: der er intet sted at gemme et navn eller noget andet. Adgangskoder håndteres af Firebase Authentication og er aldrig synlige for os. Hvis et brud på sikkerheden nogensinde bringer dine data i fare, underretter vi Datatilsynet inden for 72 timer og dig uden unødig forsinkelse, hvor loven kræver det.

## 8. Dine rettigheder

| Rettighed | Sådan |
|---|---|
| **Indsigt** og **dataportabilitet** (art. 15, 20) | Indstillinger › Privatliv › **Download mine data** gemmer hele loggen og børnenes navne som en JSON-fil. Backuppen indeholder ikke mere end din telefon. Du kan også skrive til os og få en kopi af det, backuppen og din konto indeholder. |
| **Berigtigelse** (art. 16) | Ret enhver registrering i appen; backuppen følger med. |
| **Sletning** (art. 17) | Slet en registrering i appen, eller slet hele kontoen og backuppen: Indstillinger › Konto › **Slet konto…**. Uden telefonen kan du skrive til os fra kontoens adresse, så sletter vi den inden for en måned. |
| **Tilbagetrækning af samtykke** (art. 7, stk. 3) | Slet kontoen, eller skriv til os. |
| **Begrænsning** og **indsigelse** (art. 18, 21) | Skriv til os. |
| **Klage** (art. 77) | Til Datatilsynet, Carl Jacobsens Vej 35, 2500 Valby, [www.datatilsynet.dk](https://www.datatilsynet.dk), eller til myndigheden, hvor du bor. |

Vi svarer på henvendelser inden for en måned, gratis. Vi kan bede dig bekræfte, at kontoen er din, for eksempel ved at svare fra dens e-mailadresse.

## 9. Børn

Appen bruges af forældre og andre omsorgspersoner, ikke af børn. Data om barnet indtastes af forælderen, som også giver samtykket til dem (afsnit 4).

## 10. Ændringer

Når politikken ændres, offentliggøres den nye version her med en ny dato. Når en ændring påvirker, hvad backuppen gemmer eller hvorfor, beder appen om dit samtykke igen, før den tager backup af mere.
