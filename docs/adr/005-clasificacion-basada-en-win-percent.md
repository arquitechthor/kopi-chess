# ADR-005: Clasificación de jugadas basada en Win %

- **Estado:** aceptado
- **Fecha:** 2026-10-26

## Contexto

El análisis posterior (fase 5) dice de cada jugada si fue buena, una
imprecisión, un error o un error grave, y da una precisión por jugador.
Medir la pérdida en centipeones crudos engaña: perder dos peones con la
partida igualada la cambia, y perderlos con +9 no cambia nada. Inventar
umbrales propios llevaría a discusiones sin fin y a números que nadie
reconoce.

## Decisión

- **Se juzga por la probabilidad de victoria**, con la curva que Lichess
  publica: `2 / (1 + exp(−0.00368208 · cp)) − 1`, en `[−1, 1]`, con los
  centipeones limitados a ±1000 y los mates como ±1.
- **Umbrales de Lichess** sobre la pérdida: `< 0.1` buena, `< 0.2`
  imprecisión, `< 0.3` error, a partir de ahí error grave. Además: `BOOK`
  (aún en el libro), `BEST` (la del motor o pérdida ≤ 0.01) y `MISSED_MATE`
  (había mate en ≤ 5 y ya no hay ninguno), que prevalece sobre todas. Una
  jugada forzada nunca es un error.
- **Precisión de Lichess**: por jugada `103.1668 · exp(−0.04354 · ΔWin%) −
  3.1669`; por partida, la media de una media armónica y una media ponderada
  por volatilidad (desviación típica del Win % en ventanas de posiciones).
  En la media armónica una precisión por debajo de 1 cuenta como 1, para
  que un solo 0 no anule la partida.
- **La evaluación de una posición ya en mate es un caso aparte**
  (`Evaluation.Checkmate(winner)`), no un `MateIn(0)`: Stockfish da
  `mate 0` desde el bando que mueve y, al pasarlo al punto de vista de las
  blancas, el cero pierde el signo que dice quién ganó.

Todo vive en el dominio (`domain.analysis`), sin motor: entra una lista de
evaluaciones y sale la clasificación.

## Consecuencias

- Los números se parecen a los que los jugadores ya conocen de Lichess, y la
  fórmula se puede enlazar y explicar.
- Con la curva, una jugada que pierde 300 cp ganando por +9 es una
  imprecisión, no un error grave (está probado con una posición real).
- La pérdida depende de dos búsquedas distintas (antes y después), que
  nunca coinciden del todo: por eso la jugada del motor es siempre `BEST` y
  hay una tolerancia de 0.01.
- Quedan fuera `BRILLIANT`, `GREAT` y similares (diseño §8.5): necesitan
  heurísticas con muchos falsos positivos, y tendrán su propio ADR.
- Los umbrales se podrán ajustar con datos; cambiarlos es cambiar este ADR.
