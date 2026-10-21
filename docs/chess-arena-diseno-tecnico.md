# Chess Arena --- Documento de diseño técnico

**Estado:** propuesta inicial de arquitectura\
**Versión:** 1.0\
**Objetivo:** definir un MVP de ajedrez contra cinco niveles de bot, con
análisis completo al terminar cada partida.\
**Stack propuesto:** Angular, Spring Boot, MongoDB, Stockfish y Docker
Compose.

------------------------------------------------------------------------

## 1. Resumen ejecutivo

Chess Arena será una aplicación web para jugar partidas de ajedrez
contra cinco perfiles de dificultad controlados por Stockfish. Al
terminar una partida, el usuario podrá revisar sus movimientos,
consultar una clasificación de calidad para cada jugada y descubrir
oportunidades tácticas importantes, incluyendo mates que pudo encontrar
y no encontró.

La primera versión **no incluirá análisis en tiempo real**. Durante la
partida solo se necesita ejecutar el motor para que el bot elija sus
movimientos. El análisis detallado se ejecutará después de finalizar la
partida, evitando competir por CPU con la experiencia de juego.

### Objetivos del MVP

1.  Jugar partidas completas contra cinco niveles de dificultad.
2.  Validar legalidad de movimientos, reglas de ajedrez y condiciones de
    finalización.
3.  Guardar partidas y movimientos.
4.  Analizar la partida al finalizar.
5.  Clasificar movimientos con etiquetas explicables.
6.  Detectar oportunidades de mate que se hayan desaprovechado, dentro
    de los límites de análisis configurados.
7.  Permitir revisar la partida movimiento a movimiento con evaluaciones
    y variantes.
8.  Ejecutar el entorno completo localmente con Docker Compose.
9.  Mantener la arquitectura preparada para añadir cuentas, estadísticas
    y funciones educativas en versiones posteriores.

### Fuera del alcance inicial

-   Análisis y animaciones de jugadas brillantes durante la partida.
-   Multijugador entre personas.
-   Torneos, ligas, chat y funciones sociales.
-   Un rating que pretenda equivaler al Elo FIDE.
-   Curso completo de ajedrez, repetición espaciada o itinerarios
    educativos.
-   Aplicación móvil nativa.
-   Dependencia obligatoria de una API externa para jugar o analizar.
-   Prometer que las categorías serán idénticas a las de Chess.com.

------------------------------------------------------------------------

## 2. Decisiones tecnológicas

  -----------------------------------------------------------------------
  Área                    Elección inicial        Motivo
  ----------------------- ----------------------- -----------------------
  Frontend                Angular + TypeScript    Estructura clara para
                                                  una aplicación con
                                                  varias vistas,
                                                  componentes de tablero,
                                                  análisis e historial.

  Tablero                 Biblioteca de UI de     Separar la
                          ajedrez compatible con  representación visual
                          Angular, o componente   de las reglas y del
                          propio                  motor. Validar
                                                  compatibilidad y
                                                  licencia antes de
                                                  elegir la biblioteca
                                                  definitiva.

  Reglas de ajedrez en    Biblioteca Java de      No implementar
  backend                 reglas/FEN/PGN, tras    manualmente todas las
                          evaluar mantenimiento y reglas si una
                          licencia                biblioteca fiable cubre
                                                  los casos necesarios.

  Backend                 Java 25 LTS + Spring Boot  API REST, validación,
                                                  lógica de aplicación,
                                                  integración con MongoDB
                                                  y coordinación de
                                                  procesos.

  Motor de ajedrez        Stockfish mediante      Motor open source capaz
                          protocolo UCI           de jugar y analizar
                                                  posiciones localmente.

  Base de datos           MongoDB                 Adecuada para
                                                  documentos de partida,
                                                  movimientos y
                                                  resultados de análisis
                                                  que evolucionan como
                                                  una unidad lógica.

  Contenedores            Docker + Docker Compose Entorno reproducible
                                                  para frontend, backend,
                                                  MongoDB y motor.

  API de apertura         Lichess Opening         Enriquecer la
  opcional                Explorer                identificación de
                                                  jugadas de libro; no es
                                                  necesaria para el
                                                  núcleo del MVP.

  Pruebas                 JUnit 5, Spring Boot    Cubrir reglas,
                          Test, Testcontainers y  integración, análisis y
                          pruebas de frontend     comportamiento visual.
  -----------------------------------------------------------------------

### Principios de diseño

-   El backend es la autoridad sobre el estado de la partida y la
    legalidad de los movimientos.
-   Stockfish es un motor externo al proceso de negocio, comunicado
    mediante UCI.
-   La interfaz no debe decidir por sí sola el resultado oficial de una
    partida.
