# Plan: jugador contra jugador

- **Estado:** **prioridad 1** (decisión del usuario, 2026-11-07), seguida de
  la app Android (§11). El orden de todos los modos está en
  [`plan-modos-de-juego.md`](plan-modos-de-juego.md) §1.
- **Dónde se hace cada parte:** la parte de AWS (fases B y C) se prepara en
  la máquina de desarrollo que tiene acceso a AWS; la otra no lo tiene. Ver
  §10, «Qué necesita AWS».
- **Fecha:** 2026-10-29, sobre la versión 1.0.0.
- **Alcance:** dos modos nuevos, que se entregan **juntos** en una sola
  versión:
  - **Local:** dos personas en la misma máquina, cada una con su nombre.
  - **En línea:** dos personas, cada una con su Kopi Chess en *localhost*,
    con una cuenta Kopi.
- **Después (§11), prioridad 2:** una app Android sencilla para jugar contra
  los bots de **tu** PC a través del mismo relé. Vive **en este
  repositorio**, porque está fuertemente acoplada a Kopi Chess (protocolo,
  casos de uso, versión).

---

## 1. Supuestos

Son las reglas de partida; si alguno cambia, el plan cambia con él.

- **Partidas amistosas, basadas en el honor.** No es competitivo: sin Elo ni
  clasificación. La plataforma **no protege de trampas**; cada jugador tiene
  Stockfish en su PC y nada impide consultarlo.
- **Coste mínimo.** Nada encendido todo el mes: cada jugador pone su CPU
  (reglas, Stockfish, análisis) y AWS solo guarda y reparte jugadas, sin
  servidor propio.
- **Tiempo ilimitado por jugada.** Sin reloj. Una partida puede quedarse a
  medias y retomarse cuando sea, tanto si el rival está conectado como si no;
  mientras tanto aparece **en curso**, como hoy una partida contra un bot.
- **Pero no para siempre: 30 días sin jugadas terminan la partida**, y gana
  quien hizo la última jugada (el otro la dejó enfriar). Se borra del relé.
- **El relé no guarda partidas terminadas:** cada una se borra en cuanto los
  dos jugadores tienen el final (§4). El historial vive solo en cada PC.
- **Cuenta Kopi obligatoria para el modo en línea**, la misma de las otras
  apps Kopi (Cognito). Además, cada persona crea un **id de Kopi**, público,
  para invitar y ser invitada **sin mostrar nunca su correo**.
- **Las jugadas se validan siempre en local**, con el dominio de cada app:
  al hacer una jugada y al recibir la del rival. AWS no conoce las reglas del
  ajedrez.
- **El modo local no necesita cuenta** ni conexión.

---

## 2. Cómo encaja

```
 PC de Ana                              AWS (backend Kopi)                  PC de Luis
┌──────────────────────┐  HTTPS/wss   ┌──────────────────────────────┐   ┌──────────────────────┐
│ Kopi Chess (local)   │  salientes,  │ API Gateway (HTTP + WebSocket)│   │ Kopi Chess (local)   │
│ reglas, Stockfish,   │ ───────────► │ Lambda (relé, sin reglas)     │ ◄─│ reglas, Stockfish,   │
│ análisis, PostgreSQL │ ◄─────────── │ DynamoDB: ids, partidas,      │ ─►│ análisis, PostgreSQL │
│ solo en 127.0.0.1    │  token Kopi  │ jugadas, invitaciones         │   │ solo en 127.0.0.1    │
└──────────────────────┘              │ Cognito (cuenta Kopi)         │   └──────────────────────┘
                                      └──────────────────────────────┘
```

- **Solo conexiones salientes** desde cada PC: sin abrir puertos ni tocar el
  router.
- **ADR-016 se mantiene:** la interfaz sigue respondiendo solo a esta
  máquina. Lo nuevo es que la app también *habla* con el backend Kopi.
- **Cada PC guarda su copia** de la partida en su PostgreSQL: historial,
  revisión y análisis funcionan igual que hoy, sin conexión.
- **El relé** ordena y reparte jugadas; no decide si son legales.

---

## 3. El id de Kopi (común a todas las apps Kopi)

Una pieza del **backend compartido de Kopi**, no de Kopi Chess, para que
otras apps la puedan usar después.

- **Qué es:** un nombre público y único, asociado al `sub` de Cognito.
  - De 3 a 20 caracteres: letras, números y `_`.
  - Sin distinguir mayúsculas al comparar (`Ana_92` y `ana_92` son el
    mismo), pero se muestra como se escribió.
