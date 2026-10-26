# Kopi Chess — Documento de diseño técnico

**Estado:** propuesta revisada (v2)
**Fecha:** 2026-10-21
**Licencia del proyecto:** GPL-3.0 (ya definida en el repositorio)
**Lema:** *Open source educational project in Chess and Code.*

---

## 0. Cómo leer este documento

Este documento parte de una propuesta inicial llamada «Chess Arena» (un
primer borrador generado con ChatGPT, que ya no forma parte del repositorio)
y la **reformula** con tres criterios:

1. **El código también es producto.** Kopi Chess es un proyecto educativo:
   además de servir para jugar y aprender ajedrez, debe servir para aprender
   **Java 25, Spring Boot y Arquitectura Hexagonal**. Las decisiones se toman
   pensando en qué enseña cada pieza, no solo en qué es más rápido.
2. **Útil desde el día uno**, sin depender de servicios externos.
3. **Honestidad técnica:** no prometer métricas («precisión», «Elo»,
   «brillante») que no podamos definir y probar.

La sección 2 resume los cuestionamientos a la propuesta original; el resto es
el diseño nuevo.

### Premisas del proyecto

- **Foco de aprendizaje:** Java (con preferencia por las *features*
  recientes), Spring Boot y Arquitectura Hexagonal. Cualquier decisión puede
  cuestionarse, pero se evalúa por cuánto aporta a ese foco.
- **Ejecución local:** se ejecuta en la máquina de quien lo use
  (`docker compose up`). No se diseña para un despliegue público: sin
  cuentas, sin *rate limiting*, sin protección frente a otros usuarios.
- **Código público:** el repositorio es abierto (GPL-3.0). Nada de secretos
  en el repo y la documentación se escribe para lectores externos.
- **Interfaz bilingüe:** inglés y español desde la primera pantalla.

---

## 1. Visión

Kopi Chess es una aplicación web que se ejecuta en local con un solo
`docker compose up`, en inglés y español, para:

- **Jugar** contra cinco bots con personalidades y niveles diferenciados.
- **Revisar** cada partida al terminar: evaluación por jugada, clasificación
  explicable (inexactitud / error / error grave), mejor alternativa y mates
  desaprovechados.
- **Importar un PGN** de cualquier partida (Lichess, Chess.com, OTB) y
  analizarla igual que una partida propia. *(Esto es lo que convierte el
  proyecto en algo realmente útil: el análisis no queda limitado a partidas
  contra nuestros bots.)*
- **Jugar en modo oficina**: una apariencia discreta que simula la web de una
  empresa cualquiera, con el tablero como tabla de datos y las jugadas
  escritas a mano. Sirve para pasar desapercibido y, sobre todo, para
  **entrenar la notación algebraica** (`e4`, `Cf3`, `O-O`) en el idioma de la
  interfaz (§11.5).

Y, como proyecto de código, ser un **ejemplo de referencia** de un dominio
rico (las reglas del ajedrez) protegido por una arquitectura hexagonal real,
con pruebas que demuestran su corrección.

### Objetivos del MVP

1. Reglas completas del ajedrez implementadas en un dominio Java puro,
   verificadas con *perft*.
2. Cinco bots diferenciables.
3. Persistencia de partidas y movimientos.
4. Análisis posterior asíncrono con progreso en vivo.
5. Clasificación de jugadas basada en una fórmula pública y documentada.
6. Detección de mates desaprovechados.
7. Etiqueta de apertura (nombre + ECO) sin conexión a internet.
8. Entorno completo con Docker Compose.
9. Interfaz en inglés y español, con selector de idioma.
10. Modo oficina: tablero en texto y jugadas introducidas en notación
    algebraica, sin JavaScript.

### Fuera del alcance del MVP

- Despliegue público en internet (y lo que conlleva: autenticación,
  *rate limiting*, protección entre usuarios).
- Cuentas de usuario, multijugador humano, torneos, chat.
- Control de tiempo (reloj). *Se puede añadir en v2 como regla del dominio.*
- Análisis en tiempo real durante la partida.
- Etiquetas «brillante», «excelente», «gran jugada» (ver §8.5).
- Rating propio o equivalencias de Elo.
- App móvil nativa.

---

## 2. Cuestionamientos a la propuesta original

### 2.1 Base de datos

- **Propuesta original:** MongoDB, un documento por partida.
- **Cuestionamiento:** el modelo **es relacional**: partidas → movimientos →
  análisis, y las consultas útiles a futuro («mis errores graves por
  apertura», «resultados contra cada bot») son agregaciones SQL naturales.
  Además necesitamos una cola de trabajos duradera, que en Mongo exige más
  artificio.
- **Decisión:** **PostgreSQL** + `JSONB` para los datos de análisis
  semiestructurados. Cola de trabajos en la propia BD con
  `FOR UPDATE SKIP LOCKED`.

### 2.2 Reglas del ajedrez

- **Propuesta original:** usar una librería Java de terceros.
- **Cuestionamiento:** en hexagonal, el **dominio es el corazón**; si el
  corazón es una librería externa, el dominio queda vacío y la arquitectura
  no enseña nada. Las reglas del ajedrez son el dominio perfecto: finito,
  bien especificado y verificable al 100 % con *perft*.
- **Decisión:** **implementación propia** en `kopi-domain`, sin
  dependencias. Una librería (p. ej. chesslib) solo como **oráculo en
  pruebas** diferenciales.

### 2.3 Cinco bots

- **Propuesta original:** todos con Stockfish y `UCI_Elo` / `Skill Level`.
- **Cuestionamiento:** `UCI_Elo` tiene un **mínimo de 1320** (comprobado
  en Stockfish 19, ver [`notas/stockfish-opciones.md`](notas/stockfish-opciones.md)),
  demasiado fuerte para un principiante real. Y un `Skill Level` bajo juega «raro» más que
  «humano».
- **Decisión:** los dos niveles bajos son **bots propios** (escritos por
  nosotros, buen ejercicio) y los tres altos usan Stockfish. Todos detrás
  del mismo puerto `BotPlayer`.

### 2.4 Clasificación de jugadas

- **Propuesta original:** umbrales de pérdida en centipeones (CPL),
  configurables «a validar».
- **Cuestionamiento:** el CPL castiga igual perder 200 cp en +0,5 que en +9
  (en el segundo caso la partida sigue ganada). Dejar los umbrales «por
  validar» posterga una decisión central.
- **Decisión:** usar **probabilidad de victoria (Win %)** con la fórmula
  pública de Lichess y sus umbrales (§8.3). Es explicable, está documentada
  y es reproducible.

### 2.5 Jugadas de libro

- **Propuesta original:** API Lichess Opening Explorer (opcional).
- **Cuestionamiento:** dependencia de red, límites de uso y condiciones
  cambiantes para algo que puede resolverse en local.
- **Decisión:** dataset **lichess-org/chess-openings** (CC0, 3.865
  aperturas) incluido en la aplicación y cargado en memoria. Búsqueda por
  posición, sin red (ADR-009).

### 2.6 Progreso del análisis

- **Propuesta original:** *polling* a `GET /analysis`.
- **Cuestionamiento:** funciona, pero Spring trae soporte nativo para
  *Server-Sent Events*, que es más simple para el cliente y más didáctico.
- **Decisión:** `202 Accepted` + **SSE** en `/analysis/events`.
  `GET /analysis` se mantiene como alternativa.

### 2.7 Cola de análisis

- **Propuesta original:** en memoria («se pierde al reiniciar»).
- **Cuestionamiento:** perder trabajos es un defecto, no una simplificación,
  y la solución duradera no cuesta infraestructura extra si ya tenemos
  PostgreSQL.
- **Decisión:** tabla `analysis_job` con *workers* que toman trabajos con
  `SKIP LOCKED`. Recuperación tras reinicio incluida.

### 2.8 Arquitectura del backend

- **Propuesta original:** módulos por capa técnica (`api`, `persistence`,
  `engine`…).
- **Cuestionamiento:** es un monolito en capas con nombres de hexagonal.
  Nada impide que el dominio importe Spring o JDBC.
- **Decisión:** **módulos Maven separados** por anillo hexagonal +
  **ArchUnit** para que la regla de dependencias falle la compilación si se
  viola.

### 2.9 Etiquetas especiales

- **Propuesta original:** `BRILLIANT`, `EXCELLENT`, `FORCED`, `MISSED_WIN`
  en el MVP.
