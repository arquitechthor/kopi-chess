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

Requiere **Docker**.

```shell
docker compose up --build
```

Después comprueba `http://localhost:8080/actuator/health` (la interfaz de
ajedrez llega en las próximas fases). Si el puerto 8080 está ocupado, usa
otro: `KOPI_PORT=8090 docker compose up --build` (PowerShell:
`$env:KOPI_PORT=8090; docker compose up --build`).

Para parar: `docker compose down`; con `-v` también se borran las partidas
guardadas.

**Durante el desarrollo**, ejecuta en su lugar `TestKopiApplication` (en
`backend/kopi-bootstrap/src/test`) desde el IDE: levanta la app con un
PostgreSQL desechable mediante Testcontainers. Solo necesita Docker.

## Compilar desde el código fuente

Requiere **JDK 25**. No hace falta Maven: el repositorio incluye el *wrapper*.

```shell
cd backend
./mvnw verify        # Windows: mvnw.cmd verify
```

Si tu `JAVA_HOME` apunta a otro JDK, cámbialo solo para la terminal actual;
por ejemplo, en PowerShell: `$env:JAVA_HOME = "C:\ruta\al\jdk-25"`.

Si tu `~/.m2/settings.xml` redirige todo a un *mirror* privado no accesible,
compila con un archivo de *settings* vacío:
`./mvnw -s ruta/a/settings-vacio.xml verify`, donde el archivo solo contiene
`<settings/>`.

## Contribuir

El código, los *commits*, los *issues* y las *pull requests* van en
**inglés**. Los documentos de diseño, en **español**. Las pautas de
contribución se añadirán antes de la v1.0.

## Licencia

[GPL-3.0](LICENSE). Kopi Chess usa Stockfish y Chessground, ambos GPL-3.0.
