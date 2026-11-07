# ADR-018: Un jugador por color, tablas de mutuo acuerdo e inactividad

- **Estado:** aceptado
- **Fecha:** 2026-11-07

## Contexto

Hasta la 1.0, una partida jugada aquí era siempre **una persona contra un
bot**: `Game` guardaba `humanColor` y `botProfile`, y todo lo demás se
deducía de ahí (el turno del bot, el PGN, el balance contra cada bot).

El [plan de jugador contra jugador](../plan-jugador-contra-jugador.md) añade
dos modos: dos personas en el mismo equipo y una persona contra un amigo en
línea. Con «persona + bot» no se pueden describir, y añadir otro par de campos
para cada modo dejaría combinaciones sin sentido (un bot y un amigo a la vez).

Además hacen falta dos formas de terminar que entre una persona y un bot no
existen: las **tablas de mutuo acuerdo** y, en línea, la **inactividad** (30
días sin jugadas, plan §1 y §6).

## Decisión

- **Un jugador por color**, en el dominio:

  ```java
  sealed interface Player permits Player.Bot, Player.Local, Player.Remote {
      record Bot(BotProfile profile) implements Player {}
      record Local(String name) implements Player {}     // "" contra un bot
      record Remote(String kopiId) implements Player {}  // un amigo en línea
  }
  ```

  `Game` guarda `white` y `black` (ninguno en una partida importada). Solo se
  admiten tres parejas, y `Game.start` rechaza el resto (dos bots, dos amigos
  en línea, un bot contra un amigo):

  | Modo (`GameMode`) | Pareja |
  |---|---|
  | `AGAINST_BOT` | `Local` contra `Bot` |
  | `LOCAL` | `Local` contra `Local`, cada uno con su nombre |
  | `ONLINE` | `Local` contra `Remote` |
  | `IMPORTED` | sin jugadores |

- **Lo de antes se deduce:** `humanColor()` es el color de **la** persona de
  este equipo (contra un bot o en línea; vacío entre dos personas aquí),
  `botColor()` y `botProfile()` los del bot, si lo hay. El código que solo
  entendía de bots sigue funcionando igual.
- **El dominio no decide quién mueve en este equipo; la aplicación sí**,
  según el modo: entre dos personas aquí, quien tenga el turno; contra un bot
  o en línea, la persona. `Game.play` sigue comprobando solo turno y
  legalidad. `Game.isLocalToMove()` dice si el turno es de alguien de este
  equipo.
- **Dos formas de terminar nuevas** en `GameOutcome`:
  - `DrawByAgreement`: `Game.agreeDraw`, solo en `LOCAL` y `ONLINE` (un bot
    no acuerda nada).
  - `Inactivity(winner)`: `Game.endByInactivity`, solo en `ONLINE`. Gana
    quien hizo la última jugada; sin jugadas, pierde el que debía empezar.
    Cuándo han pasado los 30 días lo decide la aplicación (con la hora de la
    última jugada según el relé), no el dominio.
- **En la base de datos** (V5), cada jugador es una columna de texto
  `TIPO:valor` (`BOT:PAWN`, `LOCAL:`, `LOCAL:Ana`, `REMOTE:luis`), con una
  restricción que solo admite esas formas. Las partidas existentes pasan a
  `LOCAL:` contra `BOT:…`. `human_color` y `bot_profile` se quedan como
  columnas derivadas, porque el historial y el balance contra los bots las
  consultan; entre dos personas aquí van vacías.
- **El balance contra cada bot cuenta solo partidas contra un bot**, nunca
  las de dos personas ni las de en línea.

## Consecuencias

- El modo local no necesita nada más en el dominio, y el modo en línea solo
  la parte de sincronización (fase D), no otro modelo.
- Como `Player` y `GameOutcome` son *sealed*, cada `switch` sobre ellos
  obliga a tratar los casos nuevos: la web, la API, el PGN y la base de datos
  no compilaban hasta saber mostrarlos.
- El PGN exportado usa los nombres: `Kopi Pawn` para un bot, el nombre de la
  persona (o `Player` si no tiene) y el id de Kopi de un amigo en línea.
- Entre dos personas aquí no hay «tú»: la página dice a quién le toca por su
  nombre y gira el tablero tras cada jugada; la revisión se ve desde las
  blancas, como una partida importada.
- La API cambia sin romper: las respuestas ganan `mode`, `white` y `black`;
  `humanColor` y `botProfile` siguen ahí con el mismo significado.