- **Cuestionamiento:** son heurísticas difíciles de definir sin falsos
  positivos y empujan a imitar a Chess.com.
- **Decisión:** MVP con `BEST`, `GOOD`, `INACCURACY`, `MISTAKE`, `BLUNDER`,
  `BOOK` y `MISSED_MATE`. El resto queda en el roadmap.

### 2.10 Propiedad de la partida

- **Propuesta original:** sin usuarios; cualquiera con el `gameId` puede
  mover.
- **Cuestionamiento:** sería un problema en una demo pública, pero Kopi Chess
  se ejecuta en local para una sola persona: un *token* por partida añadiría
  complejidad sin aportar al foco de aprendizaje.
- **Decisión:** **sin *tokens* ni autenticación.** Lo que sí se mantiene es
  el **control optimista por versión**, porque protege frente a dobles clics
  y peticiones duplicadas, que ocurren igual en local.

### 2.11 Utilidad

- **Propuesta original:** solo partidas contra bots.
- **Cuestionamiento:** limita mucho el valor del análisis.
- **Decisión:** **importación de PGN** en el MVP (entregable 5).

### 2.12 Licencias

- **Propuesta original:** «revisar GPLv3 de Stockfish».
- **Cuestionamiento:** el repo **ya es GPL-3.0**, igual que Stockfish y
  Chessground: la duda desaparece.
- **Decisión:** mantener GPL-3.0; añadir `NOTICE` con atribuciones.

### 2.13 Frontend

- **Propuesta original:** SPA en Angular servida por Nginx.
- **Cuestionamiento:** el objetivo de aprendizaje es Java, Spring y
  hexagonal; un SPA añade otro lenguaje, otro *toolchain* (Node) y otro
  contenedor sin aportar a ese objetivo.
- **Decisión:** **Thymeleaf + htmx** renderizado por Spring Boot, como
  segundo adaptador de entrada (`kopi-adapter-web`) junto al REST. Solo JS
  mínimo para el tablero (Chessground).

### 2.14 Idiomas

- **Propuesta original:** solo español, sin mención a internacionalización.
- **Cuestionamiento:** la interfaz debe estar en inglés y español. Si se deja
  para el final, los textos acaban incrustados en plantillas, excepciones y
  dominio.
- **Decisión:** **i18n desde la fase 1** con `MessageSource` de Spring. El
  dominio nunca produce textos para personas, solo **códigos** (enums,
  records); la traducción ocurre en los adaptadores de entrada (§11.4).

### 2.15 Lo que se mantiene

De la propuesta original se conserva, porque es acertado:

- El backend es la autoridad sobre el estado y la legalidad.
- El análisis es un proceso posterior e independiente de la partida.
- Stockfish como proceso hijo gestionado por el backend, detrás de una
  interfaz, con pool limitado, *timeouts* y reinicio ante fallos.
- Normalizar todas las evaluaciones a una sola perspectiva.
- «El motor encontró mate en N», nunca «existe mate en N».
- Versiones fijadas y parámetros del análisis registrados con el resultado.

---

## 3. Stack tecnológico

- **Lenguaje — Java 25 (LTS).** Records, *sealed interfaces*, *pattern
  matching* en `switch`, hilos virtuales, `ScopedValue` (final en 25).
- **Framework — Spring Boot 4.x (Spring Framework 7).** Fijar la última 4.x
  estable al arrancar. Usar `ProblemDetail` (RFC 9457), `RestClient`, SSE
  (`SseEmitter`), Actuator, null-safety con JSpecify.
- **Build — Maven multi-módulo.** Gradle también vale; Maven por ser más
  explícito para la separación de módulos.
- **Persistencia — PostgreSQL 18.** `JSONB` para variantes y detalles de
  análisis.
- **Acceso a datos — Spring Data JDBC o `JdbcClient`.** Sin JPA: evita la
  tentación de anotar el dominio y encaja mejor con agregados DDD.
- **Migraciones — Flyway.** Esquema versionado desde el primer commit.
- **Motor — Stockfish 19 (o la estable vigente) vía UCI.** Fijar versión y
  hash del binario en el `Dockerfile`.
- **Aperturas — lichess-org/chess-openings (CC0).** Los TSV van como
  recursos y se cargan en memoria (ADR-009).
- **Interfaz web — Thymeleaf + htmx (servidas por Spring Boot).** HTML
  generado en el servidor; htmx para interacciones parciales sin escribir un
  SPA. Sin Node en el flujo principal.
- **Tablero — Chessground (GPL-3.0, sin dependencias).** Es el tablero de
  Lichess. Se incluye como módulo ES ya compilado (fijado por versión) y se
  controla con un script propio pequeño.
- **Pruebas — JUnit 5, AssertJ, ArchUnit, Testcontainers, jqwik** (opcional,
  *property-based*). Ver §13.
- **Contenedores — Docker + Docker Compose.** `app` (Boot + Stockfish +
  interfaz web) y `postgres`.
- **Observabilidad — Actuator + Micrometer** (+ Prometheus/Grafana
  opcional).

> **Por qué no MongoDB, en una línea:** el único argumento a su favor era
> «la partida es un documento», y eso se cubre con una columna `JSONB` sin
> renunciar a transacciones, *joins*, consultas agregadas ni a una cola
> duradera. Y como en hexagonal la persistencia es un adaptador, si algún día
> se quisiera Mongo, sería un módulo más, sin tocar el dominio.

---

## 4. Arquitectura hexagonal

### 4.1 Vista general

```text
                   ┌─────────────────────────── Adaptadores de entrada ───┐
                   │  Web (Thymeleaf+htmx)  REST  SSE  Worker de análisis │
                   └───────────────┬──────────────────────────────────────┘
                                   │ llama a puertos de entrada (use cases)
                   ┌───────────────▼──────────────────────────────────────┐
                   │                 kopi-application                     │
                   │  CreateGame · PlayMove · Resign · ImportPgn          │
                   │  RequestAnalysis · RunAnalysisJob · GetGame          │
                   │                                                      │
                   │        ┌────────────────────────────────┐            │
                   │        │          kopi-domain           │            │
                   │        │  Board · Position · Move       │            │
                   │        │  MoveGenerator · Rules · SAN   │            │
                   │        │  FEN · PGN · Game (agregado)   │            │
                   │        │  Evaluation · WinChance        │            │
                   │        │  MoveClassifier · BotProfile   │            │
                   │        └────────────────────────────────┘            │
                   │                                                      │
                   │  Puertos de salida: GameRepository, ChessEngine,     │
                   │  BotPlayer, OpeningBook, AnalysisJobQueue, Clock,    │
                   │  AnalysisEvents                                      │
                   └───────────────┬──────────────────────────────────────┘
                                   │ implementados por
                   ┌───────────────▼─────────────── Adaptadores de salida ┐
                   │ PostgreSQL (JDBC) · Stockfish (UCI) · Bots propios   │
                   │ Opening book (tabla) · SSE publisher                 │
                   └──────────────────────────────────────────────────────┘
```

**Regla de dependencias:** las flechas solo apuntan hacia dentro.
`kopi-domain` no conoce a nadie; `kopi-application` solo conoce al dominio;
los adaptadores conocen a la aplicación; `kopi-bootstrap` lo conecta todo.

### 4.2 Módulos Maven

```text
kopi-chess/
├── backend/
│   ├── pom.xml                       (parent, BOM de Spring Boot)
│   ├── kopi-domain/                  Java puro. Cero dependencias (solo JUnit en test).
│   ├── kopi-application/             Casos de uso + puertos. Depende de domain.
│   ├── kopi-adapter-rest/            API JSON pública: controladores, DTO, ProblemDetail, SSE.
│   ├── kopi-adapter-web/             Interfaz web: controladores Thymeleaf, plantillas, htmx, JS del tablero.
│   ├── kopi-adapter-persistence/     Spring Data JDBC / JdbcClient + Flyway.
│   ├── kopi-adapter-stockfish/       Cliente UCI, pool de procesos.
│   ├── kopi-adapter-bots/            Bots propios (niveles 1 y 2).
│   ├── kopi-adapter-openings/        Libro de aperturas (en memoria, desde TSV).
│   ├── kopi-bootstrap/               @SpringBootApplication, configuración, wiring.
│   ├── kopi-architecture-tests/      Reglas ArchUnit sobre todos los módulos.
│   └── kopi-labs/                    Ejemplos con features preview (--enable-preview). Nadie depende de él.
├── compose.yaml                       docker compose up --build
└── docs/
    ├── kopi-chess-diseno-tecnico.md
    └── adr/                           Architecture Decision Records
```

