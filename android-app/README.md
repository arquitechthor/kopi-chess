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

The signing keystore never goes into the repository.
