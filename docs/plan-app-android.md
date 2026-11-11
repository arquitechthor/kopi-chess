# Plan: app Android (fase F)

- **Estado:** decisiones tomadas (§9), [ADR-022](adr/022-movil-cliente-remoto-del-pc.md)
  y [protocolo remoto](protocolo-remoto.md) escritos (fase F1). La parte de
  AWS ya está hecha y probada (2026-11-07); todo lo demás se puede hacer en
  cualquier máquina.
- **Fecha:** 2026-11-07, sobre la versión 1.1.0.
- **Origen:** §11 del [plan de jugador contra jugador](plan-jugador-contra-jugador.md),
  que fija la idea: una app sencilla para jugar desde el móvil contra los bots
  del propio PC. Este plan la baja a tareas.
- **Contrato con el relé:** [`protocolo-rele.md`](protocolo-rele.md) §8 (el
  cliente de Cognito de la app) y §10 (el canal entre dispositivos).

---

## 1. Qué hace la app

1. **Entrar** con la cuenta Kopi (correo y contraseña; la misma cuenta que en
   el PC).
2. **Ver si el PC está conectado.** Si hay varios PCs en modo servidor, elegir
   uno por su nombre («PC de casa»).
3. **Jugar contra los bots del PC:**
   - elegir bot y color, o retomar una partida en curso;
   - mover; el PC responde con la jugada del bot;
   - abandonar.

## 2. Qué no hace

- **No sabe de ajedrez.** Dibuja lo que el PC le dice (posición, jugadas
  legales, estado) y envía la jugada elegida, como hoy `board.js` en el
  navegador.
- **No guarda partidas ni las analiza.** Todo ocurre en el PC: bots, reglas,
  Stockfish, guardado e historial.
- **No borra partidas, no cambia la configuración del PC ni enseña el
  historial** (D2): el PC solo acepta la lista blanca de operaciones (§4).
- **No enciende el PC a distancia.**

## 3. Cómo encaja

```
 Móvil (app)                AWS (relé Kopi)                 PC (Kopi Chess, «modo servidor»)
┌────────────────┐  wss   ┌──────────────────────┐  wss    ┌──────────────────────────────────┐
│ Chessground    │ ─────► │ kopi-chess-ws         │ ─────►  │ kopi-adapter-remote               │
│ + Amplify (SRP)│ ◄───── │ role=device ↔ server  │ ◄─────  │ → casos de uso → bots, BD         │
└────────────────┘        │ misma cuenta, sin     │         └──────────────────────────────────┘
                          │ guardar nada (§10)    │
                          └──────────────────────┘
```

En términos hexagonales, el móvil es un cliente más de los mismos casos de
uso, como las páginas web y la API REST. `PlayMove` ya devuelve la jugada del
bot en la misma respuesta: un ida y vuelta por jugada.

## 4. El PC: `kopi-adapter-remote`

Un adaptador de entrada nuevo, con su módulo Maven:

- **«Modo servidor»,** apagado por defecto. Se enciende en la página de
  cuenta (hace falta haber entrado con la cuenta Kopi) y se recuerda al
  reiniciar (D7). Mientras está encendido:
  - el PC mantiene una conexión `role=server&name=<nombre del equipo>` con el
    relé (la misma lógica de reconexión y *ping* que `RelayWebSocket`);
  - todas las páginas muestran un aviso visible: «Modo servidor: tu móvil
    puede jugar contra los bots de este equipo».
- **Lista blanca de operaciones.** Cada `relay` trae un `payload`
  `{ "op": …, … }` y el PC responde con otro `relay` con el mismo `id`:

  | `op` | Caso de uso | Respuesta |
  |---|---|---|
  | `hello` | — | versión de Kopi Chess y del protocolo remoto, nombre del equipo |
  | `bots` | `ListBots` | los bots con su nivel |
  | `games` | `ListGames` (en curso, contra un bot) | las partidas para retomar |
  | `new` | `CreateGame` | la partida creada (y la jugada del bot si abre él) |
  | `game` | `GetGame` | posición, jugadas, jugadas legales, estado |
  | `move` | `PlayMove` | la partida tras la jugada y la respuesta del bot |
  | `resign` | `Resign` | la partida terminada |

  Cualquier otra `op` responde `{ "error": "NOT_ALLOWED" }`. Las respuestas
  tienen la misma forma que las de la API REST, con sus propios `record`
  (los adaptadores no se conocen entre sí).
- **Versión del protocolo remoto** dentro de `payload` (`"v": 1`), aparte de
  la del relé: el relé no mira dentro, y así la app y el PC pueden avisar de
  que hay que actualizar uno de los dos.
- **Mover a la vez en el PC y en el móvil** no rompe nada: el bloqueo
  optimista que ya existe (versión de la partida) deja pasar la primera
  jugada, y la otra pantalla se actualiza.
- **ADR-016 sigue en pie:** la interfaz web sigue siendo solo local, y la
  conexión con el relé es saliente.
- **Pruebas** con el relé falso (`FakeRelay`), como `TwoComputersTest`: un
  «móvil» de prueba juega una partida entera por el canal, y una `op` fuera
  de la lista blanca se rechaza.

## 5. La app

- **En este repositorio,** en `android-app/` junto a `backend/`, porque
  depende del protocolo y de los casos de uso de Kopi Chess.
- **Tecnología** (D1): Capacitor + Vite + TypeScript, sin React, con
  Chessground (el mismo tablero que la web) y Amplify Auth (SRP con
  `kopi-chess-mobile-client`, como `kopi-tools-app`).
- **Identificador** (D4): `com.arquitechthor.kopichess`, nombre «Kopi
  Chess». **Licencia** (D8): GPL-3.0-or-later; gratis y sin anuncios.
