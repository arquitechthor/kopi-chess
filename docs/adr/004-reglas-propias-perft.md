# ADR-004: Reglas de ajedrez propias, verificadas con *perft*

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

En arquitectura hexagonal, el dominio es el corazón del sistema. Si las reglas
del ajedrez vinieran de una librería externa, `kopi-domain` quedaría casi
vacío: un envoltorio sobre el código de otros, sin nada que enseñar.

Las reglas del ajedrez, en cambio, son un dominio ideal: finito, bien
especificado y **verificable al 100 %**. *Perft* cuenta todas las posiciones
alcanzables a una profundidad dada, y existen valores publicados para
posiciones diseñadas para provocar los casos difíciles (enroques, capturas al
paso, promociones, clavadas). Un generador que da esos números exactos es
correcto.

## Decisión

- **Implementación propia** de las reglas en `kopi-domain`, sin
  dependencias.
- **Tipos públicos inmutables y legibles:**
  - `Square` es un record con índice 0–63 (`a1` = 0, `h8` = 63) y nombre
    algebraico.
  - `Piece` es un record `(Color, PieceType)` en lugar de un enum de 12
    constantes: se lee mejor y admite *record patterns*.
  - `Move` lleva un `MoveKind`, una *sealed interface* con seis records
    (`Quiet`, `Capture`, `DoublePawnPush`, `EnPassant`, `Castling`,
    `Promotion`). Aplicar una jugada es un `switch` exhaustivo: si aparece un
    tipo nuevo, el compilador obliga a tratarlo.
  - Las jugadas las produce el generador; el texto externo (UCI) se busca
    entre las jugadas legales, nunca se convierte en `Move` por sí solo.
- **Representación interna del tablero: 0x88.** Un array de 128 casillas
  donde la mitad derecha queda fuera del tablero. Detectar que un
  desplazamiento se sale es un único `(casilla & 0x88) != 0`. Es más legible
  que los *bitboards* y suficiente para validar jugadas y para bots de poca
  profundidad. Queda oculta detrás de `Square` y de la API de `Position`.
- **`Position` inmutable:** aplicar una jugada devuelve una posición nueva.
  Copiar 128 casillas es barato y elimina toda una familia de errores de
  *make/unmake*.
- **Verificación:**
  - *Perft* con las seis posiciones de referencia de la Chess Programming
    Wiki, más `perft divide` para depurar comparando con `go perft` de
    Stockfish.
  - Opcionalmente, una librería externa como **oráculo solo en pruebas**
    (comparación diferencial), nunca como dependencia de producción.

## Consecuencias

- El generador de jugadas es el trabajo más largo de la fase 2, pero tiene una
  meta objetiva (los números de *perft*).
- 0x88 es más lento que los *bitboards*. Si algún día hace falta velocidad
  (bots más fuertes), se puede cambiar la representación interna con *perft*
  como red de seguridad, sin tocar la API pública.
- Plan B si el generador se atasca: un adaptador temporal sobre una librería,
  detrás del mismo contrato, para no bloquear las fases siguientes.
