# Cómo leer el código de Kopi Chess

Una guía para quien llega a aprender: por dónde empezar, en qué orden leer y
qué buscar en cada sitio. El porqué de cada decisión está en el
[diseño técnico](kopi-chess-diseno-tecnico.md) y en los [ADR](adr/); aquí solo
el mapa.

## La idea en una frase

El código está en **anillos**, como una cebolla: el ajedrez en el centro, los
casos de uso alrededor y, por fuera, los adaptadores que hablan con el mundo
(el navegador, la base de datos, Stockfish). **Las dependencias solo apuntan
hacia dentro**: el centro no sabe que existe Spring, ni PostgreSQL, ni la web.

```
            ┌──────────────────────── kopi-bootstrap ────────────────────────┐
            │  ┌──────────────────────── adaptadores ─────────────────────┐  │
            │  │  web · rest            persistence · stockfish ·         │  │
            │  │  (entrada)             bots · openings (salida)          │  │
            │  │        ┌──────────── kopi-application ─────────┐         │  │
            │  │        │  casos de uso         puertos         │         │  │
            │  │        │        ┌──── kopi-domain ────┐        │         │  │
            │  │        │        │  chess · game ·     │        │         │  │
            │  │        │        │  analysis           │        │         │  │
            │  │        │        └─────────────────────┘        │         │  │
            │  │        └───────────────────────────────────────┘         │  │
            │  └──────────────────────────────────────────────────────────┘  │
            └────────────────────────────────────────────────────────────────┘
```

Cada anillo es un módulo Maven (ADR-001), así que una dependencia hacia fuera
ni siquiera compila. Lo que Maven no puede impedir (que un adaptador use otro,
que el dominio importe Spring) lo vigila ArchUnit en
`kopi-architecture-tests` (`HexagonalArchitectureTest`): cinco reglas cortas
que se leen como frases.

## Orden de lectura recomendado

### 1. El dominio: `backend/kopi-domain`

Java puro, sin ninguna dependencia. Es la parte más interesante si te gusta
el ajedrez, y la que mejor se entiende sola.

- **`domain.chess`**: las reglas.
  - `Square`, `Piece`, `Move`: los ladrillos, casi todos *records* y *enums*.
  - `Board` y `Position`: una posición inmutable; `Position.apply(move)`
    devuelve otra.
  - `MoveGenerator` y `Rules`: las jugadas legales, el jaque, el mate, las
    tablas.
  - `Fen`, `San`, `Pgn`: leer y escribir las notaciones de siempre.
  - `Perft`: cuenta las posiciones a una profundidad dada. Es **la prueba**
    de que las reglas están bien (ADR-004): `PerftTest` compara con cifras
    publicadas, y `DifferentialTest` juega partidas al azar y compara cada
    posición con Stockfish.
- **`domain.game`**: una partida. `Game` guarda la posición inicial, las
  jugadas y el estado; `Game.play(...)` comprueba el turno y la legalidad y
  devuelve una partida nueva. Errores de regla como excepciones propias
  (`IllegalMoveException`, `NotYourTurnException`…), con códigos en vez de
  textos (ADR-011).
- **`domain.analysis`**: juzgar jugadas. `WinChance` convierte la evaluación
  del motor en probabilidad de ganar; `MoveClassifier` decide si una jugada es
  la mejor, una imprecisión o un error grave (ADR-005); `Accuracy` calcula la
  precisión con la fórmula de Lichess.

Las pruebas de este módulo están al lado (`src/test`) y son la mejor
documentación: nombres en inglés que se leen como frases y posiciones
escritas siempre en FEN.

### 2. La aplicación: `backend/kopi-application`

Los **casos de uso**, uno por cosa que la persona puede hacer, y los
**puertos**, las interfaces que necesitan del exterior. Tampoco aquí hay
Spring (ADR-002): cada caso de uso es una clase normal que recibe sus puertos
en el constructor.

- **`application.port`**: lo que la aplicación pide al mundo, sin saber quién
  lo hará. `GameRepository` (guardar partidas), `BotPlayer` (elegir la jugada
  del bot), `ChessEngine` (analizar una posición), `AnalysisJobQueue` (la cola
  de análisis), `OpeningBook`, `AnalysisEvents`…