Decisión a tomar (ADR-002): **¿`kopi-application` puede depender de Spring?**
Recomendación: **no**. Los casos de uso son clases Java planas; se registran
como beans en `kopi-bootstrap` mediante `@Configuration`. Esto obliga a
entender de verdad la inversión de dependencias y deja la transaccionalidad
como una preocupación del adaptador/bootstrap (p. ej., un decorador
`TransactionalPlayMove` o un puerto `UnitOfWork`).

### 4.3 Puertos

**Entrada (casos de uso):**

```java
public interface PlayMoveUseCase {
    GameView play(PlayMoveCommand command);
}

public record PlayMoveCommand(GameId gameId, String uci, long expectedVersion) {}
```

- **`CreateGame`** — crea partida contra un perfil; si el bot juega con
  blancas, hace su primera jugada.
- **`PlayMove`** — valida y aplica la jugada humana; si la partida sigue,
  obtiene y aplica la del bot.
- **`Resign`** — termina la partida por abandono.
- **`ImportPgn`** — parsea y valida un PGN, crea una partida `IMPORTED` ya
  terminada.
- **`RequestAnalysis`** — encola el análisis (idempotente).
- **`RunAnalysisJob`** — lo ejecuta el *worker*: analiza, clasifica, guarda,
  publica progreso.
- **`GetGame` / `ListGames` / `GetAnalysis`** — lecturas.

**Salida:**

```java
public interface ChessEngine {               // análisis "puro"
    EngineAnalysis analyse(Position position, SearchLimits limits, int multiPv);
}

public interface BotPlayer {                 // elegir jugada para un perfil
    Move chooseMove(Position position, BotProfile profile);
}

public interface GameRepository {
    Optional<Game> findById(GameId id);
    void save(Game game);                    // con control optimista por versión
}

public interface OpeningBook {
    Optional<Opening> lookup(Position position);
}

public interface AnalysisJobQueue {
    void enqueue(GameId id);
    Optional<AnalysisJob> claimNext();       // SKIP LOCKED en el adaptador
}

public interface AnalysisEvents {
    void progress(GameId id, int analysedPlies, int totalPlies);
    void completed(GameId id);
    void failed(GameId id, String reason);
}
```

Nótese que `ChessEngine` y `BotPlayer` son **puertos distintos**: el bot
nivel 1 no necesita un motor, y el análisis no necesita «personalidad».

### 4.4 Java 25 al servicio del diseño

- **`record`** — `Move`, `Square`, `Evaluation`, comandos, DTO, eventos.
- **`sealed interface` + `switch` exhaustivo** — `Evaluation = Centipawns |
  MateIn`; `GameOutcome = Checkmate | Stalemate | ...`; `Piece`. El
  compilador obliga a tratar todos los casos.
- **Patrones de registro** — `case MateIn(int n) when n > 0 -> ...`
- **Hilos virtuales** — lectura bloqueante de `stdout` de Stockfish y
  *workers* de análisis sin agotar hilos de plataforma.
- **`ScopedValue`** — propagar `gameId` / *correlation id* a logs sin
  `ThreadLocal`.
- **Variables sin nombre `_`** (final en 22) — `case Checkmate _ -> ...`,
  `catch (TimeoutException _)`.
- **Stream Gatherers** (final en 24) — `Gatherers.windowSliding(n)` para las
  ventanas de volatilidad del cálculo de precisión (§8.4) y para recorrer
  pares de posiciones consecutivas (antes/después de cada jugada).
- **Comentarios de documentación en Markdown `///`** (final en 23) — en todo
  el dominio, que es el código que más se va a leer.
- **Cuerpos de constructor flexibles** (final en 25) — validar argumentos
  antes de `super(...)` en las excepciones de dominio y en clases con
  herencia.
- **Archivos fuente compactos y `void main()`** (final en 25) — utilidades de
  desarrollo ejecutables con `java Perft.java` sin proyecto Maven (p. ej. un
  *perft* de consola o el conversor del dataset de aperturas).
- **Caché AOT** (JEP 514/515, en 25) — arranque más rápido de la app en
  local; Spring Boot 4 tiene soporte para generarla.
- ***Structured concurrency* (preview en 25)** — analizar varias posiciones
  en paralelo con cancelación conjunta.

**Política de *preview*:** el código principal usa solo *features* finales.
Las *preview* (*structured concurrency*, *stable values*, patrones con tipos
primitivos) se muestran en un módulo aislado `kopi-labs`, compilado con
`--enable-preview`, que no es dependencia de ningún otro módulo. Así se
aprenden sin atar la app a APIs que pueden cambiar.

Ejemplo del estilo esperado en el dominio:

```java
public sealed interface Evaluation permits Evaluation.Centipawns, Evaluation.MateIn {
    record Centipawns(int value) implements Evaluation {}
    record MateIn(int moves) implements Evaluation {}   // >0 ganan blancas, <0 negras

    default double whiteWinChance() {                     // [-1, 1]
        return switch (this) {
            case Centipawns(int cp) -> 2 / (1 + Math.exp(-0.00368208 * clamp(cp))) - 1;
            case MateIn(int m)      -> m > 0 ? 1.0 : -1.0;
        };
    }
}
```

---

## 5. Modelo de dominio

### 5.1 Representación del tablero

Para un proyecto educativo, priorizar **legibilidad** sobre velocidad:

- **MVP: tablero 0x88 o *mailbox* 10×12.** Fácil de depurar, detección de
  casillas fuera del tablero trivial, suficientemente rápido para validar
  jugadas y para bots de profundidad baja.
- **Futuro (opcional): *bitboards*** detrás de la misma interfaz `Board`. Un
  buen segundo ejercicio, con *perft* garantizando que nada se rompe.

Tipos principales:

- **`Square`, `Piece`, `Color`, `PieceType`** — valores básicos (records /
  enums).
- **`Move`** — origen, destino, promoción, tipo (normal, enroque, al paso,
  doble avance).
- **`Position`** — inmutable: tablero, turno, derechos de enroque, casilla al
  paso, reloj de 50 jugadas, número de jugada, *hash* Zobrist.
- **`MoveGenerator`** — jugadas pseudo-legales y legales.
- **`Rules`** — jaque, mate, ahogado, material insuficiente, 50 jugadas,
  triple repetición.
- **`Fen`, `San`, `Pgn`** — notaciones: parseo y serialización.
- **`Game` (agregado raíz)** — historial de posiciones, estado, resultado,
  motivo, versión. Protege las invariantes.

### 5.2 Agregado `Game`

```text
          create()                     applyMove() termina
   ──────────────► IN_PROGRESS ─────────────────────────► FINISHED
                       │                                     ▲
                       │ resign()                            │
                       └─────────────────────────────────────┘
   importPgn() ────────────────────────────────────────────► FINISHED (origin = IMPORTED)
```

Invariantes que el agregado garantiza (no el controlador, no el servicio):

- Solo se mueve en `IN_PROGRESS` y solo el color al que le toca.
- Toda jugada aplicada es legal en la posición actual.
- Tras cada jugada se evalúa el fin de partida.
- La versión se incrementa con cada cambio (control optimista).

Motivos de finalización, **todos implementados** (no «si la librería lo
soporta»): `CHECKMATE`, `STALEMATE`, `INSUFFICIENT_MATERIAL`,
`THREEFOLD_REPETITION`, `FIFTY_MOVE_RULE`, `RESIGNATION`.
Para simplificar el MVP, la repetición y los 50 movimientos se aplican de
forma **automática** (en reglas FIDE son reclamables; la quíntuple repetición
y las 75 jugadas sí son automáticas). Documentarlo en un ADR.

### 5.3 Perfiles de bot

```java
public enum BotProfile { PAWN, KNIGHT, BISHOP, ROOK, QUEEN }
```

Nombres temáticos en lugar de BEGINNER…EXPERT: más simpáticos y no prometen
un nivel concreto. Detalle en §7.

---

## 6. Persistencia (PostgreSQL)

### 6.1 Esquema inicial

