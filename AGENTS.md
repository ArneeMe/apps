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
  - `digipost/`: send brev i Digipost eller på papir, med vedlegg, faktura, lenke eller avtale. Samtaler med svar fra innboksen, historikk med status og hendelser. Se Digipost under.
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

## Digipost

- Appen snakker direkte med REST-API-et til Digipost, uten klientbiblioteket. Formatet er hentet fra `digipost-api-client-java` 18.4.2 og `api_v8.xsd`.
- Hvert kall signeres med virksomhetssertifikatet (.p12): SHA256withRSA over metode, sti, `date`, `x-content-sha256` og `x-digipost-userid`.
- Oppsett i appen: avsender-ID, sertifikat og passord. Produksjon og testmiljø har hvert sitt oppsett, siden testmiljøet bruker egen avsender-ID og eget sertifikat.
- Testmiljøet er `https://api.test.digipost.no`, og appen viser tydelig når det er på.
- Sertifikatet og passordet ligger bare i appens private lager.
- `Digipost.kt` er API-et, `Lager.kt` innstillinger, favoritter og historikk, `Ui.kt` felles tekster og rader.
- Mottaker: navn eller adresse søkes, 11 siffer er fødselsnummer, 9 siffer er org.nr., og tekst med `#` er en Digipost-adresse.
- Filer: PDF, HTML og bilder. Første fil er hoveddokument, resten vedlegg. Appen tar imot filer delt fra andre apper.
- Status for sendte brev hentes fra `/documents/<avsender>/<uuid>/status`.
- Papir: bare papir, papir som reserve, papir hvis ikke åpnet, og be om registrering med papir som reserve. Krever returadresse i innstillingene.
- Ekstra i Digipost er datatypene `invoice`, `externalLink` og `appointment` fra `digipost-data-types`.
- Samtaler slår sammen sendte brev og innboksen (`get_inbox` på `/<avsender>`) etter navn. Innboksen hentes når Samtaler åpnes.
- Papir som reserve er på som standard. Mottakere uten Digipost slår det på av seg selv, og appen stopper sending uten postadresse.
- Flere mottakere: samme brev sendes som én melding per mottaker.
- Dokumentdeling: `share-documents-request` som datatype, status fra `get_share_documents_request_state` på `/<avsender>`, stopp med `additional-data` til `stop_sharing`.
- Varsler: `Varsel.kt` planlegger en jobb med `JobScheduler` som henter innboksen og hendelser, og varsler om nye svar og om brev som åpnes, postlegges eller feiler. Arne sa ja til `POST_NOTIFICATIONS`. Jobben planlegges når appen åpnes, og overlever ikke omstart før appen er åpnet igjen.
- Kunder: `Kunder.kt` lagrer kundelisten i `kunder.json` på telefonen, med Digipost-status, postadresse, gruppe og flettefelt. CSV-import kjenner vanlige kolonnenavn, og ukjente kolonner blir flettefelt.
- Flettebrev: brevtekst skrevet i appen blir PDF per mottaker (`Pdf.kt`, `PdfDocument`). `{felt}` fylles inn i emne, brevtekst, e-postvarsel og faktura. Beløp, KID, konto og forfall hentes fra kunden når feltene i appen er tomme.
- Ikke laget: åpningskvittering, arkiv og batch.
- Sjekk uten Android SDK: kompiler `felles` og `digipost` med Kotlin mot `org.robolectric:android-all` fra Maven Central, og valider meldings-XML mot `api_v8.xsd` og `datatypes.xsd`.

## ZKP

Målet er at Pass-appen skal kunne erstatte ZKPassport-appen mot arnee.me/zkp, som bruker `@zkpassport/sdk` 0.14.2 i fast mode.

- `docs/zkp/bro.md`: protokollen mellom SDK og app: lenkeformat, broen, kryptering, meldinger, bevisformat og hva SDK-et sjekker.
- `docs/zkp/kretser.md`: kretsene, input, versjoner, bevismotor og testpass.

Ferdig:
1. Lese pass: PACE (ECDH-GM, P-256 og Brainpool P256) og BAC, DG1 og SOD, sjekk av hash og DSC-signatur. Testet mot ICAO 9303-eksempelet.
2. Testpass fra ZKR, signert med ZKPassport sin publiserte RSA-2048-testnøkkel, slik `createMockPassport` gjør.
3. Broen: secp256k1, AES-GCM, zlib og egen WebSocket-klient. Testet mot vektoren i ZKPassport sin Rust-klient. Appen viser forespørselen og kan avslå.
4. arnee.me (branch `zkp-velg-data`): velg alder, nasjonalitet og navn, testmodus som bryter, og sjekk av at hvert krav har et bevis.

5. Bevis: `zkp/` lager bevisene med ZKPassport sin egen kode, `@zkpassport/utils` 0.36 for input, `bb.js` 4.2 for bevis og kretser 0.18.0. Det er samme bb-versjon som SDK 0.14.2 verifiserer med. Arne sa ja til ett unntak fra «ingen biblioteker» for bevis, og det er denne bunten.
   - `zkp/src/bevis.mjs`: `lagBevis` tar DG1 og SOD, bygger input, laster kretser og lager DSC, ID-data, integritet, `compare_age` og `disclose_bytes`.
   - `zkp/web/`: Vite pakker det som en side for WebView. CI bygger den inn i `android/pass/src/main/assets/zkp/`.
   - `BevisMotor.kt`: skjult WebView. Appen serverer siden med COOP og COEP, så bb.js får tråder, og henter kretser, sertifikater og SRS selv med mellomlagring på disk.
   - Testene i `zkp/test/` kjører i CI (`.github/workflows/zkp.yml`): de lager bevis for et ZKR-testpass i Node og i Chrome, og verifiserer dem med SDK 0.14.2 i testmodus. Begge gir `verified: true`.
6. Flyt i appen: lenke fra arnee.me → velg pass → bevisene lages med en gang i bakgrunnen → Del sender `accept`, hvert `proof` og `done`.

Gjenstår:
- Prøve hele flyten på telefonen mot arnee.me i testmodus, og så med ekte pass.
- Ekte pass krever at landets CSCA ligger i ZKPassport sitt register på Ethereum mainnet.
- Fart: grunnbevisene (DSC, ID-data, integritet) avhenger ikke av forespørselen og kan lages når passet leses inn.

## Ikke laget ennå

- Lommebok for kort med strekkoder.

## Sjekk

- CI må være grønn. Lokalt trengs Android SDK: `cd android && ./gradlew assembleRelease`.
- Se på hver app i mørk og lys modus, og med nattmodus på.
- `node ~/Code/design/scripts/check-ai-look.mjs android`.
