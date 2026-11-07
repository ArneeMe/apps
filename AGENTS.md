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

- `android/`: Android-appen Verktøy. Kotlin med vanlige Views, ingen Compose og ingen biblioteker.
  - `me.arnee.verktoy.MainActivity`: forsiden med én boks per modul.
  - `me.arnee.verktoy.<modul>/`: én pakke per modul. Første modul er `rodfilter`.
  - `me.arnee.design` og `res/` med `waddle_*`, `ic_*` og fontene: kopiert fra designsystemet. Ikke rediger for hånd.
  - `res/drawable*-nodpi/pingvin.png` og `mipmap-nodpi/ic_launcher.png`: pingvinen fra `build/waddle/penguin/png/` i design.
- `.github/workflows/android.yml`: bygger på hver push og lager release når en tag `v*` blir pushet.

## Regler

- Farger, avstand og fonter kommer fra `WaddleTokens` og `WaddleUi`. Ingen hex eller rå tall i UI.
- Oppdater designet med `~/Code/design/scripts/sync-app.sh android/app`, og commit det som endrer seg.
- Aldri tegn pingvinen selv. Én per skjerm. Skaler med hele tall uten utjevning.
- UI-tekst er norsk, kort og tørr. Knapper er verb.
- Nye moduler får sin egen pakke og sin egen boks på forsiden. Spør Arne før en modul får nye tillatelser.

## Utgivelse

Appen blir installert og oppdatert med Obtainium fra GitHub Releases.

1. Bump ingenting i koden. `versionName` kommer fra taggen, `versionCode` fra kjøringsnummeret i GitHub Actions.
2. Tag `main` som `vX.Y.Z` og push taggen.
3. Workflowen bygger, signerer og legger `verktoy-vX.Y.Z.apk` på releasen.

Signeringsnøkkelen ligger bare i GitHub-secrets: `NOKKEL_BASE64`, `NOKKEL_ALIAS` og `NOKKEL_PASSORD`. Mister du den, må appen avinstalleres før neste versjon kan installeres.

## Sjekk

- `cd android && ./gradlew assembleDebug` må gå gjennom. Det trenger Android SDK.
- Se på appen i mørk og lys modus, og med rødfilteret på.
- `node ~/Code/design/scripts/check-ai-look.mjs android/app/src`.
