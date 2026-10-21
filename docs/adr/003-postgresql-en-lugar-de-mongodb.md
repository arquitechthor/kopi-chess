# ADR-003: PostgreSQL en lugar de MongoDB

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

La propuesta inicial («Chess Arena») elegía MongoDB con un documento por
partida. Los datos de Kopi Chess son partidas, sus jugadas y el análisis de
cada jugada, y las consultas útiles a futuro son agregaciones: resultados
contra cada bot, errores graves por apertura, partidas pendientes de análisis.

Además, el análisis posterior necesita una **cola de trabajos duradera** que
sobreviva a reinicios, y el proyecto se ejecuta en local con
`docker compose up`, así que cada pieza de infraestructura extra es un coste
para quien lo use.

## Decisión

Usar **PostgreSQL** (18.x, imagen oficial `alpine`) como única base de datos:

- Tablas relacionales para partidas y jugadas; columnas `JSONB` para los
  datos semiestructurados del análisis (variantes, resumen, configuración).
- La cola de análisis vive en una tabla y se consume con
  `FOR UPDATE SKIP LOCKED`, sin RabbitMQ ni Kafka.
- Esquema versionado con **Flyway** desde el primer arranque.
- Acceso con JDBC (`JdbcClient` / Spring Data JDBC), **sin JPA**: el
  *mapping* entre filas y agregados vive en `kopi-adapter-persistence` y el
  dominio no lleva anotaciones.
- Pruebas de integración contra un PostgreSQL real con **Testcontainers**.

## Consecuencias

- Transacciones, *joins*, consultas agregadas y la cola de trabajos, todo con
  una sola pieza de infraestructura.
- El «documento de partida» que justificaba MongoDB se cubre con `JSONB`
  donde de verdad hace falta flexibilidad.
- Hay que escribir el *mapping* a mano (sin JPA). Es un coste aceptado: hace
  visible la frontera entre el dominio y la persistencia.
- Como la persistencia es un adaptador, cambiar de base de datos sería un
  módulo nuevo, sin tocar el dominio ni los casos de uso.
- Las imágenes de PostgreSQL 18 guardan los datos en
  `/var/lib/postgresql/18/docker`; el volumen se monta en
  `/var/lib/postgresql`, no en el clásico `.../data`.