- **Dónde vive:** una tabla de DynamoDB (`KopiIds`), con una escritura
  condicional para garantizar que es único. **No** en un alias de Cognito: los
  alias de un User Pool ya creado no se pueden cambiar.
- **Operaciones** (rutas del API Gateway compartido, con el *authorizer* de
  Cognito):
  - Reservar el mío, si aún no tengo.
  - Consultar el mío.
  - Comprobar si uno existe (para invitar), sin devolver nada más que eso.
- **Privacidad:** el correo nunca sale del backend; entre jugadores solo
  viaja el id.
- **Cambiarlo:** fuera del primer alcance. Si se permite más adelante, las
  partidas guardan el `sub` y muestran el id vigente.
- **Borrar la cuenta:** libera el id (y entra en el procedimiento de borrado
  de cuenta que ya tienen las apps Kopi).

---

## 4. El relé en AWS

Lambdas pequeñas en el API Gateway compartido (`api.kopitools.link`), con el
*authorizer* de Cognito. **No contienen reglas de ajedrez.**

### Datos (DynamoDB bajo demanda)

- **`PvpGames`:** una fila por partida.
  - Jugadores (`sub` de cada color), estado (`INVITED`, `ACTIVE`,
    `FINISHED`, `DECLINED`), posición inicial, número de la última jugada,
    resultado y forma de terminar.
  - Hora de la última jugada aceptada (la del relé, no la de cada PC).
  - **Se borra** (con sus jugadas) en tres casos:
    - **Terminada:** en cuanto la app del rival confirma que ha recibido el
      final. No en el instante en que termina: si el rival está desconectado,
      se quedaría sin saber que la partida acabó (un abandono, unas tablas).
    - **Inconclusa:** a los **30 días sin jugadas** (TTL de DynamoDB, que se
      renueva con cada jugada aceptada).
    - **Invitación sin respuesta:** a los 30 días.
  - Una terminada que nadie confirma también cae a los 30 días por el mismo
    TTL, así que nada se queda en el relé indefinidamente.
- **`PvpMoves`:** las jugadas, por partida y número (`ply`), con la jugada en
  UCI y el hash Zobrist de la posición resultante (§6).

### Operaciones

- **Invitar** a un id de Kopi, eligiendo color: blancas, negras o al azar.
- **Ver mis invitaciones** pendientes, **aceptar** o **rechazar**.
- **Listar mis partidas** en línea y **traer una partida** (sus jugadas desde
  un número dado: así se retoma).
- **Enviar una jugada** con su número: el relé solo comprueba que es mi
  partida, que me toca por color y que ese número es el siguiente. Una
  escritura condicional garantiza que **cada número se acepta una vez**.
- **Abandonar**, **ofrecer tablas** y **aceptar tablas**.
- **Anotar el final** por mate, ahogado o regla de tablas: lo comunica la
  app que hace la última jugada, junto con ella; el relé lo apunta sin
  comprobarlo, y la app del rival lo verifica al recibir esa jugada (§6).

### Tiempo real, solo si los dos están conectados

- Un **WebSocket** (API Gateway) por app abierta: cuando llega una jugada o
  una invitación, el relé avisa a quien esté conectado.
- Si no lo está, no pasa nada: lo recibe la próxima vez que abra la app. **Sin
  correos ni notificaciones *push*** (coste y complejidad).

---

## 5. Cambios en Kopi Chess, por anillos

### Dominio (`kopi-domain`)

- **Un jugador por color**, en lugar de `humanColor` + `botProfile`:

  ```java
  sealed interface Player permits Player.Bot, Player.Local, Player.Remote {
      record Bot(BotProfile profile) implements Player {}
      record Local(String name) implements Player {}        // modo local, o "yo" en línea
      record Remote(String kopiId) implements Player {}     // el rival en línea
  }
  ```

  Las partidas contra un bot siguen igual (`Local` contra `Bot`); las
  importadas, como hoy.
- **Dos formas de terminar nuevas** (hoy solo hay mate, ahogado, material
  insuficiente, repetición, 50 jugadas y abandono):
  - **Tablas de mutuo acuerdo.**
  - **Inactividad:** 30 días sin jugadas; gana quien hizo la última.
- **Quién puede mover:** en el modo local, cualquiera de las dos personas en
  su turno; en línea, solo `Local` en su turno, y `Remote` llega por el relé.
