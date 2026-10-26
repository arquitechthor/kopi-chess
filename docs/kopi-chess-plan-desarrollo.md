# Kopi Chess — Plan de desarrollo

**Fecha:** 2026-10-21
**Documento base:** [`kopi-chess-diseno-tecnico.md`](kopi-chess-diseno-tecnico.md)
**Equipo:** una persona (con posibles contribuciones externas)

Este plan convierte el diseño técnico en trabajo ejecutable: fases, tareas en
orden, criterios de aceptación y lo que se aprende en cada paso. Está pensado
para seguirse de arriba abajo; cada tarea de las listas puede convertirse
directamente en un *issue* de GitHub.

**Premisas** (detalle en el diseño, §0):

- El foco de aprendizaje es **Java** (con *features* recientes), **Spring
  Boot** y **Arquitectura Hexagonal**.
- Se ejecuta **en local**; no hay despliegue público.
- El código es **público** (GPL-3.0).
- La interfaz está en **inglés y español** desde la fase 1.
- Los cambios llegan a `main` **solo por *pull request***, que revisa y
  aprueba el responsable del proyecto.

---

## 1. Cómo usar este plan

- **Una fase = un *milestone* de GitHub.** Cada casilla `- [ ]` es un *issue*
  (o una tarea dentro de un *issue* si es pequeña).
- **No se empieza una fase sin cerrar la anterior**, salvo las tareas marcadas
  como *paralelizable*.
- **Cada fase termina con una *release* etiquetada** (`v0.1.0`, `v0.2.0`…),
  aunque nadie más la use. Practicar el ciclo completo es parte del
  aprendizaje.
- Las **estimaciones** suponen unas **8 h por semana**. Son orientativas: lo
  importante es el orden y los criterios de aceptación, no la fecha.

### Resumen de fases

- **Fase 0 — Preparación** · ~1 semana · `v0.0.1`
- **Fase 1 — Esqueleto hexagonal** · ~2 semanas · `v0.1.0`
- **Fase 2 — Dominio de ajedrez** · ~5–6 semanas · `v0.2.0`
- **Fase 3 — Jugar contra un bot** · ~3–4 semanas · `v0.3.0`
- **Fase 4 — Cinco bots** · ~3–4 semanas · `v0.4.0`
- **Fase 5 — Análisis posterior** · ~4–5 semanas · `v0.5.0`
- **Fase 6 — PGN e historial** · ~2–3 semanas · `v0.6.0`
- **Fase 7 — Estabilización y 1.0** · ~2–3 semanas · `v1.0.0`

**Total orientativo:** 22–28 semanas (5–7 meses) a ritmo de 8 h/semana.

La fase 2 es la más larga a propósito: es donde está el valor educativo y
donde un error cuesta más caro después.

---

## 2. Reglas de trabajo (aplican a todas las fases)

### 2.1 Flujo de Git

- Rama principal `main`, siempre en verde.
- Una rama por *issue*: `feat/12-fen-parser`, `fix/31-castling-rights`,
  `docs/adr-004`.
- *Pull request* aunque trabajes solo: obliga a releer el *diff* y deja
  historia. Fusionar con *squash*.
- Mensajes con **Conventional Commits** (`feat:`, `fix:`, `test:`,
  `refactor:`, `docs:`, `build:`, `ci:`). Permite generar el *changelog*
  automáticamente más adelante.

### 2.2 Definición de terminado (DoD)

Una tarea está terminada cuando:

- [ ] El código compila y `mvn verify` pasa en local y en CI.
- [ ] Tiene pruebas del nivel que corresponde (dominio → unitarias; adaptador
      → integración).
- [ ] Las reglas ArchUnit siguen pasando.
- [ ] No hay TODO sin *issue* asociado.
- [ ] Todo texto visible nuevo existe en inglés y en español.
- [ ] Si se tomó una decisión de diseño relevante, existe su ADR.
- [ ] El `README` o la documentación reflejan el cambio si afecta al uso.

### 2.3 ADRs

Formato corto en `docs/adr/NNN-titulo.md`:

```markdown
# ADR-NNN: Título

- **Estado:** propuesto | aceptado | sustituido por ADR-XXX
- **Fecha:** AAAA-MM-DD

## Contexto
Qué problema hay y qué restricciones existen.

## Decisión
Qué se decide, en una o dos frases.

## Consecuencias
Qué se gana, qué se pierde, qué queda pendiente.
```

### 2.4 Estilo de pruebas

- Nombres descriptivos **en inglés**, como todo el código. Ejemplo:
  `blackCannotCastleKingsideThroughAttackedSquare()`.
- Patrón *Given / When / Then* en el cuerpo.
- Posiciones de prueba siempre como **FEN** literal, nunca construidas a mano
  pieza a pieza (a partir de la fase 2).

---

## 3. Fase 0 — Preparación

**Objetivo:** dejar el entorno y el repositorio listos para trabajar sin
fricción.
**Release:** `v0.0.1` (solo documentación y configuración).

### Tareas

- [x] **Instalar herramientas** (todo funciona **sin permisos de
      administrador**):
  - **JDK 25:** descargarlo desde IntelliJ (*File → Project Structure → SDKs
    → + → Download JDK*, versión 25, Temurin) o como `.zip` portable de
    Adoptium descomprimido en una carpeta del usuario. No hace falta cambiar
    el `JAVA_HOME` global si se usa otro JDK para otros proyectos: en la
    terminal basta con fijarlo para la sesión
    (`$env:JAVA_HOME = "<ruta-jdk-25>"`).
  - **Maven:** no se instala; se usa el *wrapper* `mvnw` del repositorio
    (fase 1).
  - **Docker Desktop** con Compose v2.
  - **IDE:** IntelliJ IDEA (Community basta) con soporte de Java 25. VS Code
    sirve para documentos y plantillas.
  - **Stockfish** como `.zip` portable (binario oficial), en `tools/` (está
    en `.gitignore`) o en cualquier carpeta del usuario.
- [x] **Experimentar con UCI a mano** (30 minutos, muy recomendable).
      Resultado: [`notas/stockfish-opciones.md`](notas/stockfish-opciones.md).
  - Abrir Stockfish en la terminal y escribir: `uci`, `isready`,
    `position startpos moves e2e4`, `go depth 15`.
  - Anotar el formato de las líneas `info ... score cp ... pv ...` y
    `bestmove`.
  - Ejecutar `uci` y **copiar el rango real de `UCI_Elo` y `Skill Level`**
    en `docs/notas/stockfish-opciones.md`.
