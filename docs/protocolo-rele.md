# Protocolo del relé (versión 1)

El contrato entre Kopi Chess y el backend Kopi para el modo en línea:
el id de Kopi ([ADR-019](adr/019-id-de-kopi.md)) y el relé de partidas
([ADR-020](adr/020-rele-de-partidas.md)). Es lo que implementan las Lambdas
de `kopi-media-admin/documentation/Lambdas/kopi-chess/` y lo que usará el
futuro `kopi-adapter-relay`, y lo que debe imitar un relé falso en las
pruebas.

- **Estado:** versión 1, 2026-11-07. El mismo día, sin cambiar de versión
  (solo se añaden rutas y mensajes), se añadieron el historial en la cuenta
  (§9) y el canal entre dispositivos para la app Android (§8 y §10). Están
  desplegados en AWS y probados contra AWS real
  (`test-chess-history-devices.ps1` de `kopi-media-admin`, 43 de 43).
  Kopi Chess usa el historial desde la 1.2 ([ADR-021](adr/021-historial-en-la-cuenta.md));
  el canal entre dispositivos, todavía no.
- **Cambios:** un cambio que rompa clientes sube la versión. Añadir campos a
  una respuesta no la sube: el cliente ignora lo que no conoce.

---

## 1. Conexión y autenticación

- **Login:** página de login gestionada de Cognito, *Authorization Code* +
  PKCE, cliente público `kopi-chess-client` (sin secreto).
- **Valores** (creados el 2026-11-07 con `bootstrap-chess-relay.ps1` de
  `kopi-media-admin`; no son secretos, y se apuntan también en su
  `config/cognito-values.txt`):

  | Qué | Valor |
  |---|---|
  | Client ID | `6fonqj1j1fnaa4ujgk2nf887nv` |
  | *Issuer* (OIDC) | `https://cognito-idp.eu-south-2.amazonaws.com/eu-south-2_Yunp5ZGUR` |
  | Dominio de login | `https://eu-south-2yunp5zgur.auth.eu-south-2.amazoncognito.com` |
  | URL de vuelta | `http://localhost:8080/login/oauth2/code/kopi` |
  | *Scopes* | `openid email` |
  | API HTTP | `https://api.kopitools.link` |
  | WebSocket | `wss://01q3xfi4m6.execute-api.eu-south-2.amazonaws.com/prod` |

  Con el *issuer*, Spring Security descubre el resto
  (`/.well-known/openid-configuration`): basta
  `spring.security.oauth2.client.provider.kopi.issuer-uri`, el `client-id`,
  `client-authentication-method: none` y los *scopes*.
- **HTTP:** `https://api.kopitools.link`, con dos cabeceras en cada petición:
  - `Authorization: Bearer <access token>`
  - `X-Kopi-Chess-Protocol: 1`
- **Cuerpos** en JSON (`Content-Type: application/json`), en UTF-8.
- **Colores** siempre en minúsculas: `"white"` y `"black"`.
- **Fechas** en ISO 8601 UTC (`2026-11-07T18:30:00.000Z`).

## 2. Errores

Todas las respuestas de error tienen la misma forma:

```json
{ "status": 409, "error": "Conflict", "code": "WRONG_PLY", "detail": "Se esperaba la jugada 12." }
```

