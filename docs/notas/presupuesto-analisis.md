# Notas: presupuesto del análisis por posición

El diseño (§8.2) deja el límite de búsqueda del análisis «a medir»: `depth`
o `nodes`, empezando por `depth 16–18`. Medido el 2026-10-26 con los mismos
ajustes que usa `StockfishAnalysis`.

## Cómo se midió

- Stockfish 19 (`stockfish-windows-x86-64-universal.exe` de `tools/`),
  `Threads 1`, `Hash 64`, `MultiPV 2`, `ucinewgame` al empezar cada serie.
- Una partida real de 40 jugadas (Kasparov–Topalov, Wijk aan Zee 1999):
  **81 posiciones**, de la inicial a la final, una búsqueda por posición
  como hará el análisis (§8.1).
- Portátil de desarrollo, 8 núcleos lógicos (solo se usa uno).

## Resultados

| Límite | Total partida | Mediana | Máximo |
|---|---|---|---|
| `depth 14` | 15 s | 143 ms | 1,3 s |
| `depth 16` | 38 s | 362 ms | 2,0 s |
| `depth 18` | 200 s | 954 ms | **93 s** |
| `nodes 500000` | 45 s | 551 ms | 0,9 s |
| `nodes 1000000` | 87 s | 1,1 s | 1,8 s |
| `nodes 2000000` | 181 s | 2,3 s | 3,5 s |

## Conclusiones

- **La profundidad no acota el tiempo.** A `depth 18` una sola posición tardó
  93 s, más que el *timeout* por posición del adaptador (60 s): el análisis
  habría fallado. A `depth 16` el máximo fue 2 s, pero nada garantiza que
  otra partida no tenga su posición patológica.
- **Los nodos sí**: el tiempo es casi proporcional al límite (≈ 0,9 M
  nodos/s con un hilo), sin sorpresas, y el resultado es el mismo en
  cualquier máquina (solo cambia cuánto se tarda).
- Propuesta para el valor por defecto (se decide al conectar el caso de uso,
  §8.3): **`nodes 1000000`**, del orden de un minuto y medio por partida de
  40 jugadas en este portátil, con un máximo de unos 2 s por posición, muy
  lejos del *timeout*. `nodes 500000` si se prefiere rapidez.

## Máquina modesta (2026-10-28)

Fase 7: ¿aguanta `nodes 1000000` en un equipo con poca CPU?

### Cómo se midió

- La aplicación entera en Docker (`compose.yaml`, imagen local
  `kopi-chess:local`): Stockfish 19 de Linux, con los ajustes de
  `application.yml` (`nodes 1000000`, `MultiPV 2`, un *worker*).
- La misma partida de 40 jugadas, importada por la API
  (`POST /api/v1/games/import`), con el análisis pedido por la API. Tiempo
  medido en el servidor: de `startedAt` a `completedAt` del análisis.
- Máquina modesta simulada limitando el contenedor de la aplicación a medio
  núcleo (`docker update --cpus 0.5`), con `docker stats` cada segundo.
  Docker Desktop con WSL 2 en el mismo portátil (8 CPU y 8 GB para la VM).

### Resultados

| Contenedor | Análisis (40 jugadas) | CPU | Memoria de la app |
|---|---|---|---|
| sin límite | 91 s | ≈ 100 % (un núcleo) | ≈ 830 MiB |
| `--cpus 0.5` | 176 s | 50 % | ≈ 830 MiB |

Jugando contra la Dama (`movetime 1s`) con medio núcleo, 8 jugadas por
caso:

| Mientras | Respuesta del bot | Lecturas (`GET`) |
|---|---|---|
| no hay análisis | 1,03–1,09 s | ≤ 31 ms |
| hay un análisis en curso | 1,10–2,09 s (media 1,36 s) | ≤ 69 ms |

### Conclusiones

- **Se queda `nodes 1000000`.** Con medio núcleo el análisis tarda unos
  3 minutos, el doble que con uno entero, como corresponde a un límite en
  nodos. El tablero de revisión muestra el progreso mientras tanto (SSE).
  Por posición, extrapolando la tabla anterior, el máximo rondaría los 4 s,
  muy lejos del *timeout* de 60 s.
- **Se puede seguir jugando mientras se analiza.** El bot tarda hasta un
  segundo más en responder, y, como su límite es de tiempo y comparte la CPU
  con el análisis, en ese rato busca menos (juega algo más flojo; no medido).
- La memoria no depende de la CPU. No se ha probado con `--memory`.
- **Pendiente: el análisis no es del todo reproducible.** Los motores de
  análisis no reciben `ucinewgame` entre partidas, así que la tabla hash
  pasa de un análisis al siguiente. Dos análisis seguidos de la misma partida
  dieron resultados algo distintos (precisión de las blancas 89,8 frente a
  88,7, una jugada GOOD en un caso e INACCURACY en el otro). Con un hilo y un
  límite en nodos, la explicación más probable es esa hash. «El resultado es
  el mismo en cualquier máquina» solo vale con el motor recién empezado.