- [x] **Decidir el `groupId` y paquete raíz:** `com.arquitechthor.kopi`
      (ADR-001).
- [x] **Decidir idiomas:** código, *commits*, *issues* y PR en inglés;
      documentos de diseño, plan y ADRs en español; README en ambos
      (`README.md` en inglés, `README.es.md` en español).
- [x] **Configurar GitHub**
  - *Milestones* `F0` … `F7` con las fases de este plan.
  - Etiquetas: `domain`, `application`, `adapter`, `web`, `infra`, `docs`,
    `i18n`, `post-1.0`, `good first issue`, `bug`.
  - Plantillas de *issue* (tarea, bug) y de *pull request* con la DoD.
  - Proteger `main` exigiendo CI verde antes de fusionar: **se aplaza a la
    fase 1**, cuando exista la CI.
- [x] **Actualizar `.gitignore`** para Maven, IntelliJ, VS Code, `.env` y
      `tools/`.
- [x] **Reescribir el README** en inglés y español: qué es Kopi Chess,
      estado, enlace al diseño y a este plan, licencia GPL-3.0.
- [x] **Escribir ADR-001** (arquitectura hexagonal con módulos Maven y
      ArchUnit) y **ADR-002** (casos de uso sin Spring).

### Criterio de aceptación

- El repositorio tiene README, plan, diseño, dos ADRs y *milestones* creados.
- Sabes ejecutar Stockfish a mano y conoces su rango real de opciones.

---

## 4. Fase 1 — Esqueleto hexagonal

**Objetivo:** que exista toda la estructura de módulos, que arranque, que la
CI funcione y que **la arquitectura esté vigilada desde el primer día**.
**Release:** `v0.1.0`.

### Tareas (en orden)

- [x] **POM padre en `backend/`**
  - `packaging pom`, `maven.compiler.release=25`.
  - Importar el BOM de Spring Boot 4.x en `dependencyManagement` (no usar
    `spring-boot-starter-parent` como padre: así el módulo `kopi-domain` no
    hereda nada de Spring).
  - Plugins comunes: `maven-surefire-plugin`, `maven-failsafe-plugin`
    (pruebas de integración `*IT.java`), `jacoco-maven-plugin`.
  - Maven *wrapper* (`mvn wrapper:wrapper`).
- [x] **Crear los módulos vacíos**: `kopi-domain`, `kopi-application`,
      `kopi-adapter-rest`, `kopi-adapter-web`, `kopi-adapter-persistence`,
      `kopi-adapter-stockfish`, `kopi-adapter-bots`, `kopi-adapter-openings`,
      `kopi-bootstrap`, `kopi-architecture-tests`.
  - Dependencias entre módulos según la regla del diseño (§4 del diseño).
  - `kopi-domain`: **ninguna** dependencia de compilación.
- [x] **`kopi-bootstrap`** con `@SpringBootApplication`, `spring-boot-maven-plugin`
      y Actuator. Endpoint `GET /actuator/health` respondiendo `UP`.
- [x] **Docker Compose** con `postgres` (versión fijada, volumen, *healthcheck*).
      `.env.example` con `POSTGRES_PASSWORD`.
- [x] **Flyway** en `kopi-adapter-persistence` con una migración `V1__baseline.sql`
      vacía (o una tabla `schema_info`). El arranque debe aplicarla.
- [x] **Reglas ArchUnit** en `kopi-architecture-tests`:
  - El paquete de dominio no depende de `org.springframework..`,
    `jakarta..`, `java.sql..`, ni de paquetes de aplicación/adaptadores.
  - La aplicación no depende de Spring ni de adaptadores.
  - Ningún adaptador depende de otro adaptador.
  - Truco: añadir temporalmente un `import org.springframework...` en el
    dominio y **comprobar que la build falla**. Luego quitarlo.
- [x] **CI en GitHub Actions** (`.github/workflows/ci.yml`)
  - `actions/setup-java` con Java 25 y caché de Maven.
  - `./mvnw -B verify` (Testcontainers funciona en los *runners* Ubuntu).
  - Publicar el informe de JaCoCo como artefacto.
  - Proteger `main`: exigir que la CI pase antes de fusionar (aplazado desde
    la fase 0). **Hecho:** PR obligatoria, *check* «Build and test»,
    conversaciones resueltas, sin *force push* ni borrado. Sin aprobaciones
    obligatorias, porque GitHub no permite aprobar una PR propia.
- [x] **Dockerfile del backend** *multi-stage* (JDK 25 para compilar, JRE 25
      para ejecutar, usuario no root). Stockfish todavía no.
- [x] **Servicio `app` en Compose** que arranca y conecta a Postgres.
- [x] **Arranque en desarrollo sin Compose:** `TestKopiApplication` (en
      `src/test` de `kopi-bootstrap`) levanta la app con un PostgreSQL de
      Testcontainers; solo necesita Docker en marcha.
- [x] **Página web inicial** en `kopi-adapter-web` (Thymeleaf) en `/`, para
      comprobar que el adaptador web funciona.
- [x] **i18n desde el principio** (diseño §11.4):
  - `messages.properties` (inglés, por defecto) y `messages_es.properties`.
  - `CookieLocaleResolver` + `LocaleChangeInterceptor` (`?lang=`), con
    `Accept-Language` como valor inicial.
  - Selector EN/ES en la cabecera de la página inicial.
  - Prueba que falla si una clave existe en un idioma y no en el otro.
  - Pendiente para la fase 3: detectar texto literal sin `#{...}` en las
    plantillas. **Hecho en la fase 3** (`TemplatesAreTranslatedTest`): lee
    las plantillas con AttoParser (el *parser* de Thymeleaf) y falla si hay
    texto con letras fuera de un elemento que Thymeleaf reemplaza, o un
    `title`/`alt`/`placeholder`/`aria-label` sin su `th:`.
- [x] **Módulo `kopi-labs`** compilado con `--enable-preview`, sin
      dependientes (regla ArchUnit). Un primer ejemplo: *structured
      concurrency* lanzando dos tareas y cancelando ambas si una falla.
- [x] **ADR-003** (PostgreSQL en lugar de MongoDB), **ADR-011** (i18n) y
      **ADR-012** (política de *preview*).

### Criterio de aceptación

- `docker compose up` levanta Postgres + app; `http://localhost:8080` muestra
  la página en inglés o español según el selector, y `/actuator/health`
  responde `UP`.
- La CI pasa en `main`.
- Una violación de la regla de dependencias **rompe la build**.