-   El análisis debe ser reproducible en la medida de lo posible, con
    parámetros registrados.
-   Las categorías de movimientos son una capa de producto propia, no
    etiquetas nativas universales de Stockfish.
-   Las APIs públicas son auxiliares; el producto debe seguir
    funcionando si una API externa no está disponible.
-   Las versiones de las dependencias y de Stockfish deben quedar
    fijadas para facilitar la reproducibilidad.

------------------------------------------------------------------------

## 3. Arquitectura lógica

``` text
┌──────────────────────────────────────────────────────┐
│                    Navegador                         │
│                                                      │
│  Angular                                             │
│  ├── Pantalla de juego                               │
│  ├── Tablero y controles                             │
│  ├── Selector de bot                                 │
│  ├── Historial de movimientos                        │
│  └── Visor de análisis posterior                     │
└───────────────────────┬──────────────────────────────┘
                        │ HTTPS / REST
                        ▼
┌──────────────────────────────────────────────────────┐
│                 Spring Boot API                      │
│                                                      │
│  ├── Game Application Service                       │
│  ├── Chess Rules Adapter                             │
│  ├── Engine Manager (UCI)                            │
│  ├── Game Analysis Service                           │
│  ├── Move Classification Service                     │
│  ├── Persistence Repositories                        │
│  └── Optional Analysis Job Worker                    │
└───────────────┬─────────────────────┬────────────────┘
                │                     │ UCI
                ▼                     ▼
       ┌────────────────┐     ┌──────────────────┐
       │    MongoDB     │     │    Stockfish     │
       │ partidas y     │     │ jugar y analizar │
       │ análisis       │     │ posiciones       │
       └────────────────┘     └──────────────────┘

Opcional:
Spring Boot ── HTTPS ──> Lichess Opening Explorer
```

### Despliegue inicial

Para un MVP local o una demo pequeña, Docker Compose puede ejecutar:

-   `frontend`: aplicación Angular servida por Nginx.
-   `backend`: API Spring Boot.
-   `mongodb`: base de datos.
-   Stockfish puede ejecutarse como proceso hijo del backend dentro de
    su contenedor, o como servicio dedicado si se necesita aislar CPU y
    escalarlo por separado.

**Recomendación inicial:** ejecutar Stockfish como proceso hijo
gestionado por el backend para reducir complejidad operativa. Aun así,
diseñar una interfaz `ChessEngine` para permitir moverlo a un servicio
independiente más adelante.

No se debe iniciar un proceso Stockfish nuevo para cada petición sin
control. El backend debe administrar procesos reutilizables,
concurrencia, tiempos de espera, reinicio ante fallos y límites de
recursos.

------------------------------------------------------------------------

## 4. Componentes y responsabilidades

### 4.1 Frontend Angular

Responsabilidades:

-   Mostrar tablero, piezas, coordenadas y turno actual.
-   Permitir seleccionar el color del usuario.
-   Mostrar los cinco bots y su dificultad.
-   Enviar movimientos al backend y representar la respuesta
    autoritativa.
-   Mostrar estado de carga, error y fin de partida.
-   Presentar el historial de movimientos.
-   Mostrar la pantalla de análisis final.
-   Permitir seleccionar un movimiento del historial para actualizar
    tablero, evaluación y variante.
-   Mostrar etiquetas como «Mejor», «Buena», «Inexactitud», «Error» y
    «Error grave».
-   Resaltar en el tablero la jugada analizada y la variante
    recomendada.
-   Mostrar oportunidades de mate encontradas por el análisis.

El frontend puede validar movimientos para ofrecer una experiencia
fluida, pero la validación del servidor es obligatoria.

### 4.2 Backend Spring Boot

Módulos lógicos recomendados:

-   `game`: creación y ciclo de vida de partidas.
-   `chess`: reglas, posiciones, notación y adaptación a la biblioteca
    de ajedrez elegida.
-   `engine`: comunicación con Stockfish mediante UCI.
-   `analysis`: análisis posterior a la partida.
-   `classification`: asignación de etiquetas a movimientos.
-   `persistence`: documentos y repositorios MongoDB.
-   `api`: controladores REST, DTO y manejo uniforme de errores.
-   `configuration`: límites, timeouts, perfiles y parámetros del motor.

La lógica de dominio no debería depender directamente de controladores
HTTP ni de detalles de MongoDB.

### 4.3 Stockfish

Se usará para dos trabajos distintos:

1.  **Juego:** elegir el movimiento del bot según su perfil de
    dificultad.
2.  **Análisis:** evaluar posiciones y obtener las mejores
    continuaciones después de la partida.

El motor se comunica mediante UCI. La integración debe gestionar al
menos los comandos de inicialización, configuración de opciones,
posición, búsqueda, parada y cierre.

