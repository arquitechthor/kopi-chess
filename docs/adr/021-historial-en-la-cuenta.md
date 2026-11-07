# ADR-021: Historial en la cuenta Kopi

- **Estado:** aceptado
- **Fecha:** 2026-11-07

## Contexto

Hasta la 1.1, el historial vive solo en cada PC ([ADR-016](016-solo-esta-maquina.md)).
Las partidas en línea pasan por el relé solo mientras se juegan
([ADR-020](020-rele-de-partidas.md)). Quien usa Kopi Chess en dos equipos
tiene dos historiales distintos, y la futura app Android
([plan](../plan-app-android.md)) no podría ver nada con el PC apagado.

El backend Kopi ya tiene lo necesario (`docs/protocolo-rele.md` §9): una
entrada por cuenta y partida, con una revisión para la concurrencia
optimista y lápidas para lo borrado. El backend solo guarda y ordena; no
mira dentro de las partidas.

## Decisión

- **Opcional y apagado por defecto.** Se enciende en la página de cuenta,
  con la cuenta Kopi con la que se entró. Si después entra otra cuenta,
  queda en pausa. Apagarlo no borra nada de la cuenta; hay un botón aparte
  para borrar todo el historial de la cuenta.
- **Qué viaja:** cada partida como datos (`GameRecord`): jugadores, posición
  inicial, jugadas con su hora, final y fechas; las etiquetas PGN de una
  importada. Sin posiciones derivadas ni análisis: el equipo que la recibe la
  **reconstruye jugada a jugada con el dominio**, como la base de datos, así
  que una partida que no cuadra nunca entra. El análisis se recalcula en cada
  equipo con su cola de siempre.
- **Qué no viaja:** una partida en línea en curso, que vive en el relé; sube
  cuando termina.
- **Sincronización** (`HistorySync`, en segundo plano cada pocos minutos y
  con «Sincronizar ahora»):
  1. Bajar los cambios desde el último cursor y aplicarlos.
  2. Subir los borrados: partidas que estaban en la cuenta y ya no están
     aquí.
  3. Subir lo nuevo y lo cambiado: la versión de la partida aquí es distinta
     de la última sincronizada.
- **Unir una partida cambiada en los dos lados:** las partidas solo crecen.
  - Si las jugadas de una son el principio de las de la otra, gana la más
    larga; con las mismas jugadas, la terminada.
  - Si divergen (la misma partida en curso jugada en dos equipos), se
    guardan las dos: la de la cuenta entra como partida nueva, y la de aquí
    sube sobre ella.
  - Un borrado gana a una versión anterior y pierde ante cambios hechos
    después de él.
- **Dónde se apunta el estado:** `history_sync` (el interruptor, la cuenta y
  el cursor) y `history_game` (por partida, la revisión de la cuenta y la
  versión sincronizada), migración V8. `history_game` no tiene clave ajena a
  `game`: una fila sin su partida es un borrado pendiente de subir.

## Consecuencias

- ADR-016 sigue en pie: la interfaz sigue siendo solo local, y la conexión
  con Kopi es saliente y voluntaria.
- La privacidad cambia: mientras esté encendido, las partidas se guardan en
  la cuenta Kopi. Los textos legales de `kopi-web` lo dicen, y borrar la
  cuenta borra también su historial.
- Si un equipo pasa más de 180 días sin sincronizar, las lápidas pueden haber
  caducado: sincroniza como la primera vez (une los dos lados), y una partida
  borrada en otro equipo puede volver.
- Una partida jugada a la vez en dos equipos acaba duplicada en lugar de
  perder jugadas. Es un caso raro, y el historial no pierde nada.
- La app Android no lo usa: desde el móvil no se ve el historial (decisión
  D2 de su plan, [ADR-022](022-movil-cliente-remoto-del-pc.md)).
