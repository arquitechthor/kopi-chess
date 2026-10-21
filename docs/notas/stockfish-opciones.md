# Notas: Stockfish por UCI

Resultados de una sesión UCI manual (fase 0) con **Stockfish 19**, binario
`stockfish-windows-x86-64-universal.exe`, el 2026-10-21. Sirven de
referencia para el adaptador `kopi-adapter-stockfish` (fase 4), y los
fragmentos de salida pueden reutilizarse como datos de prueba del *parser*.

## Opciones (`uci`)

```text
id name Stockfish 19
id author the Stockfish developers (see AUTHORS file)

option name Debug Log File type string default <empty>
option name NumaPolicy type string default auto
option name Threads type spin default 1 min 1 max 1024
option name Hash type spin default 16 min 1 max 33554432
option name Clear Hash type button
option name Ponder type check default false
option name MultiPV type spin default 1 min 1 max 256
option name Skill Level type spin default 20 min 0 max 20
option name Move Overhead type spin default 10 min 0 max 5000
option name nodestime type spin default 0 min 0 max 10000
option name UCI_Chess960 type check default false
option name UCI_LimitStrength type check default false
option name UCI_Elo type spin default 1320 min 1320 max 3190
option name UCI_ShowWDL type check default false
option name SyzygyPath type string default <empty>
option name SyzygyProbeDepth type spin default 1 min 1 max 100
option name Syzygy50MoveRule type check default true
option name SyzygyProbeLimit type spin default 7 min 0 max 7
option name EvalFile type string default nn-1a298aa575a0.nnue
uciok
```

Las que usará Kopi Chess:

- **`UCI_Elo`:** **1320–3190**. Confirma que los niveles bajos necesitan
  bots propios (diseño §7).
- **`Skill Level`:** 0–20 (por defecto 20).
- **`UCI_LimitStrength`:** booleano; tiene que estar a `true` para que
  `UCI_Elo` surta efecto.
- **`MultiPV`:** 1–256.
- **`Threads`:** por defecto 1. **`Hash`:** por defecto 16 MB.
- **`UCI_ShowWDL`:** añade `wdl W D L` (en tantos por mil) a cada línea
  `info`. Interesante para el análisis: es la estimación del propio motor de
  victoria/tablas/derrota.

## Comportamientos observados

### Puntuación desde el lado que mueve

```text
> position startpos
> go depth 15
info depth 15 seldepth 25 multipv 1 score cp 32 ... pv e2e4 c7c5 b1c3 ...
bestmove e2e4 ponder c7c5

> position startpos moves e2e4
> go depth 15
info depth 15 seldepth 25 multipv 1 score cp -27 ... pv e7e5 g1f3 b8c6 ...
bestmove e7e5 ponder g1f3
```

Tras `1.e4` mueven las negras y la puntuación sale **negativa**: el motor
puntúa siempre desde el lado que mueve. El adaptador debe normalizar a
perspectiva de blancas (diseño §6.1).

### Mate disponible

```text
> position startpos moves f2f3 e7e5 g2g4
> go depth 10
info depth 1 seldepth 3 multipv 1 score mate 1 nodes 31 ... pv d8h4
...
bestmove d8h4
```

### Posición ya terminada (mate)

```text
> position startpos moves f2f3 e7e5 g2g4 d8h4
> go depth 10
info depth 0 score mate 0
bestmove (none)
```

El *parser* debe aceptar `bestmove (none)` y `score mate 0`. En Kopi Chess
no debería ocurrir (el dominio detecta el fin de partida antes de llamar al
motor), pero el adaptador no debe romperse si pasa.

### `UCI_Elo` por debajo del mínimo

`setoption name UCI_Elo value 1000` **no da error**: el valor se ajusta en
silencio al rango. El adaptador debe validar los valores contra el rango
anunciado por `uci` en lugar de confiar en que el motor proteste.

### MultiPV

Con `MultiPV 3`, cada profundidad produce tres líneas `info`, una por
candidata (`multipv 1`, `2`, `3`), y no siempre llegan juntas en todas las
profundidades:

```text
info depth 12 seldepth 19 multipv 1 score cp 35 ... pv e2e4 e7e5 g1f3 g8f6 f3e5 f6e4
info depth 12 seldepth 22 multipv 2 score cp 21 ... pv d2d4 g8f6 c2c4 e7e6 ...
info depth 12 seldepth 22 multipv 3 score cp 21 ... pv g1f3 g8f6 d2d4 e7e6 ...
bestmove e2e4 ponder e7e5
```

Hay que quedarse con la última línea completa de cada `multipv` antes del
`bestmove`.

### WDL

```text
info depth 12 seldepth 13 multipv 1 score cp 34 wdl 68 927 5 nodes 3198 ...
```

### Perft

```text
> position startpos
> go perft 5
a2a3: 181046
b2b3: 215255
c2c3: 222861
d2d3: 328511
e2e3: 402988
f2f3: 178889
g2g3: 217210
h2h3: 181044
a2a4: 217832
b2b4: 216145
c2c4: 240082
d2d4: 361790
e2e4: 405385
f2f4: 198473
g2g4: 214048
h2h4: 218829
b1a3: 198572
b1c3: 234656
g1f3: 233491
g1h3: 198502

Nodes searched: 4865609
```

Es el formato de `perft divide` que usaremos para depurar el generador de
jugadas en la fase 2.

### Líneas informativas y comandos desconocidos

- Al empezar la primera búsqueda aparecen líneas `info string ...` (hilos,
  red NNUE). El *parser* debe ignorarlas.
- Un comando no reconocido responde
  `Unknown command: '...'. Type help for more information.` y **no** detiene
  el motor. Si el adaptador solo espera `bestmove`, un comando mal escrito
  pasaría desapercibido: conviene registrar esas líneas como error.
- Curiosidad encontrada al automatizar la sesión desde PowerShell 5.1: añade
  un BOM UTF-8 delante de lo primero que escribe en `stdin`, y Stockfish lo
  rechazó como comando desconocido. En Java (`ProcessBuilder` +
  `OutputStreamWriter` con `UTF_8`) no ocurre, pero es un buen ejemplo de por
  qué el adaptador debe vigilar esas respuestas.

### `go infinite` y `stop`

`stop` interrumpe la búsqueda y el motor responde igualmente con su
`bestmove`. Es el mecanismo para aplicar *timeouts*.
