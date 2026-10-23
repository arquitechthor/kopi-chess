# ADR-006: Bots propios para los niveles bajos

- **Estado:** aceptado
- **Fecha:** 2026-10-23

## Contexto

Kopi Chess ofrece cinco niveles de bot (`PAWN` … `QUEEN`). Stockfish puede
debilitarse con `Skill Level`, `UCI_LimitStrength` y tiempos cortos, pero
incluso muy limitado juega de forma poco humana en los niveles más bajos:
alterna jugadas excelentes con errores aleatorios, y un principiante no
reconoce en él a un rival «de su nivel». Además, depender de un proceso
externo para el nivel 1 hace que lo más sencillo del proyecto falle si
Stockfish no está.

## Decisión

- **`PAWN` (nivel 1) y `KNIGHT` (nivel 2) son bots propios**, en Java, en
  `kopi-adapter-bots`, sobre el generador de jugadas del dominio:
  - `PAWN`: jugada legal al azar ponderada (capturas, jaques y coronar
    dama tientan más). No mira nada por delante.
  - `KNIGHT`: búsqueda negamax con poda alfa-beta a profundidad 2 sobre una
    evaluación sencilla (material más bonificación por casilla). No cuelga
    piezas en una jugada ni pierde un mate en uno, pero no ve tácticas más
    largas. Elige al azar entre las jugadas a 10 centipeones de la mejor.
- **`BISHOP`, `ROOK` y `QUEEN` usan Stockfish** (adaptador propio, fase 4),
  con límites de fuerza distintos.
- Los dos tipos implementan el mismo puerto `BotPlayer`; la aplicación no
  distingue unos de otros.

## Consecuencias

- Los dos primeros niveles juegan de forma reconocible («un niño que
  aprende», «uno que mira una jugada por delante») y funcionan sin
  Stockfish.
- Son buen material didáctico: una búsqueda alfa-beta legible, probada con
  posiciones concretas (pieza colgada, mate en uno).
- Hay más código que mantener que si todo fuera Stockfish, y la calibración
  (§7.3 del plan) debe comprobar que `KNIGHT` queda claramente por debajo de
  `BISHOP`. Una comprobación rápida ya muestra que gana a `PAWN` con
  claridad (19 victorias y unas tablas en 20 partidas).
