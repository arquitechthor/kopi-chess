# ADR-012: Política de *features preview* (solo en `kopi-labs`)

- **Estado:** aceptado
- **Fecha:** 2026-10-21

## Contexto

Uno de los objetivos de Kopi Chess es mostrar *features* recientes de Java.
Algunas de las más interesantes siguen en *preview* en Java 25: concurrencia
estructurada (JEP 505), *stable values* (JEP 502) y patrones con tipos
primitivos (JEP 507).

Las *features preview* pueden cambiar o desaparecer entre versiones, exigen
compilar y ejecutar con `--enable-preview`, y las clases compiladas así se
marcan en el *bytecode* (versión menor 65535): cualquier código que dependa
de ellas también necesita `--enable-preview`. Usarlas en la aplicación ataría
el proyecto a APIs inestables y complicaría cada actualización de JDK.

## Decisión

- **La aplicación usa solo *features* finales.** Ningún módulo del hexágono
  (dominio, aplicación, adaptadores, bootstrap) se compila con
  `--enable-preview`.
- **Las *preview* se muestran en `kopi-labs`**, un módulo Maven aparte:
  - compilado y probado con `--enable-preview`;
  - un subpaquete por *feature*, con pruebas que documentan su
    comportamiento;
  - ejemplos con sabor a ajedrez, para que se vea dónde encajarían en la
    aplicación;
  - fuera de la imagen Docker (la imagen solo construye `kopi-bootstrap` y
    sus dependencias).
- **Nadie depende de `kopi-labs`:** no figura en el `dependencyManagement`
  y una regla ArchUnit (`nothingDependsOnLabs`) rompe la build si alguna
  clase de fuera lo usa.
- **Cuando una *feature* pasa a final**, su ejemplo puede llevarse al código
  real y salir de `kopi-labs`.

## Consecuencias

- Se aprenden las *previews* con pruebas reales, sin riesgo para la
  aplicación.
- Actualizar el JDK puede romper `kopi-labs` (las APIs *preview* cambian).
  Es aceptable: se arregla o se elimina el ejemplo, y la aplicación no se ve
  afectada.
- Primer ejemplo: `structuredconcurrency.PositionBatchAnalyser`, que analiza
  varias posiciones en paralelo con `StructuredTaskScope`; si una falla,
  cancela las demás.
