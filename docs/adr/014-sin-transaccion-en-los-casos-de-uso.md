# ADR-014: Sin transacción alrededor de los casos de uso

- **Estado:** aceptado
- **Fecha:** 2026-10-23

## Contexto

El ADR-002 dejó para la fase 3 cómo aplicar la transaccionalidad fuera del
núcleo: con un decorador del caso de uso en `kopi-bootstrap` o con un puerto
`UnitOfWork`. El plan (§6.3) proponía decorar los casos de uso con
transacciones.

Al implementarlos se ve un problema: `PlayMove` carga la partida, aplica la
jugada de la persona, **espera la jugada del bot** y guarda. Con `PAWN` la
espera es nula, pero desde la fase 4 los bots `BISHOP`, `ROOK` y `QUEEN`
piensan con Stockfish durante segundos (`movetime`). Una transacción
alrededor del caso de uso mantendría abierta una transacción y una conexión
del *pool* todo ese tiempo, por cada partida en juego.

Y no hace falta para la coherencia:

- El **bloqueo optimista** (`update … where id = ? and version = ?`)
  detecta a cualquiera que haya guardado entre la carga y el guardado; en
  ese caso no se guarda nada (`412`).
- Cada caso de uso **guarda una sola vez**, al final.
- El **repositorio** hace atómico cada guardado: la fila de la partida y sus
  jugadas nuevas van en una sola transacción, corta.

## Decisión

Los casos de uso **no** se envuelven en una transacción. `kopi-bootstrap` los
crea tal cual (`UseCaseConfiguration`). La atomicidad es responsabilidad de
cada operación del repositorio, y la coherencia entre la lectura y la
escritura, del bloqueo optimista.

## Consecuencias

- No hay transacciones largas mientras piensa un bot, ni decoradores que
  mantener por cada caso de uso.
- Ni el núcleo ni `kopi-bootstrap` contienen código de transacciones; solo
  el adaptador de persistencia (`TransactionTemplate` en `JdbcGameRepository`).
- Si un caso de uso futuro necesitara escribir en varios agregados o tablas
  de forma atómica (por ejemplo, encolar el análisis al terminar una
  partida, fase 5), se resolverá entonces: con una operación de repositorio
  que haga ambas cosas o con un puerto `UnitOfWork`, sin cambiar este
  criterio para los demás.
- Las lecturas (`GetGame`, `ListGames`) no ven una instantánea transaccional
  de varias consultas; el repositorio ya carga cada página con dos
  consultas coherentes para este uso (una aplicación local con un usuario).
