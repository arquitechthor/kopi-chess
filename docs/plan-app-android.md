# Plan: app Android (fase F)

- **Estado:** propuesta, con decisiones abiertas (§9). La parte de AWS ya está
  hecha y probada (2026-11-07); todo lo demás se puede hacer en cualquier
  máquina.
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
4. **Ver el historial** guardado en la cuenta, aunque el PC esté apagado, si
   la decisión D2 lo incluye (§9).

## 2. Qué no hace

- **No sabe de ajedrez.** Dibuja lo que el PC le dice (posición, jugadas
  legales, estado) y envía la jugada elegida, como hoy `board.js` en el
  navegador.
- **No guarda partidas ni las analiza.** Todo ocurre en el PC: bots, reglas,
  Stockfish, guardado e historial.
- **No borra partidas ni cambia la configuración del PC:** el PC solo acepta
  la lista blanca de operaciones (§4).
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
  cuenta (hace falta haber entrado con la cuenta Kopi). Mientras está
  encendido:
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
  reutilizan los objetos de la API REST (`ApiModel`), para no inventar otro
  formato.
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
- **Tecnología** (decisión D1): Capacitor + Vite + TypeScript + Chessground
  (el mismo tablero que la web) + Amplify Auth (SRP con
  `kopi-chess-mobile-client`, como `kopi-tools-app`).
- **Pantallas:**
  1. Entrar (correo y contraseña; registro y recuperación, en la web de Kopi).
  2. Inicio: «tu PC está conectado» o «no hay ningún PC en modo servidor»,
     con qué hacer para conectarlo.
  3. Elegir bot y color, o retomar una partida.
  4. Tablero: jugadas, turno, abandonar, resultado.
  5. Historial (si D2).
- **Idiomas:** inglés y español, como Kopi Chess (decisión D5).
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

- [ ] Decisiones de §9 tomadas.
- [ ] ADR-022: el móvil como cliente remoto del PC, el «modo servidor», la
      lista blanca y el protocolo remoto (`payload` con `op` y `v`).
- [ ] `protocolo-remoto.md`: cada `op` con su petición y su respuesta.

### Fase F2: el PC en modo servidor

- [ ] `kopi-adapter-remote`: conexión `role=server`, lista blanca y
      traducción a los casos de uso.
- [ ] Interruptor en la página de cuenta, aviso visible y nombre del equipo.
- [ ] Pruebas con el relé falso.

### Fase F3: la app

- [ ] `android-app/` con Capacitor, Vite y Chessground; CI.
- [ ] Entrar con la cuenta Kopi (Amplify, SRP).
- [ ] Presencia, elegir PC, elegir bot, jugar, retomar, abandonar.
- [ ] Historial desde la cuenta (si D2).
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

## 9. Decisiones abiertas

Cada una con una propuesta. Hasta decidirlas no conviene empezar la fase F3.

| # | Decisión | Propuesta |
|---|---|---|
| D1 | Tecnología de la app: Capacitor + web (como Kopi Tools) o nativa en Kotlin (como Kopi Player). | **Capacitor + Vite + TypeScript**, sin React: reutiliza Chessground y el aspecto de la web, y la app es pequeña. |
| D2 | ¿Ver el historial guardado en la cuenta desde el móvil (aunque el PC esté apagado)? | **Sí, de solo lectura** (lista y repetir la partida), en una versión posterior a la primera si retrasa la publicación. El historial en la cuenta ya está hecho (ADR-021). |
| D3 | ¿Jugar en línea contra un amigo desde el móvil? | **No en la primera versión.** Se podría más adelante por el mismo canal, con el PC haciendo de cliente del relé. |
| D4 | Nombre e identificador: «Kopi Chess» y `com.arquitechthor.kopichess`. | **Sí**, como `com.arquitechthor.kopitools` y `com.arquitechthor.kopimediaplayer`. |
| D5 | Idiomas: inglés y español (como Kopi Chess) o solo español (como las demás apps Kopi). | **Inglés y español**, según el idioma del móvil. |
| D6 | Versión de la app: la misma que Kopi Chess (se publican juntas) o una propia. | **La misma versión**, etiquetada a la vez: el protocolo remoto los acopla. |
| D7 | El modo servidor, ¿sigue encendido al reiniciar Kopi Chess? | **Sí, se recuerda**, y el aviso visible lo deja claro. Apagarlo es un clic. |
| D8 | ¿Precio y anuncios? | **Gratis y sin anuncios**, como el resto de Kopi. |