- *Perft*, SAN y las reglas no cambian.

### Aplicación (`kopi-application`)

- **Casos de uso nuevos:** crear partida local, invitar, aceptar o rechazar,
  recibir la jugada del rival, ofrecer y aceptar tablas, sincronizar.
- **Puertos nuevos:**
  - `KopiAccount`: quién soy (mi `sub` y mi id de Kopi), reservar el id.
  - `PvpRelay`: invitar, enviar jugada, traer jugadas, abandonar, tablas.
- **Recibir una jugada** = validarla con el dominio, como si la hiciera yo. Si
  no es legal o el hash no coincide, la partida se marca **desincronizada**
  (§6).

### Adaptadores

- **Web:**
  - Nueva partida: «Contra un bot», «Dos personas en este equipo» (dos
    nombres) y «Contra un amigo (en línea)».
  - Modo local: el tablero se gira en cada turno.
  - Una bandeja de invitaciones; la jugada del rival llega a la página por
    SSE, como ya llega la del bot.
  - Historial: el nombre o el id del rival; el balance contra cada bot no
    cuenta estas partidas.
  - «Entrar con Kopi» y la pantalla para crear el id.
- **Persistencia:** una migración de Flyway: los jugadores de cada color, el
  id de la partida en el relé, la última jugada sincronizada y la forma de
  terminar nueva. Las partidas existentes pasan a `Local` contra `Bot`.
- **Nuevo `kopi-adapter-relay`:** implementa `PvpRelay` y `KopiAccount` con
  HTTP y WebSocket hacia el backend Kopi.
- **Seguridad:** un **cliente de aplicación nuevo** en el User Pool de Kopi
  (*managed login*, PKCE, callback a `http://localhost:8080/…`), con el flujo
  hecho en `kopi-adapter-relay` sin Spring Security (ADR-019). Los *tokens* se
  guardan en la base de datos local. El cliente que usan las otras apps no se
  toca.
- **REST:** las mismas operaciones en `/api/v1`, para scripts y una futura
  app Android.

---

## 6. Validación y coherencia

- **Cada app valida todo** con su dominio: la jugada propia antes de
  enviarla, la del rival al recibirla.
- **Orden:** el número de jugada (`ply`) lo fija el relé, que acepta cada
  número una sola vez. Si dos PCs con la misma cuenta mueven a la vez, gana el
  primero y el otro recibe la jugada aceptada al sincronizar.
- **Comprobación cruzada:** cada jugada viaja con el hash Zobrist de la
  posición resultante (el dominio ya lo calcula). Si al aplicarla el hash no
  coincide, o la jugada es ilegal, la partida se marca **desincronizada**: se
  muestra un aviso y se puede abandonar. No debería ocurrir entre
  dos versiones honestas de la app; si ocurre, es un fallo o una versión
  incompatible.
- **Versión del protocolo** en cada mensaje desde el principio: una app no
  acepta mensajes de una versión que no entiende y lo dice.
- **La regla de los 30 días se aplica en cada PC**, sin que el relé tenga que
  avisar: cada app guarda la hora (del relé) de la última jugada aceptada y,
  al abrir una partida en línea en curso que no está en el relé o lleva 30
  días sin jugadas, la termina por **inactividad** dando la victoria a quien
  movió el último. Las dos apps llegan al mismo resultado porque parten de la
  misma jugada y la misma hora.
- **Un caso límite aceptado:** si alguien abandona u ofrece tablas que se
  aceptan y el rival no abre su app en 30 días, el relé borra la partida sin
  que el rival reciba el final; su app aplicará la regla de inactividad, que
  puede no coincidir (por ejemplo, si quien abandonó fue también quien movió
  el último). Es raro y, en partidas amistosas, aceptable; evitarlo exigiría
  guardar finales en el relé sin límite.

---

## 7. Flujos

- **Primera vez:** «Entrar con Kopi» → Cognito → crear mi id de Kopi.
- **Invitar:** escribo el id del amigo y el color → el amigo ve la invitación
  al abrir su app → la acepta → la partida aparece en curso para los dos.
- **Jugar con los dos conectados:** muevo → mi app valida y envía → el relé
  la acepta y avisa al rival → su app la valida y la muestra.
- **Jugar por correspondencia:** muevo y cierro la app → el rival la recibe
  cuando abre la suya, aunque sea días después.
