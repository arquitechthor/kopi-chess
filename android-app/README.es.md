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

El *keystore* de firma nunca va al repositorio.