`code` es estable y es lo que debe mirar Kopi Chess; `detail` es un texto en
español para depurar, no para la interfaz.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | JSON no válido o falta un campo |
| 400 | `INVALID_KOPI_ID` | el id no cumple el formato (§3) |
| 400 | `RESERVED_KOPI_ID` | el id está reservado |
| 400 | `INVALID_FEN` | la posición inicial no tiene forma de FEN |
| 400 | `INVALID_MOVE_FORMAT` | `uci` o `zobrist` sin el formato de §5.4 |
| 400 | `INVALID_END` | final de partida no válido (§5.4) |
| 400 | `CANNOT_INVITE_YOURSELF` | el rival es uno mismo |
| 401 | — | sin *token*, caducado o de otro cliente (lo responde API Gateway, sin `code`) |
| 403 | `PENDING_APPROVAL` | la cuenta Kopi aún no está aprobada |
| 403 | `NO_KOPI_ID` | hay que reservar un id antes de jugar en línea |
| 403 | `NOT_ALLOWED` | la operación no es de este jugador (p. ej. aceptar una invitación propia) |
| 404 | `KOPI_ID_NOT_FOUND` | no existe ese id |
| 404 | `GAME_NOT_FOUND` | no existe, caducó o no es una partida de este jugador |
| 409 | `KOPI_ID_TAKEN` | otra cuenta tiene ese id |
| 409 | `ALREADY_HAS_KOPI_ID` | esta cuenta ya tiene otro id (no se cambia) |
| 409 | `WRONG_STATUS` | la partida no está en el estado que pide la operación |
| 409 | `NOT_YOUR_TURN` | no le toca a este color |
| 409 | `WRONG_PLY` | el número de jugada no es el siguiente (otra jugada llegó antes) |
| 409 | `NO_DRAW_OFFER` | no hay oferta de tablas del rival que aceptar o rechazar |
| 409 | `TOO_MANY_MOVES` | la partida llegó a 1.000 medias jugadas |
| 409 | `REV_CONFLICT` | historial: la partida cambió en otro equipo (§9.4); trae `current` |
| 413 | `TOO_LARGE` | historial: `data` pasa de 300.000 caracteres (§9.4) |
| 426 | `PROTOCOL_MISMATCH` | falta `X-Kopi-Chess-Protocol` o no es `1`: actualizar Kopi Chess |
| 429 | `TOO_MANY_REQUESTS` | límite por usuario (§6) |
| 500 | `INTERNAL` | fallo del relé |

## 3. Id de Kopi