- **Retomar:** al abrir una partida en línea, la app trae del relé las
  jugadas que le falten.
- **Terminar:** mate o ahogado (lo ven las dos apps), abandono, o tablas
  ofrecidas y aceptadas. En cuanto el rival recibe el final, la partida se
  borra del relé. Cada uno puede analizarla en su PC.
- **Una partida que se enfría:** si pasan 30 días sin jugadas, termina por
  inactividad y gana quien movió el último; el relé la borra y cada app lo
  anota al abrirla.
- **Modo local:** «Dos personas en este equipo» → dos nombres → se juega en
  el mismo tablero, girándolo en cada turno → historial, PGN y análisis como
  cualquier otra partida.

---

## 8. Seguridad y privacidad

- **La interfaz sigue siendo local** (ADR-016). Lo que sale a Internet son
  llamadas con el *token* de la cuenta Kopi.
- **El correo no se muestra nunca:** entre jugadores solo viaja el id.
- **El relé solo deja tocar una partida a sus dos jugadores**, y solo mover
  en el turno propio.
- **Límites en el relé** (invitaciones y jugadas por minuto) para que nadie
  dispare el coste.
- **Trampas:** fuera de alcance por diseño (§1). Partidas entre conocidos.

---

## 9. Costes

Prácticamente **0 $/mes** con el uso esperado, como el resto del backend
Kopi:

- **Lambda, DynamoDB bajo demanda y Cognito** (hasta 10.000 usuarios activos
  al mes): dentro de las capas gratuitas.
- **API Gateway:** HTTP por petición y WebSocket por mensaje y minuto de
  conexión. Una partida son decenas de mensajes: céntimos.
- **Lo caro, Stockfish, lo pone cada jugador** en su PC.
- Sin servidor encendido, sin base de datos gestionada, sin balanceador.

---

## 10. Fases

Se entregan juntas en una versión, pero se construyen en este orden: cada
fase deja algo que funciona y se prueba.

### Qué necesita AWS

El backend Kopi es compartido y su código vive en `kopi-media-admin`
(Lambdas en `documentation/Lambdas/`, tablas en
`documentation/dynamodb-schema.md`, un *script* de *bootstrap* idempotente
por funcionalidad en `documentation/scripts/`). Ese repositorio va directo a
`main`; Kopi Chess, con *pull request* (§2.1 del plan de desarrollo).

- **Solo se puede hacer en la máquina con acceso a AWS:** crear y probar de
  verdad lo de las fases B y C (tabla `KopiIds` y sus rutas, el cliente de
  aplicación con *callback* a `localhost`, las tablas del relé, sus Lambdas,
  el API WebSocket, los límites y las alarmas), y la prueba con `curl` de la
  aceptación de la fase C.
- **Se puede hacer en cualquier máquina:** la fase A entera, y en las fases B
  y D todo lo que se prueba con un relé falso (`PvpRelay` y `KopiAccount`
  falsos). Por eso, cuando se trabaje en la máquina con AWS, se adelanta la
  parte de AWS de las fases B y C aunque la fase A no esté terminada: deja el
  relé listo para que la otra máquina programe contra él.

### Fase A: el dominio con personas, y el modo local

- [x] ADR-018: jugadores por color (`Player`), tablas de mutuo acuerdo e
      inactividad como formas de terminar.
- [x] `Player` en el dominio; `Game` con un jugador por color; tablas por
      acuerdo e inactividad como terminaciones. Pruebas unitarias; *perft*
      intacto.
- [x] Migración de Flyway (V5); las partidas existentes, `Local` contra `Bot`.
- [x] Caso de uso y páginas de «Dos personas en este equipo» (nombres,
      tablero que gira, historial, PGN con los nombres), modo oficina, API
      (`POST /api/v1/games/local`, `…/draw`) y tablas de mutuo acuerdo.
- [x] Prueba E2E: una partida local completa hasta la revisión
      (`PlayAndReviewE2EIT`, mate del pastor con el tablero girando).

**Aceptación:** dos personas juegan en un PC una partida completa y la
analizan; las partidas contra bots y las importadas siguen igual.

### Fase B: cuenta Kopi e id de Kopi

- [x] ADR-019: el id de Kopi, común a las apps Kopi (2026-11-07).
- [x] Backend Kopi: tabla `kopi_ids` y sus rutas (reservar, consultar,
      comprobar), con un *authorizer* propio (2026-11-07, ADR-019).
