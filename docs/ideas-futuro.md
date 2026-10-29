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

### Notación española opcional

- **Qué:** elegir `Cf3` en lugar de `♘f3` en la interfaz (diseño §11).
- **Hoy:** el modo oficina ya muestra la notación localizada (ADR-013); la
  vista normal usa figuras, que no dependen del idioma.

## Ideas grandes

Ordenadas por relación valor/esfuerzo (antes en el plan, §11).

### Puzzles desde tus propios errores

- **Qué:** cada `BLUNDER` o `MISSED_MATE` de tus partidas se convierte en un
  ejercicio «encuentra la mejor jugada».
- **Por qué merece la pena:** reutiliza todo el análisis; es la idea con más
  valor por esfuerzo.

### Reloj y controles de tiempo

- **Qué:** partidas con tiempo, como regla del dominio (quien se queda sin
  tiempo pierde, con los matices de material insuficiente).

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