Formato: `^[A-Za-z0-9_]{3,20}$`. Único sin distinguir mayúsculas; se devuelve
como se escribió al reservarlo.

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /kopi-id` | — | `200 { "kopiId": "Ana_92" }` · `404 NO_KOPI_ID` |
| `PUT /kopi-id` | `{ "kopiId": "Ana_92" }` | `201 { "kopiId": "Ana_92" }` · `200` si ya era el mío · `409 KOPI_ID_TAKEN` · `409 ALREADY_HAS_KOPI_ID` |
| `GET /kopi-id/{kopiId}` | — | `200 { "kopiId": "Ana_92" }` (como se escribió) · `404 KOPI_ID_NOT_FOUND` |

Nunca se devuelve el correo ni el `sub` de nadie.

## 4. La partida

```json
{
  "gameId": "5f0c…",
  "status": "ACTIVE",
  "white": { "kopiId": "Ana_92" },
  "black": { "kopiId": "luis" },
  "myColor": "white",
  "invitedBy": "white",
  "initialFen": null,
  "firstMover": "white",
  "lastPly": 12,
  "lastMoveAt": "2026-11-07T18:30:00.000Z",
  "drawOfferBy": null,
  "result": null,
  "termination": null,
  "finishedBy": null,
  "createdAt": "2026-11-07T18:00:00.000Z",
  "updatedAt": "2026-11-07T18:30:00.000Z",
  "expiresAt": "2026-12-07T18:30:00.000Z",
  "moves": [ { "ply": 1, "uci": "e2e4", "zobrist": "463b96181691fc9c", "at": "…" } ]
}
```

- **`status`:** `INVITED` → `ACTIVE` → `FINISHED`, o `INVITED` → `DECLINED`.
- **`initialFen`:** `null` es la posición inicial normal. Si no, el FEN que
  eligió quien invitó (el relé solo comprueba su forma).
- **`firstMover`:** el color que juega la jugada 1 (sale del FEN). Le toca
  a `firstMover` si `lastPly` es par, y al otro si es impar.
- **`expiresAt`:** cuándo caduca: 30 días desde la última jugada aceptada o
  desde la invitación. Pasada esa fecha, la partida responde `404` y cada
  Kopi Chess aplica la regla de inactividad (plan, §6).
- **`result`:** `"1-0"`, `"0-1"`, `"1/2-1/2"` o `null`.
- **`termination`:** `CHECKMATE`, `STALEMATE`, `INSUFFICIENT_MATERIAL`,
  `THREEFOLD_REPETITION`, `FIFTY_MOVE_RULE`, `RESIGNATION`, `DRAW_AGREEMENT`
  o `null`. La inactividad no aparece nunca: la decide cada PC.
- **`finishedBy`:** el color que terminó la partida (la última jugada, el
  abandono o la aceptación de tablas).
- **`moves`:** solo en `GET /chess/games/{gameId}`, desde `fromPly`.

## 5. Operaciones de partida

### 5.1 Invitar

`POST /chess/games`

```json
{ "opponent": "luis", "color": "white", "initialFen": null }
```

- `color`: el mío, `"white"`, `"black"` o `"random"`.
- `initialFen`: opcional.
- `201` con la partida en `INVITED`. Errores: `NO_KOPI_ID`,
  `KOPI_ID_NOT_FOUND`, `CANNOT_INVITE_YOURSELF`, `INVALID_FEN`,
  `TOO_MANY_REQUESTS`.

### 5.2 Listar y traer

- `GET /chess/games` → `200 { "games": [ … ] }`, sin `moves`: todas las de
  este jugador que no han caducado ni se han borrado, en cualquier estado.
  Es lo que Kopi Chess llama al arrancar para sincronizar.
- `GET /chess/games/{gameId}?fromPly=13` → `200` con la partida y las jugadas
  desde la 13 (por defecto, todas). Así se retoma.

### 5.3 Responder a una invitación

`POST /chess/games/{gameId}/{acción}`, sin cuerpo:

| Acción | Quién | De → a | Respuesta |
|---|---|---|---|
| `accept` | el invitado | `INVITED` → `ACTIVE` | `200` partida |
| `decline` | el invitado | `INVITED` → `DECLINED` | `200` partida |
| `cancel` | quien invitó | `INVITED` → borrada | `204` |

### 5.4 Jugar

`POST /chess/games/{gameId}/move`

```json
{ "ply": 13, "uci": "g1f3", "zobrist": "a1b2c3d4e5f60718", "end": null }
```

- `ply`: tiene que ser `lastPly + 1`. Si otra jugada llegó antes, `409
  WRONG_PLY`: hay que traer la partida y volver a validar.
- `uci`: `^[a-h][1-8][a-h][1-8][qrbn]?$`.
- `zobrist`: el hash Zobrist de la posición **después** de la jugada, en
  hexadecimal, 16 caracteres en minúsculas (los 64 bits de `Zobrist`).
- `end`: solo si esta jugada termina la partida por las reglas:
  `{ "result": "1-0", "termination": "CHECKMATE" }`.
  - `CHECKMATE`: el resultado tiene que dar la victoria a quien mueve.
  - `STALEMATE`, `INSUFFICIENT_MATERIAL`, `THREEFOLD_REPETITION`,
    `FIFTY_MOVE_RULE`: `"1/2-1/2"`.
  - El relé lo apunta sin comprobarlo; la app del rival lo verifica al
    aplicar la jugada.
- `200` con la partida, sin `moves`. Jugar rechaza la oferta de tablas
  pendiente **del rival** (la propia se mantiene).

### 5.5 Abandonar y tablas

`POST /chess/games/{gameId}/{acción}`, sin cuerpo, solo en `ACTIVE`:

| Acción | Efecto |
|---|---|
| `resign` | `FINISHED`, `RESIGNATION`, gana el rival |
| `offer-draw` | `drawOfferBy` = mi color. Si el rival ya había ofrecido, se aceptan: `DRAW_AGREEMENT` |
| `accept-draw` | con oferta del rival: `FINISHED`, `DRAW_AGREEMENT`, `"1/2-1/2"` · sin ella: `409 NO_DRAW_OFFER` |
| `decline-draw` | con oferta del rival: `drawOfferBy` = `null` · sin ella: `409 NO_DRAW_OFFER` |

Todas responden `200` con la partida.

### 5.6 Confirmar el final

`POST /chess/games/{gameId}/ack`, sin cuerpo.

- En una partida `FINISHED` o `DECLINED`: «ya lo he visto». Quien la terminó
  (o rechazó la invitación) ya cuenta como confirmado; cuando confirma el
  otro, la partida **se borra** del relé. `204`.
- Es idempotente: repetirlo responde `204`, y también `404` si ya se borró.
  Kopi Chess debe tratar los dos como «hecho».

## 6. Límites por usuario

- 20 invitaciones por hora.
- 120 operaciones por minuto, todas las rutas juntas, salvo las de subir y
  borrar en el historial (§9), que tienen su propio límite: 300 por minuto.
  Borrar todo el historial: 10 veces por hora.
- Canal entre dispositivos (§10): 600 mensajes `relay` por minuto.
- Al pasarse: `429 TOO_MANY_REQUESTS`. Kopi Chess reintenta más tarde.

## 7. Avisos en tiempo real (WebSocket)

- **URL:** `wss://01q3xfi4m6.execute-api.eu-south-2.amazonaws.com/prod?token=<access token>&protocol=1`. El *token* va en la URL porque los clientes
  WebSocket de navegador no pueden mandar cabeceras.
