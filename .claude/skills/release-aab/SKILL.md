---
name: release-aab
description: Genera el Android App Bundle (.aab) firmado de la app Android de Kopi Chess (android-app/) por línea de comandos, sin Android Studio, y lo copia a kopi-keys/, listo para subir a Google Play Console. Solo en una máquina con el SDK de Android.
---

Genera el AAB de release firmado de `android-app/` de principio a fin y lo deja en
`kopi-keys/kopi-chess-release.aab`, fuera del repositorio, junto al resto de credenciales de
firma. Es el mismo procedimiento que `kopi-tools-app` (skill `release-aab` de ese repo).

## 1. Requisitos

- **JDK 21 y el SDK de Android** (Android Studio instala los dos). `ANDROID_HOME` apuntando
  al SDK, o `android-app/android/local.properties` con `sdk.dir=C:/ruta/al/Sdk` (no se
  commitea). Sin SDK, para y díselo al usuario: en esa máquina solo se compila en la CI.
- **`android-app/android/keystore.properties`** (no se commitea nunca), copiado de
  `keystore.properties.example` y rellenado (`storeFile`, `storePassword`, `keyAlias`,
  `keyPassword`). Si no existe, para y pídeselo al usuario: sin él el build termina bien pero
  produce un AAB **sin firmar**, que Play Console rechaza.
- **El keystore** es el mismo de `kopi-tools-app`: `arquitechthor.jks`, en `kopi-keys/`
  (carpeta hermana de los repositorios, fuera de git). Solo está en la máquina donde se
  publica `kopi-tools-app`; si no está en esta, para y díselo al usuario. Nunca crees uno
  nuevo ni lo copies dentro del repositorio: el `keystore.properties` de `kopi-tools-app`
  en esa máquina tiene los mismos valores (alias y contraseñas) que hacen falta aquí. Si se
  pierde (o sus contraseñas) no se podrán actualizar las apps publicadas.

## 2. Versión

En `android-app/android/app/build.gradle`, `defaultConfig`:

- **`versionName`**: la versión de Kopi Chess que se publica (D6), por ejemplo `"1.2.0"`, la
  misma que los `pom.xml` y `android-app/package.json` (sin `-SNAPSHOT`).
- **`versionCode`**: **+1 en cada subida** a Google Play, sea cual sea el cambio de versión.
  Play Console rechaza un `versionCode` repetido o menor.

No cambies la versión por tu cuenta: es parte de la release (CONTRIBUTING, «Releases»), y la
decide el usuario.

## 3. Build

Desde `android-app/`:

```bash
npm ci
npm test
npm run build                            # tipos + Vite -> dist/
npx cap sync android                     # copia dist/ al proyecto Android
cd android && ./gradlew bundleRelease    # gradlew.bat en cmd/PowerShell
```

El AAB sale en `android-app/android/app/build/outputs/bundle/release/app-release.aab`.

## 4. Comprobar que está firmado

Que `bundleRelease` no falle no basta (sin `keystore.properties` también termina bien):

```bash
"$JAVA_HOME/bin/jarsigner" -verify android/app/build/outputs/bundle/release/app-release.aab
```

La primera línea debe ser `jar verified.`; los avisos de certificado autofirmado o «PKIX path
building failed» que salen después son normales. Si dice que no está firmado,
`keystore.properties` no se está leyendo: revisa que exista y que `storeFile` use barras
normales (`/`).

## 5. Copiar a kopi-keys/

Desde `android-app/`:

```bash
cp android/app/build/outputs/bundle/release/app-release.aab ../../kopi-keys/kopi-chess-release.aab
```

El `.aab` nunca se commitea (`android-app/.gitignore`).

## 6. Confirmar al usuario

Dile el `versionCode` y `versionName` del AAB, y recuérdale que subirlo a Play Console
(prueba interna primero, fase F4) es un paso manual suyo.
