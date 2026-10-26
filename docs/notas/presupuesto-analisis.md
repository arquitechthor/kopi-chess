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
