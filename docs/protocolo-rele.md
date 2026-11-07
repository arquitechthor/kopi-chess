# Protocolo del relé (versión 1)

El contrato entre Kopi Chess y el backend Kopi para el modo en línea:
el id de Kopi ([ADR-019](adr/019-id-de-kopi.md)) y el relé de partidas
([ADR-020](adr/020-rele-de-partidas.md)). Es lo que implementan las Lambdas
de `kopi-media-admin/documentation/Lambdas/kopi-chess/` y lo que usará el
futuro `kopi-adapter-relay`, y lo que debe imitar un relé falso en las
pruebas.

- **Estado:** versión 1, 2026-11-07.
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
- 120 operaciones por minuto, todas las rutas juntas.
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