### Aprendizaje

Maven multi-módulo, BOMs, separación real de capas, CI básica.

### Trampas habituales

- Usar `spring-boot-starter-parent` como padre de todo y acabar con Spring en
  el *classpath* del dominio.
- Poner `@SpringBootApplication` en un paquete que escanea los módulos de
  dominio y aplicación. El *wiring* se hace con `@Configuration` explícitas.

---

## 5. Fase 2 — Dominio de ajedrez

**Objetivo:** implementar las reglas completas del ajedrez en Java puro y
**demostrar que son correctas** con *perft*.
**Release:** `v0.2.0`.
**Módulo:** solo `kopi-domain`. Nada de Spring, nada de base de datos.

Esta fase se hace con **TDD estricto**: primero la prueba con una FEN, luego el
código.

*Features* de Java que esta fase debe mostrar: records y *sealed interfaces*
para todos los tipos de valor, `switch` exhaustivo con patrones de registro,
variables sin nombre `_`, comentarios de documentación Markdown `///` en
todas las clases públicas del dominio y, para depurar, un `Perft.java`
ejecutable como archivo fuente compacto (`java Perft.java "<fen>" 4`).

### 5.1 Tipos básicos

- [x] `Color` (enum con `opposite()`).
- [x] `PieceType` (enum) y `Piece` (record `color + type`; justificado en su
      Javadoc y en ADR-004).
- [x] `Square` (record con índice 0–63; `parse("e4")`, `of(file, rank)`,
      `toString()`).
- [x] `Move` (record `from`, `to`, `kind`) con `MoveKind` como *sealed
      interface* de seis records (`Quiet`, `Capture`, `DoublePawnPush`,
      `EnPassant`, `Castling`, `Promotion`) y `toUci()`. En lugar de
      `parseUci()`, el texto UCI se buscará entre las jugadas legales de la
      posición (ADR-004).

### 5.2 Tablero y posición

- [x] **Decidir representación** (0x88 recomendado) y escribir **ADR-004**
      (reglas propias verificadas con *perft*).
- [x] `Board`: colocar/quitar/consultar pieza por casilla. Inmutable o con
      copia barata; documentar la decisión.
- [x] `CastlingRights` (record de 4 booleanos o máscara de bits).
- [x] `Position` (inmutable): tablero, turno, enroques, casilla al paso,
      `halfmoveClock`, `fullmoveNumber`.
- [x] **FEN**: `Fen.parse(String) → Position` y `Fen.format(Position)`.
  - Pruebas de ida y vuelta con 20+ FEN variadas.
  - Errores claros ante FEN inválidas (`InvalidFenException` con el motivo).

### 5.3 Generación de jugadas

Orden recomendado (cada punto con sus pruebas antes de pasar al siguiente):

- [x] Caballo y rey (sin enroque) — las más simples: tablas de desplazamientos.
- [x] Piezas deslizantes: alfil, torre, dama (rayos hasta chocar).
- [x] Peones: avance simple, doble desde la fila inicial, capturas.
- [x] Promoción (4 jugadas por avance/captura a la última fila).
- [x] Captura al paso.
- [x] `isSquareAttacked(square, byColor)`.
- [x] Enroques: derechos, casillas vacías, el rey no está en jaque, no
      atraviesa ni llega a casilla atacada.
- [x] `Position.apply(Move) → Position`: mover, capturar, actualizar
      enroques (también cuando se captura una torre en su casilla inicial),
      casilla al paso, relojes, turno.
- [x] **Jugadas legales** = pseudo-legales que no dejan al propio rey en
      jaque.

### 5.4 Perft (el hito de la fase)

- [x] `Perft.count(position, depth)` en código de prueba (o en el dominio si
      se quiere exponer). **Hecho:** en el dominio, con *bulk counting* en la
      última profundidad.
- [x] `Perft.divide(position, depth)`: conteo por jugada raíz. **Imprescindible
      para depurar**: se compara con `go perft N` de Stockfish y se baja por la
      rama que difiere. **Hecho:** también desde consola con el archivo
      fuente compacto `backend/kopi-domain/dev/Perft.java`, con la misma
      salida que Stockfish.
- [x] Prueba parametrizada con las 6 posiciones de la Chess Programming Wiki:
  - Inicial hasta d5 (4 865 609).
  - *Kiwipete* hasta d4 (4 085 603).
  - Posiciones 3–6 hasta la profundidad que dure < 10 s (d6, d5, d4, d4).
- [x] Marcar las profundidades altas como prueba lenta (`@Tag("slow")`) y
      ejecutarlas en CI pero no en cada guardado local. **Hecho:** se excluyen
      por defecto; el perfil `-Pslow-tests` (que usa la CI) las incluye.

### 5.5 Notación y fin de partida

- [x] **SAN**: `San.format(position, move)` con desambiguación (`Nbd2`,
      `R1e2`), capturas, promoción (`e8=Q`), enroque (`O-O`), `+` y `#`.
- [x] `San.parse(position, "Nf3") → Move` (necesario para importar PGN en la
      fase 6, pero se prueba aquí).
- [x] `Rules`: jaque, jaque mate, ahogado, material insuficiente (K-K, K+B-K,
      K+N-K, K+B-K+B mismo color), regla de 50 jugadas.
- [x] **Hash Zobrist** en `Position` (tabla de aleatorios con semilla fija).
- [x] Triple repetición (necesita el historial: se resuelve en el agregado
      `Game`, fase 3, pero el hash se prepara aquí). **Hash listo:**
      `Position.zobristHash()`; el conteo se hace en la fase 3 (§6.1).
- [x] **ADR-008** (repetición y 50 jugadas automáticas).

### 5.6 Prueba diferencial (opcional pero muy valiosa)

- [x] Dependencia **solo de test** a una librería de referencia (p. ej.
      chesslib; verificar licencia). **Hecho con Stockfish como oráculo**
      en lugar de una librería: chesslib solo se publica en JitPack (no en
      Maven Central) y no hay en Central una alternativa fiable. Stockfish
      es GPL-3.0 como Kopi Chess, ya forma parte del proyecto (fase 4) y no
      añade ninguna dependencia de Maven: se habla con él por UCI desde
      `StockfishOracle`, en código de prueba. La CI descarga la *release*
      oficial `sf_19` (versión fijada y SHA-256 verificado); en local se
      busca en `tools/stockfish/` o en `KOPI_STOCKFISH`, y si no está, la
      prueba se omite.
