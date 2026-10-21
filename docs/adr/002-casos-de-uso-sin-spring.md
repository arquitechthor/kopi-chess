# ADR-002: Casos de uso sin dependencia de Spring

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

En muchas implementaciones «hexagonales» con Spring, los servicios de
aplicación llevan `@Service` y `@Transactional`. Es cómodo, pero acopla el
núcleo al *framework*: los casos de uso no pueden instanciarse ni probarse sin
Spring, y la transaccionalidad queda escondida en anotaciones.

## Decisión

El módulo `kopi-application` **no depende de Spring**:

- Los casos de uso son clases Java planas que reciben sus puertos por
  constructor.
- Se registran como *beans* en `kopi-bootstrap` mediante clases
  `@Configuration` explícitas.
- La transaccionalidad se aplica fuera del núcleo: con un decorador del caso
  de uso en `kopi-bootstrap` (p. ej. usando `TransactionTemplate`) o con un
  puerto `UnitOfWork` implementado por el adaptador de persistencia. La
  elección concreta se documentará en la fase 3.
- ArchUnit prohíbe importar `org.springframework..` desde los paquetes de
  dominio y aplicación.

## Consecuencias

- Los casos de uso se prueban con `new` y dobles de los puertos, sin contexto
  de Spring.
- El *wiring* es explícito y legible: se ve qué adaptador implementa cada
  puerto.
- Hay algo más de código en `kopi-bootstrap` (configuración y decoradores) del
  que exigiría usar `@Service`.
- Se renuncia al escaneo automático de componentes en el núcleo.