La implementación debe parsear las respuestas del motor de forma
robusta, incluyendo puntuaciones en centipeones y puntuaciones de mate.
La puntuación siempre debe normalizarse respecto a un color de
referencia para no invertir el significado entre blancas y negras.

### 4.4 MongoDB

MongoDB almacenará partidas, secuencia de movimientos, configuración de
dificultad y resultados del análisis. Para el MVP se recomienda un
documento principal por partida, siempre que el tamaño se mantenga
razonable.

No es necesario guardar cada evaluación intermedia de una búsqueda del
motor. Se guardan los resultados útiles para la revisión posterior.

------------------------------------------------------------------------

## 5. Modelo de dominio

### 5.1 Game

Campos sugeridos:

``` json
{
  "_id": "ObjectId",
  "status": "IN_PROGRESS",
  "humanColor": "WHITE",
  "botProfile": "INTERMEDIATE",
  "createdAt": "2026-01-01T10:00:00Z",
  "startedAt": "2026-01-01T10:00:00Z",
  "finishedAt": null,
  "result": null,
  "terminationReason": null,
  "initialFen": "posición inicial",
  "finalFen": null,
  "moves": [],
  "analysisStatus": "NOT_REQUESTED",
  "analysis": null,
  "engineVersion": null,
  "analysisConfigVersion": null
}
```

Estados recomendados:

-   `IN_PROGRESS`
-   `FINISHED`
-   `ABANDONED`
-   `ERROR`

Estados del análisis:

-   `NOT_REQUESTED`
-   `PENDING`
-   `RUNNING`
-   `COMPLETED`
-   `FAILED`

Resultados:

-   `WHITE_WIN`
-   `BLACK_WIN`
-   `DRAW`

Motivos de finalización:

-   jaque mate;
-   tablas por ahogado;
-   tablas por material insuficiente;
-   repetición, si la biblioteca implementada la detecta;
-   regla de los cincuenta movimientos, si está implementada;
-   abandono o rendición;
-   otros finales admitidos por las reglas y el producto.

La lista definitiva debe reflejar exactamente lo que soporte la
biblioteca de reglas escogida. No declarar que una partida ha terminado
por una regla que el sistema no ha implementado.

### 5.2 Move

Cada movimiento debería guardar, como mínimo:

``` json
{
  "ply": 1,
  "color": "WHITE",
  "uci": "e2e4",
  "san": "e4",
  "fenBefore": "posición anterior",
  "fenAfter": "posición posterior",
  "playedAt": "2026-01-01T10:00:02Z",
  "analysis": {
    "status": "PENDING",
    "evaluationBefore": null,
    "evaluationAfter": null,
    "bestMoveUci": null,
    "bestLineSan": [],
    "classification": null,
    "centipawnLoss": null,
    "missedMate": null
  }
}
```

-   `ply` es el número de medio movimiento, empezando por 1.
-   `uci` es una representación compacta del movimiento.
-   `san` es la notación algebraica que se presenta al usuario.
-   Guardar `fenBefore` y `fenAfter` facilita reconstruir y analizar
    posiciones, aunque aumenta el tamaño del documento. Se puede
    recalcular a partir de la posición inicial y los movimientos, pero
    almacenarlos simplifica el análisis y la depuración.
-   Las puntuaciones deben incluir una convención explícita de
    perspectiva: por ejemplo, siempre desde el punto de vista de las
    blancas, o normalizadas desde el punto de vista del jugador que
    realizó el movimiento. No mezclar ambas convenciones.

### 5.3 AnalysisSummary

Resumen sugerido:

-   evaluación final;
-   precisión o estadísticas agregadas, si se implementan con una
    fórmula definida;
-   número de jugadas por categoría;
-   errores decisivos;
-   oportunidades de mate desaprovechadas;
-   mejor movimiento de la partida;
-   versión del motor y configuración utilizada;
-   duración y estado del análisis.

No mostrar una «precisión» como si fuera una métrica universal
comparable con Chess.com sin definir y validar la fórmula.

### 5.4 Índices

Crear un índice por `createdAt` si se ofrece historial ordenado por
fecha y por `status`/`analysisStatus` si se consultan partidas
pendientes de análisis. Añadir índices solo para consultas reales;
evitar indexar indiscriminadamente campos anidados de grandes
documentos.

------------------------------------------------------------------------

## 6. API REST propuesta