```sql
create table game (
    id               uuid primary key,
    origin           text not null,          -- PLAYED | IMPORTED
    status           text not null,          -- IN_PROGRESS | FINISHED
    human_color      text,                   -- null si IMPORTED
    bot_profile      text,                   -- null si IMPORTED
    initial_fen      text not null,
    result           text,                   -- WHITE_WIN | BLACK_WIN | DRAW
    termination      text,
    opening_eco      text,
    opening_name     text,
    pgn_headers      jsonb,                  -- solo para importadas
    version          bigint not null,
    created_at       timestamptz not null,
    finished_at      timestamptz
);

create table game_move (
    game_id    uuid not null references game(id) on delete cascade,
    ply        int  not null,                -- 1 = primera jugada de blancas
    uci        text not null,
    san        text not null,
    fen_after  text not null,
    played_at  timestamptz,
    primary key (game_id, ply)
);

-- Fase 5 (V3__analysis.sql): solo resultados; el estado vive en analysis_job (ADR-007).
create table analysis (
    game_id           uuid primary key references game(id) on delete cascade,
    engine            text not null,          -- 'Stockfish 19'
    limit_kind        text not null,          -- DEPTH | NODES
    limit_value       bigint not null,
    multi_pv          int  not null,
    missed_mate_limit int  not null,
    first_mover       text not null,          -- WHITE | BLACK
    opening_eco       text,                   -- última apertura reconocida (§9)
    opening_name      text,
    started_at        timestamptz not null,
    completed_at      timestamptz not null
);

create table move_analysis (
    game_id         uuid not null references analysis(game_id) on delete cascade,
    ply             int  not null,
    eval_before     text not null,           -- 'cp 34' | 'mate -3' | 'checkmate WHITE', perspectiva BLANCAS
    eval_after      text not null,
    best_move_uci   text not null,
    best_line_san   text not null,           -- 'Nf3 Nc6 Bb5'
    classification  text not null,
    win_chance_loss double precision not null,
    missed_mate_in  int,
    primary key (game_id, ply),
    foreign key (game_id, ply) references game_move(game_id, ply) on delete cascade
);

create table analysis_job (
    id           bigserial primary key,
    game_id      uuid not null unique references game(id) on delete cascade,
    state        text not null,              -- QUEUED | RUNNING | DONE | FAILED
    attempts     int  not null default 0,
    last_error   text,
    locked_at    timestamptz,                -- solo mientras RUNNING
    created_at   timestamptz not null
);

create index game_created_at_idx on game (created_at desc);
create index analysis_job_queued_idx on analysis_job (created_at) where state = 'QUEUED';
```

Sin `jsonb` (el diseño inicial lo usaba para evaluaciones, configuración y
resumen): las evaluaciones van como texto con un `CHECK` de formato, la
configuración en columnas, y el **resumen no se guarda**: se calcula de las
jugadas (`GameAnalysis.summary()`), así que no puede contradecirlas.

Decisiones:

- **Convención de perspectiva única:** todas las evaluaciones se guardan
  desde el punto de vista de las **blancas**. La conversión a «perspectiva del
  jugador» ocurre solo en el dominio, al clasificar.
- `fen_after` se guarda por comodidad de análisis y depuración; es
  redundante (se reconstruye desde `initial_fen` + jugadas) y está bien que
  lo sea.
- El *mapping* fila ↔ agregado vive en el adaptador. El dominio no tiene
  anotaciones.

### 6.2 Cola de análisis sin infraestructura extra

```sql
-- claimNext()
update analysis_job
   set state = 'RUNNING', locked_at = now(), attempts = attempts + 1
 where id = (select id from analysis_job
              where state = 'QUEUED'
              order by created_at
              for update skip locked
              limit 1)
returning id, game_id;
```

Al arrancar, los trabajos `RUNNING` con `locked_at` antiguo vuelven a
`QUEUED` (o pasan a `FAILED` si superan N intentos). Es un patrón real de
producción y se aprende sin añadir RabbitMQ/Kafka.

---

## 7. Los cinco bots

#### `PAWN` — nivel 1

- **Implementación:** propia (`kopi-adapter-bots`).
- **Idea:** jugada aleatoria ponderada: prefiere capturas y jaques, pero
  cuelga piezas a menudo. Juega como un niño que acaba de aprender.

#### `KNIGHT` — nivel 2

- **Implementación:** propia.
- **Idea:** minimax/alfa-beta a profundidad 2 con evaluación de material +
  tabla de casillas sencilla. Ya no regala piezas en una jugada, pero no ve
  tácticas largas.

#### `BISHOP` — nivel 3

- **Implementación:** Stockfish.
- **Idea:** `UCI_LimitStrength` con `UCI_Elo` 1320 (el mínimo) y `movetime`
  corto. (Calibrado: ver `docs/notas/calibracion.md`.)

#### `ROOK` — nivel 4

- **Implementación:** Stockfish.
- **Idea:** `UCI_LimitStrength=true`, `UCI_Elo` 2000.

#### `QUEEN` — nivel 5

- **Implementación:** Stockfish.
- **Idea:** sin limitar; `movetime` acotado (p. ej. 1 s) para no bloquear la
  CPU.

Por qué así:

- `UCI_Elo` no baja de **1320** (rango 1320–3190 en Stockfish 19; valores
  menores se ajustan al mínimo sin avisar).
  Por debajo, **escribir bots propios es a la vez la solución y un ejercicio
  excelente**: generar jugadas, evaluar, buscar. Además, el bot `KNIGHT`
  reutiliza el dominio entero, lo que valida su API.
- Los valores concretos (`Skill Level`, `UCI_Elo`, `movetime`) van en
  configuración, no en el código, y se registran con cada partida.
- **Calibración** (entregable 3): un *harness* que enfrenta perfiles entre sí
  (N partidas, aperturas variadas) y comprueba que cada nivel gana al anterior
  con claridad. No se publica ningún Elo.
- Aleatoriedad con semilla configurable → partidas reproducibles en pruebas.

---

## 8. Análisis posterior

### 8.1 Flujo

```text
FINISHED ─► RequestAnalysis ─► analysis_job(QUEUED)
                                      │  worker (hilo virtual)
                                      ▼
            para cada posición (ply 0..N):
              ChessEngine.analyse(pos, limits, multiPv=2)
              → eval (perspectiva blancas) + mejor jugada + PV
                                      │
                                      ▼
            para cada jugada: MoveClassifier (dominio, puro)
            OpeningBook.lookup → BOOK / nombre de apertura
                                      │
                                      ▼
            guardar move_analysis + summary ─► AnalysisEvents (SSE)
```

En el código (§8.3 del plan): finalizar una partida (`PlayMove`, `Resign`)
la encola sin que nadie lo pida; si encolar falla, la partida queda
guardada igualmente y el análisis se puede pedir después
(`RequestAnalysis`). Un `AnalysisWorker` en `kopi-bootstrap` (hilos
virtuales, `kopi.analysis.workers`, 1 por defecto) llama a
`RunAnalysisJob` en bucle; al arrancar devuelve a la cola, o da por
fallidos, los trabajos que un arranque anterior dejó en `RUNNING`. Una
jugada es `BOOK` mientras todas las posiciones hasta ella, la suya
incluida, tengan nombre en el libro; la primera sin nombre cierra el libro
para el resto de la partida. La apertura de la partida es la última
posición con nombre por la que pasó.

Optimización clave: **una sola búsqueda por posición**. La evaluación
«después» de la jugada *k* es la evaluación «antes» de la jugada *k+1*.
Para N jugadas se analizan N+1 posiciones, no 2N.

### 8.2 Presupuesto

Configurable y registrado en `analysis.config`:

- Límite por posición: `depth` **o** `nodes` (más reproducible que tiempo
  entre máquinas distintas). Medido (`docs/notas/presupuesto-analisis.md`):
  la profundidad no acota el tiempo (a `depth 18` una posición tardó 93 s),
  los nodos sí; propuesta `nodes 1000000` (~1 s por posición).
- `MultiPV = 2`: permite saber si la mejor jugada era «única» (útil para
  futuras etiquetas como «solo jugada»).
- `Threads = 1`, `Hash` fijo (64 MB), puestos una vez al arrancar cada
  motor; un análisis concurrente en el MVP, con **su propio *pool* de
  motores**, aparte del de los bots, para que una partida no espere detrás
  de un análisis.
- *Timeout* total por partida y reintentos limitados.

### 8.3 Clasificación (fórmula pública)

