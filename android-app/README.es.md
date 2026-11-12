# Kopi Chess para Android

*[Read in English](README.md)*

Una app sencilla para jugar desde el móvil contra los bots de tu propio PC, a
través del relé de Kopi. La app no sabe de ajedrez: el PC (Kopi Chess en
«modo servidor») juega, guarda y analiza. Plan:
[`docs/plan-app-android.md`](../docs/plan-app-android.md).

Capacitor + Vite + TypeScript, con Chessground (el mismo tablero que la web).
En desarrollo (fase F3): por ahora, el esqueleto de la app y un tablero fijo.

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
   y rellénalo. El *keystore* (`kopi-chess.jks`) vive fuera del repositorio,
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