Prefijo: `/api/v1`

  ----------------------------------------------------------------------------
  Método                  Endpoint                     Descripción
  ----------------------- ---------------------------- -----------------------
  `POST`                  `/games`                     Crear una partida
                                                       contra un perfil de
                                                       bot.

  `GET`                   `/games/{gameId}`            Obtener el estado
                                                       actual de la partida.

  `POST`                  `/games/{gameId}/moves`      Enviar un movimiento
                                                       humano.

  `POST`                  `/games/{gameId}/resign`     Rendirse.

  `GET`                   `/games/{gameId}/moves`      Consultar el historial.

  `POST`                  `/games/{gameId}/analysis`   Solicitar análisis si
                                                       aún no existe.

  `GET`                   `/games/{gameId}/analysis`   Consultar el estado y
                                                       resultado del análisis.

  `GET`                   `/games`                     Listar partidas, con
                                                       paginación.

  `GET`                   `/bot-profiles`              Obtener los cinco
                                                       perfiles disponibles.
  ----------------------------------------------------------------------------

### Ejemplo de creación de partida

Petición:

``` json
{
  "humanColor": "WHITE",
  "botProfile": "BEGINNER"
}
```

Respuesta conceptual:

``` json
{
  "gameId": "identificador",
  "status": "IN_PROGRESS",
  "humanColor": "WHITE",
  "sideToMove": "WHITE",
  "fen": "posición inicial",
  "availableActions": ["MOVE", "RESIGN"]
}
```

### Ejemplo de movimiento

Petición:

``` json
{
  "uci": "e2e4"
}
```

El backend debe:

1.  Comprobar que la partida existe y está activa.
2.  Verificar que le toca jugar al humano.
3.  Validar el movimiento contra las reglas.
4.  Aplicar el movimiento y persistirlo.
5.  Comprobar si la partida ha terminado.
6.  Si continúa, pedir al bot una respuesta.
7.  Validar y guardar el movimiento del bot.
8.  Devolver el estado actualizado, incluyendo FEN, historial reciente y
    turno siguiente.

Si el movimiento es inválido, devolver un error controlado, por ejemplo
`400 Bad Request`, sin alterar la partida.

La operación debe protegerse frente a peticiones duplicadas o
concurrentes. Se puede utilizar un número de versión de partida
(`version`) o control optimista para evitar que dos solicitudes
modifiquen el mismo estado simultáneamente.

### Análisis asíncrono

El análisis completo puede tardar. Por eso, el endpoint de solicitud
debería responder con `202 Accepted` cuando el trabajo quede encolado, y
el frontend consultar su estado mediante `GET /analysis`.

En la primera versión puede utilizarse una cola en memoria y un
trabajador dentro de Spring Boot, siempre que se documente que los
trabajos en curso pueden perderse al reiniciar el contenedor. Para una
versión más robusta, persistir los trabajos pendientes o utilizar una
cola duradera.

------------------------------------------------------------------------

## 7. Los cinco perfiles de bot

Todos los perfiles pueden utilizar el mismo motor, pero con parámetros
diferentes. Los valores concretos deben validarse con pruebas y no
tratarse como equivalencias oficiales de Elo.

  -----------------------------------------------------------------------
  Perfil                  Objetivo                Estrategia inicial
  ----------------------- ----------------------- -----------------------
  `BEGINNER`              Fácil                   Fuerza limitada y
                                                  selección de
                                                  movimientos
                                                  imperfectos.

  `CASUAL`                Fácil-intermedio        Fuerza limitada, con
                                                  menos errores graves
                                                  que el perfil anterior.

  `INTERMEDIATE`          Intermedio              Búsqueda moderada y
                                                  selección más
                                                  consistente.

  `ADVANCED`              Difícil                 Mayor presupuesto de
                                                  búsqueda y selección
                                                  fuerte.

  `EXPERT`                Muy difícil             Mayor profundidad o
                                                  tiempo de búsqueda
                                                  dentro de los límites
                                                  del entorno.
  -----------------------------------------------------------------------

### Consideraciones

-   Limitar `Skill Level` y usar opciones como
    `UCI_LimitStrength`/`UCI_Elo` si la versión de Stockfish las admite.
-   Verificar las opciones disponibles en la versión exacta del motor;
    no asumir que todas las compilaciones las soportan.
-   En perfiles fáciles, se puede seleccionar entre varias jugadas
    candidatas con una probabilidad ponderada, pero siempre deben ser
    legales.
-   Evitar que un bot principiante haga movimientos ilegales o se quede
    bloqueado.
-   No asignar un Elo público a cada bot hasta calibrarlo mediante un
    conjunto de partidas contra jugadores o motores de referencia.
-   Registrar la versión y configuración del motor para reproducir
    incidencias.

Para un MVP, empezar con perfiles basados en parámetros de Stockfish y
añadir selección imperfecta solo si los niveles bajos no resultan
suficientemente diferenciados.

------------------------------------------------------------------------