- **`application.game`**: crear, jugar, abandonar, importar, exportar, listar
  y borrar partidas. Empieza por `PlayMove`: carga la partida, comprueba la
  versión, juega tu jugada, deja que responda el bot y guarda **una sola
  vez** al final.
- **`application.analysis`**: `RunAnalysisJob` es el corazón del análisis
  posterior: toma un trabajo de la cola, pide al motor cada posición una vez y
  clasifica cada jugada.

Las pruebas usan implementaciones en memoria de los puertos
(`InMemoryGameRepository`, `ScriptedBot`…): sin base de datos ni motor, en
milisegundos.

### 3. Los adaptadores: `backend/kopi-adapter-*`

Cada uno implementa puertos (salida) o llama a casos de uso (entrada), y
ninguno conoce a los demás.

| Módulo | Dirección | Qué hace |
|---|---|---|
| `kopi-adapter-web` | entrada | Las páginas: Thymeleaf, formularios clásicos y un poco de JavaScript para el tablero (Chessground). Sin reglas de ajedrez en JavaScript. |
| `kopi-adapter-rest` | entrada | La API JSON (`/api/v1`), errores como `ProblemDetail` y el progreso del análisis por *Server-Sent Events* (`SseAnalysisEvents`). |
| `kopi-adapter-persistence` | salida | PostgreSQL con SQL a mano (`JdbcClient`) y migraciones Flyway. La cola de análisis también vive aquí (ADR-007). |
| `kopi-adapter-stockfish` | salida | Habla UCI con el proceso de Stockfish: los bots fuertes (`StockfishBots`) y el análisis (`StockfishAnalysis`). |
| `kopi-adapter-bots` | salida | Los bots propios para los niveles bajos, `PAWN` y `KNIGHT` (ADR-006). |
| `kopi-adapter-openings` | salida | El libro de aperturas de Lichess, en memoria (ADR-009). |

### 4. El arranque: `backend/kopi-bootstrap`

El único sitio que lo conoce todo. `UseCaseConfiguration` crea cada caso de
uso con sus adaptadores; es el mejor resumen de qué usa qué. Ahí se ve, por
ejemplo, cómo un solo puerto `BotPlayer` reparte los bots entre dos
adaptadores. `AnalysisWorker` vacía la cola de análisis con unos pocos hilos
virtuales.

`kopi-labs` queda aparte: ejemplos de funciones *preview* de Java, que nadie
más puede usar (ADR-012).

## Dos recorridos completos

**Juegas una jugada en el navegador.** `board.js` envía la jugada →
`GameController.playLive` (web) → `PlayMoveUseCase` → `PlayMove` → `Game.play`
(dominio) → `BotPlayer` → `KopiBots` o `StockfishBots` → `Game.play` otra vez
con la respuesta → `GameRepository.update` → `JdbcGameRepository`. La página
recibe solo el trozo que cambia y anima la jugada del bot.

**La partida termina y se analiza.** `AnalysisOnFinish` encola el trabajo
(`AnalysisJobQueue` → `JdbcAnalysisJobQueue`) → `AnalysisWorker` lo recoge →
`RunAnalysisJob` → `ChessEngine` (`StockfishAnalysis`) por cada posición →
`MoveClassifier` (dominio) → `AnalysisRepository`. Mientras tanto,
`AnalysisEvents` (`SseAnalysisEvents`) cuenta el progreso a la página de
revisión, que dibuja la barra.

## Dónde buscar Java moderno

- *Records* y *sealed interfaces*: por todo el dominio; mira `Evaluation`
  (centipeones, mate en N o mate) y `GameOutcome`.
- *Pattern matching* en `switch`: `GameController.refusal`, que traduce cada
  excepción a un código, y el reparto de bots en `UseCaseConfiguration`.
- Hilos virtuales: `AnalysisWorker` (`Thread.ofVirtual()`).
- Patrones sin nombre (`_`), en esos mismos `switch` y en los `catch`.
- Comentarios de documentación en Markdown (`///`), el estilo de todo el
  código.

## Para seguir

- Cómo trabajar en el proyecto: [`CONTRIBUTING.md`](../CONTRIBUTING.md).
- El diseño completo: [`kopi-chess-diseno-tecnico.md`](kopi-chess-diseno-tecnico.md).
- Las decisiones, una por archivo: [`adr/`](adr/).