- **Al conectar,** el relé verifica el *token* (ADR-020). Si no vale, rechaza
  la conexión (`401` en el *handshake*).
- **Mensajes del relé**, en JSON:

  ```json
  { "type": "game", "gameId": "5f0c…", "event": "move", "ply": 13, "protocol": 1 }
  ```

  `event`: `invited`, `accepted`, `declined`, `cancelled`, `move`,
  `resigned`, `draw-offered`, `draw-accepted`, `draw-declined`. Se avisa a
  los dos jugadores, en todas sus conexiones abiertas.
- **El aviso no trae datos:** al recibirlo, Kopi Chess llama a
  `GET /chess/games/{gameId}?fromPly=…`. Un aviso perdido no rompe nada.
- **Mantener viva la conexión:** API Gateway la cierra tras 10 minutos sin
  mensajes y, en cualquier caso, a las 2 horas. Kopi Chess manda
  `{ "type": "ping" }` cada 5 minutos (el relé responde
  `{ "type": "pong" }`) y reconecta al cerrarse, sincronizando después con
  `GET /chess/games`.

## 8. La app Android

La app Android de Kopi Chess (fase F del
[plan de jugador contra jugador](plan-jugador-contra-jugador.md), §11) usa
su propio cliente de Cognito. Inicia sesión con SRP desde Amplify, como las
demás apps Kopi, sin página de login ni URL de vuelta.

| Qué | Valor |
|---|---|
| Client ID (`kopi-chess-mobile-client`) | `6kgqvf70nuvmfc89obppl2t398` |
| Flujos | `ALLOW_USER_SRP_AUTH`, `ALLOW_REFRESH_TOKEN_AUTH` (sin secreto, sin OAuth) |
| User Pool | `eu-south-2_Yunp5ZGUR` (región `eu-south-2`) |
| *Tokens* | acceso 1 hora, *refresh* 30 días |

Las rutas HTTP de Kopi Chess (`/kopi-id*`, `/chess/*`) y el WebSocket
aceptan los *tokens* de los dos clientes de Kopi Chess, y de ningún otro: con
ellos no se abre el resto de la API de Kopi.

## 9. Historial en la cuenta

Opcional: guardar las partidas de Kopi Chess en la cuenta Kopi y
sincronizarlas entre los equipos de la misma persona (idea «Historial en la
cuenta Kopi» de [`ideas-futuro.md`](ideas-futuro.md)). El relé solo guarda y
ordena; **no mira dentro de las partidas**, y la unión de dos versiones la
hace Kopi Chess.

### 9.1 La entrada

Una por cuenta y partida:

```json
{
  "gameId": "0b6c5a52-4a9e-4d0e-9a51-2f3c7c1d8e10",
  "rev": 3,
  "deleted": false,
  "changedAt": "2026-11-07T18:30:00.000Z",
  "data": { "format": 1, "…": "lo que decida Kopi Chess" }
}
```

- **`gameId`:** el UUID de la partida en Kopi Chess. El relé lo guarda y lo
  devuelve en minúsculas.
- **`rev`:** la revisión. Empieza en 1 y cada subida o borrado la sube en 1.
  Es lo que evita que un equipo pise sin saberlo lo que subió otro
  (concurrencia optimista).
- **`deleted`:** `true` en una **lápida**: la partida se borró en algún
  equipo y los demás deben borrar su copia. Una lápida no lleva `data`
  (`null`) y caduca a los 180 días.