- **Pantallas:**
  1. Entrar (correo y contraseña; registro y recuperación, en la web de Kopi).
  2. Inicio: «tu PC está conectado» o «no hay ningún PC en modo servidor»,
     con qué hacer para conectarlo.
  3. Elegir bot y color, o retomar una partida.
  4. Tablero: jugadas, turno, abandonar, resultado.
- **Idiomas** (D5): inglés y español, según el idioma del móvil.
- **Conexión:** un WebSocket `role=device`; se reconecta al volver la app a
  primer plano y pide `presence` y el estado de la partida.
- **Pruebas:** unitarias del cliente del canal (Vitest), con un relé falso en
  memoria.
- **CI propio:** un *workflow* que compila y prueba `android-app/` cuando
  cambia, y otro que construye el AAB firmado al etiquetar una versión.

## 6. Publicación

- **Google Play:** ficha, iconos, capturas, categoría «Juegos de mesa»,
  clasificación de contenido y formulario de seguridad de datos.
- **Privacidad:** la app guarda la sesión de la cuenta Kopi en el móvil; no
  guarda partidas. Los textos de `kopi-web` (privacidad y eliminación de
  cuenta) añaden la app.
- **Firma:** un *keystore* nuevo en `kopi-keys/` (nunca en el repositorio).

## 7. Fases

### Fase F1: decisiones y ADR

- [x] Decisiones de §9 tomadas (2026-11-07).
- [x] [ADR-022](adr/022-movil-cliente-remoto-del-pc.md): el móvil como
      cliente remoto del PC, el «modo servidor», la lista blanca y el
      protocolo remoto (`payload` con `op` y `v`).
- [x] [`protocolo-remoto.md`](protocolo-remoto.md): cada `op` con su
      petición y su respuesta.

### Fase F2: el PC en modo servidor

- [x] `kopi-adapter-remote`: lista blanca y traducción de cada `op` a los
      casos de uso (puerto `RemoteRequestHandler`).
- [x] Conexión `role=server` en `kopi-adapter-relay` (puerto
      `ServerConnection`), que entrega las peticiones a ese puerto y
      responde `SERVER_MODE_OFF` con el modo apagado.
- [ ] Interruptor en la página de cuenta, aviso visible y nombre del equipo.
- [x] Pruebas con el relé falso (`FakeRelay`, un WebSocket mínimo en las
      pruebas de `kopi-adapter-relay`).

### Fase F3: la app

- [ ] `android-app/` con Capacitor, Vite y Chessground; CI.
- [ ] Entrar con la cuenta Kopi (Amplify, SRP).
- [ ] Presencia, elegir PC, elegir bot, jugar, retomar, abandonar.
- [ ] Prueba real: una partida completa desde el móvil por 4G.

### Fase F4: publicación

- [ ] Textos legales en `kopi-web`.
- [ ] Ficha de Google Play, AAB firmado, prueba interna y producción.
- [ ] Changelog y versión.

**Aceptación:** con el PC en modo servidor, desde el móvil por 4G se juega
una partida completa contra un bot; la partida aparece en el historial del
PC y se analiza allí. Con el modo servidor apagado, el móvil no puede hacer
nada.

**Esfuerzo orientativo:** 3–5 semanas a tiempo parcial: F1, unos días; F2,
una semana; F3, dos o tres semanas; F4, lo que tarde la revisión de Google
Play.

## 8. Riesgos

- **El PC apagado o suspendido** es el caso normal fuera de casa: la app debe
  decirlo con claridad, y no parecer rota.
- **API Gateway cierra las conexiones a las 2 horas** y tras 10 minutos sin
  mensajes: el PC y la app hacen *ping* cada 5 minutos y se reconectan solos.
- **Android cierra los WebSocket en segundo plano:** la app reconecta y pide
  el estado al volver; nunca da por hecha una jugada sin respuesta del PC.
- **Revisión de Google Play:** una app que solo funciona con un PC propio
  encendido necesita una ficha y unas instrucciones muy claras, y una cuenta
  de prueba para el revisor.

## 9. Decisiones (tomadas el 2026-11-07)

| # | Decisión | Qué se decidió |
|---|---|---|
| D1 | Tecnología de la app | **Capacitor + Vite + TypeScript**, sin React, con Chessground. |
| D2 | ¿Ver el historial desde el móvil? | **No.** El móvil solo juega contra los bots del PC. |
| D3 | ¿Jugar en línea contra un amigo desde el móvil? | **No en la primera versión;** queda documentado para después (§10). |
| D4 | Nombre e identificador | **«Kopi Chess»**, `com.arquitechthor.kopichess`. |
| D5 | Idiomas | **Inglés y español**, según el idioma del móvil. |
| D6 | Versión | **La misma que Kopi Chess,** etiquetadas y publicadas juntas. |
| D7 | ¿El modo servidor sigue encendido al reiniciar? | **Sí, se recuerda,** con el aviso visible en todas las páginas. |
| D8 | Precio y licencia | **Gratis, sin anuncios y código libre** (GPL-3.0-or-later, como el resto del repositorio). |

## 10. Después de la primera versión

- **Jugar en línea contra un amigo desde el móvil** (D3). El móvil seguiría
  hablando solo con su PC: nuevas operaciones del protocolo remoto
  (`protocolo-remoto.md` §5) que el PC traduce a su cliente del relé de
  partidas. Así el móvil sigue sin saber de ajedrez, y cada jugada se valida
  en el PC como ahora.
- **Publicar también en F-Droid,** al ser código libre, si la app no
  necesita nada propietario (Amplify es Apache-2.0).