Se usa la **probabilidad de victoria** en vez de centipeones crudos, con la
fórmula que Lichess publica:

```text
Win% = 50 + 50 · (2 / (1 + exp(−0.00368208 · cp)) − 1)
```

En el dominio se trabaja con *winning chances* en `[-1, 1]`
(`Win%/50 − 1`). Para la jugada de un color:

```text
loss = winChance(antes, perspectiva del que mueve) − winChance(después, misma perspectiva)
```

Reglas del MVP:

- **`BOOK`** — la posición resultante está en el libro de aperturas y aún no
  ha salido de él.
- **`BEST`** — la jugada coincide con la mejor del motor (o pérdida ≤ 0.01:
  dos búsquedas de posiciones casi iguales nunca coinciden del todo).
- **`GOOD`** — `loss < 0.1`
- **`INACCURACY`** — `0.1 ≤ loss < 0.2`
- **`MISTAKE`** — `0.2 ≤ loss < 0.3`
- **`BLUNDER`** — `loss ≥ 0.3`
- **`MISSED_MATE`** — antes había `MateIn(n)` a favor del que mueve (n ≤
  umbral configurable, p. ej. 5) y después ya no. Prevalece sobre las demás.

Los umbrales 0.1 / 0.2 / 0.3 son los que usa Lichess (`Advice.scala`).
Partir de algo probado y público evita discusiones interminables; se pueden
ajustar después con datos.

Detalles a tratar explícitamente (con pruebas):

- Evaluaciones de mate → `winChance = ±1`. Una posición que ya es mate es
  `Evaluation.Checkmate(winner)`, no `MateIn(0)`: Stockfish da `mate 0`
  desde el bando que mueve y, al pasarlo a las blancas, el cero pierde el
  signo (ADR-005).
- *Clamp* de centipeones (p. ej. ±1000) antes de la fórmula.
- Posiciones ya decididas: con la fórmula de Win % un −200 cp en +900 apenas
  pesa, que es justo lo que queremos.
- Jugadas forzadas (una sola legal): no clasificar como error.

### 8.4 Precisión

Precisión por jugada, también fórmula pública de Lichess:

```text
Accuracy% = 103.1668 · exp(−0.04354 · (Win%antes − Win%después)) − 3.1669
```

Precisión de la partida, como Lichess: la media de dos medias de las
precisiones por jugada de cada color (ADR-005):

- **Media armónica**, que tira hacia las peores jugadas. Una precisión por
  debajo de 1 cuenta como 1, para que un solo 0 no anule la partida.
- **Media ponderada por volatilidad**: cada jugada pesa la desviación típica
  del Win % en una ventana de posiciones (entre 0.5 y 12). Las ventanas
  tienen una décima parte de la partida, entre 2 y 8 posiciones
  (`Gatherers.windowSliding`), y las primeras repiten la inicial para que
  haya una por jugada.

En la interfaz se muestra como «Precisión (fórmula Lichess)», enlazando a la
explicación.

### 8.5 Qué no hacemos todavía

`BRILLIANT`, `GREAT`, `EXCELLENT`, `MISSED_WIN`: requieren heurísticas
(sacrificio de material, jugada única, dificultad «humana»…) con alto riesgo
de falsos positivos. Quedan para v2 con un ADR propio y un conjunto de
posiciones de prueba antes de implementarlas.

### 8.6 Mates desaprovechados

Igual que en la propuesta original, con dos precisiones:

- Se reporta solo si el motor da `mate n` **en la posición antes de la
  jugada** y la jugada elegida no lo mantiene.
- El texto siempre es «el motor encontró mate en *n*» y se muestra la línea
  completa en SAN, reproducible en el tablero.

---

## 9. Libro de aperturas

- Fuente: `lichess-org/chess-openings` (CC0). Archivos `a.tsv`…`e.tsv`
  con `eco`, `name`, `pgn`, fijados a un *commit* (la versión `dist/`, con
  `epd`, no está en el repositorio: se genera).
- Carga (ADR-009): `LichessOpeningBook` reproduce cada línea con las reglas
  de Kopi y guarda en memoria la posición a la que llega. Sin tabla.
- Búsqueda: por posición, con `Position.zobristHash()`, la misma «misma
  posición» que la regla de repetición (sin relojes, captura al paso solo si
  es utilizable), equivalente al EPD. Esto detecta transposiciones, cosa
  que una búsqueda por secuencia de jugadas no hace.
- La partida guarda la **última** apertura reconocida como su nombre.
- Atribución en `NOTICE` aunque CC0 no lo exija.

---

## 10. API REST

Prefijo `/api/v1`. Errores en formato **`ProblemDetail`** (RFC 9457).

- **`GET /bots`** — los cinco perfiles con descripción.
- **`POST /games`** — crear partida. Devuelve `gameId`.
- **`POST /games/import`** — importar PGN (`text/plain` o JSON).
- **`GET /games`** — listado paginado.
- **`GET /games/{id}`** — estado: FEN, turno, jugadas, resultado, versión,
  jugadas legales.
- **`POST /games/{id}/moves`** — jugada humana. Header
  `If-Match: <version>`.
- **`POST /games/{id}/resign`** — rendirse.
- **`GET /games/{id}/pgn`** — exportar PGN.
- **`POST /games/{id}/analysis`** — encolar análisis. `202 Accepted`;
  idempotente.
- **`GET /games/{id}/analysis`** — resultado o estado.
- **`GET /games/{id}/analysis/events`** — SSE: `progress`, `completed`,
  `failed`.

### 10.1 Jugada

```http
POST /api/v1/games/7c1e.../moves
If-Match: "12"
Accept-Language: es
Content-Type: application/json

{ "uci": "e7e8q" }
```

Respuesta `200`: estado actualizado **incluyendo la jugada del bot**, para
que el cliente haga una sola petición por turno. El bot está acotado por
`movetime`, así que la latencia es predecible.

Errores:

- **`400`** — UCI mal formado.
- **`409`** — partida terminada o no es tu turno.
- **`412`** — `If-Match` no coincide (petición duplicada o concurrente).
- **`422`** — jugada ilegal en la posición actual.
- **`503`** — motor no disponible (la partida no cambia).

Los campos `title` y `detail` del `ProblemDetail` se traducen según
`Accept-Language` (inglés por defecto). Además se incluye una propiedad
`code` estable (p. ej. `ILLEGAL_MOVE`) para clientes que no quieran depender
del texto.

### 10.2 Jugadas legales en la respuesta

`GET /games/{id}` incluye `legalMoves` (lista UCI). Así el cliente
resalta destinos posibles **sin implementar reglas**: el servidor sigue
siendo la única autoridad y el cliente queda tonto a propósito.

La API REST es la **interfaz pública** del proyecto (scripts, futuras apps,
otros frontends). La interfaz web propia **no** la consume por HTTP: usa los
mismos casos de uso directamente, como cualquier otro adaptador de entrada.

---

## 11. Interfaz web (Thymeleaf + htmx)

### 11.1 Por qué así

El objetivo del proyecto es aprender Java, Spring y Arquitectura Hexagonal.
Un SPA (Angular, React…) obligaría a aprender otro lenguaje y otro ecosistema
y a mantener un segundo proyecto, sin aportar a ese objetivo. Con Thymeleaf
+ htmx:

- Todo vive en Spring Boot: un solo build, un solo contenedor.
- La interfaz es **otro adaptador de entrada** (`kopi-adapter-web`), al lado
  del REST. Ambos llaman a los mismos casos de uso: la demostración más
  clara de que el núcleo no depende de cómo se entra.
- Si en el futuro alguien quiere un SPA, la API REST ya existe y el núcleo
  no cambia.

### 11.2 Cómo funciona

- **Páginas Thymeleaf:** Inicio (elegir bot y color) · Partida · Revisión ·
  Importar PGN · Historial.
- **htmx** para actualizar fragmentos sin recargar: lista de jugadas, estado,
  resultado, panel de análisis. El servidor devuelve fragmentos HTML
  (`th:fragment`), no JSON.
- **Tablero:** Chessground cargado como módulo ES fijado por versión (copiado
  en `static/vendor/` o desde un CDN con versión exacta). Un script propio
  `board.js` (~100 líneas) hace tres cosas:
  1. Pinta la posición y los destinos legales que el servidor incluye en el
     HTML (`data-fen`, `data-legal-moves`).
  2. Al soltar una pieza, envía la jugada (`htmx.ajax` o `fetch`) y aplica la
     respuesta: nueva posición + jugada del bot.
  3. En la revisión, mueve el tablero al navegar por las jugadas.
