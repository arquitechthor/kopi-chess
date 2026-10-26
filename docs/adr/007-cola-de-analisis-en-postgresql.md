# ADR-007: Cola de análisis en PostgreSQL

- **Estado:** aceptado
- **Fecha:** 2026-10-26

## Contexto

Analizar una partida lleva del orden de un minuto (una búsqueda por
posición, `docs/notas/presupuesto-analisis.md`): no cabe en la petición que
termina la partida. Hace falta trabajo **asíncrono y duradero**: si la
aplicación se para a mitad, el análisis no se puede perder ni quedarse
«en curso» para siempre. Lo habitual sería un *broker* (RabbitMQ, Kafka,
Redis), pero Kopi Chess ya tiene PostgreSQL, y una pieza más de
infraestructura es mucho para una cola de un trabajo por partida.

## Decisión

- **La cola es una tabla**, `analysis_job`, con un trabajo por partida
  (`game_id` único) y un estado `QUEUED → RUNNING → DONE | FAILED`.
- **Reclamar** es un único `update … returning` cuyo `select` interior usa
  **`for update skip locked`** (diseño §6.2): cada trabajo va a un solo
  *worker*, y un *worker* nunca espera a otro.
- **Encolar es idempotente** (`insert … on conflict (game_id)`): pedir otra
  vez un análisis en cola, en curso o hecho no cambia nada; uno `FAILED`
  vuelve a empezar.
- **Al arrancar**, los trabajos `RUNNING` con `locked_at` antiguo han perdido
  su *worker*: vuelven a `QUEUED`, o pasan a `FAILED` si ya agotaron los
  intentos, siempre con el motivo en `last_error`.
- **El estado vive en el trabajo; el resultado, en `analysis` y
  `move_analysis`.** El diseño original ponía también un `status` en
  `analysis`: dos fuentes de verdad para lo mismo. Guardar un resultado
  reemplaza el anterior en una transacción, así que repetir un trabajo que
  ya había guardado no hace daño.
- Los tiempos (`now`, «antiguo») los pasa quien llama, desde su `Clock`, no
  `now()` de la base de datos: las pruebas deciden qué es «ahora».

## Consecuencias

- Sin infraestructura nueva: lo mismo que ya se despliega con
  `docker compose up`, y los trabajos sobreviven a un reinicio.
- Se aprende un patrón real de producción (`SKIP LOCKED`), probado contra
  PostgreSQL de verdad: dos *workers* a la vez, y la recuperación tras un
  reinicio.
- No escala como un *broker* (sondeo en vez de avisos, una base de datos
  compartida), pero para un análisis cada vez está sobrado. Si hiciera
  falta, `LISTEN/NOTIFY` evitaría el sondeo sin salir de PostgreSQL.
- El progreso dentro de un análisis (posición *k* de *n*) no se guarda: se
  publica en vivo (§8.4). Un análisis interrumpido empieza de cero al
  reintentarse.