- [x] Generar miles de posiciones jugando partidas aleatorias con semilla y
      comparar el conjunto de jugadas legales y el FEN resultante. **Hecho:**
      120 partidas desde las 6 posiciones de *perft*; en cada posición se
      comparan el FEN, el jaque y el conjunto exacto de jugadas legales
      (`@Tag("slow")`, ~15 s). Comprobado que detecta la trampa del enroque
      al capturar una torre.
- [x] Con jqwik: propiedad «para toda posición alcanzable, `Fen.parse(Fen.format(p)) == p`».
      **Hecho sin jqwik:** jqwik 1.x apunta a la plataforma de JUnit 5 y
      este proyecto usa JUnit 6 (Spring Boot 4). La propiedad, junto con la
      ida y vuelta de SAN, se comprueba con JUnit sobre partidas aleatorias
      con semilla.

### Criterio de aceptación

- *Perft* correcto en las 6 posiciones de referencia.
- Ida y vuelta FEN y SAN en verde.
- `kopi-domain` sigue sin dependencias de compilación.
- Cobertura de líneas del dominio > 90 % (orientativo, no un fin en sí).

### Aprendizaje

Records, *sealed interfaces*, *switch* con patrones, inmutabilidad, TDD,
depuración guiada por pruebas (*perft divide*).

### Trampas habituales

- Olvidar quitar el derecho de enroque cuando **se captura** una torre en su
  casilla inicial.
- Captura al paso que deja al propio rey en jaque por la horizontal
  (posición 3 de *perft* lo detecta).
- Promoción con captura no generada.
- Optimizar antes de tener *perft* en verde. Primero correcto, luego rápido.

---

## 6. Fase 3 — Jugar contra un bot

**Objetivo:** primera experiencia jugable de extremo a extremo: elegir color,
jugar contra el bot `PAWN`, terminar la partida, verla guardada.
**Release:** `v0.3.0`.

### 6.1 Dominio

- [x] Agregado **`Game`**: id, origen, estado, color humano, perfil, posición
      inicial, lista de jugadas aplicadas, historial de hashes (repetición),
      resultado, motivo, versión.
  - `Game.start(...)`, `game.play(Move, Color)`, `game.resign(Color)`.
  - Invariantes del diseño (§5.2): solo en `IN_PROGRESS`, solo el color al que
    le toca, solo jugadas legales, fin de partida evaluado tras cada jugada.
  - Excepciones de dominio tipadas: `GameNotInProgress`, `NotYourTurn`,
    `IllegalMove`.
  - **Hecho** en el paquete `domain.game`. `Game` es inmutable como
    `Position`: `play` y `resign` devuelven la partida nueva con la versión
    +1. Reciben también el instante (`Instant`) del cambio, que guardan las
    tablas (`created_at`, `finished_at`, `played_at`); el reloj lo pone la
    aplicación. Las excepciones son una familia *sealed*
    (`GameRuleException` → `GameNotInProgressException`,
    `NotYourTurnException`, `IllegalMoveException`). Orden de los finales:
    mate, ahogado, material insuficiente, triple repetición, 50 jugadas
    (ADR-008). Pendiente para la persistencia (§6.3): reconstruir una
    partida guardada (por ejemplo, rejugando sus jugadas).
- [x] `GameOutcome` como *sealed interface* (`Checkmate`, `Stalemate`,
      `InsufficientMaterial`, `ThreefoldRepetition`, `FiftyMoveRule`,
      `Resignation`).
- [x] `BotProfile` (enum `PAWN` … `QUEEN`).

### 6.2 Aplicación

- [x] Puertos de salida: `GameRepository`, `BotPlayer`, `Clock`,
      `IdGenerator`. **Hecho** en `application.port`. El reloj es
      `java.time.Clock` (la abstracción estándar del JDK; `Clock.fixed` en
      pruebas), no una interfaz propia. `GameRepository` tiene `insert` y
      `update(game, loadedVersion)`: una jugada humana más la del bot suben
      la versión en 2, así que el repositorio necesita la versión cargada.
      Excepciones de los puertos: `ConcurrentGameUpdateException` y
      `BotUnavailableException`.
- [x] Casos de uso: `CreateGame`, `PlayMove`, `Resign`, `GetGame`, `ListGames`.
  - `PlayMove`: cargar → comprobar versión → aplicar jugada humana → si sigue,
    pedir jugada al bot → aplicarla → guardar → devolver vista.
  - **Hecho** en `application.game`: una interfaz por caso de uso (el
    puerto de entrada, que además permite envolverlo en una transacción
    desde `kopi-bootstrap`) y una clase que la implementa. Se guarda una
    sola vez al final: cualquier fallo deja la partida como estaba.
    `PlayMoveCommand` rechaza el texto que no parece UCI
    (`InvalidUciException`, el 400 de la API); la legalidad la decide
    `Game.play(String uci, …)`, nuevo en el dominio.
- [x] Vista de lectura `GameView` (record) con FEN, turno, jugadas en SAN,
      jugadas legales en UCI, estado, resultado, versión. **Hecho**, y las
      jugadas legales llevan también su SAN (para el modo oficina).
      `GameSummary` y `GamePage` para el listado.
- [x] **Pruebas con dobles**: repositorio en memoria, bot que devuelve una
      jugada fija, reloj fijo. Sin Spring. **Hecho:** 21 pruebas, incluida
      una carrera real (otra petición guarda mientras el bot piensa).

### 6.3 Adaptadores

- [x] **`kopi-adapter-bots`**: bot `PAWN` (aleatorio ponderado con semilla:
      peso extra a capturas y jaques). **Hecho:** peso 1, +3 si captura, +2
      si da jaque, +3 si corona dama. El azar llega como
      `Supplier<RandomGenerator>`: `ThreadLocalRandom` en producción (el
      bot atiende peticiones de varios hilos), con semilla en pruebas.
      `KopiBots` implementa `BotPlayer`; hasta la fase 4 los demás perfiles
      responden `BotUnavailableException` (503).