- **`data`:** un objeto JSON de hasta 300.000 caracteres. Su forma la decide
  Kopi Chess (por ejemplo jugadas, jugadores, resultado y fechas), con un
  campo `format` propio para poder cambiarla. El relé no lo interpreta.

### 9.2 Reglas

- **Solo el dueño** ve y cambia sus entradas: la cuenta sale del *token*,
  nunca de la petición.
- **Subir exige la `rev` vista por última vez** (0 si es nueva). Si otro
  equipo cambió la entrada entretanto, el relé no la pisa: responde
  `409 REV_CONFLICT` con la entrada actual (`current`). Kopi Chess une las
  dos versiones y vuelve a subir con la `rev` de `current`.
- **Subir sobre una lápida** con su `rev` restaura la partida.
- **Borrar la cuenta Kopi** borra también todo su historial.

### 9.3 Bajar los cambios

`GET /chess/history?since=<cursor>&limit=<n>`

- Sin `since`, todo el historial; con `since`, solo lo que cambió después.
- `limit` va de 1 a 500 (100 por defecto). Además, una respuesta no pasa de
  unos 4 MB.
- La respuesta (`200`):

  ```json
  {
    "entries": [ { "gameId": "…", "rev": 3, "deleted": false, "changedAt": "…", "data": { } } ],
    "cursor": "000017625401234#",
    "more": false,
    "tombstoneDays": 180
  }
  ```

- **Las entradas llegan en orden de cambio,** las lápidas incluidas.
- **`cursor` se guarda tal cual** y se manda como `since` la próxima vez. Es
  opaco: no hay que interpretarlo.
- **Con `more: true`** hay más páginas: se pide otra vez enseguida con el
  nuevo `cursor`.
- **Al terminar (`more: false`)** el cursor se queda 10 segundos atrás, para
  que una escritura concurrente que se confirme tarde no se pierda. Por eso
  una entrada puede llegar dos veces con la misma `rev`, y aplicarla dos
  veces no debe cambiar nada.
- **Si el último cursor tiene más de `tombstoneDays` días,** puede que las
  lápidas de lo borrado entretanto ya hayan caducado. Kopi Chess debe
  sincronizar entonces como la primera vez (§9.6).

### 9.4 Subir una partida

`PUT /chess/history/{gameId}` con `{ "rev": 2, "data": { … } }`

- `200` con la entrada nueva, sin `data`:
  `{ "gameId": "…", "rev": 3, "deleted": false, "changedAt": "…" }`.
- `409 REV_CONFLICT` si `rev` ya no es la actual, o es 0 y la entrada existe
  (aunque sea una lápida). Trae `current`, la entrada actual con su `data`:

  ```json
  { "status": 409, "error": "Conflict", "code": "REV_CONFLICT", "detail": "…", "current": { "gameId": "…", "rev": 3, "deleted": false, "changedAt": "…", "data": { } } }
  ```

- `400 BAD_REQUEST` si `gameId` no es un UUID, `rev` no es un entero desde 0
  o `data` no es un objeto.
- `413 TOO_LARGE` si `data` pasa de 300.000 caracteres.

### 9.5 Borrar

- **`DELETE /chess/history/{gameId}?rev=<n>`** deja una lápida con
  `rev = n + 1`. Responde:
  - `204`, también si la entrada no existe o ya era una lápida (borrar es
    idempotente);
  - `409 REV_CONFLICT`, con `current`, si la partida cambió en otro equipo
    desde `n`. Kopi Chess decide entonces si la borra igualmente (con la
    `rev` de `current`) o se queda la versión nueva.
- **`DELETE /chess/history`** borra **todo** el historial de la cuenta, sin
  lápidas, para dejar de guardarlo en Kopi. Responde `204`. Los equipos que
  sigan sincronizando lo volverían a subir, así que Kopi Chess debe apagar
  antes la sincronización en ese equipo y avisar de que los demás también
  deben apagarla.

### 9.6 Cómo sincroniza Kopi Chess

Lo fija el [ADR-021](adr/021-historial-en-la-cuenta.md) (`HistorySync`):

1. **La primera vez:** bajar todo (`since` vacío) y unir. Lo que solo está
   en un lado se copia al otro: se baja lo que falta y se sube con `rev: 0`
   lo local.
