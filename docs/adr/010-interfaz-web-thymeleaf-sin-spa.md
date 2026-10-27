# ADR-010: Interfaz web con Thymeleaf, sin SPA y sin htmx

- **Estado:** aceptado
- **Fecha:** 2026-10-27

## Contexto

El diseño (§2.13, §11) eligió renderizar la interfaz en el servidor con
Thymeleaf, como un adaptador de entrada más (`kopi-adapter-web`), en lugar de
un SPA: el objetivo del proyecto es aprender Java, Spring y arquitectura
hexagonal, y un SPA añade otro lenguaje, otro *toolchain* y otro contenedor.
Para las partes que cambian sin recargar (la jugada del bot, el progreso del
análisis) proponía **htmx** y su extensión SSE.

Este ADR quedó pendiente al empezar. Al construir la interfaz (fases 3 a 5)
htmx no llegó a hacer falta:

- **Fase 3:** la jugada es un formulario clásico con *Post/Redirect/Get*.
  Recargar nunca repite una jugada y el modo oficina funciona igual, sin
  JavaScript.
- **Fase 4:** para animar la respuesta del bot sin recargar, `board.js` envía
  la jugada con `fetch` a `…/live-moves`, que devuelve solo el fragmento
  `live` de la plantilla (`th:fragment`). Es lo que htmx habría hecho, en
  unas pocas líneas.
- **Fase 5:** el progreso del análisis llega con `EventSource`, nativo en el
  navegador, desde el endpoint SSE de la API REST; al terminar, `review.js`
  recarga la página. Añadir htmx para un solo flujo de eventos no compensaba.

## Decisión

La interfaz se renderiza en el servidor con **Thymeleaf**: formularios
clásicos con *Post/Redirect/Get* y **JavaScript propio y pequeño** (`fetch`
de fragmentos, `EventSource`) solo donde hace falta, más Chessground para el
tablero. **Sin SPA y sin htmx.**

## Consecuencias

- Una dependencia menos que fijar, descargar y declarar en `NOTICE`. Todo el
  JavaScript del proyecto es propio: cuatro archivos, unas 250 líneas, que se
  leen de una vez (`board.js`, `review.js`, `office.js`, `nav.js`).
- Si el `fetch` de una jugada falla, el formulario clásico la envía igual; y
  sin JavaScript se puede jugar en el modo oficina y seguir el análisis
  recargando la página.
- Cada nueva interacción parcial se escribe a mano. Si llegaran a ser muchas
  (por ejemplo, un análisis interactivo de variantes), htmx o un SPA sobre la
  API REST siguen siendo posibles sin tocar el núcleo; se decidiría en un
  ADR nuevo.
- Las reglas de §11.3 no cambian: ninguna regla de ajedrez en JavaScript y
  todo texto visible traducido desde las plantillas.
