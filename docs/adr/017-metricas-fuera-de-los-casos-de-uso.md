# ADR-017: Métricas fuera de los casos de uso: decoradores de puertos y adaptadores

- **Estado:** aceptado
- **Fecha:** 2026-10-29

## Contexto

El diseño (§14) pide cuatro métricas propias, además de las que Spring Boot
ya da (HTTP, JVM, *pool* de conexiones): cuánto tarda un bot en elegir su
jugada, cuánto tarda el análisis de una partida, cuántos análisis esperan en
la cola y cuántas veces se reinicia Stockfish.

Lo natural sería medir donde ocurre cada cosa: `Timer` alrededor de
`botPlayer.chooseMove(...)` en `PlayMove`, y otro en `RunAnalysisJob`. Pero
eso mete Micrometer en el anillo de aplicación, que hoy no conoce ninguna
biblioteca de infraestructura (ADR-002), y mezcla en cada caso de uso lo que
hace con cómo se observa.

## Decisión

Ni el dominio ni la aplicación usan Micrometer; `HexagonalArchitectureTest`
lo prohíbe (`io.micrometer..`). Cada métrica se mide en el anillo exterior,
donde ya se conoce el dato:

- **Decoradores de puertos**, puestos por `UseCaseConfiguration` en
  `kopi-bootstrap`: `TimedBotPlayer` envuelve el `BotPlayer`
  (`kopi.bot.move.duration{profile, outcome}`) y `TimedAnalysisRepository`
  envuelve el `AnalysisRepository` que recibe `RunAnalysisJob`
  (`kopi.analysis.duration`, con el `startedAt` y el `completedAt` que el
  análisis ya trae, así que solo cuenta los análisis terminados).
- **Un puerto que responde una pregunta de negocio**:
  `AnalysisJobQueue.countQueued()`, que un `Gauge` de `MetricsConfiguration`
  consulta cuando Prometheus pide las métricas (`kopi.analysis.queue.size`).
- **El adaptador que sabe algo que nadie más ve**: solo el `EnginePool` de
  Stockfish sabe cuándo tira un motor y arranca otro. Lo cuenta él y el
  adaptador lo publica (`kopi.engine.restarts{pool}`), igual que ya publica
  su `HealthIndicator`.

Se exponen en `/actuator/prometheus`. Prometheus y Grafana, con un
*dashboard* ya hecho, van en un perfil de Compose opcional (`metrics`).

## Consecuencias

- Los casos de uso no cambian y sus pruebas siguen sin saber nada de
  métricas. Medir algo nuevo de un puerto es escribir otro decorador.
- El puerto de la cola gana un método, `countQueued()`, que también
  implementan los dobles de prueba. Es una consulta que tiene sentido por sí
  misma, no un detalle de Micrometer.
- Un decorador solo ve lo que cruza el puerto: no puede medir pasos internos
  de un caso de uso (por ejemplo, el tiempo de cada posición dentro de un
  análisis). Si hiciera falta, se mediría en el adaptador del motor
  (`ChessEngine`), no dentro de `RunAnalysisJob`.
- `kopi.engine.restarts` cuenta motores descartados: cada uno se sustituye
  en el siguiente préstamo, así que coincide con los reinicios salvo un
  motor que muera justo antes de parar la aplicación.
- Prometheus llama a la aplicación por su nombre en la red de Compose,
  `app`, que se añade a `KOPI_ALLOWED_HOSTS` (ADR-016). Ningún navegador
  puede usar ese nombre, así que no abre la puerta al *DNS rebinding*.