## 8. Análisis al terminar la partida

### 8.1 Flujo de trabajo

``` text
Partida finalizada
       |
       v
Guardar estado final y PGN
       |
       v
Crear trabajo de análisis
       |
       v
Recorrer movimientos de la partida
       |
       v
Analizar posiciones y alternativas
       |
       v
Calcular pérdida de evaluación
       |
       v
Clasificar movimientos
       |
       v
Buscar mates desaprovechados
       |
       v
Guardar análisis y resumen
       |
       v
Mostrar revisión al usuario
```

### 8.2 Presupuesto de análisis

El análisis de cada posición puede consumir bastante CPU. Configurar
límites explícitos:

-   profundidad máxima o tiempo máximo por posición;
-   número de variantes principales (`MultiPV`);
-   tiempo máximo total por partida;
-   número máximo de análisis concurrentes;
-   límite de memoria y CPU del contenedor;
-   cancelación y recuperación de trabajos fallidos.

Para la primera versión, utilizar una profundidad/tiempo fijo por perfil
de análisis y probar con partidas de distinta duración. Es preferible
una experiencia consistente que un análisis muy profundo que bloquee la
aplicación.

### 8.3 Comparación de movimientos

Para cada jugada:

1.  Obtener la posición anterior.
2.  Analizarla para determinar la mejor continuación disponible.
3.  Comparar el movimiento elegido con la mejor alternativa.
4.  Analizar la posición resultante.
5.  Calcular la pérdida de evaluación con una convención de perspectiva
    consistente.
6.  Detectar cambios decisivos de evaluación, amenazas de mate y
    variantes principales.
7.  Asignar una etiqueta según reglas configurables.

La pérdida de evaluación (centipawn loss, CPL) no basta por sí sola para
clasificar correctamente todas las jugadas. Hay que considerar el signo
de la evaluación, las puntuaciones de mate, la posición del jugador y si
la partida ya estaba ganada o perdida.

### 8.4 Etiquetas iniciales

Propuesta de categorías para el MVP:

-   `BEST`: jugada considerada óptima o equivalente a la mejor.
-   `EXCELLENT`: jugada muy fuerte.
-   `GOOD`: jugada sólida.
-   `BOOK`: jugada identificada en una base de aperturas.
-   `INACCURACY`: pérdida pequeña de calidad.
-   `MISTAKE`: pérdida relevante.
-   `BLUNDER`: pérdida grande o giro decisivo.
-   `BRILLIANT`: etiqueta especial, basada en heurísticas adicionales.
-   `MISSED_WIN`: se desaprovecha una ventaja ganadora.
-   `MISSED_MATE`: se pierde una oportunidad de mate forzado.
-   `FORCED`: jugada prácticamente obligada, si se decide mostrar esta
    categoría.

Los umbrales de CPL y evaluación deben ser configurables y validados con
partidas reales. No conviene fijar números arbitrarios como verdades
universales. Las categorías pueden depender de la posición y no solo de
una cifra.

### 8.5 Qué significa «brillante»

Stockfish no devuelve una etiqueta universal `BRILLIANT`. Debe
implementarse como una heurística propia y conservadora. Por ejemplo,
exigir una combinación de:

-   movimiento difícil de encontrar;
-   sacrificio material o recurso táctico no obvio;
-   evaluación posterior que conserve o mejore la posición;
-   ausencia de una refutación sencilla;
-   beneficio significativo frente a alternativas naturales.

La clasificación debe evitar premiar cualquier sacrificio o movimiento
inesperado. Guardar una razón o evidencia interna de por qué se asignó
la etiqueta facilita depurarla.

### 8.6 «De libro»

Esta etiqueta no debe depender únicamente de Stockfish. Usar una base de
aperturas que permita consultar la posición y verificar si el movimiento
aparece en la teoría disponible.

Integración opcional:

-   consultar Lichess Opening Explorer;
-   cachear las respuestas;
-   gestionar límites de uso y errores de red;
-   no hacer que una caída del servicio impida jugar o analizar;
-   respetar los términos aplicables y evitar consultas excesivas.

Si no hay conexión o la posición no está cubierta, clasificar la jugada
con el resto de la lógica sin etiquetarla como `BOOK`.

------------------------------------------------------------------------

## 9. Detección de oportunidades de mate

Después de cada posición relevante del jugador, el análisis puede buscar
una continuación forzada de mate. Si el motor devuelve una puntuación de
mate, guardar:

-   si existía mate forzado;
-   distancia de mate indicada por el motor;
-   mejor movimiento de la variante;
-   línea de movimientos en UCI y SAN;
-   si el jugador eligió la continuación ganadora;
-   si el mate se perdió en el movimiento analizado.

### Límites importantes