- [x] **`kopi-adapter-persistence`**:
  - Migración `V2__games.sql` con `game` y `game_move` (diseño §6.1).
  - Implementación de `GameRepository` con `JdbcClient` o Spring Data JDBC.
  - Control optimista: `UPDATE ... WHERE id = ? AND version = ?`; si afecta
    0 filas → `ConcurrentModification`.
  - Prueba de integración con **Testcontainers** (`PostgreSQLContainer`).
  - **Hecho** con `JdbcClient` y SQL a mano. Una partida guardada se
    reconstruye **rejugando** sus jugadas con el propio agregado (`start` +
    `play`, y `resign` si terminó por abandono): el dominio no necesita
    una puerta trasera para la persistencia, y una fila incoherente
    (versión o final que el rejuego no reproduce) falla en lugar de cargar
    una partida corrupta. El mapeo filas ↔ `Game` (`GameRows`) es Java puro
    con pruebas unitarias; las jugadas se añaden (nunca se reescriben) y la
    partida y sus jugadas se guardan en una sola transacción. Los
    instantes se truncan a microsegundos, la precisión de PostgreSQL.
- [x] **`kopi-adapter-rest`**:
  - `POST /games`, `GET /games/{id}`, `POST /games/{id}/moves`,
    `POST /games/{id}/resign`, `GET /games`, `GET /bots`.
  - `@RestControllerAdvice` que traduce excepciones de dominio a
    `ProblemDetail` (400, 409, 412, 422) con `title`/`detail` traducidos
    según `Accept-Language` y una propiedad `code` estable.
  - Versión en `If-Match`.
  - Pruebas con `MockMvc`.
  - **Hecho** bajo `/api/v1`. JSON con DTOs propios (`ApiModel`), no las
    vistas de la aplicación: es el contrato público. Las respuestas llevan
    `ETag: "<versión>"`; jugar y rendirse exigen `If-Match` y, sin él,
    responden `428 Precondition Required` (RFC 6585). Además de los
    códigos previstos: `404` (partida inexistente) y `503` (bot no
    disponible). El idioma sale solo de `Accept-Language` (`ApiLocale`),
    sin depender del adaptador web. Mensajes en `i18n/rest/messages*`, con
    claves `rest.*`. Las pruebas usan los casos de uso reales sobre un
    repositorio en memoria (sin *mocks*). Falta importarlo en
    `kopi-bootstrap`, junto con el cableado de los casos de uso.
- [x] **`kopi-adapter-web`**:
  - Página de inicio: elegir bot y color (formulario Thymeleaf).
  - Página de partida: tablero Chessground, lista de jugadas, estado.
  - Identidad visual de la familia de webs (diseño §11.6), con el tablero
    de Chessground en la paleta de Kopi. La base (menú, pie, *tokens*) ya
    está desde la fase 2.
  - `board.js`: leer `data-fen` y `data-legal-moves`, enviar la jugada,
    aplicar respuesta (FEN + última jugada del bot).
  - Fragmentos htmx para la lista de jugadas y el resultado.
  - Todos los textos con `#{...}` en inglés y español (categorías, motivos de
    fin y errores traducidos desde los enums del dominio).
  - Jugadas mostradas en notación de figuras (`♘f3`).
  - **Hecho**, con dos cambios. **Sin htmx por ahora:** la jugada es un
    formulario clásico con *Post/Redirect/Get* (`board.js` solo rellena y
    envía el formulario; la respuesta es la página entera con la jugada del
    bot). Es más simple, funciona igual en el modo oficina y recargar nunca
    repite una jugada; htmx queda como mejora posterior. **Chessground
    9.2.1** copiado en `static/vendor/` (descargado de npm con su SHA-512
    verificado, GPL-3.0-or-later), sin CDN. Tablero en la paleta de Kopi
    (casillas por CSS, resaltados en cian), coronación con un `<dialog>`,
    partidas recientes en la portada y página 404 propia. Hasta la fase 4,
    los bots que no juegan aparecen como «Próximamente».
  - **Después (fase 4):** la jugada ya no recarga la página. `board.js` la
    envía con `fetch` a `…/live-moves`, que devuelve solo el fragmento
    `live` de la plantilla (estado, jugadas, `#board-state`); Chessground
    anima la respuesta del bot desde su casilla de origen. Sigue sin htmx,
    y el formulario clásico queda como respaldo sin JavaScript.
- [x] **`kopi-bootstrap`**: `@Configuration` que crea los casos de uso con sus
      adaptadores y los decora con transacciones. **Hecho**
      (`UseCaseConfiguration`) **sin** decorarlos con transacciones: el bot
      puede pensar segundos entre la carga y el guardado, y el bloqueo
      optimista más el guardado atómico del repositorio bastan (ADR-014).
      Reloj UTC en microsegundos (la precisión de PostgreSQL), UUID
      aleatorios, `KopiBots`. Importa ya el adaptador REST: la API funciona
      de extremo a extremo (`GameApiIT`).

### 6.4 Modo oficina (diseño §11.5)

Todo en `kopi-adapter-web`; el dominio y la aplicación no cambian. Necesita
`San.format` (§5.5) y la vista `GameView`.

- [x] **Letras de pieza localizadas**: claves `piece.letter.*` en los dos
      idiomas (`T A C D R P` / `R B N Q K P`) y un formateador que convierte
      la SAN del dominio a la del idioma (`Nf3` → `Cf3`, `e8=Q` → `e8=D`).
- [x] **Correspondencia de la entrada** (`OfficeMoveInput` o similar): dada
      la entrada y las jugadas legales formateadas, devuelve *una jugada*,
      *ninguna* o *varias candidatas*. Normaliza mayúsculas, `x`, `+`/`#`,
      `0`/`O` y espacios; acepta también UCI.
  - Pruebas unitarias: `E4` → `e4`; `cf3` → `Cf3`; `0-0` → `O-O`;
    `axb5` con peón y alfil pudiendo capturar en `b5` → dos candidatas;
    `Ce5` sin caballo que llegue → ninguna; `g1f3` → `Cf3`.
- [x] **Plantilla `game-office.html`**: cabecera, menú y pie de una empresa
      ficticia; tabla del tablero con cabeceras `a`–`h` / `1`–`8` orientada
      según el color humano, blancas en mayúscula y negras en minúscula,
      leyenda, última jugada en negrita; a la derecha, jugadas legales
      agrupadas por pieza, historial en dos columnas y estado.
- [x] **Formulario de jugada** sin JavaScript (*Post/Redirect/Get*): el texto
      se resuelve contra las jugadas legales y se pasa a `PlayMove` como
      jugada concreta. Mensajes traducidos para «ilegal», «¿quisiste decir…?»
      (con las candidatas) y «Has jugado `Cf3`»; el texto erróneo se conserva
      en el campo.
- [x] **Activación**: `?view=office` y *cookie* de preferencia; enlace
      discreto de ida y vuelta. Título de pestaña neutro.
