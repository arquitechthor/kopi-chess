# Ideas para el futuro

Lo que **no** entra en la 1.0, recogido en un solo sitio para que no se
pierda ni distraiga mientras se estabiliza la versión. Nada de esto está
decidido: cada idea grande merece su propio ADR antes de empezar, y las
pequeñas pasan a ser una *issue* cuando llegue su momento.

Las tareas pequeñas y bien acotadas ya están en GitHub como
[*good first issues*](https://github.com/arquitechthor/kopi-chess/issues?q=is%3Aissue+label%3A%22good+first+issue%22)
(#89–#95); no se repiten aquí.

## Ideas pequeñas

### Girar el tablero en la revisión

- **Qué:** un botón junto a la navegación (⏮ ◀ ▶ ⏭) que da la vuelta al
  tablero, para ver la partida desde el lado del rival.
- **Cómo:** `toggleOrientation()` de Chessground en `review.js`; el botón con
  su `aria-label` en los dos idiomas.
- **Por qué no ahora:** es cómodo, pero no cambia lo que se puede hacer.

### Exportar el PGN con el análisis

- **Qué:** que el PGN descargado de una partida analizada lleve el veredicto
  de cada jugada como NAG (`$6` imprecisión, `$2` error, `$4` error grave) y
  la evaluación como comentario (`{ [%eval 0.35] }`), que entienden Lichess,
  ChessBase y la mayoría de programas.
- **Cómo:** hoy `Pgn.format` (dominio) no escribe comentarios ni NAG, aunque
  `Pgn` ya los sabe leer. Haría falta ampliar el formateador, decidir si es
  otra ruta (`…/pgn?annotated=true`) o el mismo PGN cuando hay análisis, y
  que `ExportPgn` lea el análisis.
- **Por qué no ahora:** toca el dominio, la aplicación y la API; demasiado
  para una primera contribución y fuera del alcance de la 1.0.

### Enlace didáctico a la apertura

- **Qué:** en la revisión, que el nombre de la apertura enlace a una página
  que la explique, en una pestaña nueva.
- **Cómo:** el nombre y el código ECO salen del conjunto de datos local
  `lichess-org/chess-openings` (ADR-009), así que el enlace natural es
  `https://lichess.org/opening/<nombre>` (el nombre con `_` en vez de
  espacios). Sin red ni dependencia nueva: solo se forma la URL.
- **Por qué no ahora:** pequeño y aislado; una PR propia cuando toque.

### Notación española opcional

- **Qué:** elegir `Cf3` en lugar de `♘f3` en la interfaz (diseño §11).
- **Hoy:** el modo oficina ya muestra la notación localizada (ADR-013); la
  vista normal usa figuras, que no dependen del idioma.

## Ideas grandes

Ordenadas por relación valor/esfuerzo (antes en el plan, §11). El orden de
entrega propuesto y el plan de los modos de juego (puzzles, entrenador, reloj)
están en [`plan-modos-de-juego.md`](plan-modos-de-juego.md).

### Jugador contra jugador

- **Qué:** partidas entre dos personas, en el mismo equipo o en línea con la
  cuenta Kopi, amistosas y sin límite de tiempo.
- **Plan detallado:** [`plan-jugador-contra-jugador.md`](plan-jugador-contra-jugador.md),
  con supuestos, arquitectura (relé sin servidor en AWS, validación local),
  costes y fases.
- **Extensión:** una app Android sencilla para jugar desde el móvil contra
  los bots de tu propio PC, por el mismo relé (§11 del plan).

### Historial en la cuenta Kopi

- **Qué:** opcional, con un interruptor en la página de cuenta: guardar las
  partidas en la cuenta Kopi y sincronizarlas entre todos tus equipos (y,
  más adelante, con la app Android aunque el PC esté apagado).
- **Hoy:** el historial vive solo en cada PC (ADR-016); las partidas en línea
  solo pasan por el relé mientras se juegan. Esto lo cambiaría (de forma
  opcional), así que necesita su ADR.
- **Cómo (estimación del 2026-11-07):**
  - **AWS: hecho el 2026-11-07** y probado contra AWS real; contrato en
    [`protocolo-rele.md`](protocolo-rele.md) §9. Tabla
    `kopi_chess_history` (una fila por cuenta y partida: jugadas, jugadores,
    resultado, fechas, versión), tres Lambdas (subir cambios, bajar cambios
    desde una marca, borrar) con el mismo autorizador `kopi-chess-authorizer`,
    borrado al eliminar la cuenta y su bootstrap. Coste: prácticamente 0 €.
  - **Kopi Chess** (1–1,5 semanas a tiempo parcial): un puerto
    `HistorySync` y su caso de uso (sube lo nuevo, baja lo que falta, une),
    marcas de sincronización y «lápidas» para los borrados en la base local,
    el interruptor y el estado en la página de cuenta, y pruebas con un
    backend falso, como `TwoComputersTest`.
  - Reutiliza la cuenta Kopi, el worker de sincronización y la validación
    con el dominio: una partida que baja se reconstruye jugada a jugada, así
    que una corrupta no entra.
  - **Legal:** ampliar privacidad y eliminación de cuenta en `kopi-web`.
- **Lo delicado:**
  - Una partida en curso jugada en dos equipos: si las jugadas de un lado
    son el principio de las del otro, gana la más larga; si divergen, se
    guardan las dos como partidas distintas.
  - Los borrados viajan como lápidas que caducan.
  - El análisis: al principio se recalcula en cada equipo (Stockfish da lo
    mismo con los mismos límites); sincronizar su resumen, más adelante.

### Puzzles desde tus propios errores

- **Qué:** cada `BLUNDER` o `MISSED_MATE` de tus partidas se convierte en un
  ejercicio «encuentra la mejor jugada».
- **Por qué merece la pena:** reutiliza todo el análisis; es la idea con más
  valor por esfuerzo.
- **Plan detallado:** [`plan-modos-de-juego.md`](plan-modos-de-juego.md) §2.

### Reloj y controles de tiempo

- **Qué:** partidas con tiempo, como regla del dominio (quien se queda sin
  tiempo pierde, con los matices de material insuficiente).
- **Plan detallado:** [`plan-modos-de-juego.md`](plan-modos-de-juego.md) §4.

### *Bitboards*

- **Qué:** otra implementación de `Board` con *bitboards*, detrás de la misma
  interfaz.
- **Red de seguridad:** *perft* (ADR-004) y un *benchmark* antes y después.

### Etiquetas `BRILLIANT` y `GREAT`

- **Qué:** reconocer jugadas brillantes, no solo errores (ADR-005).
- **Antes de empezar:** un conjunto de posiciones de prueba con la etiqueta
  esperada, porque es fácil equivocarse.

### Estadísticas

- **Qué:** por apertura y por bot, a partir de las partidas guardadas
  (resultados, errores más frecuentes, en qué fase de la partida).

### Modo entrenador

- **Qué:** durante la partida, una pista opcional cuando la jugada elegida
  sería un error, antes de confirmarla.
- **Plan detallado:** [`plan-modos-de-juego.md`](plan-modos-de-juego.md) §3.