- [x] Cliente de aplicación nuevo en el User Pool, con callback a
      `localhost` (`kopi-chess-client`; valores en
      [`protocolo-rele.md`](protocolo-rele.md) §1).
- [x] Kopi Chess: «Entrar con Kopi» (PKCE en `kopi-adapter-relay`, sin
      Spring Security), *tokens* guardados en local (V6, `kopi_session`),
      página `/account` para crear el id. Puertos `KopiAccount` y
      `KopiSessionStore`.

**Aceptación:** entro con mi cuenta Kopi desde mi Kopi Chess local y reservo
un id que nadie más puede tener.

### Fase C: el relé

- [x] ADR-020: relé de partidas en AWS sin reglas, validación local, y su
      relación con ADR-016 (2026-11-07). Contrato en
      [`protocolo-rele.md`](protocolo-rele.md).
- [x] Tabla `kopi_chess_games` (una partida por ítem, con sus jugadas
      dentro, en lugar de `PvpGames` y `PvpMoves`; ADR-020): TTL de 30 días
      que se renueva con cada jugada, y borrado al confirmar el final el rival.
- [x] Lambdas: invitar, invitaciones, aceptar, rechazar, cancelar, listar,
      traer, mover (escritura condicional por número), abandonar, tablas.
- [x] WebSocket para avisar a quien esté conectado (`kopi-chess-ws`).
- [x] Límites por usuario. Pruebas de las Lambdas
      (`kopi-media-admin/documentation/scripts/test-chess-relay.ps1`).

**Estado (2026-11-07):** fases B (parte de AWS) y C hechas y desplegadas. La
prueba de extremo a extremo invoca las Lambdas con dos cuentas sintéticas
contra DynamoDB real (56 comprobaciones, incluida una jugada repetida y otra
fuera de turno), y con `curl` se comprobó que API Gateway rechaza peticiones
sin *token* o con uno no válido (`401`) y que el WebSocket rechaza conexiones
sin *token* (`401`) o con otra versión del protocolo (`426`).

**Prueba con una cuenta real (2026-11-07), en verde:** login en la página
gestionada con PKCE y cambio del código por los *tokens*; renovación con el
*refresh token*; las rutas con el *token* real; **aislamiento**: el mismo
*token* da `401` en `/notes` y en `/study/*`; WebSocket conectado con ese
*token* (`ping` → `pong`); y una partida contra una cuenta sintética en la que
llegaron todos los avisos (`invited`, `accepted`, `move`, `resigned`) en unos
100 ms, hasta borrarse del relé tras el `ack`. La cuenta del dueño tiene el id
de Kopi `guzman6001`.

**Aceptación:** con dos cuentas de prueba y `curl`, se juega una partida de
principio a fin; una jugada con número repetido o fuera de turno se rechaza.

### Fase D: Kopi Chess en línea

- [x] `kopi-adapter-relay` (`PvpRelay` por HTTP y avisos por WebSocket con
      reconexión), con la versión del protocolo.
- [x] Casos de uso: invitar, aceptar, recibir y validar, sincronizar al
      abrir, tablas, abandonar, confirmar el final al relé, terminar por
      inactividad a los 30 días; marca de partida desincronizada
      (`OnlineGames`, `OnlineService`; tabla `online_game`, V7). Sincroniza
      al recibir un aviso y, por si se pierde, cada 2 minutos
      (`OnlineSyncWorker`).
- [x] Páginas: «Jugar en línea» (invitar, invitaciones, partidas), jugada
      del rival por SSE en la página de la partida, ofertas de tablas.
- [x] API REST equivalente (`/api/v1/online`, `…/draw-offer|accept|decline`).
- [x] Prueba E2E: `TwoComputersTest` juega con **dos Kopi Chess contra un
      relé falso** que aplica las reglas del protocolo (invitar, aceptar,
      jugadas validadas al otro lado, desincronización, mate, abandono,
      tablas, inactividad con el reloj de las pruebas, otra cuenta).
- [x] **Prueba real (2026-11-07)**, con Kopi Chess de esta rama en
      `localhost:8080` contra el relé y Cognito de verdad: el dueño entró con
      su cuenta (`guzman6001`) por la página de login de Kopi; una cuenta
      sintética (`KopiTester`, manejada invocando las Lambdas) le invitó; la
      invitación apareció sola por el WebSocket; aceptó y jugaron 1. e4 e5 2.
      ♘f3 ♘c6 (las jugadas del rival llegaron al tablero abierto por SSE, con
      su Zobrist comprobado); ofreció tablas y el rival las rechazó; el rival
      abandonó y la partida terminó sola en su pantalla, se confirmó y el relé
      la borró. Falta repetirla con dos cuentas reales en dos equipos.

