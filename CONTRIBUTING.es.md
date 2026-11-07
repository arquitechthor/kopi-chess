# Contribuir a Kopi Chess

[Read in English](CONTRIBUTING.md)

Gracias por querer ayudar. Kopi Chess es un proyecto pequeño, pensado para
leerlo y aprender de él, así que un cambio claro y con buenos tests vale más
que uno grande.

**Idiomas.** El código, los comentarios, los *commits*, los *issues* y las
*pull requests* van en **inglés**. Los documentos de diseño de `docs/` están en
**español**. La interfaz existe en los dos.

## Antes de empezar

- **Arreglos pequeños** (una errata, un fallo evidente): abre la *pull request*
  directamente.
- **Cualquier cosa mayor** (una funcionalidad, una dependencia nueva, un cambio
  de diseño): abre antes un *issue*, para acordar el enfoque antes de que le
  dediques tiempo. Los *issues* con la etiqueta
  [`good first issue`](https://github.com/arquitechthor/kopi-chess/labels/good%20first%20issue)
  son una buena forma de empezar.

Para orientarte en el código, lee
[`docs/arquitectura.md`](docs/arquitectura.md): los módulos, en qué orden
leerlos y dos peticiones seguidas de principio a fin.

## Preparar el entorno

Necesitas **JDK 25** y **Docker** (los tests de integración usan
Testcontainers). No hace falta Maven: el repositorio trae el *wrapper*.

```shell
cd backend
./mvnw verify          # Windows: mvnw.cmd verify
```

- Para levantar la aplicación mientras desarrollas, arranca
  `TestKopiApplication` (en `backend/kopi-bootstrap/src/test`) desde tu IDE:
  levanta un PostgreSQL desechable.
- Los bots `BISHOP`, `ROOK` y `QUEEN`, el análisis y algunos tests necesitan
  **Stockfish 19**: descárgalo de <https://stockfishchess.org> y apunta
  `KOPI_STOCKFISH` al binario (o ten `stockfish` en el `PATH`). Git ignora
  `tools/`, un buen sitio para dejarlo. Sin él, los tests del adaptador de
  Stockfish se saltan, pero los tests de extremo a extremo de `kopi-bootstrap`
  (`GameApiIT`, `AnalysisIT`) fallan.
- Los tests lentos (*perft* profundo) y el test del navegador se ejecutan en la
  CI; en local, añade `-Pslow-tests`. El test del navegador,
  `PlayAndReviewE2EIT`, juega, abandona, revisa y borra una partida en Chromium
  con Playwright; solo él: `./mvnw -pl kopi-bootstrap -am verify -Pe2e`.
  Playwright descarga el navegador la primera vez y deja una traza de cada
  ejecución en `kopi-bootstrap/target/playwright/` (ábrela en
  <https://trace.playwright.dev>).

## Jugar en línea sin una cuenta real

El modo en línea habla con el backend Kopi: una cuenta Kopi (Cognito) y un
relé de partidas en AWS, cuyo contrato es [`docs/protocolo-rele.md`](docs/protocolo-rele.md)
(ADR-019, ADR-020). No necesitas ninguno de los dos para trabajar en él:

- **Todas las pruebas funcionan sin AWS.** El adaptador del relé se prueba
  contra un Cognito y un backend falsos en un puerto local (`FakeKopi`, el
  `HttpServer` del JDK), y las partidas en línea de extremo a extremo con dos
  Kopi Chess y un relé falso que aplica las reglas del protocolo
  (`TwoComputersTest`, con `FakeRelay`). Un cambio en el modo en línea debería
  traer su caso ahí.
- **Al arrancar la aplicación**, Kopi Chess arranca y juega contra los bots sin
  red: solo llama a Kopi cuando alguien entra con su cuenta o juega en línea.
- **Para probarlo de verdad** necesitas una cuenta Kopi aprobada, y Kopi Chess
  en `http://localhost:8080`: es la única dirección de vuelta registrada en la
  página de login de Kopi. Las direcciones están en `kopi.online.*`
  (`application.yml`); no son secretos (un cliente público con PKCE), y cada
  una se puede cambiar con una variable de entorno (`KOPI_ONLINE_API`…) para
  apuntar a otro backend.

## Cómo entra un cambio

1. **Una rama por cambio**, desde `main`: `feat/delete-game`,
   `fix/31-castling-rights` (con el número del *issue*, si lo hay),
   `docs/…`, `build/…`.
2. Los ***commits*** siguen [Conventional Commits](https://www.conventionalcommits.org):
   `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `build:`, `ci:`, con un
   ámbito opcional (`feat(web): …`). El asunto dice qué cambia para quien usa
   la aplicación; el cuerpo, por qué. [`CHANGELOG.md`](CHANGELOG.md) se genera
   a partir de esos asuntos, así que escríbelos pensando en quien lo lee. Van
   en inglés.
3. **Una *pull request*** a `main`, rellenando la plantilla. La CI tiene que
   estar en verde: compilación, todos los tests, las reglas de ArchUnit, la
   imagen de Docker y un análisis con
   [gitleaks](https://github.com/gitleaks/gitleaks) de todos los *commits*. El
   repositorio es público, así que un secreto nunca debe llegar a él, ni
   siquiera en un *commit* que otro posterior deshaga. Para pasar el análisis
   antes de hacer *push*:
   `docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo`.
4. Quien mantiene el proyecto la revisa y la fusiona con un ***merge commit***,
   así que tus *commits* se quedan tal cual: haz que cada uno tenga sentido por
   sí mismo.

## Definición de terminado

Un cambio está terminado cuando:

- `./mvnw verify` pasa en local y en la CI.
- Tiene tests al nivel adecuado: el dominio con tests unitarios, los casos de
  uso con puertos en memoria, un adaptador con tests de integración
  (PostgreSQL, Stockfish o MockMvc).
- Las reglas de ArchUnit siguen pasando: el dominio no depende de nada, los
  casos de uso solo del dominio y los adaptadores nunca unos de otros.
- No hay ningún `TODO` sin su *issue*.
- Todo texto nuevo que lee una persona existe en inglés y en español (los
  ficheros `messages*.properties`). Lo comprueban `MessageBundlesTest` y
  `TemplatesAreTranslatedTest`.
- Una decisión de diseño relevante tiene su ADR (ver más abajo).
- El README o la documentación lo reflejan, si cambia cómo se usa Kopi Chess.

## Escribir código

- **Sigue el código que lo rodea**: sus nombres, sus modismos y cuánto comenta.
  Los comentarios de documentación usan Markdown (`///`).
- **El dominio sigue siendo Java puro.** Nada de Spring, ni de persistencia, ni
  de nada fuera del propio `kopi-domain`.
- **Ninguna regla de ajedrez en JavaScript**: el navegador solo muestra el
  tablero y envía las jugadas; el servidor decide.
- **Las dependencias nuevas** necesitan un motivo en la *pull request* (y
  normalmente un *issue* antes).

## Escribir tests

- Nombres descriptivos, en inglés, que se lean como una frase:
  `blackCannotCastleKingsideThroughAttackedSquare()`.
- *Given / When / Then* en el cuerpo, separados por líneas en blanco.
- Las posiciones son siempre **FEN** literales, nunca construidas pieza a
  pieza.
- Los casos de uso se prueban con los puertos en memoria que ya están en las
  fuentes de test (`InMemoryGameRepository`, `ScriptedBot`…).

## Registros de decisiones de arquitectura

Una decisión que da forma al código (una librería, un modelo de datos, un
compromiso por el que alguien preguntará más adelante) tiene un registro breve
en `docs/adr/NNN-titulo.md`, en español, con esta forma:

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

## Versiones (quien mantiene el proyecto)

1. Una *pull request* que pone la versión en los `pom.xml` y regenera el
   *changelog* con git-cliff (`cliff.toml`; con Docker, sin instalar nada):

   ```shell
   docker run --rm -v "$PWD":/app -w /app orhunp/git-cliff:2.14.2 --tag vX.Y.Z -o CHANGELOG.md
   ```

2. Una vez fusionada, la etiqueta `vX.Y.Z` en `main`. `release.yml` comprueba
   que `CHANGELOG.md` tiene esa versión y publica la imagen en GHCR.
3. La *release* de GitHub, con lo más destacado en unas líneas; la lista
   completa es el *changelog*.

## Licencia

Kopi Chess es [GPL-3.0-or-later](LICENSE). Al contribuir, aceptas que tu
contribución se publique con la misma licencia.
