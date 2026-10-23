# ADR-008: Repetición y 50 jugadas automáticas

- **Estado:** aceptado
- **Fecha:** 2026-10-23

## Contexto

En las reglas FIDE, la triple repetición y la regla de las 50 jugadas no
terminan la partida solas: el jugador **puede reclamar** el empate. Solo la
quíntuple repetición y las 75 jugadas son automáticas. Implementar la
reclamación obliga a tener un botón «reclamar tablas», a que el bot sepa
cuándo reclamar y a guardar el estado «reclamable», todo para un caso que en
partidas contra bots casi siempre se resuelve igual: el empate.

Además, «la misma posición» y «material insuficiente» necesitan una
definición precisa para poder implementarlas y probarlas.

## Decisión

- **Automáticas.** La partida termina en tablas en cuanto se produce la
  **triple repetición** o se llega a **50 jugadas por bando** (100
  medias jugadas, `halfmoveClock >= 100`) sin captura ni movimiento de peón.
  No hay reclamación.
- **Misma posición** = mismo [hash Zobrist](../kopi-chess-diseno-tecnico.md):
  mismas piezas en las mismas casillas, mismo turno, mismos derechos de
  enroque y misma captura al paso **solo si es legal**. Una casilla al paso
  que nadie puede usar (o cuyo uso dejaría al rey en jaque) no cuenta. Los
  relojes nunca cuentan. Es la definición de la FIDE (las mismas jugadas
  posibles).
- **El jaque mate gana.** Si la jugada que da mate llega también al límite
  de 50 jugadas o repite la posición, cuenta el mate (como en la FIDE).
- **Material insuficiente** (automático también en la FIDE): sin peones,
  torres ni damas, y además o bien no queda nada más, o un solo caballo, o
  solo alfiles y todos en casillas del mismo color. Dos caballos, caballo
  contra caballo o alfiles de distinto color **no** cuentan: el mate es
  posible, aunque sea con ayuda del rival.
- **Dónde vive cada regla.** `Rules` (dominio) decide lo que se ve en una
  sola posición: mate, ahogado, material insuficiente y 50 jugadas. La
  triple repetición necesita el historial y la decide el agregado `Game`
  (fase 3), comparando los hashes de `Position.zobristHash()`.

## Consecuencias

- Más simple: no hay estado «reclamable» ni botón, y el bot no necesita
  lógica de reclamación.
- Difiere de la FIDE en un detalle visible: una partida que un humano
  habría querido seguir tras la tercera repetición termina sola. Se documenta
  en la interfaz como «tablas por repetición».
- Otras posiciones muertas (cadenas de peones bloqueadas, por ejemplo) no se
  detectan. Esas partidas terminan por las 50 jugadas.
- La tabla de Zobrist usa una semilla fija y un generador especificado por
  su algoritmo (`L64X128MixRandom`): el hash de una posición es el mismo en
  cualquier ejecución, así que podría guardarse en la base de datos.
- Si algún día se quiere la reclamación FIDE, basta con cambiar el agregado
  `Game`: `Rules` y el hash no cambian.