-   La ausencia de un mate detectado no prueba que no exista mate; el
    motor puede necesitar más tiempo o profundidad.
-   La puntuación de mate depende del análisis y no debe confundirse con
    una garantía matemática fuera de las condiciones de búsqueda.
-   La interfaz debe decir «el motor encontró mate en N» o «posible mate
    detectado», según el grado de confirmación.
-   Diferenciar «mate en una desaprovechado» de una secuencia más larga
    de mate forzado.
-   No buscar todos los mates en todas las posiciones con presupuesto
    ilimitado: establecer límites y priorizar posiciones relevantes.

------------------------------------------------------------------------

## 10. Experiencia de usuario

### Pantalla de partida

Debe mostrar:

-   tablero;
-   piezas capturadas, si se desea;
-   turno actual;
-   nombre y nivel del bot;
-   botón de rendición;
-   historial de movimientos;
-   indicador de estado;
-   resultado final al terminar.

### Pantalla de análisis

Debe mostrar:

-   resultado de la partida;
-   resumen de errores y jugadas destacadas;
-   lista de movimientos con etiquetas;
-   tablero navegable por movimiento;
-   evaluación del motor;
-   mejor alternativa y variante principal;
-   resaltado de la jugada analizada;
-   oportunidad de mate, si se detectó.

No llenar el tablero de efectos. En esta versión, las animaciones se
reservan para la revisión posterior: por ejemplo, resaltar una jugada
brillante, señalar una oportunidad de mate o reproducir la variante
recomendada. El análisis no debe bloquear la navegación por otras
pantallas.

### Accesibilidad y usabilidad

-   Soportar ratón y, si es viable, movimiento por arrastre y selección
    de origen/destino.
-   Añadir etiquetas accesibles a las casillas y controles.
-   No depender solo del color para diferenciar errores.
-   Permitir revisar variantes sin alterar la partida original.
-   Mostrar un indicador claro cuando el análisis esté pendiente o haya
    fallado.

------------------------------------------------------------------------

## 11. Contenedores y Docker Compose

### Servicios previstos

``` text
docker-compose.yml
├── frontend
│   └── Angular compilado y servido por Nginx
├── backend
│   └── Spring Boot + integración UCI con Stockfish
└── mongodb
    └── Persistencia mediante volumen
```

En desarrollo se puede ejecutar Angular con su servidor de desarrollo y
habilitar recarga en caliente. En un entorno de demo o producción,
construir el frontend y servir los archivos estáticos con Nginx.

### Requisitos del entorno

-   Imágenes base con versiones fijadas.
-   Usuario no root cuando sea posible.
-   Health checks para backend y MongoDB.
-   Volumen persistente para MongoDB.
-   Variables de entorno para configuración.
-   Credenciales de MongoDB fuera del repositorio.
-   Red interna de Docker para la base de datos.
-   No exponer el puerto de MongoDB al host en el perfil de producción
    salvo necesidad explícita.
-   Límites de CPU y memoria para el motor y el backend.
-   `.dockerignore` para evitar copiar dependencias, secretos y archivos
    innecesarios.
-   Perfiles separados para desarrollo y demo.

### Configuración orientativa

Variables del backend:

``` text
SPRING_PROFILES_ACTIVE=docker
SPRING_DATA_MONGODB_URI=mongodb://...
CHESS_ENGINE_PATH=/opt/stockfish/stockfish
CHESS_ENGINE_THREADS=1
CHESS_ANALYSIS_MAX_CONCURRENT=1
CHESS_ANALYSIS_TIMEOUT_SECONDS=120
```

Los valores son ejemplos, no una configuración de producción definitiva.
La URI y las credenciales deben configurarse mediante variables seguras.
El nombre y ruta del binario Stockfish dependerán de la imagen y
arquitectura elegidas.

### Persistencia

Declarar un volumen Docker para los datos de MongoDB. La aplicación debe
poder reiniciarse sin perder partidas ya guardadas. Los trabajos de
análisis pendientes requieren una estrategia explícita: en memoria para
un prototipo, o persistencia duradera para evitar perderlos.

### Health checks

-   MongoDB: comprobar disponibilidad de la base de datos.
-   Backend: usar Spring Boot Actuator y un endpoint de readiness.
-   Frontend: comprobar que Nginx sirve la aplicación.
-   Motor: comprobar disponibilidad de un proceso UCI funcional antes de
    aceptar solicitudes que dependan de él.

------------------------------------------------------------------------

## 12. Seguridad y robustez

Aunque el MVP no tenga cuentas de usuario, conviene aplicar buenas
prácticas:

