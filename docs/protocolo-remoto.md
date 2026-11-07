# Protocolo remoto (versión 1)

El contrato entre la app Android y el Kopi Chess del PC en «modo servidor»
([ADR-022](adr/022-movil-cliente-remoto-del-pc.md)). Viaja dentro del
`payload` de los mensajes `relay` del canal entre dispositivos
([`protocolo-rele.md`](protocolo-rele.md) §10), que el relé reenvía sin
mirar.

- **Estado:** versión 1, 2026-11-07. Aún no implementado (fases F2 y F3 del
  [plan](plan-app-android.md)).
- **Cambios:** un cambio que rompa clientes sube `v`. Añadir campos a una
  respuesta no la sube: quien la recibe ignora lo que no conoce.

---

## 1. Petición y respuesta

La app envía un `relay` al PC con `to` = el `connectionId` del PC (de
`presence`) y un `id` propio:

```json
{ "type": "relay", "to": "<PC>", "id": "q7", "payload": { "v": 1, "op": "move", "gameId": "…", "uci": "e2e4", "version": 3 } }
```

El PC responde con otro `relay` al `from` recibido y el **mismo `id`**:

```json
{ "type": "relay", "to": "<móvil>", "id": "q7", "payload": { "v": 1, "ok": true, "game": { } } }
```

o, si no pudo hacerlo:

```json
{ "type": "relay", "to": "<móvil>", "id": "q7", "payload": { "v": 1, "ok": false, "error": "CONFLICT", "detail": "…" } }
```

- **`error` es estable,** y es lo que mira la app; `detail` es un texto para
  depurar, en inglés.
- **Tiempo de espera:** la app da la petición por perdida a los 15 s sin
  respuesta (o al recibir `DEVICE_NOT_CONNECTED` del relé). Vuelve a pedir el
  estado con `game` antes de repetir una jugada: nunca da por hecha una
  jugada sin respuesta.
- **Idioma:** la app manda `"lang": "en"` o `"es"` en `hello`; el PC usa ese
  idioma para los textos que devuelve (descripción de los bots).

## 2. Operaciones (lista blanca)

| `op` | Campos de la petición | Respuesta (`ok: true`) |
|---|---|---|
| `hello` | `lang`, `appVersion` | `kopiChessVersion`, `remoteVersion` (1), `name` del equipo |
| `bots` | — | `bots`: `[{ profile, level, description, record: { won, lost, drawn, unfinished } }]` |
| `games` | — | `games`: las partidas **en curso contra un bot**, la más reciente primero, como en `game` sin `moves` |
| `new` | `botProfile`, `humanColor` (`WHITE`, `BLACK` o `RANDOM`) | `game`: la partida creada, con la jugada del bot si abre él |
| `game` | `gameId` | `game` |
| `move` | `gameId`, `uci`, `version` | `game`: tras la jugada y la respuesta del bot |
| `resign` | `gameId`, `version` | `game`: terminada |

Cualquier otra `op` responde `NOT_ALLOWED`. `game`, `move` y `resign` solo
valen para partidas contra un bot: con otra, `NOT_ALLOWED`.

## 3. La partida (`game`)

La misma forma que `GameResponse` de la API REST, recortada a lo que dibuja
la app:

```json
{
  "id": "0b6c5a52-…",
  "status": "IN_PROGRESS",
  "botProfile": "KNIGHT",
  "humanColor": "WHITE",
  "fen": "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
  "sideToMove": "WHITE",
  "inCheck": false,
  "humanToMove": true,
  "moves": [ { "ply": 1, "uci": "e2e4", "san": "e4" }, { "ply": 2, "uci": "e7e5", "san": "e5" } ],
  "lastMove": { "ply": 2, "uci": "e7e5", "san": "e5" },
  "legalMoves": [ "g1f3", "…" ],
  "outcome": null,
  "result": null,
  "version": 2,
  "createdAt": "2026-11-07T18:30:00Z"
}
```

`outcome`, cuando termina: `{ "termination": "CHECKMATE", "winner": "BLACK" }`
(como en la API REST).

## 4. Errores

| `error` | Cuándo |
|---|---|
| `NOT_ALLOWED` | `op` fuera de la lista blanca, o una partida que no es contra un bot |
| `BAD_REQUEST` | falta un campo o tiene otra forma |
| `NOT_FOUND` | no hay partida con ese `gameId` |
| `CONFLICT` | `version` no es la actual: la partida cambió (por ejemplo, se jugó en el PC). La app pide `game` y vuelve a dibujar |
| `ILLEGAL_MOVE` | la jugada no es legal en esa posición |
| `GAME_OVER` | la partida ya terminó |
| `BOT_UNAVAILABLE` | el bot no pudo jugar (Stockfish no responde) |
| `VERSION_MISMATCH` | `v` no es 1: hay que actualizar la app o Kopi Chess |
| `SERVER_MODE_OFF` | el PC apagó el modo servidor mientras tanto |
| `INTERNAL` | cualquier otro fallo del PC |

## 5. Para después de la primera versión

- **Jugar en línea contra un amigo desde el móvil** (decisión D3): nuevas
  `op` (`online`, `invite`, `accept`…) que el PC traduciría a su cliente del
  relé, sin que el móvil hable con el relé de partidas.
