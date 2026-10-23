# Notas: calibración de los bots

Comprobación de la §7.3 del plan: cada bot debe ganar claramente al del nivel
anterior (**más del 70 % de los puntos**). No se publica ningún Elo: solo se
comprueba el orden.

## Cómo se ejecuta

```shell
cd backend
KOPI_STOCKFISH=/ruta/a/stockfish ./mvnw -pl kopi-bootstrap -am test -Pcalibration
```

`CalibrationTest` (en `kopi-bootstrap/src/test`, etiqueta `calibration`, fuera
de todos los builds normales y de la CI):

- usa los perfiles de Stockfish del propio `application.yml`, los mismos que
  la aplicación;
- cada pareja juega **8 aperturas** (abierta, siciliana, francesa,
  Caro-Kann, gambito de dama declinado, india de rey, inglesa y Réti), **dos
  veces cada una**, una con cada color: 16 partidas;
- una partida termina por las reglas (mate, ahogado, material insuficiente,
  triple repetición, 50 jugadas) o, como mucho, a las 300 medias jugadas
  (tablas);
- escribe la tabla en `kopi-bootstrap/target/calibration.md` y falla si algún
  nivel no llega al 70 %.

## Entorno

- **Fecha:** 2026-10-23
- **Motor:** Stockfish 19 (`stockfish-windows-x86-64-universal.exe`), 1 hilo,
  16 MB de *hash* (valores por defecto).
- **Máquina:** el portátil de desarrollo, Windows 11. Los tiempos por jugada
  son fijos (`movetime`), así que una máquina más rápida hace más fuertes a
  todos los bots de Stockfish a la vez; el orden debería mantenerse, pero
  conviene repetir la calibración si cambia mucho el hardware.

## Ronda 1 (descartada)

| Perfil | Configuración |
|---|---|
| `BISHOP` | `Skill Level` 3, 100 ms |
| `ROOK` | `UCI_LimitStrength`, `UCI_Elo` 1600, 300 ms |
| `QUEEN` | sin límite, 1 s |

| Más fuerte | Más débil | + | = | − | Puntos |
|---|---|---|---|---|---|
| KNIGHT | PAWN | 16 | 0 | 0 | 100 % |
| BISHOP | KNIGHT | 16 | 0 | 0 | 100 % |
| ROOK | BISHOP | 7 | 1 | 8 | **47 %** ❌ |
| QUEEN | ROOK | 16 | 0 | 0 | 100 % |

`Skill Level` y `UCI_Elo` son dos mecanismos de debilitamiento distintos y no
comparables: `Skill Level 3` resultó tan fuerte como `UCI_Elo 1600`. Además,
`BISHOP` aplastaba a `KNIGHT` (100 %): el salto del nivel 2 al 3 era excesivo.

## Ronda 2 (la configuración actual)

Los tres perfiles en la **misma escala** (`UCI_Elo`), bien separados:

| Perfil | Configuración |
|---|---|
| `BISHOP` | `UCI_LimitStrength`, `UCI_Elo` **1320** (el mínimo de Stockfish), 100 ms |
| `ROOK` | `UCI_LimitStrength`, `UCI_Elo` **2000**, 300 ms |
| `QUEEN` | sin límite, 1 s |

| Más fuerte | Más débil | + | = | − | Puntos | Tiempo |
|---|---|---|---|---|---|---|
| KNIGHT | PAWN | 16 | 0 | 0 | 100 % | 4 s |
| BISHOP | KNIGHT | 12 | 1 | 3 | 78 % ✅ | 51 s |
| ROOK | BISHOP | 16 | 0 | 0 | 100 % ✅ | 3 min 50 s |
| QUEEN | ROOK | 16 | 0 | 0 | 100 % ✅ | 9 min 46 s |

Cada nivel supera al anterior. El paso de `KNIGHT` a `BISHOP` es ahora el
más ajustado (78 %), que es lo esperable: `KNIGHT` es el bot propio más
fuerte y `BISHOP`, el Stockfish más débil posible.

## Limitaciones

- **16 partidas por pareja** son pocas para medir diferencias pequeñas; bastan
  para confirmar diferencias grandes como estas, que es lo que se pide. Un
  78 % con 16 partidas es compatible con una ventaja real algo menor.
- `QUEEN` gana a `ROOK` el 100 %: la distancia entre los niveles 4 y 5 es
  grande. Si se quisiera una progresión más suave, se podría subir `ROOK`
  (p. ej. `UCI_Elo` 2400) y volver a calibrar.
