# ADR-001: Arquitectura hexagonal con módulos Maven y ArchUnit

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

Kopi Chess es un proyecto educativo cuyo foco técnico es Java, Spring Boot y
Arquitectura Hexagonal. El dominio (las reglas del ajedrez, la clasificación de
jugadas) es rico y debe poder probarse sin infraestructura.

Una estructura «por capas» dentro de un único módulo (paquetes `api`,
`persistence`, `engine`…) solo separa por convención: nada impide que el
dominio importe Spring, JDBC o un adaptador. Con el tiempo, esas dependencias
aparecen y la arquitectura deja de enseñar lo que pretende.

## Decisión

El backend se organiza como un **proyecto Maven multi-módulo**, con un módulo
por anillo hexagonal:

- `kopi-domain`: Java puro, sin dependencias de compilación.
- `kopi-application`: casos de uso y puertos; solo depende del dominio.
- `kopi-adapter-*`: un módulo por adaptador (web, REST, persistencia,
  Stockfish, bots, aperturas); dependen de la aplicación, nunca entre sí.
- `kopi-bootstrap`: aplicación Spring Boot que conecta todo.
- `kopi-architecture-tests`: reglas **ArchUnit** que hacen fallar la build si
  se viola la regla de dependencias.

El paquete raíz es `com.arquitechthor.kopi`.

Las dependencias de Spring se gestionan importando el BOM de Spring Boot en el
POM padre, **sin** usar `spring-boot-starter-parent` como padre, para que el
dominio no herede nada de Spring.

## Consecuencias

- La regla de dependencias la verifica el compilador (dependencias Maven) y
  ArchUnit (paquetes), no la disciplina.
- El dominio se prueba en milisegundos, sin Spring ni base de datos.
- Hay más módulos y más POMs que mantener, y algo de código de *mapping* entre
  el dominio y cada adaptador. Es un coste aceptado: hacer visible esa
  frontera es parte del aprendizaje.
- Añadir un adaptador nuevo (otra base de datos, otra interfaz) no toca el
  dominio ni la aplicación.