**Aceptación:** dos personas en dos PCs juegan una partida con los dos
conectados y otra por correspondencia (cerrando la app entre jugadas), y
cada una la analiza en su PC; al terminar, la partida ya no está en el relé.
Una partida sin jugadas en 30 días (simulado con el reloj de las pruebas)
termina igual en los dos PCs.

### Fase E: publicación

- [ ] Documentación: README, CONTRIBUTING (cómo probar sin cuenta real),
      diseño y este plan actualizado.
- [ ] Changelog y versión (probablemente 1.1.0: no rompe nada de lo
      anterior).

---

## 11. Después: jugar desde el móvil contra los bots del PC (prioridad 2)

Una vez hecho el modo en línea, el relé y la conexión saliente del PC ya
existen. Esta extensión los usa **entre los dispositivos de una misma
persona**: una app Android sencilla se conecta, a través del relé, al Kopi
Chess abierto en el PC en «modo servidor», y juega contra sus bots.

### Supuestos

- **Los dos dispositivos son de la misma cuenta Kopi.** El relé solo une
  conexiones con el mismo `sub` de Cognito.
- **La app es tonta:** ni guarda partidas ni las analiza. Dibuja lo que el
  PC le dice (posición, jugadas legales, estado) y envía la jugada elegida,
  como hoy `board.js` en el navegador. No sabe de ajedrez.
- **Todo ocurre en el PC:** bots, reglas, Stockfish, guardado e historial.
  Las partidas jugadas desde el móvil aparecen en el historial del PC y se
  analizan allí, como cualquier otra.

### Cómo encaja

```
 Móvil (app tonta)          AWS (relé Kopi)                PC (Kopi Chess, «modo servidor»)
┌───────────────┐  wss    ┌──────────────────────┐  wss   ┌──────────────────────────────┐
│ tablero       │ ──────► │ reenvía mensajes     │ ─────► │ adaptador «remoto»           │
│ + cuenta Kopi │ ◄────── │ entre dispositivos   │ ◄───── │ → casos de uso → bot, BD     │
└───────────────┘         │ de la MISMA cuenta   │        └──────────────────────────────┘
                          └──────────────────────┘
```

En términos hexagonales, el móvil es un cliente más de los mismos casos de
uso, como las páginas web y la API REST. `PlayMove` ya devuelve la jugada del
bot en la misma respuesta, así que basta un ida y vuelta por jugada.

### Qué hace falta, sobre lo construido en las fases A–E

- **Relé: un canal entre dispositivos.**
  - Cada conexión se registra con su papel: «PC servidor» o «móvil».
  - El relé reenvía mensajes solo entre conexiones del mismo `sub`, sin
    guardar nada.
  - Le dice al móvil si el PC está conectado.
- **PC: un adaptador de entrada nuevo** (por ejemplo `kopi-adapter-remote`).
  - Traduce los mensajes a los casos de uso existentes: listar bots, crear
    partida, jugar, abandonar, ver una partida y listar las que están en
    curso para retomarlas. Reutiliza los objetos de respuesta de la API REST.
  - **«Modo servidor» apagado por defecto**, con un interruptor y un aviso
    visible mientras está activo.
  - **Lista blanca de operaciones:** jugar contra los bots sí; borrar
    partidas o cambiar la configuración, no.
  - ADR-016 sigue en pie: la interfaz web sigue siendo solo local y la
    conexión es saliente.
- **App Android:** Capacitor + Chessground + Amplify, lo mismo que ya usa
  `kopi-tools-app`. Vive **en este repositorio** (por ejemplo, una carpeta
  `android-app/` junto a `backend/`), con su propio CI y su versión ligada a
  la del protocolo del relé.
  - Pantallas: entrar con Kopi → «tu PC está conectado» → elegir bot y
    color, o retomar una partida → tablero → resultado.
  - Publicación en Google Play: ficha, iconos y política de privacidad.

### Lo que sale casi gratis

- **Desde cualquier red** (4G, otra wifi), sin tocar el router. Conectar el
  móvil directamente al PC por la red de casa obligaría a abrir Kopi Chess a
  la red local y a añadir autenticación, justo lo que ADR-016 evita.
