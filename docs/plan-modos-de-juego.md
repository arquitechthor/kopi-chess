# Plan: modos de juego (puzzles, entrenador y reloj) y hoja de ruta

- **Estado:** propuesta. Nada está comprometido ni tiene fecha; recoge cómo
  se haría cada modo para que, al retomarlo, se empiece con las decisiones a
  la vista.
- **Fecha:** 2026-11-07, sobre la versión 1.0.1.
- **Alcance:** los tres modos de juego de
  [`ideas-futuro.md`](ideas-futuro.md) que aún no tenían plan:
  - **Puzzles desde tus propios errores** (§2).
  - **Modo entrenador:** aviso antes de confirmar una jugada mala (§3).
  - **Reloj y controles de tiempo** (§4).
- **Jugador contra jugador** (local y en línea) y la **app Android** para
  jugar contra los bots del PC ya tienen su plan en
  [`plan-jugador-contra-jugador.md`](plan-jugador-contra-jugador.md); aquí
  solo se ordenan con el resto (§1).

---

## 1. Hoja de ruta

**Prioridad decidida por el usuario (2026-11-07).** Una versión *minor* por
modo: ninguno rompe lo anterior.

| Orden | Modo | Infraestructura nueva | Esfuerzo orientativo (tiempo parcial) |
|---|---|---|---|
| 1 (1.1.0, hecho) | Jugador contra jugador, local y en línea ([plan aparte](plan-jugador-contra-jugador.md), fases A–E) | backend Kopi en AWS (relé, id de Kopi) | 5–8 semanas |
| 2 | App Android contra los bots del PC (plan JcJ, §11, fase F), **en este repositorio** | relé (ya hecho en 1) y Google Play | 3–5 semanas |
| después | Puzzles desde tus errores (§2) | ninguna | 2–3 semanas |
| después | Modo entrenador (§3) | ninguna | 1–2 semanas |
| después | Reloj y controles de tiempo (§4) | ninguna | 2–3 semanas |

**Por qué este orden:**

- **El multijugador primero** porque es lo que necesita AWS, y solo una de
  las máquinas de desarrollo tiene acceso. Todo lo que se pueda dejar
  preparado en AWS desde ella (fases B y C) desbloquea el resto del trabajo
  en la otra.
- **La app Android, justo después:** reutiliza el relé y la cuenta Kopi del
  multijugador. Vive en este repositorio porque depende del protocolo y de los
  casos de uso de Kopi Chess.
- **Después, puzzles, entrenador y reloj,** en ese orden propuesto. El
  entrenador y el reloj se apoyan en el modelo de un jugador por color
  (`Player`, fase A del multijugador), así que no conviene empezarlos antes.
- **Decisiones pospuestas** hasta que les toque: D2 (borrar puzzles con su
  partida), D3 (pista del entrenador sin haber movido) y D4 (controles de
  tiempo a medida). Sus propuestas se quedan como punto de partida.

### Estado al 2026-11-07 y siguientes pasos

Para retomar desde cualquier máquina:

