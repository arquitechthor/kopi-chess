# Kopi Chess for Android

*[Leer en español](README.es.md)*

A small app to play your own PC's bots from your phone, through the Kopi
relay. The app knows no chess: the PC (Kopi Chess in "server mode") plays,
saves and analyses. Plan: [`docs/plan-app-android.md`](../docs/plan-app-android.md).

Capacitor + Vite + TypeScript, with Chessground (the same board as the web
app). Work in progress (phase F3): for now, the app shell and a static board.

## Develop

Node 24:

```bash
npm ci
npm test          # unit tests (Vitest)
npm run dev       # the web app in the browser
npm run build     # type check + build into dist/
```

## Android

Needs JDK 21 and the Android SDK (Android Studio installs both):

```bash
npm run sync                                 # build and copy into android/
cd android && ./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

## Signed release (AAB for Google Play)

Same procedure as `kopi-tools-app`, from the command line, no Android Studio
wizard. Step by step (versions, checks): the
[`release-aab` skill](../.claude/skills/release-aab/SKILL.md).

1. Copy `android/keystore.properties.example` to `android/keystore.properties`
   and fill it in. The keystore is the same as `kopi-tools-app`'s
   (`arquitechthor.jks`); it lives outside the
   repository, in `kopi-keys/`; neither file is ever committed.
2. In `android/app/build.gradle`: `versionName` = the Kopi Chess version,
   `versionCode` + 1.
3. Build and check the signature:

   ```bash
   npm run sync
   cd android && ./gradlew bundleRelease
   "$JAVA_HOME/bin/jarsigner" -verify app/build/outputs/bundle/release/app-release.aab   # "jar verified."
   ```

Without `keystore.properties`, `bundleRelease` still succeeds but the AAB is
**unsigned**.
