# ADR-015: Partidas importadas: final declarado y sin tablas automáticas

- **Estado:** aceptado
- **Fecha:** 2026-10-26

## Contexto

Importar un PGN (fase 6) trae partidas jugadas en otro sitio. El agregado
`Game` solo conocía dos clases de final:

- los que se ven en el tablero (mate, ahogado, material insuficiente,
  triple repetición, cincuenta jugadas), aplicados **automáticamente** en
  cuanto ocurren (ADR-008);
- el abandono, que en Kopi siempre es de la persona.

Un PGN real dice `1-0` sin mate en el tablero muy a menudo: abandono, pérdida
por tiempo, tablas de mutuo acuerdo. Y en ajedrez de verdad las tablas por
repetición o por cincuenta jugadas **hay que reclamarlas**: muchas partidas
reales siguen después de una triple repetición. Reproducir un PGN así con
las reglas de ADR-008 terminaría la partida antes de tiempo y rechazaría las
jugadas siguientes.

## Decisión

- **Un final nuevo, `GameOutcome.Declared(result)`**: el resultado tal como lo
  registra el PGN, cuando el tablero no muestra por qué. Solo existe en
  partidas importadas (la base de datos lo garantiza).
- **El tablero tiene la última palabra** (`Game.imported`):
  - si la posición final es mate o ahogado, ese es el final, y el resultado
    del PGN tiene que coincidir (o ser `*`); si no, se rechaza
    (`RESULT_CONTRADICTS_BOARD`);
  - si no, vale el resultado del PGN; si el tablero también muestra unas
    tablas que coinciden (material insuficiente…), se nombran como tales;
  - `*` sin final en el tablero es una partida sin terminar: se rechaza
    (`NOT_FINISHED`). Solo se importan partidas terminadas.
- **En una partida importada no se aplican tablas automáticas a mitad de
  partida.** ADR-008 sigue valiendo para las partidas que se juegan en Kopi.
- Una partida importada **no tiene persona ni bot** (`humanColor()` y
  `botProfile()` son `Optional`), y **guarda sus etiquetas PGN**, como texto
  PGN en `game.pgn_tags` (no en `jsonb`: Kopi ya lee PGN, y así el adaptador
  de persistencia no necesita una biblioteca de JSON). Al exportarla se
  usan esas etiquetas.

## Consecuencias

- Se puede importar casi cualquier partida terminada real tal cual, y el
  análisis la trata como a las propias.
- El tablero sigue mandando: un PGN que diga algo imposible (un mate que
  «ganó» el otro) se rechaza con un motivo concreto.
- Kopi no sabe *por qué* terminó una partida `DECLARED`; la interfaz lo dice
  así: «Ganaron las blancas (resultado según el PGN)».
- Los movimientos importados se marcan con la hora de la importación: el PGN
  no dice cuándo se jugó cada uno.