- **Multijugador (1): programado y fusionado** (fases A, B y D: #119–#122).
  La parte AWS está desplegada (relé, id de Kopi, cliente `kopi-chess-client`;
  valores reales en [`protocolo-rele.md`](protocolo-rele.md)). Se probó con
  una cuenta real contra el relé real y una segunda cuenta sintética.
- **Publicar la 1.1.0:** la PR de la versión (`chore(release): 1.1.0`, #123)
  está abierta. Tras fusionarla, seguir CONTRIBUTING: etiqueta `v1.1.0` en
  `main` (publica la imagen en GHCR), *release* de GitHub y el commit
  `build: start 1.2.0-SNAPSHOT` con la línea de estado del README. No
  necesita AWS.
- **Pendiente de probar:** que una invitación nueva aparece al momento en la
  página en línea abierta (arreglado en #122, sin probar de verdad) y una
  partida entre dos personas reales en dos PCs (aceptación de la fase D).
- **Siguiente (2): la app Android, fase F.** Lo primero que necesita AWS es
  el canal entre dispositivos del relé: conviene hacerlo en la máquina con
  acceso a AWS. ADR-021, el adaptador del PC y la app se pueden hacer en
  cualquiera.
- **Propuesto, sin decidir:** guardar el historial en la cuenta Kopi
  ([`ideas-futuro.md`](ideas-futuro.md)), antes o después de la app Android.
  Encaja con ella: el móvil vería las partidas aunque el PC esté apagado.

---

## 2. Puzzles desde tus propios errores

### 2.1 Supuestos

- **Salen solo de tus partidas analizadas.** Cada `BLUNDER` o `MISSED_MATE`
  de una jugada **tuya** se convierte en un ejercicio: «aquí jugaste otra
  cosa; encuentra la mejor jugada».
- **Solo de partidas jugadas aquí.** Las importadas no dicen qué lado eras tú
  (`Game.humanColor()` está vacío); quedan fuera del primer alcance (§2.9).
- **Todo en local:** generación, comprobación y repaso. Sin cuenta ni
  conexión, como el resto de la 1.0.
- **Stockfish decide si una respuesta vale** (§2.4). No hace falta que el
  puzzle tenga una única solución.
- **Repaso espaciado sencillo:** un puzzle fallado vuelve pronto; uno
  resuelto varias veces seguidas se da por dominado.

### 2.2 Qué ve el usuario

- En la portada, una tarjeta **«Puzzles: N pendientes»** junto a «Nueva
  partida».
- **`/puzzles`:** el siguiente puzzle pendiente.
  - El tablero en la posición anterior a tu error, orientado hacia tu lado,
    y el texto «Juegan las blancas: encuentra la mejor jugada» (o «…: hay mate
    en 3»).
  - Al mover: ✓ «¡Correcto!» o ✗ «No es la mejor» con la evaluación, y otro
    intento o «Ver la solución».
  - **Pista en dos pasos:** primero la pieza que hay que mover; después, la
    jugada.
  - En los puzzles de mate, el rival responde solo con la jugada del motor
    hasta dar el mate.
  - Al terminar: «Siguiente» y **«Ver en la partida»** (la revisión abierta
    en esa jugada).
- **`/puzzles/lista`:** todos los puzzles, con filtros (pendientes, fallados,
  dominados; tema: error grave o mate perdido; bot de la partida) y el
  porcentaje de aciertos.
- **Modo oficina** (ADR-013): la misma pantalla con la jugada escrita en
  notación localizada, como al jugar.

### 2.3 Qué posiciones se convierten en puzzle

Una jugada es candidata si cumple todo esto:

- La hizo la persona (el color de `humanColor`), no el bot.
- Su categoría es `BLUNDER` o `MISSED_MATE`. `MISTAKE` queda fuera al
  principio, porque da puzzles menos claros (se puede activar con
  `kopi.puzzles.include-mistakes`).
- **La posición no estaba ya decidida:** la probabilidad de ganar antes de la
  jugada estaba entre 0,1 y 0,9 (`WinChance`), salvo en los mates perdidos.
- **La mejor jugada se distingue de las demás:** entre la mejor línea y la
  segunda hay al menos 0,2 de probabilidad de ganar, o la segunda también da
  mate. Si no, cualquier jugada razonable valdría y el puzzle no enseña nada.

**Lo que falta hoy:** el análisis ya pide dos líneas (`multi-pv: 2`), pero
solo guarda la mejor (`move_analysis.best_move_uci`, `best_line_san`). Hace
falta guardar también **la evaluación de la segunda línea** (`eval_second`,
nula si solo había una jugada legal). Las partidas analizadas antes de la
migración no la tienen. Para ellas se calcula al generar el puzzle, con una
consulta al motor con los mismos límites del análisis (§2.5).

### 2.4 Cómo se comprueba una respuesta

- **Con el motor, no comparando texto:** la jugada propuesta se evalúa con
  Stockfish y se clasifica con el mismo `MoveClassifier` del análisis
  (ADR-005).
  - **Correcta** si sale `BEST` o `GOOD`: no pierde más de 0,1 de
    probabilidad de ganar respecto a la mejor.
  - **En un mate perdido,** si mantiene un mate igual de corto o más corto.
- **Atajo:** si la jugada es la `bestMove` guardada, es correcta sin
  consultar al motor.
- **Sin Stockfish** (caído o arrancando): solo vale la `bestMove` guardada, y
  la pantalla lo avisa.
- **Puzzles de varias jugadas** (mates): tras cada jugada correcta, el rival
  responde con la línea del motor desde la posición nueva. Si la persona se
  desvía con una jugada que también vale, el motor da la respuesta adecuada.

### 2.5 Cambios en Kopi Chess, por anillos

#### Dominio (`kopi-domain`, paquete nuevo `puzzle`)

- `Puzzle`: id, partida y `ply` de origen, posición (FEN), lado que juega,
  tema (`BLUNDER`, `MISSED_MATE`), jugada de la partida (el error), mejor
  jugada y línea, mate en *n* si lo hay, y fecha de creación.
- `PuzzleCriteria`: las reglas de §2.3 como función pura sobre
  `MoveAnalysis`, con la evaluación de la segunda línea. Con pruebas
  unitarias con posiciones de ejemplo.
- `PuzzleReview`: el estado de repaso (sistema de cajas de Leitner):
  - Cajas 1 → 4, con el siguiente repaso a 1, 3, 7 y 21 días.
  - Acierto sin pista: sube una caja. Fallo o pista: vuelve a la 1.
  - Tres aciertos seguidos en la caja 4: **dominado**.
  - Usa el `Clock` que ya reciben los casos de uso, para poder probarlo.
- `MoveAnalysis` gana la evaluación de la segunda línea (opcional).

#### Aplicación (`kopi-application`, paquete nuevo `puzzle`)

- **`GeneratePuzzles`:** al terminar el análisis de una partida
  (`RunAnalysisJob`), crea sus puzzles. Es idempotente: se puede volver a
  generar sin duplicar (clave partida + `ply`).
- **`NextPuzzle`, `ListPuzzles`, `GetPuzzle`.**
- **`CheckPuzzleMove`:** comprueba una jugada (§2.4) y devuelve si vale, la
  evaluación y, en los mates, la respuesta del rival.
- **`RecordPuzzleAttempt`:** apunta el resultado y mueve el puzzle de caja.
- **`BackfillPuzzles`:** genera los puzzles de las partidas ya analizadas
  (botón en `/puzzles/lista` y operación REST); con el motor, para las que no
  tienen la segunda línea.
- **Puerto nuevo** `PuzzleRepository`. Se reutilizan `ChessEngine` y
  `AnalysisRepository`.
- **Borrar una partida borra sus puzzles** (decisión D2, abajo).

#### Adaptadores

- **Persistencia:** migración `V5__puzzles.sql`:
  - `move_analysis.eval_second`, nula.
  - Tabla `puzzle` con FK a `game` y `on delete cascade`.
  - Tabla `puzzle_attempt` (fecha, resuelto, con pista, intentos).
  - Columnas de repaso en `puzzle`: caja, siguiente repaso, dominado.
- **Stockfish:** `StockfishAnalysis` guarda la evaluación de la segunda
  línea, que ya recibe.
- **Web:** `PuzzleController` + plantillas Thymeleaf; `puzzle.js` sobre
  Chessground, como `board.js`; textos en inglés y español; versión del modo
  oficina.
- **REST:** `/api/v1/puzzles` (siguiente, lista, uno, comprobar jugada,
  apuntar intento, generar los que faltan).
- **Métricas:** puzzles generados, resueltos y fallados, y tiempo de
  comprobación con el motor (ADR-017).

### 2.6 Decisiones

- **ADR (siguiente libre): puzzles desde los propios errores.** Criterios de §2.3,
  comprobación con el motor (§2.4) y repaso espaciado.
- **D2 (pospuesta):** ¿borrar una partida borra sus puzzles? **Propuesta: sí**
  (`on delete cascade`). Un puzzle sin su partida pierde el «Ver en la
  partida», y el historial es de la persona.

### 2.7 Fases

#### Fase P1: el dominio y los datos

- [ ] ADR de los puzzles.
- [ ] `Puzzle`, `PuzzleCriteria` y `PuzzleReview` con pruebas unitarias
      (posiciones de ejemplo para cada criterio y cada caja).
- [ ] Segunda línea en `MoveAnalysis` y en `StockfishAnalysis`.
- [ ] Migración `V5__puzzles.sql` y `JdbcPuzzleRepository`, con pruebas de
      integración (Testcontainers).

#### Fase P2: generación

- [ ] `GeneratePuzzles` enganchado al final de `RunAnalysisJob`.
- [ ] `BackfillPuzzles` para las partidas ya analizadas.
- [ ] Revisar a mano los puzzles generados de unas cuantas partidas reales
      de los cinco bots y ajustar los umbrales si salen puzzles malos.

#### Fase P3: resolver

- [ ] `CheckPuzzleMove` (motor, atajo, sin motor) y `RecordPuzzleAttempt`.
- [ ] `/puzzles`: tablero, pista en dos pasos, solución, mates de varias
      jugadas, «Ver en la partida».
- [ ] Modo oficina y API REST.
- [ ] Prueba E2E (Playwright): una partida con un error grave forzado acaba
      dando un puzzle que se resuelve.

#### Fase P4: repaso y lista

- [ ] `/puzzles/lista` con filtros y aciertos; tarjeta en la portada.
- [ ] Documentación (README, diseño), changelog y la siguiente versión *minor*.

**Aceptación:** tras analizar una partida con un error grave, aparece su
puzzle; se resuelve con la mejor jugada y con otra igual de buena; un fallo
lo hace volver al día siguiente; borrar la partida borra el puzzle.

### 2.8 Riesgos

- **Puzzles malos** (ambiguos o triviales).
  - **Señal:** muchos puzzles que se resuelven con cualquier jugada, o que ni
    el motor explica bien.
  - **Acción:** los umbrales de §2.3 van en configuración; revisión a mano
    en la fase P2.
- **La comprobación con el motor compite con el análisis** por el
  `EnginePool`.
  - **Señal:** comprobar tarda más de un segundo con un análisis en marcha.
  - **Acción:** límites más bajos que los del análisis para comprobar, y
    medir con la métrica de §2.5.

### 2.9 Fuera de alcance

- Puzzles de partidas importadas. Exigiría preguntar qué lado era la persona
  al importar; es fácil de añadir después.
- Puzzles de otras fuentes (bases de puzzles públicas, partidas de otros).
- Clasificación o Elo de puzzles.

---

## 3. Modo entrenador

### 3.1 Supuestos

- **Una ayuda opcional, partida a partida:** se activa al crear la partida
  («Con entrenador») y no se puede cambiar después.
- **Solo contra los bots.** En el modo local no tiene sentido (¿a quién
  avisa?), y en línea sería ayuda externa. Necesita el modelo `Player` de la
  fase A del jugador contra jugador, que dice cuál es la persona.
- **Avisa, no prohíbe:** si la jugada elegida sería un error, la app lo dice
  **antes de confirmarla** y la persona decide.
- **No regala la solución:** el aviso no dice cuál es la mejor jugada; la
  pista se pide aparte.
- **Las partidas con entrenador se marcan** y **no cuentan** en el balance
  contra cada bot.

### 3.2 Qué ve el usuario

- Nueva partida: la casilla **«Con entrenador»**, con una línea que explica
  qué hace.
- Al soltar una pieza en una jugada que sería `MISTAKE`, `BLUNDER` o
  `MISSED_MATE`, aparece un aviso sin confirmar aún la jugada:
  - «Esa jugada es un error grave: pierdes mucha ventaja.» (o «Tenías mate en
    2 y lo dejas escapar»)
  - **«Pensar otra vez»:** la pieza vuelve a su sitio.
  - **«Pista»:** primero la pieza que conviene mover; después, la jugada.
  - **«Jugarla igualmente»:** se juega como siempre.
- El umbral (avisar desde `MISTAKE` o solo desde `BLUNDER`) se elige al crear
  la partida.
- **En la revisión,** las jugadas avisadas llevan una marca («avisada»), y el
  historial muestra «con entrenador».
- **Modo oficina:** después de escribir la jugada, un paso de confirmación
  con el mismo texto.

### 3.3 Cómo funciona

- **Una consulta, no una jugada:** un caso de uso nuevo,
  **`CheckCandidateMove`**, evalúa la posición antes y después de la jugada
  propuesta y la clasifica con `MoveClassifier` (ADR-005). No cambia la
  partida. `PlayMove` no cambia: confirmar es la misma llamada de siempre.
- **Rápido:** límites propios, más bajos que los del análisis
  (`kopi.trainer.limits`, por ejemplo profundidad 12), con un objetivo de
  menos de 0,5 s.
- **Se adelanta el trabajo:** la evaluación de la posición actual se pide en
  cuanto empieza el turno de la persona (mientras piensa) y se guarda en
  memoria por hash Zobrist. Al soltar la pieza solo falta evaluar la posición
  siguiente.
- **Si el motor no responde a tiempo,** la jugada se juega sin aviso y la
  pantalla dice «El entrenador no ha podido revisar esta jugada». Nunca
  bloquea la partida.

### 3.4 Cambios en Kopi Chess

- **Dominio:**
  - `Game` gana el ajuste del entrenador (apagado, desde `MISTAKE` o desde
    `BLUNDER`).
  - `PlayedMove` gana `warned` (la jugada se confirmó tras un aviso).
- **Aplicación:** `CheckCandidateMove` y `TrainerHint` (pieza y jugada). Si
  la jugada se confirma tras un aviso, `PlayMoveCommand` lleva esa marca. El
  balance contra los bots (`GetBotRecords`) excluye las partidas con
  entrenador.
- **Persistencia:** migración con `game.trainer` (nula = apagado) y
  `game_move.warned`.
- **Web:** casilla al crear la partida; diálogo de aviso en `board.js`
  (antes del `POST` de la jugada, una llamada a la comprobación); marca en la
  revisión y en el historial; versión del modo oficina.
- **REST:** `POST /api/v1/games/{id}/candidate` (comprobar) y
  `…/hint`.
- **PGN:** una etiqueta propia `[KopiTrainer "BLUNDER"]` y un comentario
  `{avisada}` en las jugadas avisadas.

### 3.5 Decisiones

- **ADR (siguiente libre): modo entrenador.** Consulta sin efecto
  separada de `PlayMove`; límites propios; partidas marcadas que no cuentan
  en el balance.
- **D3 (pospuesta):** ¿se puede pedir una pista sin haber movido (botón
  «Pista» siempre visible)? **Propuesta: sí**, con el mismo registro que una
  jugada avisada (se marca la jugada siguiente).

### 3.6 Fases

#### Fase T1: comprobar una jugada

- [ ] ADR del entrenador.
- [ ] `CheckCandidateMove` y `TrainerHint` con un motor falso en las pruebas;
      caché por Zobrist; tiempo máximo.
- [ ] Ajuste del entrenador en `Game`, `warned` en `PlayedMove` y su
      migración.

#### Fase T2: la pantalla

- [ ] Casilla al crear la partida; diálogo de aviso y pista en `board.js`.
- [ ] Marca en la revisión y en el historial; el balance contra los bots sin
      estas partidas.
- [ ] Modo oficina, REST y PGN.
- [ ] Prueba E2E: un error grave forzado contra `PAWN` muestra el aviso;
      «Pensar otra vez» lo deshace y «Jugarla igualmente» lo juega.

#### Fase T3: publicación

- [ ] Medir la latencia con un análisis en marcha y ajustar los límites.
- [ ] Documentación, changelog y la siguiente versión *minor*.

**Aceptación:** en una partida con entrenador, un error grave avisa antes de
jugarse y la pista lleva a una jugada mejor. Sin Stockfish, la partida sigue
sin avisos. El balance contra el bot no cambia.

### 3.7 Riesgos

- **Latencia:** el aviso llega tarde y estorba.
  - **Acción:** evaluación adelantada mientras la persona piensa; límites
    bajos; si se pasa del tiempo máximo, sin aviso.
- **Falsos avisos** por la poca profundidad.
  - **Acción:** el umbral por defecto es `BLUNDER`; el análisis posterior,
    con más profundidad, sigue siendo la referencia.
- **Competencia por el motor** con el análisis y con los bots de Stockfish.
  - **Acción:** medir; si hace falta, un proceso de Stockfish reservado para
    el entrenador en el `EnginePool`.

---

## 4. Reloj y controles de tiempo

### 4.1 Supuestos

- **Opcional, partida a partida:** «Sin reloj» sigue siendo lo normal.
- **Contra los bots y en el modo local.** Las partidas **en línea siguen sin
  reloj** (supuesto del plan de jugador contra jugador: se juega por
  correspondencia).
- **Controles Fischer** (tiempo inicial + incremento por jugada), con
  opciones fijas: 3+2, 5+0, 10+5, 15+10, 30+0 y 60+0.
- **El servidor manda:** el tiempo se calcula con el `Clock` del servidor, no
  con el del navegador. La cuenta atrás de la página es solo una vista.
- **Sin pausa:** cerrar la página no para el reloj, como en una partida
  real. Contra un bot se puede abandonar o dejar que caiga la bandera.
- **El bot también tiene reloj:** se le descuenta lo que tarda (hasta 1 s con
  `QUEEN`). En la práctica no pierde por tiempo, pero las dos esferas se ven
  igual.

### 4.2 Reglas (en el dominio)

- Al jugar, se descuenta del reloj del jugador el tiempo desde el inicio de
  su turno y se suma el incremento.
- **Sin tiempo:** si el tiempo del jugador llega a cero antes de que juegue,
  pierde (`TimeForfeit(loser)`), **salvo** que el rival no pueda dar mate con
  ningún orden de jugadas legales. En ese caso son tablas
  (`TimeoutVsInsufficientMaterial`), como en el reglamento de la FIDE
  (art. 6.9). Para eso basta una comprobación de «material del rival
  insuficiente para dar mate» sobre `Rules`; el caso de la posición imposible
  de ganar por bloqueo queda fuera.
- **Caída perezosa, como la inactividad del jugador contra jugador:** el
  dominio aplica la caída de bandera cada vez que se lee o se juega la
  partida (`game.withTimeCheck(now)`). Así no hace falta un proceso de fondo:
  una partida abandonada a medias aparece terminada por tiempo en cuanto se
  abre.
- **Aviso desde la página:** cuando la cuenta atrás llega a cero, la página
  pide al servidor que compruebe el tiempo (`POST …/flag`); el servidor
  decide con su reloj.
- Las demás formas de terminar no cambian. Un mate con el último segundo
  vale: la jugada llegó antes de que se acabara el tiempo.

### 4.3 Cambios en Kopi Chess

- **Dominio:**
  - `TimeControl(initial, increment)`.
  - `Game` gana el control, el tiempo restante de cada color y el inicio del
    turno.
  - `PlayedMove` gana el tiempo restante tras la jugada.
  - **Dos formas de terminar nuevas** en `GameOutcome`: `TimeForfeit(Color
    loser)` y `TimeoutVsInsufficientMaterial()`. El `switch` exhaustivo
    obliga a tratarlas en la web, en el PGN y en la base de datos.
  - Pruebas con un `Clock` fijo: incremento, caída, tablas por material y
    mate en el último segundo.
- **Aplicación:** `CreateGameCommand` con el control; `GetGame` y `PlayMove`
  aplican la comprobación de tiempo; caso de uso nuevo `ClaimTimeout`. El
  turno del bot descuenta su tiempo real.
- **Persistencia:** migración con `game.time_initial_ms`,
  `time_increment_ms`, el tiempo restante de cada color, el inicio del turno,
  `game_move.clock_after_ms` y los códigos de terminación nuevos.
- **Web:** selector de control al crear la partida; dos relojes con cuenta
  atrás sincronizada con lo que dice el servidor en cada respuesta y en cada
  evento SSE; aviso de poco tiempo (menos de 10 s). En el modo local, el
  reloj del jugador al que le toca se resalta. En el modo oficina, el tiempo
  restante como texto.
- **PGN:** etiqueta `[TimeControl "600+5"]` y comentarios `{[%clk 0:09:57]}`
  en cada jugada, que entienden Lichess y ChessBase. Al importar, el
  `%clk` se puede leer, pero no es necesario.
- **Revisión:** el tiempo usado en cada jugada junto a la lista de jugadas.
  Más adelante, en la gráfica de evaluación.

### 4.4 Decisiones

- **ADR (siguiente libre): el reloj como regla del dominio.** El servidor
  manda; la caída se aplica de forma perezosa; tablas por material
  insuficiente del rival.
- **D4 (pospuesta):** ¿añadir un control a medida (minutos + incremento
  libres) además de los fijos? **Propuesta: no en el primer alcance;** los
  fijos cubren casi todo y simplifican las pruebas.

### 4.5 Fases

#### Fase R1: las reglas

- [ ] ADR del reloj.
- [ ] `TimeControl`, tiempo en `Game` y `PlayedMove`, las dos terminaciones
      nuevas y `withTimeCheck`, con pruebas con un `Clock` fijo.
- [ ] Migración y persistencia.

#### Fase R2: jugar con reloj

- [ ] `CreateGame`, `PlayMove`, `GetGame` y `ClaimTimeout`; el tiempo del
      bot.
- [ ] Selector de control, relojes en la página (con SSE), aviso de poco
      tiempo, modo local y modo oficina.
- [ ] Prueba E2E: una partida 3+2 contra `PAWN` que se pierde por tiempo
      (con el reloj de las pruebas, no esperando de verdad).

#### Fase R3: PGN y revisión

- [ ] `TimeControl` y `%clk` en el PGN exportado; el tiempo por jugada en la
      revisión.
- [ ] Documentación, changelog y la siguiente versión *minor*.

**Aceptación:** una partida con reloj se juega entera con el incremento bien
sumado. Si se deja correr el tiempo, termina por tiempo al volver a abrirla.
Contra un rey solo, son tablas. El PGN lleva el control y el tiempo de cada
jugada.

### 4.6 Riesgos

- **Desfase entre la cuenta atrás de la página y el servidor.**
  - **Acción:** el servidor manda y la página se corrige con cada respuesta.
    La diferencia solo se nota en la última décima.
- **El análisis no conoce el reloj:** un error con 2 s en el reloj cuenta
  igual que uno con 10 min.
  - **Acción:** aceptado. Mostrar el tiempo usado en la revisión ayuda a
    entenderlo.

### 4.7 Fuera de alcance

- Reloj en las partidas en línea.
- Retraso Bronstein o «delay» simple; controles por tramos (40 jugadas en
  90 min…).
- Pausar una partida con reloj.