2. **Después:** subir lo que cambió en local (con la `rev` guardada de cada
   partida) y bajar desde el último `cursor`.
3. **Unir dos versiones de una partida:** las partidas solo crecen. Si las
   jugadas de una son el principio de las de la otra, gana la más larga; si
   divergen (la misma partida en curso jugada en dos equipos), se guardan las
   dos, una como partida nueva con otro UUID.
4. **El análisis** se puede recalcular en cada equipo, o viajar dentro de
   `data` más adelante (subiendo `format`).

## 10. Canal entre dispositivos (app Android ↔ PC)

El mismo WebSocket de §7 une la app Android con el Kopi Chess del PC de la
**misma cuenta** en «modo servidor» (fase F del plan). El relé reenvía los
mensajes sin guardarlos ni mirar dentro. El formato de `payload` (listar
bots, crear partida, jugar…) lo decidirán el ADR-021 y el
`kopi-adapter-remote`; para el relé es opaco.

### 10.1 Conectar

`wss://01q3xfi4m6.execute-api.eu-south-2.amazonaws.com/prod?token=<access token>&protocol=1&role=<papel>&name=<nombre>`

| Papel (`role`) | Quién | Para qué |
|---|---|---|
| `notify` (por defecto en el PC) | Kopi Chess de escritorio | solo los avisos de partidas de §7, como hasta ahora |
| `server` | Kopi Chess de escritorio, con el «modo servidor» activo | además, recibir peticiones de la app |
| `device` (siempre en el móvil) | app Android | hablar con un PC servidor |

- **`server` solo con un *token* de `kopi-chess-client`.** El móvil
  (`kopi-chess-mobile-client`) siempre es `device`: si pide otro papel, el
  relé rechaza la conexión (`403`).
- **`name`** es opcional: hasta 40 caracteres que la app muestra («PC de
  casa»).
- **Las conexiones `server` y `device` también reciben los avisos de
  partidas** de §7. Quien no los use los ignora.

### 10.2 Mensajes

Mensajes al relé y del relé en JSON. Todos los del relé llevan
`"protocol": 1`.

| Al relé | Respuesta a quien lo envía |
|---|---|
| `{ "type": "ping" }` | `{ "type": "pong" }` |
| `{ "type": "presence" }` | `{ "type": "presence", "servers": [ { "connectionId": "…", "name": "PC de casa", "connectedAt": "…" } ] }` |
| `{ "type": "relay", "to": "<connectionId>", "id": "q1", "payload": { … } }` | nada si se entregó; si no, `{ "type": "error", "code": "…", "id": "q1" }` |

- **`presence`** dice qué PCs servidores de la cuenta están conectados. El
  relé también se lo **empuja** a los móviles cada vez que un PC servidor se
  conecta o se desconecta.
- **`relay`** solo va de un `device` a un `server`, o de un `server` a un
  `device`, de la misma cuenta. Quien lo recibe ve:

  ```json
  { "type": "relay", "from": "<connectionId>", "fromRole": "device", "fromName": "Móvil", "id": "q1", "payload": { }, "protocol": 1 }
  ```

  Para responder, se envía otro `relay` con `to` = el `from` recibido y el
  mismo `id`, que sirve para emparejar petición y respuesta.
- **`payload`** es un objeto JSON de hasta 32.000 caracteres.

| `code` de error | Cuándo |
|---|---|
| `DEVICE_NOT_CONNECTED` | `to` no existe, ya se desconectó, es de otra cuenta o no tiene el papel contrario |
| `ROLE_NOT_ALLOWED` | una conexión `notify` intenta reenviar |
| `BAD_MESSAGE` | falta `payload` o no es un objeto |
| `TOO_LARGE` | `payload` pasa de 32.000 caracteres |
| `TOO_MANY_REQUESTS` | más de 600 `relay` por minuto en la cuenta |
| `NOT_CONNECTED` | el relé no conoce esta conexión (reconectar) |

- **Sin garantía de entrega:** si el PC se desconecta a mitad, la petición se
  pierde. La app reintenta, o vuelve a pedir el estado, con su propio
  tiempo de espera.