-   Validar todos los identificadores, movimientos y parámetros.
-   Limitar tamaño de peticiones y número de partidas activas.
-   Aplicar límites de tiempo y concurrencia al motor.
-   No aceptar comandos UCI arbitrarios desde el cliente.
-   No permitir que el usuario envíe rutas de archivos o parámetros de
    proceso.
-   Gestionar correctamente procesos bloqueados y cerrarlos al detener
    el backend.
-   Evitar inyección en consultas MongoDB.
-   No incluir credenciales en logs.
-   Manejar errores de motor, base de datos y API externa sin corromper
    la partida.
-   Añadir autenticación antes de publicar cuentas personales o datos
    privados.
-   Aplicar rate limiting en un despliegue público.
-   Documentar que la versión inicial no está diseñada para competir con
    un servicio de ajedrez de alto tráfico.

### Concurrencia del motor

Cada instancia de Stockfish debe tener un propietario claro. No se deben
enviar búsquedas simultáneas no coordinadas al mismo proceso UCI. Usar
un pool limitado de motores o una cola de trabajo. Para el MVP, un
límite bajo de concurrencia es preferible a saturar CPU y ralentizar las
partidas.

------------------------------------------------------------------------

## 13. Pruebas y calidad

### Backend

-   Pruebas unitarias de la máquina de estados de una partida.
-   Pruebas de movimientos legales e ilegales.
-   Casos de enroque, promoción, captura al paso, jaque, mate y tablas.
-   Pruebas de integración MongoDB.
-   Pruebas de integración con Stockfish mediante UCI.
-   Pruebas de análisis para posiciones de referencia.
-   Pruebas de clasificación con partidas conocidas y casos límite.
-   Pruebas de timeout, proceso muerto y análisis fallido.
-   Pruebas de concurrencia y peticiones duplicadas.

### Frontend

-   Pruebas de componentes del tablero.
-   Pruebas de creación de partida y envío de movimientos.
-   Pruebas de estados de carga y errores.
-   Pruebas de navegación del análisis.
-   Pruebas de resaltado de movimientos y variantes.
-   Pruebas de accesibilidad básicas.

### Integración de extremo a extremo

Escenarios esenciales:

1.  Crear partida con cada perfil.
2.  Completar una partida y verificar su resultado.
3.  Reiniciar backend y comprobar que la partida finalizada sigue
    almacenada.
4.  Analizar una partida finalizada.
5.  Navegar por todas las jugadas analizadas.
6.  Verificar que el análisis distingue una oportunidad de mate
    conocida.
7.  Simular un motor no disponible y confirmar que el error es
    controlado.
8.  Comprobar que el usuario no puede jugar dos movimientos seguidos
    cuando le toca al bot.

------------------------------------------------------------------------

## 14. Observabilidad y diagnóstico

Registrar, sin datos sensibles:

-   identificador de partida;
-   duración de la respuesta del bot;
-   duración total del análisis;
-   número de posiciones analizadas;
-   profundidad y presupuesto de búsqueda;
-   número de trabajos pendientes y fallidos;
-   errores y reinicios del motor;
-   latencia de endpoints.

Usar logs estructurados. Evitar registrar cada línea UCI en producción;
habilitar trazas detalladas solo durante depuración. Añadir métricas de
salud y uso de CPU para entender si el cuello de botella es el motor, la
base de datos o la API.

------------------------------------------------------------------------

## 15. Licencias y servicios externos

### Stockfish

Stockfish se distribuye bajo GPLv3. Antes de redistribuir una imagen o
binario que lo incluya, revisar las obligaciones de la licencia, los
avisos y la disponibilidad del código fuente correspondiente. La
licencia del motor no implica automáticamente que todo el resto de la
aplicación deba tener la misma licencia, pero la forma de integración y
distribución debe evaluarse con cuidado.

### Lichess

Lichess ofrece APIs públicas y datos abiertos, pero cada servicio o
conjunto de datos puede tener condiciones específicas. Revisar
documentación, límites, atribución y licencia de los datos usados. No
depender de la API para la lógica crítica de juego.

### Bibliotecas frontend y de reglas

Antes de seleccionar las bibliotecas definitivas:

-   confirmar que están mantenidas;
-   comprobar compatibilidad con Angular y Java elegidos;
-   revisar licencia;
-   fijar versión;
-   comprobar soporte para FEN, PGN, SAN, promoción, enroque, captura al
    paso y tablas.

------------------------------------------------------------------------

## 16. Plan de implementación por entregables

### Entregable 1 --- Esqueleto ejecutable

-   Repositorio con frontend Angular y backend Spring Boot.
-   Docker Compose con frontend, backend y MongoDB.
-   Endpoint de health check.
-   Documentación de cómo levantar y detener el entorno.

