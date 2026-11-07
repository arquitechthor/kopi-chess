# ADR-020: Relé de partidas en AWS sin reglas de ajedrez

- **Estado:** aceptado
- **Fecha:** 2026-11-07

## Contexto

Para jugar en línea, dos Kopi Chess en dos PCs tienen que intercambiar
jugadas ([plan](../plan-jugador-contra-jugador.md), §2, §4 y fase C). Las
restricciones del plan:

- **Coste casi cero:** nada encendido todo el mes.
- **Sin abrir puertos:** cada PC solo hace conexiones salientes, y la
  interfaz sigue respondiendo solo a esa máquina (ADR-016).
- **Partidas por correspondencia,** sin reloj: el rival puede tardar días.
- **Las reglas viven en el dominio de Kopi Chess** (ADR-004), no en otro
  lenguaje en la nube.

## Decisión

- **Un relé sin reglas en el backend Kopi:** Lambdas de Node.js detrás del
  HTTP API compartido (`api.kopitools.link`, rutas `/chess/*`, *authorizer*
  de ADR-019) y una tabla de DynamoDB bajo demanda. El relé comprueba **quién
  puede hacer qué** (que sea su partida, su turno por color, el número de
  jugada siguiente), nunca si una jugada es legal.
- **Cada Kopi Chess valida todo con su dominio:** su jugada antes de
  enviarla y la del rival al recibirla, comparando el hash Zobrist que viaja
  con ella. Si no cuadra, la partida queda **desincronizada** en ese PC.
- **Una partida = un ítem** en `kopi_chess_games`, con sus jugadas dentro
  (lista de `uci` + Zobrist). Una partida larga son unos pocos KB, lejos de
  los 400 KB de un ítem, y así:
  - el orden se garantiza con **una sola escritura condicional**
    (`lastPly = :anterior`): cada número de jugada se acepta una vez;
  - la caducidad (TTL) y el borrado afectan a un solo ítem, sin jugadas
    huérfanas.

  Sustituye a las dos tablas del plan (`PvpGames` y `PvpMoves`). Límite: 1.000
  medias jugadas por partida.
- **Nombres de tabla con el prefijo del backend:** `kopi_ids`,
  `kopi_chess_games`, `kopi_chess_connections` y `kopi_chess_limits`. El rol
  de las Lambdas ya da acceso a `kopi_*`.
- **Caducidad:** el TTL de la partida es 30 días desde la última jugada
  aceptada (o desde la invitación). El relé trata un ítem caducado como
  inexistente aunque DynamoDB aún no lo haya borrado. La victoria por
  **inactividad la decide cada PC** (plan, §6); el relé no la anota.
- **Borrado de las terminadas:** cuando una partida termina, quien la termina
  ya lo sabe; la partida se borra en cuanto el otro jugador confirma que ha
  recibido el final (`ack`). Igual con una invitación rechazada: se borra
  cuando quien invitó lo ha visto.
- **Tiempo real, solo como aviso:** un API WebSocket aparte, con una sola
  Lambda (`$connect`, `$disconnect`, `$default`). Al conectar, Kopi Chess
  manda su *access token* y la Lambda lo verifica (firma RS256 con las claves
  públicas del User Pool, emisor, cliente y caducidad). El relé solo avisa
  («hay novedades en la partida X»); los datos se piden siempre por HTTP. Si
  el aviso se pierde, no pasa nada: Kopi Chess sincroniza al abrir y al
  reconectar.
- **Versión del protocolo** en la cabecera `X-Kopi-Chess-Protocol` de cada
  petición (hoy `1`). Con otra versión, el relé responde `426` y Kopi Chess
  dice «actualiza Kopi Chess».
- **Límites por usuario** (contador en `kopi_chess_limits` con TTL): 20
  invitaciones por hora y 120 operaciones por minuto. Al pasarse, `429`. Hay
  además un presupuesto mensual en AWS Budgets para toda la cuenta.

El contrato completo (rutas, cuerpos, errores, avisos) está en
[`protocolo-rele.md`](../protocolo-rele.md).

## Consecuencias

- **ADR-016 sigue en pie:** Kopi Chess no acepta conexiones de fuera; solo
  *habla* con el backend Kopi, hacia fuera, con el *token* de ADR-019.
- **Coste:** sin servidor encendido; Lambda, DynamoDB y Cognito dentro de las
  capas gratuitas con el uso esperado. API Gateway cobra por petición y por
  minuto de WebSocket: céntimos.
- **Las trampas quedan fuera por diseño:** el relé no puede saber si una
  jugada salió de Stockfish (plan, §1).
- **Un relé falso basta para las pruebas** de Kopi Chess: el contrato es
  pequeño y no tiene reglas. Las pruebas reales necesitan la máquina con
  acceso a AWS.
- **El relé no conoce los finales:** apunta sin comprobarlo el mate, el
  ahogado o las tablas por regla que le comunica la app que hace la última
  jugada; la app del rival lo verifica al aplicar esa jugada.
- Queda abierto el caso límite de §6 del plan (un final que no llega porque el
  rival no abre su app en 30 días), aceptado en partidas amistosas.