- [x] Pruebas `MockMvc`: la vista oficina se elige con el parámetro y con la
      *cookie*; la tabla tiene 64 celdas y las cabeceras correctas para
      ambos colores; una jugada aceptada redirige y una ambigua muestra las
      candidatas.
- [x] **ADR-013** (notación localizada solo en el adaptador; entrada resuelta
      contra las jugadas legales).
- **Hecho.** Dos ajustes sobre el diseño: si el texto coincide
  **exactamente** con una jugada (mayúsculas incluidas) se juega sin
  preguntar (`Axb5` es el alfil); solo se pregunta cuando, ignorando
  mayúsculas, encaja con varias. Y el portal **no lleva nombre de
  empresa** («Portal del empleado», «Departamento de Operaciones»), para no
  imitar a ninguna real. La vista se elige con `?view=office` /
  `?view=board` y se recuerda en la *cookie* `kopi-view`.

### Criterio de aceptación

- Desde el navegador se juega una partida completa contra `PAWN` hasta mate,
  ahogado o rendición.
- La misma partida se puede jugar entera en **modo oficina**, en español y en
  inglés, escribiendo las jugadas, con JavaScript desactivado.
- Reiniciar el contenedor `app` no pierde la partida.
- Dos peticiones simultáneas con la misma versión: una pasa y la otra recibe
  `412`.
- Una jugada ilegal enviada a mano por `curl` devuelve `422` y no altera la
  partida.

### Aprendizaje

Agregados y casos de uso, dos adaptadores de entrada sobre el mismo núcleo,
mapeo dominio ↔ filas, control optimista, `ProblemDetail`, Thymeleaf + htmx,
y una segunda presentación (modo oficina) sobre los mismos casos de uso.

### Trampas habituales

- Poner lógica de negocio en el controlador («si es el turno del bot…»). Todo
  eso vive en el caso de uso o en el agregado.
- Anotar el agregado `Game` con anotaciones de persistencia. El adaptador
  tiene sus propias clases de fila.
- Meter las letras en español en el dominio o escribir un segundo parser de
  SAN para el modo oficina. Basta con formatear las jugadas legales y
  comparar la entrada contra ellas.
- Hacer la entrada insensible a mayúsculas y olvidar que `a`/`A`, `c`/`C`,
  `d`/`D` (y `b`/`B` en inglés) pueden ser columna o pieza.

---

## 7. Fase 4 — Cinco bots

**Objetivo:** cinco niveles diferenciables y la integración robusta con
Stockfish.
**Release:** `v0.4.0`.

### 7.1 Bot `KNIGHT` (propio)

- [x] Función de evaluación: material + tablas de casillas (*piece-square
      tables*) sencillas.
- [x] Búsqueda minimax con poda alfa-beta a profundidad 2 (configurable).
- [x] Ordenación básica de jugadas (capturas primero) para que la poda rinda.
- [x] Pruebas: en posiciones con una pieza colgada, la captura; en mate en 1,
      lo encuentra.
- **Hecho:** `Evaluation` (material + bonificaciones por casilla escritas
  como fórmulas propias) y `KnightBot` (negamax + alfa-beta, MVV-LVA,
  azar entre jugadas a 10 cp de la mejor). Comprobación rápida: 19
  victorias y 1 tablas en 20 partidas contra `PAWN`, ~4 ms por jugada.

### 7.2 Adaptador Stockfish

- [x] **`UciProcess`**: arranca el binario con `ProcessBuilder`, hilo virtual
      que lee `stdout` línea a línea hacia una cola.
- [x] **Protocolo**: `uci` → esperar `uciok`; `setoption`; `isready` →
      `readyok`; `ucinewgame`; `position fen ... moves ...`; `go ...`;
      esperar `bestmove`. Siempre con *timeout*.
- [x] **Parser de `info`**: `depth`, `multipv`, `score cp N` / `score mate N`,
      `pv ...`. Pruebas con líneas reales copiadas en la fase 0.
- [x] **Normalización de la puntuación** a perspectiva de blancas (Stockfish
      la da desde el lado que mueve).
- [x] **Pool** (`EnginePool`) de tamaño configurable: préstamo exclusivo,
      devolución, reinicio de procesos muertos, `quit` ordenado al cerrar.
- [x] Implementar `BotPlayer` para `BISHOP`, `ROOK`, `QUEEN` con la
      configuración de cada perfil en `application.yml`.
- [ ] Implementar `ChessEngine.analyse(...)` (se usará en la fase 5).
      **Aplazado a la fase 5**, donde se define el puerto y se usa; el
      *parser* (`InfoLine`, con la puntuación normalizada) ya está listo.
- [x] `HealthIndicator` que hace `isready` contra el pool. **Hecho:** componente
      `stockfish` de `/actuator/health` (UP con el nombre del motor, DOWN con
      el motivo); fuera del grupo de *readiness*, porque sin Stockfish siguen
      jugando `PAWN` y `KNIGHT`.
- [x] **Dockerfile**: añadir Stockfish con versión fijada y **checksum**
      verificado (o compilarlo desde la etiqueta oficial). Elegir un binario
      compatible con la CPU destino (las variantes `avx2`/`bmi2` no funcionan
      en todas las máquinas; la genérica `x86-64` sí). **Hecho:** etapa
      `stockfish` con la *release* `sf_19` *universal* (`x86-64` o `arm64`
      según `TARGETARCH`), SHA-256 verificado y prueba de humo en la imagen
      final; `KOPI_STOCKFISH` apunta al binario. La CI construye la imagen.
- [x] Prueba de integración con Stockfish real (Testcontainers con la imagen
      de la app, o el binario descargado en CI). **Hecho** con el binario
      descargado en CI: `StockfishBotsTest` y una partida contra `QUEEN` en
      `GameApiIT`.
- [x] **ADR-006** (bots propios para niveles bajos).
- **Hecho (primera parte):** `UciConnection` / `UciProcess` (hilo virtual
  lector, *timeouts*), `UciEngine` (opciones validadas contra el rango que
  anuncia el motor, porque Stockfish ajusta en silencio los valores fuera
  de rango; `stop` si se agota el tiempo), `InfoLine`, `EnginePool`
  (perezoso, descarta motores rotos) y `StockfishBots`, que **reintenta
  una vez** con un motor nuevo si el primero falla: matar el proceso entre
  dos jugadas no se nota. Perfiles en `kopi.stockfish.profiles`.
  `kopi-bootstrap` reparte el puerto `BotPlayer`: bots propios para `PAWN`
  y `KNIGHT`, Stockfish para el resto.

### 7.3 Calibración