**Criterio de aceptación:** todo el entorno arranca con un comando
documentado y el backend conecta con MongoDB.

### Entregable 2 --- Partida básica

-   Tablero interactivo.
-   Reglas de ajedrez validadas en backend.
-   Creación de partida y movimientos.
-   Integración inicial de Stockfish.
-   Un perfil de bot.
-   Persistencia del estado.

**Criterio de aceptación:** se puede jugar una partida completa sin
corromper el estado.

### Entregable 3 --- Cinco niveles

-   Configuración de cinco perfiles.
-   Pruebas comparativas de fuerza y comportamiento.
-   Manejo de timeouts y fallos del motor.

**Criterio de aceptación:** los perfiles son diferenciables en pruebas,
sin afirmar un Elo exacto no validado.

### Entregable 4 --- Análisis posterior

-   Persistencia del PGN y posiciones.
-   Trabajo de análisis posterior.
-   Evaluaciones y variantes.
-   Clasificación inicial de jugadas.
-   Pantalla de revisión.

**Criterio de aceptación:** el usuario puede revisar cada movimiento y
ver una explicación básica.

### Entregable 5 --- Mates y etiquetas especiales

-   Detección de mates desaprovechados.
-   Etiqueta de libro mediante fuente de aperturas opcional.
-   Heurística conservadora para jugadas brillantes.
-   Animaciones en la pantalla de análisis.

**Criterio de aceptación:** las etiquetas se apoyan en datos del motor y
reglas documentadas; las oportunidades de mate incluyen una variante
verificable por el motor.

### Entregable 6 --- Estabilización

-   Pruebas de extremo a extremo.
-   Gestión de análisis fallidos.
-   Ajustes de rendimiento.
-   Documentación de arquitectura y operación.
-   Verificación de licencias y configuración de contenedores.

**Criterio de aceptación:** un usuario puede levantar el entorno, jugar,
finalizar y analizar partidas de manera repetible.

------------------------------------------------------------------------

## 17. Estructura de repositorio recomendada

Para mantener el MVP sencillo, empezaría con un monorepositorio:

``` text
chess-arena/
├── frontend/
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── backend/
│   ├── src/
│   │   ├── main/
│   │   └── test/
│   ├── Dockerfile
│   └── pom.xml
├── deployment/
│   ├── nginx/
│   └── docker-compose.yml
├── docs/
│   ├── architecture.md
│   └── decisions/
├── .env.example
├── .gitignore
└── README.md
```

Stockfish puede incorporarse en la imagen del backend siguiendo un
procedimiento reproducible, o montarse desde una ubicación local para
desarrollo. No se debe versionar un binario sin revisar la licencia,
arquitectura y proceso de actualización.

Separar en múltiples repositorios solo cuando haya una razón concreta,
por ejemplo equipos independientes o ciclos de despliegue distintos.

------------------------------------------------------------------------

## 18. Decisiones pendientes antes de programar

1.  **Biblioteca de reglas Java:** elegir una implementación mantenida
    que cubra las reglas oficiales necesarias.
2.  **Biblioteca de tablero Angular:** validar opciones de interfaz,
    accesibilidad y licencia.
3.  **Distribución de Stockfish:** decidir cómo obtener, fijar y
    empaquetar el binario para las arquitecturas objetivo.
4.  **Presupuesto de análisis:** medir el coste en CPU por partida y
    fijar límites iniciales.
5.  **Persistencia de trabajos:** decidir si basta una cola en memoria
    para la demo o si se necesita recuperación tras reinicios.
6.  **Usuarios:** para el primer MVP se puede permitir jugar sin cuenta;
    si se necesita historial persistente por persona, definir
    autenticación y propiedad de partidas.
7.  **Criterios de clasificación:** documentar y probar las reglas de
    `BRILLIANT`, `BOOK`, `MISTAKE`, `BLUNDER` y `MISSED_MATE`.
8.  **Licencias:** revisar las licencias de Stockfish, bibliotecas y
    datos antes de distribuir la aplicación.

------------------------------------------------------------------------

## 19. Recomendación final

La primera versión debe centrarse en una experiencia completa, no en la
cantidad de funcionalidades:

**Crear partida → jugar contra un bot → finalizar → analizar →
comprender errores → revisar la variante mejor.**

Angular ofrece una base estructurada para la interfaz interactiva;
Spring Boot controla las partidas y coordina el motor; MongoDB conserva
partidas y análisis; Stockfish proporciona el juego y la evaluación;
Docker Compose facilita que cualquier desarrollador levante el entorno.

La decisión más importante es mantener el análisis como un proceso
posterior e independiente de la partida. Así se puede entregar una
experiencia de juego fluida y evolucionar la profundidad del análisis
sin rehacer el núcleo del producto.
