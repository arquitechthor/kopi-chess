# Kopi Chess

*Open source educational project in Chess and Code.*

[Read in English](README.md)

Kopi Chess es una aplicación web local para **jugar al ajedrez contra cinco
bots** y **revisar cada partida al terminar**. Cada jugada recibe una
evaluación, una clasificación (inexactitud / error / error grave) y la mejor
alternativa del motor, y la revisión señala los mates que se escaparon. También
puedes **importar un PGN** de Lichess, Chess.com o de una partida en tablero
físico y analizarlo igual.

También es un código del que aprender. El proyecto es un ejemplo de referencia
de:

- **Java moderno (25)**: records, *sealed interfaces*, *pattern matching*,
  hilos virtuales, *stream gatherers*, *scoped values*…
- **Spring Boot 4**
- **Arquitectura Hexagonal**: un dominio de ajedrez en Java puro (reglas
  verificadas con *perft*), casos de uso sin dependencias de *frameworks* y
  adaptadores para la interfaz web, la API REST, PostgreSQL y Stockfish.

La interfaz está disponible en **inglés y español**.

## Estado

🚧 **Planificación / desarrollo inicial.** Todavía no hay nada que ejecutar.

- Diseño técnico: [`docs/kopi-chess-diseno-tecnico.md`](docs/kopi-chess-diseno-tecnico.md)
- Plan de desarrollo: [`docs/kopi-chess-plan-desarrollo.md`](docs/kopi-chess-plan-desarrollo.md)
- Registros de decisiones de arquitectura (ADR): [`docs/adr/`](docs/adr/)

## Stack previsto

Java 25 · Spring Boot 4 · Maven · PostgreSQL · Flyway · Stockfish (UCI) ·
Thymeleaf + htmx · Chessground · Docker Compose · JUnit 5 · ArchUnit ·
Testcontainers

## Ejecución en local

Llegará en la fase 1: `docker compose up` y abrir `http://localhost:8080`.

## Contribuir

El código, los *commits*, los *issues* y las *pull requests* van en
**inglés**. Los documentos de diseño, en **español**. Las pautas de
contribución se añadirán antes de la v1.0.

## Licencia

[GPL-3.0](LICENSE). Kopi Chess usa Stockfish y Chessground, ambos GPL-3.0.