- [x] *Harness* (`kopi-bootstrap` en `src/test`, o un módulo `kopi-tools`):
      enfrenta perfil N contra N+1, M partidas desde aperturas variadas,
      alternando colores.
- [x] Ajustar parámetros hasta que cada nivel gane claramente al anterior
      (p. ej. > 70 % de puntos).
- [x] Guardar los resultados en `docs/notas/calibracion.md` con la versión de
      Stockfish y los parámetros usados.
- **Hecho:** `CalibrationTest` (`-Pcalibration`, fuera de la CI), 16
  partidas por pareja desde 8 aperturas. La primera ronda falló
  (`ROOK` 47 % contra `BISHOP`: *Skill Level* y `UCI_Elo` no son
  comparables); con los tres perfiles en `UCI_Elo` (1320 / 2000 / sin
  límite) todos los niveles pasan: 100 %, 78 %, 100 %, 100 %.

### Criterio de aceptación

- Los cinco perfiles son seleccionables en la web y juegan partidas completas.
- Cada nivel supera al anterior en la calibración.
- Matar el proceso de Stockfish a mano durante una partida: el siguiente
  movimiento devuelve `503` controlado o se recupera solo, y la partida no se
  corrompe.
- `QUEEN` responde en ≤ `movetime` + margen.

### Aprendizaje

Procesos externos y protocolos de texto, hilos virtuales, *timeouts*, pools de
recursos, búsqueda en árboles, empaquetado de binarios nativos.

---

## 8. Fase 5 — Análisis posterior

**Objetivo:** al terminar una partida, analizarla, clasificar cada jugada y
mostrar la revisión.
**Release:** `v0.5.0`.

### 8.1 Dominio

- [x] `Evaluation` (*sealed*: `Centipawns`, `MateIn`, `Checkmate`) con
      `winChanceFor(color)`.
- [x] `WinChance` y fórmulas de Win % y precisión (diseño §8.3–8.4), con
      pruebas contra valores calculados a mano.
- [x] `MoveClassifier`: entradas = evaluación antes, después, mejor jugada,
      jugada jugada, si estaba en libro, número de jugadas legales. Salida =
      categoría + datos que la justifican.
  - Pruebas por categoría con posiciones reales: un mate en 2 perdido, una
    dama colgada, una jugada única, una inexactitud en posición ganada.
- [x] `AnalysisSummary`: conteos por categoría y precisión por color.
  - Recorrer pares de posiciones consecutivas y las ventanas de volatilidad
    con **Stream Gatherers** (`Gatherers.windowSliding`).
- [x] **ADR-005** (clasificación basada en Win %).

### 8.2 Libro de aperturas

- [x] Descargar los TSV de `lichess-org/chess-openings`, fijar el *commit*
      de origen. (Los originales, no `dist/`: Kopi calcula la posición.)
- [x] Cargador al arrancar que reproduce cada línea y guarda las posiciones
      en memoria (ADR-009: sin tabla `opening`).
- [x] Adaptador `OpeningBook.lookup(position)` por posición
      (`zobristHash`, equivalente al EPD).
- [x] Atribución en `NOTICE`.
- [x] **ADR-009** (libro local CC0).

### 8.3 Cola y *worker*

- [x] Migración `V3__analysis.sql`: `analysis`, `move_analysis`,
      `analysis_job` (el libro de aperturas va en memoria, ADR-009).
- [x] `AnalysisJobQueue` con `FOR UPDATE SKIP LOCKED` (diseño §6.2).
- [x] *Worker*: bucle en hilo virtual que reclama trabajos; concurrencia
      configurable (1 por defecto). (`AnalysisWorker`, `kopi.analysis.*`.)
- [x] Recuperación al arrancar: trabajos `RUNNING` antiguos → `QUEUED` o
      `FAILED` tras N intentos.
- [x] Caso de uso `RunAnalysisJob`: N+1 posiciones, una búsqueda por posición,
      clasificar, guardar, publicar progreso (§8.4).
- [x] Caso de uso `RequestAnalysis` (idempotente) y encolado automático al
      terminar una partida.
- [x] **ADR-007** (cola en PostgreSQL).

### 8.4 Publicación de progreso

- [x] Puerto `AnalysisEvents` y adaptador SSE (`SseEmitter`) con registro de
      suscriptores por `gameId`.
- [x] Endpoints REST: `POST/GET /games/{id}/analysis` y
      `/analysis/events`.

### 8.5 Pantalla de revisión

- [x] Barra de progreso en vivo. (Con `EventSource` nativo en `review.js`, no
      con la extensión de htmx: htmx no está en el proyecto y no compensa
      añadirlo para un solo flujo de eventos.)
- [x] Lista de jugadas con icono + texto de categoría.
- [x] Tablero navegable (← →), flecha con la mejor jugada, línea principal en
      SAN (las 6 primeras jugadas; la de un mate perdido, entera).
- [x] Gráfica de evaluación en SVG generado en el servidor.
- [x] Resumen: precisión por color, conteos, mates perdidos, nombre de la
      apertura.

### Criterio de aceptación

- Al terminar una partida, el análisis arranca solo y se ve el progreso en
  vivo.
- Reiniciar la app con un análisis a medias: se retoma o termina en `FAILED`
  con motivo, nunca se queda «colgado».
- Una partida de prueba con un mate en 2 perdido conocido lo marca como
  `MISSED_MATE` con la línea correcta.
- Los parámetros del análisis y la versión del motor quedan guardados.

### Aprendizaje

Trabajo asíncrono duradero, SSE, lógica de negocio no trivial en un dominio
puro y probado, presentación de datos en el servidor.

---

## 9. Fase 6 — PGN e historial

**Objetivo:** que el proyecto sea útil más allá de los bots: analizar
cualquier partida.
**Release:** `v0.6.0`.

### Tareas

- [x] **Parser PGN** en el dominio: etiquetas, jugadas en SAN, comentarios
      `{...}`, variantes `(...)` (ignorarlas), NAG `$1`, resultado.
      (`Pgn.parse`, `PgnGame`, `InvalidPgnException` con código, línea,
      número de jugada y bando.)
  - Mensajes de error con número de jugada y texto: «Jugada 23… Nxe5: ilegal
    en esta posición».
  - Pruebas con PGN reales exportados de Lichess y Chess.com (guardar en
    `src/test/resources/pgn/`).
- [x] **Exportador PGN** (las partidas propias con etiquetas estándar).
      (`Pgn.format` y el caso de uso `ExportPgn`: Seven Tag Roster,
      `Termination`, `PlyCount`; líneas de 80 columnas.)
