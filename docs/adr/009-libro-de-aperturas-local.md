# ADR-009: Libro de aperturas local (CC0), en memoria

- **Estado:** aceptado
- **Fecha:** 2026-10-26

## Contexto

El análisis necesita saber si una jugada sigue en la teoría (`BOOK`) y la
partida quiere un nombre de apertura. La propuesta original usaba la API
Opening Explorer de Lichess: red, límites de uso y condiciones que cambian,
para algo que cabe en unos cientos de kilobytes. El diseño (§2.5, §9)
eligió el dataset `lichess-org/chess-openings` (CC0) cargado en una tabla
`opening` de PostgreSQL, buscando por EPD.

Al implementarlo:

- La versión con `epd` (`dist/`) no está en el repositorio: se genera con
  un script de Python o sale de artefactos de CI que caducan.
- Son 3.865 aperturas que no cambian mientras la aplicación corre. Nadie
  consulta la tabla con SQL: las estadísticas por apertura agregan las
  columnas `opening_eco` / `opening_name` de cada partida, no el libro.

## Decisión

- **Dataset local**: los cinco TSV originales (`eco`, `name`, `pgn`),
  fijados a un *commit* con sus SHA-256, como recursos de
  `kopi-adapter-openings`. Sin red.
- **En memoria, no en una tabla**: el adaptador `LichessOpeningBook`
  reproduce cada línea con las reglas de Kopi y guarda la posición a la que
  llega en un mapa. Sin migración, sin datos que sincronizar entre versiones:
  el libro es el de los recursos con los que se compiló la aplicación.
- **La clave es `Position.zobristHash()`**, no un texto EPD: es la misma
  noción de «misma posición» que la regla de repetición (piezas, turno,
  enroques y captura al paso *utilizable*, sin relojes), que es justo lo que
  el EPD de Lichess representa. Como Kopi calcula la clave tanto al cargar
  como al buscar, no hay dos normalizaciones que puedan discrepar.
- Atribución en `NOTICE` aunque CC0 no la exija.

## Consecuencias

- Las transposiciones se detectan solas (la clave es la posición).
- Cargar el libro reproduce unas 38.000 jugadas: alrededor de un segundo con
  la JVM en frío. Se crea una sola vez.
- Cada línea del dataset se valida con las reglas de Kopi cada vez que se
  carga: si una versión futura trae una línea ilegal, falla la prueba, no la
  partida de un usuario.
- Una colisión de Zobrist (64 bits) daría un nombre de apertura equivocado
  a una posición; con ~3.900 claves la probabilidad por búsqueda es del
  orden de 10⁻¹⁶.
- Actualizar el dataset es copiar los cinco ficheros de un *commit* nuevo y
  ajustar las sumas y el número de aperturas esperado en la prueba.
- La tabla `opening` sale del esquema (§6.1) y de la migración `V3`.
