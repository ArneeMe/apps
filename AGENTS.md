# AGENTS.md

Dette er Arnes verksted for egne apper. Ikke en plattform, men en felles rigg der det går fort å bygge og dele, og der en agent bygger, drifter og overvåker.

Følg `arnes-stil` for kode, git og tekst. Følg designsystemet i [arneeme/design](https://github.com/arneeme/design), tema `waddle`, for alt som vises.

## Prinsipper

- Riggen skal overleve verktøyet. Kunnskapen ligger her som tekst, med denne filen som hovedfil. Agenten kan byttes ut.
- Arne eier retningen, repoet eier kunnskapen, agenten gjør jobben.
- Bruk administrerte tjenester som er åpen kildekode og helst europeiske, så alt kan flyttes hjem senere.
- Ingen sporing, analyse, reklame eller nettverkskall som ikke trengs for å gjøre jobben.
- Dataene er kjernen, ikke nettsiden. Bygg likevel ikke en felles kjerne før en ny klient trenger den. Det som bare bor på telefonen, blir på telefonen.

## Oppsett

- `android/`: ett Gradle-prosjekt med én modul per app. Kotlin med vanlige Views, ingen Compose og ingen biblioteker.
  - `felles/`: designet (`me.arnee.design`, `res/` med `waddle_*`, `ic_*`, fontene og pingvinen), temaet `Waddle` og `me.arnee.felles.Deler` med tittel, glider, felt og klikkbar rad.
  - `verktoy/` (`me.arnee.verktoy`, heter Nattmodus): lysstyrke, temperatur og rødt filter, tidsplan og hurtigknapp. Pakkenavnet er beholdt så installasjon og adb-tilganger lever videre.
  - `sovn/`: søvntimer. Stopper media og låser skjermen, 40 minutter som standard. Hurtigknapp.
  - `gratone/`: svart-hvit skjerm. Hurtigknapp.
  - `notater/`: notater og oppgaver som ren tekst i appens filer. Kan synke begge veier med en valgt mappe, for eksempel en Syncthing-mappe. Nyeste fil vinner, og slettinger følger med.
  - `avganger/`: neste avganger fra egne stopp, fra Entur sitt åpne API.
  - `utklipp/`: tekst og utklipp mellom telefon og PC. Se Utklipp under.
  - `sms/` (heter Meldinger): SMS- og MMS-app som kan være standard. `Pdu.kt` leser og bygger MMS-pakkene og har tester i `src/test`. `Mms.kt` henter, lagrer og sender gjennom Androids MMS-tjeneste, som selv kjenner operatørens oppsett.
  - `vaer/`: været for egne steder fra MET Norge, med stedssøk fra Kartverket.
  - `stille/`: Ikke forstyrr med tidsplan per ukedag og unntak for favoritter eller kontakter. Hurtigknapp.
  - `tall/` (heter Tallspill): tallrunden fra Countdown. Trekker tall og mål med 30 sekunder på klokka, og viser opptil ti løsninger, de korteste først. `Losning.kt` prøver alle veier og har tester i `src/test`.
  - `pass/`: les pass over NFC (PACE med BAC som reserve), sjekk signaturen, lag testpass, og ta imot ZKPassport-forespørsler fra nettsider over broen. Se ZKP under.
  - `butikk/`: installerer og oppdaterer appene fra releasen `siste`.
  - `apper.json`: listen over apper. CI bygger bare det som står her, og legger den på releasen med versjon og APK-navn, så Butikk vet hva som finnes. `ikon` er et ikon fra designsystemet og `farge` en ANSI-farge fra temaet.
  - `ikoner.py`: lager app-ikonet til hver modul fra `apper.json`. Plate i appens farge, symbol og strek, og pingvinen som titter opp fra kanten. Kjør `python3 android/ikoner.py ~/Code/design` og commit det som endrer seg.
- `pc/utklipp.py`: PC-siden av Utklipp.
- `.github/workflows/android.yml`: bygger alle apper på hver push. På standard-branchen legger den APK-ene og `apper.json` på releasen `siste`.

## Regler

- Farger, avstand og fonter kommer fra `WaddleTokens` og `WaddleUi`. Ingen hex eller rå tall i UI.
- Oppdater designet med `~/Code/design/scripts/sync-app.sh android/felles`, og commit det som endrer seg.
- Aldri tegn pingvinen selv. Én per skjerm. Skaler med hele tall uten utjevning.
- UI-tekst er norsk, kort og tørr. Knapper er verb.
- Én app per ting. En ny app er en ny modul: kopier `build.gradle.kts` fra en annen app, bytt navn, legg den i `settings.gradle.kts` og `apper.json` med `ikon` og `farge`, og kjør `ikoner.py`.
- Spør Arne før en app får nye tillatelser.

## Versjoner

`versionCode` er 100 pluss antall commits som rører appens mappe eller `felles`. En app som ikke er endret, får samme versjon, så Butikk melder bare om ekte oppdateringer.

## Installere

Første gang: installer Butikk fra PC-en. Etter det installeres og oppdateres alt fra Butikk.

```bash
curl -L -o butikk.apk https://github.com/arneeme/apps/releases/download/siste/butikk.apk && adb install -r butikk.apk
```

Noen apper trenger tilganger som bare kan gis med adb. Butikk viser kommandoene under hver app. I dag:

```bash
adb shell pm grant me.arnee.verktoy android.permission.WRITE_SECURE_SETTINGS
adb shell appops set me.arnee.verktoy SYSTEM_ALERT_WINDOW allow
adb shell pm grant me.arnee.gratone android.permission.WRITE_SECURE_SETTINGS
```

Søvntimer ber selv om å bli enhetsadministrator, så den kan låse skjermen.

Alle apper signeres med samme nøkkel. Den ligger bare i GitHub-secrets: `NOKKEL_BASE64`, `NOKKEL_ALIAS` og `NOKKEL_PASSORD`. Mister du den, må alle appene avinstalleres før neste versjon kan installeres.

## Utklipp

- Appen lager en kobling første gang: `https://ntfy.sh/<emne>#<nøkkel>`. Den står under Oppsett.
- Teksten krypteres med AES-GCM. Nøkkelen er PBKDF2-SHA256 av alt etter `#`, med emnet som salt. Den delen sendes aldri til serveren.
- ntfy er bare en postkasse og holder meldinger i 12 timer. Hver enhet lagrer sin egen historikk.
- ntfy er åpen kildekode. For å flytte hjem, kjør egen ntfy-server og bytt adressen i koblingen.
- PC: `pip install cryptography`, så `pc/utklipp.py koble '<kobling>'`. Deretter `send <tekst>`, `utklipp`, `les` og `kopier`.

## ZKP

Målet er at Pass-appen skal kunne erstatte ZKPassport-appen mot arnee.me/zkp, som bruker `@zkpassport/sdk` 0.14.2 i fast mode.

- `docs/zkp/bro.md`: protokollen mellom SDK og app: lenkeformat, broen, kryptering, meldinger, bevisformat og hva SDK-et sjekker.
- `docs/zkp/kretser.md`: kretsene, input, versjoner, bevismotor og testpass.

Ferdig:
1. Lese pass: PACE (ECDH-GM, P-256 og Brainpool P256) og BAC, DG1 og SOD, sjekk av hash og DSC-signatur. Testet mot ICAO 9303-eksempelet.
2. Testpass fra ZKR, signert med ZKPassport sin publiserte RSA-2048-testnøkkel, slik `createMockPassport` gjør.
3. Broen: secp256k1, AES-GCM, zlib og egen WebSocket-klient. Testet mot vektoren i ZKPassport sin Rust-klient. Appen viser forespørselen og kan avslå.
4. arnee.me (branch `zkp-velg-data`): velg alder, nasjonalitet og navn, testmodus som bryter, og sjekk av at hvert krav har et bevis.

Gjenstår, i denne rekkefølgen:
1. Bevismotor: noir_android (`v1.0.0-beta.20-2`, bb 4.2.0-aztecnr-rc.2) som innebygd bibliotek. Arne har sagt ja til ett innebygd bibliotek kun for bevis.
2. Kretser 0.18.0 fra `circuits2.zkpassport.id/testnet/by-version/0.18.0/manifest.json`, lastet ned i appen.
3. Input til kretsene, portet fra `@zkpassport/utils` 0.36.0 (`circuit-matcher.ts`), med Poseidon2 og Merkle-bevis fra sertifikatregisteret. Start med `compare_age` fra et testpass, og sammenlign med fiksturer laget i Node.
4. Send `accept`, bevisene og `done` over broen, og prøv mot arnee.me i testmodus.
5. Ekte pass til slutt. Landets CSCA må finnes i ZKPassport sitt register.

## Ikke laget ennå

- Lommebok for kort med strekkoder.

## Sjekk

- CI må være grønn. Lokalt trengs Android SDK: `cd android && ./gradlew assembleRelease`.
- Se på hver app i mørk og lys modus, og med nattmodus på.
- `node ~/Code/design/scripts/check-ai-look.mjs android`.