- **Progreso del análisis:** extensión SSE de htmx (`hx-ext="sse"`) conectada
  a un endpoint SSE del adaptador web; cada evento reemplaza la barra de
  progreso y, al terminar, se carga el fragmento de revisión.
- **Gráfica de evaluación:** SVG generado en el servidor desde la plantilla
  (una polilínea con los Win %); sin librería de gráficos.
- **Estilos:** un CSS propio sin *framework* (`static/css/kopi.css`), con la
  identidad visual de la familia de webs (§11.6).

### 11.3 Reglas

- Sin lógica de ajedrez en JavaScript. Todo lo que el tablero necesita saber
  (posición, destinos legales, última jugada, flechas de la mejor línea) lo
  calcula el dominio y llega en el HTML.
- Accesibilidad: categorías con icono + texto (no solo color), navegación
  por teclado con ← → en la revisión.

### 11.4 Idiomas (inglés y español)

- **Archivos de mensajes:** `i18n/web/messages.properties` (inglés, idioma
  por defecto) y `i18n/web/messages_es.properties` en `kopi-adapter-web`; el
  adaptador REST tendrá los suyos (`i18n/rest/...`) para los errores.
  `kopi-bootstrap` los declara en `spring.messages.basename` (ADR-011).
- **Elección de idioma:** `CookieLocaleResolver` + `LocaleChangeInterceptor`
  (`?lang=es` / `?lang=en`). Primera visita: `Accept-Language` del
  navegador. Selector EN/ES visible en la cabecera de todas las páginas
  (en el modo oficina, discreto en el pie; §11.5).
- **Plantillas:** todo texto visible con `#{clave}`; ninguna cadena literal
  en el HTML. Una prueba recorre las plantillas y falla si encuentra texto
  sin traducir (o si una clave existe en un idioma y no en el otro).
- **JavaScript:** los pocos textos que necesite `board.js` llegan en
  atributos `data-*` ya traducidos desde la plantilla.
- **Dominio sin textos:** categorías (`BLUNDER`), motivos de fin
  (`STALEMATE`) y errores (`ILLEGAL_MOVE`) son enums o records; la
  traducción vive en el adaptador (`classification.BLUNDER=Error grave`).
  Un `MessageSource` jamás entra en el dominio ni en la aplicación.
- **Notación:** la SAN estándar usa letras inglesas (`N`, `B`, `R`, `Q`,
  `K`). En la interfaz se muestra en **notación de figuras** (`♘f3`), que no
  depende del idioma. Opcional: notación española (`Cf3`) como preferencia.
- **Nombres de aperturas:** el dataset de Lichess solo está en inglés; se
  muestran en inglés en ambos idiomas, junto al código ECO. Traducir las más
  comunes puede ser una contribución externa posterior.
- **Fechas y números:** con `#temporals` / `#numbers` de Thymeleaf según el
  `Locale` (`21/10/2026` vs `10/21/2026`, `87,5 %` vs `87.5%`).

### 11.5 Modo oficina

Una apariencia alternativa de la página de partida, pensada para dos cosas:

- **Pasar desapercibido:** la página parece la intranet o la web corporativa
  de una empresa cualquiera. Nada de tableros, colores de ajedrez ni iconos
  de piezas.
- **Entrenar la notación:** no se arrastran piezas; cada jugada se escribe
  (`e4`, `Cf3`, `exd5`, `O-O`, `e8=D`). Quien lo use acaba leyendo y
  escribiendo notación algebraica con soltura.

**Aspecto**

- Cabecera, menú, pie y textos de relleno de un portal interno
  **genérico y sin nombre de empresa** («Portal del empleado»,
  «Departamento de Operaciones»): sin logotipos ni marcas, y sin inventar
  un nombre que pudiera coincidir con una empresa real. El título de la
  pestaña es neutro («Informe de operaciones» / «Operations report»).
- Estilo sobrio de aplicación de gestión: tipografía del sistema, grises,
  sin imágenes.
- El tablero es una **tabla HTML** (`<table>`) con aspecto de informe:
  columnas `a`–`h` y filas `8`–`1` como cabeceras (`<th>`), una celda por
  casilla y sin colorear las casillas. Se orienta desde el color del jugador.
- Cada pieza es **una letra** en el idioma de la interfaz:

  | Pieza   | Español | Inglés |
  |---------|---------|--------|
  | Peón    | `P`     | `P`    |
  | Torre   | `T`     | `R`    |
  | Alfil   | `A`     | `B`    |
  | Caballo | `C`     | `N`    |
  | Dama    | `D`     | `Q`    |
  | Rey     | `R`     | `K`    |

  Todas las letras en mayúscula; el bando se distingue por el **color**:
  verde oscuro las blancas y rojo las negras (`#145a2c` / `#d42f2f`, con
  contraste AA sobre blanco y sobre el resaltado, y distinta luminosidad
  para quien no distingue bien el rojo del verde). El `title` de cada
  casilla nombra la pieza y el bando («a8 · Torre · Negras»). **Sin
  leyenda** bajo la tabla: delataría que es ajedrez. La última jugada se
  marca con fondo amarillo en sus dos casillas. (Al principio eran
  mayúsculas y minúsculas, con una leyenda; se cambió a petición, tras
  jugar con ello.)
- **A la derecha del tablero**, el listado de **jugadas legales** en
  notación algebraica del idioma (`Cf3`, `e4`, `O-O`), agrupadas por pieza y
  ordenadas. Al pulsar un código se escribe en el campo de texto, listo para
  registrarlo (`office.js`; sin JavaScript se copian a mano). Debajo, las jugadas de la partida como una tabla de dos
  columnas (blancas / negras) y el estado (turno, jaque, resultado).
- **Entrada de texto** (`<input>` dentro de un `<form>`) con un botón de
  enviar, con aspecto de campo de búsqueda o de formulario de la empresa.

**Entrada de jugadas**

- Se acepta la **notación algebraica del idioma de la interfaz**. La
  comparación se hace contra el listado de jugadas legales ya formateado,
  así que solo puede aceptarse una jugada legal.
- Es tolerante con lo que no cambia el significado: mayúsculas o minúsculas
  (`E4` = `e4`), `x` y `+`/`#` opcionales (`Cf3` = `Cf3+`, `exd5` = `ed5`),
  `0-0` = `O-O`, espacios.
- La tolerancia a mayúsculas crea ambigüedades porque algunas letras de
  pieza coinciden con columnas (`A`/`a`, `C`/`c`, `D`/`d` en español; `B`/`b`
  en inglés): `axb5` y `Axb5` pueden ser ambas legales. Si la entrada
  normalizada encaja con **más de una** jugada legal, no se juega nada y se
  responde «¿Quisiste decir `axb5` o `Axb5`?» con las candidatas. Si encaja
  con exactamente una, se juega. Si el texto coincide **exactamente**
  (mayúsculas incluidas) con una jugada, se juega esa sin preguntar: quien
  escribe `Axb5` ya ha dicho que es el alfil (ADR-013).
- Si no encaja con ninguna, se muestra un error traducido y el texto
  introducido se conserva en el campo.
- Tras una jugada aceptada, la respuesta muestra la jugada en su forma
  canónica («Has jugado `Cf3`») y la respuesta del bot. Así se aprende la
  forma correcta aunque se haya escrito de otra manera.
- Como atajo, también se acepta **UCI** (`g1f3`), que es como se escriben
  las jugadas en otros sitios del proyecto.

**Cómo encaja en la arquitectura**

- **Solo afecta a `kopi-adapter-web`.** El dominio sigue hablando SAN
  estándar en inglés (`San.format`, §5.5 del plan) y no sabe nada de
  idiomas (ADR-011). El adaptador traduce las letras de pieza para mostrar
  y hace la correspondencia de la entrada contra las jugadas legales
  formateadas. No hace falta `San.parse` en otro idioma.
- **Sin JavaScript para jugar:** formulario clásico (`POST` + redirección,
  *Post/Redirect/Get*). El único script, `office.js`, solo escribe en el
  campo el código pulsado: una mejora que no hace falta para jugar. Es la prueba de
  que la interfaz es un adaptador más: mismos casos de uso
  (`PlayMove`, `GetGame`), otra presentación.
