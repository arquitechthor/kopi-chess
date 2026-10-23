# ADR-013: Modo oficina — notación localizada solo en el adaptador web

- **Estado:** aceptado
- **Fecha:** 2026-10-23

## Contexto

El modo oficina (diseño §11.5) muestra la partida como una tabla de letras y
acepta las jugadas escritas en notación algebraica **del idioma de la
interfaz**: `Cf3` y `e8=D` en español, `Nf3` y `e8=Q` en inglés. Hay dos
formas obvias, y malas, de hacerlo:

- Meter las letras de cada idioma en el dominio (`San.format(…, locale)`),
  rompiendo el ADR-011: el dominio no conoce idiomas.
- Escribir un segundo *parser* de SAN para cada idioma en el adaptador,
  duplicando reglas que ya resuelve `San`.

Además, la entrada debe ser tolerante (`E4`, `ed5`, `0-0`), y eso trae una
ambigüedad real: en español `A`, `C` y `D` son piezas y también columnas
(`axb5` puede ser peón o alfil); en inglés pasa con `B`.

## Decisión

- **El dominio no cambia.** Sigue produciendo SAN estándar con letras
  inglesas, y la aplicación ya entrega cada jugada legal con su SAN
  (`GameView.LegalMoveView`).
- **El adaptador web traduce letras** (`OfficeNotation`): un reemplazo
  carácter a carácter (`N→C`, `Q→D`, `R→T`, `B→A`, `K→R`) con las letras de
  los archivos de mensajes (`piece.letter.*`). Añadir un idioma no requiere
  código.
- **La entrada se resuelve contra las jugadas legales ya traducidas**
  (`OfficeMoveInput`), no se interpreta. Solo puede aceptarse una jugada
  legal, y no hace falta un segundo *parser*.
  1. Si el texto coincide **exactamente** (con mayúsculas) con una jugada,
     es esa.
  2. Si no, se compara de forma tolerante: sin mayúsculas, espacios, `x`,
     `=`, `+`/`#`, y con `0` como `O`. También se acepta UCI.
  3. Una coincidencia: se juega. Ninguna: se avisa y se conserva el texto.
     Varias: se pregunta «¿quisiste decir `axb5` / `Axb5`?».
- **Sin JavaScript:** formulario clásico con *Post/Redirect/Get*, sobre el
  mismo caso de uso `PlayMove` que el tablero.
- **Apariencia:** un portal interno **sin nombre de empresa**, para no
  imitar a ninguna organización real.

## Consecuencias

- El núcleo no sabe que existe el modo oficina: es otra presentación de los
  mismos casos de uso, la demostración que buscaba el diseño.
- La regla del punto 1 evita preguntar a quien escribe bien (`Axb5` es el
  alfil), lo que favorece aprender la notación correcta.
- La tolerancia es deliberadamente mayor que la de `San.parse` (que es para
  PGN): aquí escribe una persona aprendiendo.
- Si un idioma futuro usara letras que chocan con `O` de enroque o con
  `x`, habría que revisar la normalización tolerante.
