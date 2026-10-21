# ADR-011: i18n — el dominio emite códigos, los adaptadores traducen

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

La interfaz de Kopi Chess debe estar en inglés y español desde la primera
pantalla. Si los textos se dejan para el final, acaban incrustados en
plantillas, excepciones y, lo que es peor, en el dominio («Jugada ilegal»
dentro de una excepción de `kopi-domain`). Traducirlos después obliga a tocar
el núcleo por un motivo de presentación.

## Decisión

- **El dominio y la aplicación nunca producen texto para personas.** Emiten
  códigos: enums (`BLUNDER`, `STALEMATE`), records y excepciones tipadas
  (`IllegalMove`). Un `MessageSource` jamás entra en esos anillos.
- **La traducción vive en los adaptadores de entrada** que hablan con
  personas:
  - `kopi-adapter-web`: plantillas Thymeleaf con `#{clave}` y archivos
    `i18n/web/messages.properties` (inglés, por defecto) y
    `messages_es.properties`.
  - `kopi-adapter-rest` (fase 3): sus propios archivos para los `title` y
    `detail` de `ProblemDetail`, además de un `code` estable.
- **`kopi-bootstrap` declara los *bundles*** (`spring.messages.basename`) y
  desactiva `fallback-to-system-locale`, para que un idioma no soportado caiga
  en inglés y no en el idioma del sistema operativo del servidor.
- **Elección de idioma** (adaptador web):
  - Primera visita: el `Accept-Language` del navegador, respetando sus
    prioridades.
  - Después: `?lang=es` / `?lang=en` y una cookie `kopi-lang` de un año.
  - Solo se guardan idiomas soportados; cualquier otro se convierte en inglés.
- **Pruebas:** una prueba falla si una clave existe en un idioma y no en el
  otro, o si alguna traducción está vacía.

## Consecuencias

- Añadir un idioma es añadir archivos `messages_xx.properties` y un valor al
  enum `UiLanguage`, sin tocar el dominio.
- Los mensajes de error del dominio se diseñan como datos (código +
  parámetros), lo que además los hace fáciles de probar.
- Hay que traducir cada texto nuevo en el mismo PR (forma parte de la
  definición de terminado).
- Pendiente: una prueba que detecte texto literal sin `#{...}` en las
  plantillas, cuando haya más pantallas (fase 3).