- **Activación:** un enlace discreto en la página de partida («Vista de
  texto» / «Text view») y un parámetro `?view=office`; la preferencia se
  guarda en una *cookie*, como el idioma. Desde el modo oficina se vuelve
  con un enlace igual de discreto en el pie.
- **Textos:** todas las etiquetas, los errores y el «contenido
  de relleno» de la empresa ficticia van en los archivos de mensajes, en
  inglés y español. Las letras de pieza también (`piece.letter.KNIGHT=C`).
- **Accesibilidad de regalo:** una tabla con cabeceras y un campo de texto
  funcionan bien con lectores de pantalla y con teclado.

### 11.6 Identidad visual

Kopi Chess se ve como parte de la misma familia que las otras webs del
autor:
[arquitechthor.github.io](https://arquitechthor.github.io/),
[Kopi Tools](https://kopitools.link) (`kopi-web`) y
[Apuntes AWS](https://arquitechthor.github.io/aws-cert-study/)
(`aws-cert-study`).

- **Paleta y *tokens*:** los mismos *custom properties* de CSS que esas
  webs: fondo azul casi negro (`--bg: #070a14`), superficies `--surface` /
  `--surface-2`, texto `--text` / `--text-muted` / `--text-faint`, y los
  acentos **cian** (`#22d3ee`), **violeta** (`#a855f7`) y **magenta**
  (`#fb7185`), que salen de la mascota de Kopi y se combinan en
  `--gradient-brand`. **Solo tema oscuro**, como las demás.
- **Copiados, no compartidos:** igual que en las otras webs, los *tokens* y
  los componentes base se copian en `kopi.css`. Si cambian allí, se
  actualizan a mano aquí. Nunca se escribe un color fuera de los *tokens*.
- **Tipografía:** la del sistema (`--font`), pesos 800 en títulos, y
  `--font-mono` para FEN, PGN y notación cuando haga falta.
- **Componentes:** los mismos nombres y estilos (`.nav`, `.nav-links`,
  `.nav-toggle`, `.btn-primary` con el degradado, `.btn-secondary`,
  `.eyebrow`, `.gradient-text`, `.hero` con `--gradient-radial`, `.card`),
  contenedor de 1120 px y corte móvil a 760 px.
- **Menú:** el mismo patrón que las otras webs: marca a la izquierda
  (logo + «Kopi Chess»), «Inicio» el primer enlace y «Sobre mí» (a
  `arquitechthor.github.io`) el último, sin botón de llamada a la acción, y
  botón ☰ en móvil con `static/js/nav.js`, la misma lógica que en las otras
  webs. Kopi Chess añade al final el selector de idioma (`EN · ES`).
- **Pie:** © y licencia, enlace al código y, al final, la línea común
  `.footer-sitios`: **Kopi Tools · Apuntes AWS · Sobre mí**, con los mismos
  enlaces. Kopi Chess no aparece en esa línea porque se ejecuta en local y no
  tiene URL pública.
- **Logo:** provisional, un caballo (♞) con el degradado de la marca
  (`static/img/logo.svg`, también *favicon*). Cada web de la familia tiene
  su propio logo; el definitivo de Kopi Chess queda pendiente.
- **Diferencias deliberadas:** la interfaz es **bilingüe** (las otras webs
  solo están en español) y el **modo oficina** (§11.5) no usa nada de esta
  identidad: su objetivo es justamente no parecer Kopi.
- **Tablero (fase 3):** colores de casillas de Chessground ajustados a la
  paleta (claras y oscuras en tonos de `--surface-2` y violeta apagado) y
  resaltados de última jugada y destinos en cian, en lugar del marrón por
  defecto.

---

## 12. Docker Compose

El archivo real es [`compose.yaml`](../compose.yaml), en la raíz del repo, para
que baste con `docker compose up --build`. Forma prevista una vez integrado
Stockfish:

```yaml
services:
  postgres:
    image: postgres:18.6-alpine3.24
    environment:
      POSTGRES_DB: kopi
      POSTGRES_USER: kopi
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-kopi}   # local: valor por defecto
    volumes: [pgdata:/var/lib/postgresql]             # PG 18+: un nivel por encima de /data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U kopi -d kopi"]
    # sin "ports": solo accesible desde la red interna de Compose

  app:
    build: ./backend
    ports: ["127.0.0.1:${KOPI_PORT:-8080}:8080"]     # interfaz web + API REST, solo en local
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/kopi
      SPRING_DATASOURCE_USERNAME: kopi
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-kopi}
      KOPI_ENGINE_PATH: /opt/stockfish/stockfish
      KOPI_ENGINE_POOL_SIZE: 2
      KOPI_ANALYSIS_MAX_CONCURRENT: 1
    depends_on:
      postgres: { condition: service_healthy }
    deploy:
      resources: { limits: { cpus: "2", memory: 1g } }

volumes:
  pgdata:
```

`Dockerfile` del backend: *multi-stage* (Maven + JDK 25 → JRE 25), descarga
del binario oficial de Stockfish con **checksum verificado** (o compilación
desde fuente con la etiqueta fijada), usuario no root, *healthcheck* sobre
`/actuator/health/readiness`. La *readiness* incluye un `HealthIndicator`
que hace `isready`/`readyok` contra el pool UCI.

---

## 13. Estrategia de pruebas

La pirámide refleja los anillos hexagonales:

#### Dominio

- **Qué:** reglas, FEN/SAN/PGN, agregado `Game`, clasificador, fórmulas.
  **Sin Spring, en milisegundos.**
- **Herramientas:** JUnit 5, AssertJ, jqwik.

#### Perft

- **Qué:** conteo de nodos del generador de jugadas contra valores de
  referencia (tabla más abajo).
- **Herramientas:** JUnit parametrizado.

#### Diferencial

- **Qué:** para miles de posiciones de partidas aleatorias con semilla,
  nuestras jugadas legales, el jaque y el FEN = los de Stockfish (oráculo
  por UCI, solo en `test`; ver §5.6 del plan).
- **Herramientas:** JUnit + Stockfish.

#### Aplicación

- **Qué:** casos de uso con puertos falsos (repositorio en memoria, motor
  *fake*).
- **Herramientas:** JUnit.

#### Adaptadores

- **Qué:** JDBC contra PostgreSQL real; UCI contra Stockfish real; parser
  UCI con salidas grabadas.
- **Herramientas:** Testcontainers.

#### Arquitectura

- **Qué:** el dominio no importa `org.springframework..`, `java.sql..`, ni
  otros módulos; los adaptadores no se conocen entre sí.
- **Herramientas:** ArchUnit.

#### API

- **Qué:** controladores con `MockMvc`/`RestTestClient`, contratos de error.
- **Herramientas:** Spring Boot Test.

#### Web

- **Qué:** los controladores web devuelven la vista/fragmento correcto con el
  modelo esperado. En el modo oficina, además, pruebas unitarias de la
  traducción de letras y de la correspondencia de la entrada (tolerancias y
  ambigüedades como `axb5` / `Axb5`).
- **Herramientas:** `MockMvc`.

#### Extremo a extremo

- **Qué:** crear partida → jugar → terminar → analizar → revisar.
- **Herramientas:** Playwright (opcional).

### Valores *perft* de referencia mínimos

- **Posición inicial** — d1: 20 · d2: 400 · d3: 8 902 · d4: 197 281 ·
  d5: 4 865 609
- ***Kiwipete*** — d1: 48 · d2: 2 039 · d3: 97 862 · d4: 4 085 603

Añadir las posiciones 3–6 de la wiki de Chess Programming para cubrir
enroques, promociones y capturas al paso en casos extremos. **Un generador
que pasa perft es correcto; no hay discusión.** Es la mejor justificación de
por qué podemos escribir nuestras propias reglas.

---

## 14. Observabilidad

- Actuator: `health` (con *liveness*/*readiness*), `info` (versión de la app,
  de Stockfish y del clasificador), `metrics`, `prometheus`.
- Métricas Micrometer: `kopi.bot.move.duration{profile}`,
  `kopi.analysis.duration`, `kopi.analysis.queue.size`,
  `kopi.engine.restarts`.
- Logs estructurados (JSON) con `gameId` propagado vía `ScopedValue`/MDC.
- Trazas UCI completas solo con un *logger* específico en DEBUG.

---

## 15. Seguridad y robustez

Al ejecutarse en local no hay amenazas entre usuarios, pero el código es
público y la gente lo copiará: los hábitos importan igual.

- Puertos de Postgres **no expuestos** al host salvo en un perfil de
  desarrollo; la app escucha en `localhost` por defecto.
- Ningún secreto en el repo: `.env` ignorado, `.env.example` con valores de
  ejemplo.
- Validación estricta de UCI (`^[a-h][1-8][a-h][1-8][qrbn]?$`) antes de
  llegar al dominio; el dominio vuelve a validar legalidad.
- Tamaño máximo de PGN importado (p. ej. 100 KB) y de partidas por import.
- Nunca enviar al motor nada que venga del cliente salvo una posición
  generada por nuestro dominio (FEN serializado por nosotros, no el del
  usuario).
- Pool de motores: cada proceso tiene un único dueño a la vez; *timeout* →
  `stop`; sin respuesta → matar y recrear el proceso.
- Cierre ordenado: `quit` a todos los procesos en el `@PreDestroy`/`SmartLifecycle`.

---

## 16. Roadmap por entregables

Cada entregable tiene un **objetivo de aprendizaje** además del funcional.

### Entregable 0 — Esqueleto

- **Funcional:** multi-módulo Maven, Spring Boot arrancando, Postgres en
  Compose, Flyway, CI (GitHub Actions) con build + tests, reglas ArchUnit
  vacías pero activas.
- **Aprendizaje:** estructura hexagonal, *wiring* sin anotaciones en el
  núcleo.

### Entregable 1 — Dominio de ajedrez

- **Funcional:** tablero, generador de jugadas, FEN, SAN, fin de partida.
  *Perft* en verde.
- **Aprendizaje:** Java 25 moderno (records, sealed, switch), TDD, modelado
  de dominio.

### Entregable 2 — Jugar

- **Funcional:** agregado `Game`, `CreateGame`, `PlayMove`, `Resign`, API
  REST, persistencia, bot `PAWN`. Interfaz web mínima (Thymeleaf +
  Chessground) y modo oficina (tablero en texto, sin JavaScript).
- **Aprendizaje:** casos de uso, puertos y adaptadores, dos adaptadores de
  entrada sobre el mismo núcleo, control optimista, `ProblemDetail`.

### Entregable 3 — Cinco bots

- **Funcional:** bot `KNIGHT` (alfa-beta), adaptador Stockfish (UCI + pool),
  perfiles `BISHOP`–`QUEEN`, *harness* de calibración.
- **Aprendizaje:** procesos externos, hilos virtuales, búsqueda en árboles,
  Testcontainers con Stockfish.

### Entregable 4 — Análisis

- **Funcional:** cola en BD, *worker*, clasificador Win %, mates perdidos,
  libro de aperturas, SSE, pantalla de revisión.
- **Aprendizaje:** trabajo asíncrono duradero, SSE, dominio puro para reglas
  de negocio no triviales.

### Entregable 5 — Utilidad

- **Funcional:** importar/exportar PGN, historial, resumen y precisión.
- **Aprendizaje:** parser con buenos mensajes de error, paginación.

### Entregable 6 — Estabilización

- **Funcional:** E2E, métricas, imagen Docker final, documentación,
  `NOTICE`, release 1.0.
- **Aprendizaje:** operación, observabilidad, publicación OSS.

**Futuro (v2+):** reloj y controles de tiempo · `BRILLIANT`/`GREAT` con ADR ·
*bitboards* · estadísticas por apertura y por bot · puzzles generados a partir
de los propios errores (¡muy útil y reutiliza todo el análisis!) · modo
«entrenador» con pista opcional.

El plan detallado por fases está en
[`kopi-chess-plan-desarrollo.md`](kopi-chess-plan-desarrollo.md).

---

## 17. ADRs a escribir al empezar

1. **ADR-001** Arquitectura hexagonal con módulos Maven y ArchUnit.
2. **ADR-002** Casos de uso sin dependencia de Spring.
3. **ADR-003** PostgreSQL en lugar de MongoDB.
4. **ADR-004** Reglas de ajedrez propias, verificadas con *perft*.
5. **ADR-005** Clasificación basada en Win % (fórmula y umbrales de Lichess).
6. **ADR-006** Bots propios para niveles bajos.
7. **ADR-007** Cola de análisis en PostgreSQL con `SKIP LOCKED`.
8. **ADR-008** Repetición y 50 jugadas automáticas (simplificación sobre FIDE).
9. **ADR-009** Libro de aperturas local (CC0) en lugar de API externa.
10. **ADR-010** Interfaz web con Thymeleaf + htmx en lugar de SPA.
11. **ADR-011** i18n: el dominio emite códigos, los adaptadores traducen.
12. **ADR-012** Política de *features preview* (solo en `kopi-labs`).
13. **ADR-013** Modo oficina: notación localizada solo en el adaptador web y
    entrada resuelta contra las jugadas legales.
14. **ADR-014** Sin transacción alrededor de los casos de uso (bloqueo
    optimista + guardado atómico en el repositorio).

---

## 18. Riesgos

#### El generador de jugadas lleva más de lo previsto

- **Mitigación:** es el entregable 1 aislado; *perft* da una meta objetiva.
  Plan B: adaptador temporal con chesslib detrás del mismo contrato.

#### El análisis satura la CPU en una máquina modesta

- **Mitigación:** concurrencia 1, `depth` moderada, `nodes` como límite,
  *timeout* por partida.

#### Niveles indistinguibles entre sí

- **Mitigación:** *harness* de calibración antes de cerrar el entregable 3.

#### La interfaz consume el tiempo que querías dedicar al backend

- **Mitigación:** Thymeleaf + htmx, sin SPA; Chessground resuelve lo difícil
  del tablero.

#### htmx se queda corto si la interfaz crece mucho

- **Ejemplo:** análisis interactivo de variantes.
- **Mitigación:** la API REST ya existe: un SPA podría añadirse después como
  un cliente más, sin tocar el núcleo.

#### Cambios de versión de Spring Boot 4.x durante el desarrollo

- **Mitigación:** fijar versión; actualizar de *minor* en *minor* con CI en
  verde.

---

## 19. Datos a verificar al arrancar

Comprobados durante esta revisión, pero conviene confirmarlos contra la
versión exacta que se fije:

- ~~Rango de `UCI_Elo` y opciones disponibles~~: comprobado con Stockfish
  19 (`UCI_Elo` 1320–3190). Detalle en
  [`notas/stockfish-opciones.md`](notas/stockfish-opciones.md).
- Última versión estable de Spring Boot 4.x al arrancar la fase 1.
- Licencia de la librería que se use como oráculo de pruebas.

---

## 20. Resumen

> **Jugar → terminar → analizar → entender → (importar tus propias
> partidas).**

Kopi Chess mantiene la idea central de la propuesta original —análisis
posterior, Stockfish detrás de una interfaz, backend autoritativo— pero
cambia lo que no encajaba con un proyecto **educativo y hexagonal**:
el ajedrez pasa a ser **nuestro dominio** (no una librería), PostgreSQL
sustituye a MongoDB y además sirve de cola duradera, la clasificación usa una
fórmula pública en lugar de umbrales arbitrarios, los niveles bajos los
escribimos nosotros, el libro de aperturas funciona sin red, y la interfaz
web vive en el mismo Spring Boot como un adaptador más.

---

## Fuentes

- Lichess — fórmulas de Win % y precisión: <https://lichess.org/page/accuracy>
- Lichess — umbrales de clasificación (`Advice.scala`):
  <https://github.com/lichess-org/lila/blob/master/modules/tree/src/main/Advice.scala>
- Dataset de aperturas (CC0): <https://github.com/lichess-org/chess-openings>
- Chessground (GPL-3.0): <https://github.com/lichess-org/chessground>
- htmx (extensión SSE): <https://htmx.org/extensions/sse/>
- Opciones UCI de Stockfish (código): <https://github.com/official-stockfish/Stockfish/blob/master/src/engine.cpp>
- Descargas de Stockfish: <https://stockfishchess.org/download/>
- Spring Boot 4 / compatibilidad con Java: <https://www.baeldung.com/java-weekly-622>,
  <https://stevenpg.com/references/spring-version-compatibility/>
- Chess Programming Wiki — *Perft Results*: <https://www.chessprogramming.org/Perft_Results>
