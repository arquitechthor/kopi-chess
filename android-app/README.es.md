# Kopi Chess para Android

*[Read in English](README.md)*

Una app sencilla para jugar desde el móvil contra los bots de tu propio PC, a
través del relé de Kopi. La app no sabe de ajedrez: el PC (Kopi Chess en
«modo servidor») juega, guarda y analiza. Plan:
[`docs/plan-app-android.md`](../docs/plan-app-android.md).

Capacitor + Vite + TypeScript, con Chessground (el mismo tablero que la web).
En desarrollo (fase F3): la entrada con la cuenta Kopi (Amplify, SRP), los PCs
en modo servidor, retomar una partida o empezar otra contra un bot, jugar y
abandonar. Falta la prueba real de una partida completa por 4G.

El código:

- `src/remote/relayClient.ts`: el WebSocket con el relé (`role=device`):
  reconexión, *ping*, `presence` y cada petición emparejada con su respuesta.
- `src/remote/remotePc.ts`: las operaciones del
  [protocolo remoto](../docs/protocolo-remoto.md), con tipos.
- `src/play/board.ts`: lo que el tablero necesita de la respuesta del PC
  (jugadas legales, coronación, resultado). La app no sabe reglas de ajedrez.
- `src/screens.ts`: entrar, inicio, PC y tablero.

## Desarrollo

Node 24:

```bash
npm ci
npm test          # pruebas unitarias (Vitest)
npm run dev       # la app web en el navegador
npm run build     # comprobación de tipos y compilación en dist/
```

## Android

Hace falta JDK 21 y el SDK de Android (Android Studio instala los dos):

```bash
npm run sync                                 # compila y copia a android/
cd android && ./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

## Versión firmada (AAB para Google Play)

El mismo procedimiento que `kopi-tools-app`, por línea de comandos, sin el
asistente de Android Studio. Paso a paso (versiones, comprobaciones): la
[skill `release-aab`](../.claude/skills/release-aab/SKILL.md).

1. Copia `android/keystore.properties.example` a `android/keystore.properties`
   y rellénalo. El *keystore* es el mismo de `kopi-tools-app`
   (`arquitechthor.jks`); vive fuera del repositorio,
   en `kopi-keys/`; ninguno de los dos se commitea nunca.
2. En `android/app/build.gradle`: `versionName` = la versión de Kopi Chess,
   `versionCode` + 1.
3. Compila y comprueba la firma:

   ```bash
   npm run sync
   cd android && ./gradlew bundleRelease
   "$JAVA_HOME/bin/jarsigner" -verify app/build/outputs/bundle/release/app-release.aab   # "jar verified."
   ```

Sin `keystore.properties`, `bundleRelease` termina bien pero el AAB sale **sin
firmar**.