- **Retomar en otro dispositivo:** una partida empezada en el PC se sigue en
  el móvil y al revés.
- **Mover a la vez en el PC y en el móvil** no rompe nada: el bloqueo
  optimista que ya existe (versión de la partida, `412`) deja pasar la
  primera jugada y la otra pantalla se actualiza.
- **Más adelante,** el móvil podría *ver* el análisis que hizo el PC, sin
  calcular nada.

### Coste

Prácticamente **0 $**. Lo único continuo es la conexión del PC en modo
servidor: un WebSocket abierto todo el mes son unos 43.000 minutos de
conexión, del orden de un céntimo. Cada jugada son unos pocos mensajes.

### Limitaciones

- **El PC tiene que estar encendido**, sin suspensión, con Docker y Kopi
  Chess arrancados y el modo servidor activo. Si no, el móvil solo puede
  decir «tu PC no está conectado». Encenderlo a distancia queda fuera de
  alcance.
- **Los dos dispositivos necesitan Internet**, porque todo pasa por el relé.
- **Retardo:** unos cientos de milisegundos por el viaje móvil → AWS → PC y
  vuelta, más lo que piense el bot (hasta 1 s con QUEEN). En ajedrez sin
  reloj no se nota.

### Fase F (después de E)

- [ ] ADR-021: el móvil como cliente remoto del PC, el «modo servidor» y su
      lista blanca de operaciones.
- [x] Relé: canal entre dispositivos de la misma cuenta y presencia del PC
      (2026-11-07, con el cliente de Cognito de la app,
      `kopi-chess-mobile-client`; contrato en
      [`protocolo-rele.md`](protocolo-rele.md) §8 y §10, probado contra AWS
      real). La entrega entre dos WebSocket reales se probará con la app.
- [ ] `kopi-adapter-remote` con el interruptor de «modo servidor», el aviso
      visible y la lista blanca. Pruebas con un relé falso.
- [ ] App Android (Capacitor + Chessground + Amplify): entrar, ver si el PC
      está conectado, elegir bot y color o retomar, jugar, abandonar.
- [ ] Publicación en Google Play.

**Aceptación:** con el PC en modo servidor, desde el móvil por 4G se juega
una partida completa contra un bot; la partida aparece en el historial del
PC y se analiza allí. Con el modo servidor apagado, el móvil no puede hacer
nada.

**Esfuerzo orientativo:** unas 3–5 semanas a tiempo parcial sobre las fases
A–E (relé, unos días; adaptador del PC, unos días; la app y su ficha en la
tienda, el resto).

---

## 12. Riesgos

- **El backend es compartido con otras apps Kopi.**
  - **Señal:** un cambio del relé o de las rutas del id afecta a otra app.
  - **Acción:** rutas y tablas propias con prefijo, desplegadas por separado;
    el cliente de aplicación nuevo, aparte del que usan las demás.
- **Versiones distintas de Kopi Chess en cada PC.**
  - **Señal:** partidas desincronizadas.
  - **Acción:** versión del protocolo en cada mensaje y aviso claro de
    «actualiza Kopi Chess».
- **Coste por abuso.**
  - **Señal:** picos en API Gateway o DynamoDB.
  - **Acción:** límites por usuario y alarma de facturación.
- **El «modo servidor» deja el PC abierto a órdenes remotas** (§11).
  - **Señal:** alguien con acceso a la cuenta Kopi juega desde fuera.
  - **Acción:** apagado por defecto, aviso visible mientras está activo,
    solo conexiones de la misma cuenta y lista blanca de operaciones (nada
    de borrar ni configurar).
- **Finales que no llegan** (el caso límite de §6).
  - **Señal:** alguien ve una victoria por inactividad donde esperaba un
    abandono o unas tablas.
  - **Acción:** aceptado en partidas amistosas; si molesta, guardar en el
    relé solo el resultado (unos bytes) más tiempo que las jugadas.

---

## 13. Fuera de alcance

- Reloj y controles de tiempo (ver [`ideas-futuro.md`](ideas-futuro.md)).
- Elo, clasificaciones y emparejamiento con desconocidos.
- Protección contra trampas.
- Correos o notificaciones *push* de jugadas e invitaciones.
- Chat, espectadores y partidas públicas.
- Cambiar el id de Kopi.
- En la app Android (§11): guardar partidas, analizar en el móvil y jugar sin
  el PC encendido.