- [x] Caso de uso `ImportPgn`: crea una partida `IMPORTED` terminada y encola
      su análisis. (Final `DECLARED` y sin tablas automáticas a mitad de
      partida: ADR-015.)
- [x] Límites: tamaño máximo de PGN (100 KB), una partida por importación en
      el MVP.
- [ ] Web: página «Importar PGN» (textarea + subir archivo).
- [x] REST: `POST /games/import` (texto plano o JSON; errores con línea,
      jugada y bando), `GET /games/{id}/pgn` (con enlace «Descargar PGN» en
      la partida y en la revisión).
- [ ] **Historial**: listado paginado con filtros por origen y resultado.
- [ ] **Balance contra cada bot** (petición del 2026-10-26, después del
      historial): en la selección de bot de «Nueva partida», cuántas partidas
      llevas ganadas, perdidas, en tablas y sin terminar contra cada uno.
  - Desde el punto de vista de la persona. Abandonar es perder; «sin
    terminar» son las partidas `IN_PROGRESS`, que se pueden retomar.
  - Las partidas importadas no cuentan (no tienen bot).
  - Una consulta agregada (`group by bot_profile`), sin cargar partidas.

### Criterio de aceptación

- Pegar el PGN de una partida real de Lichess produce una revisión completa.
- Un PGN con una jugada ilegal da un error que indica exactamente dónde.
- Exportar e importar una partida propia produce la misma secuencia de
  jugadas.

### Aprendizaje

Escribir un *parser* tolerante con buenos errores, paginación, ciclo
importar → procesar en segundo plano → mostrar.

---

## 10. Fase 7 — Estabilización y 1.0

**Objetivo:** que cualquiera pueda clonar, levantar y usar Kopi Chess sin
ayuda.
**Release:** `v1.0.0`.

### Tareas

- [ ] **Pruebas E2E** con Playwright (opcional): crear partida → jugar →
      rendirse → ver análisis.
- [ ] **Observabilidad**: métricas Micrometer del diseño (§14), endpoint
      Prometheus; *dashboard* Grafana opcional en un perfil de Compose.
- [ ] **Logs estructurados** con `gameId` propagado.
- [ ] **Higiene de seguridad**: revisión de la lista del diseño (§15):
      puertos solo en `127.0.0.1`, Postgres sin exponer, ningún secreto en el
      repo (activar *secret scanning* de GitHub).
- [ ] **Arranque rápido**: generar la caché AOT de Java 25 en la imagen y
      medir el tiempo de arranque antes y después.
- [ ] **Rendimiento**: medir tiempo de análisis de una partida de 40 jugadas
      en una máquina modesta; ajustar `depth`/`nodes` por defecto.
- [ ] **Imagen Docker final**: tamaño razonable, usuario no root, etiquetas
      OCI, publicada en GitHub Container Registry desde CI al etiquetar (así
      quien quiera usarlo en local no necesita compilar).
- [ ] **Documentación**:
  - README en inglés (público general) con enlace a una versión en español:
    captura, `docker compose up` en tres líneas, requisitos.
  - `CONTRIBUTING.md` con el flujo de la sección 2 de este plan.
  - `docs/arquitectura.md`: guía de lectura del código por anillos (para
    quien llega a aprender).
  - `NOTICE` con Stockfish, Chessground, htmx y el dataset de aperturas.
- [ ] **Changelog** generado desde los commits convencionales.
- [ ] Marcar 5–10 *issues* como `good first issue` para posibles
      contribuciones.

### Criterio de aceptación

- Una persona ajena al proyecto clona el repo y, con solo el README, juega y
  analiza una partida en menos de 10 minutos.
- CI publica la imagen al crear la etiqueta `v1.0.0`.

---

## 11. Después de la 1.0

Ideas ordenadas por relación valor/esfuerzo; cada una merece su propio ADR
antes de empezar:

1. **Puzzles desde tus propios errores:** cada `BLUNDER`/`MISSED_MATE` se
   convierte en un ejercicio «encuentra la mejor jugada». Reutiliza todo el
   análisis.
2. **Reloj y controles de tiempo** como regla del dominio.
3. **Bitboards** detrás de la interfaz `Board`, con *perft* como red de
   seguridad (y un *benchmark* antes/después).
4. **Etiquetas `BRILLIANT` / `GREAT`** con un conjunto de posiciones de prueba
   previo.
5. **Estadísticas** por apertura y por bot a partir de las partidas guardadas.
6. **Traducción de nombres de aperturas** comunes (contribución externa).

---

## 12. Riesgos del plan y cómo detectarlos a tiempo

- **La fase 2 se alarga mucho.**
  - **Señal:** a las 8 semanas *perft* sigue fallando en profundidades bajas.
  - **Acción:** usar `perft divide` contra Stockfish sistemáticamente; si no
    basta, aplicar el plan B del diseño (adaptador temporal con una librería
    detrás del mismo contrato) y seguir avanzando.
- **Pérdida de motivación por no ver nada jugable.**
  - **Señal:** semanas sin *commits*.
  - **Acción:** adelantar una mini-web que muestre un tablero con jugadas
    legales del dominio a mitad de la fase 2 (es *paralelizable* con 5.4).
- **Crecimiento del alcance.**
  - **Señal:** aparecen tareas que no están en este plan.
  - **Acción:** van a un *issue* con etiqueta `post-1.0`, no a la fase actual.
- **Cambios en versiones (Spring Boot, Stockfish).**
  - **Señal:** CI rota tras actualizar.
  - **Acción:** versiones fijadas; actualizar en ramas dedicadas, una
    dependencia a la vez.

---

## 13. Lista de comprobación rápida por fase

- **F0:** herramientas, UCI a mano, GitHub configurado, ADR-001/002.
- **F1:** módulos, arranque, Compose, Flyway, ArchUnit rompe la build, CI,
  i18n EN/ES, `kopi-labs`.
- **F2:** FEN, jugadas, *perft* en verde, SAN, fin de partida, Zobrist.
- **F3:** agregado `Game`, casos de uso, Postgres, REST, web jugable con `PAWN`,
  modo oficina.
- **F4:** `KNIGHT`, cliente UCI + pool, tres perfiles Stockfish, calibración.
- **F5:** Win %, clasificador, libro, cola duradera, SSE, revisión.
- **F6:** PGN entrada/salida, historial.
- **F7:** E2E, métricas, imagen publicada, documentación, `v1.0.0`.
