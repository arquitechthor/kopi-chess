# Architecture Decision Records

Decisiones de arquitectura de Kopi Chess. Cada ADR describe el contexto, la
decisión y sus consecuencias. Un ADR aceptado no se edita: si la decisión
cambia, se escribe uno nuevo que lo sustituye.

- [ADR-001](001-arquitectura-hexagonal-modulos-maven.md) — Arquitectura hexagonal con módulos Maven y ArchUnit
- [ADR-002](002-casos-de-uso-sin-spring.md) — Casos de uso sin dependencia de Spring
- [ADR-003](003-postgresql-en-lugar-de-mongodb.md) — PostgreSQL en lugar de MongoDB
- [ADR-004](004-reglas-propias-perft.md) — Reglas de ajedrez propias, verificadas con *perft*
- [ADR-011](011-i18n-dominio-emite-codigos.md) — i18n: el dominio emite códigos, los adaptadores traducen
- [ADR-012](012-politica-features-preview.md) — Política de *features preview* (solo en `kopi-labs`)

## Plantilla

```markdown
# ADR-NNN: Título

- **Estado:** propuesto | aceptado | sustituido por ADR-XXX
- **Fecha:** AAAA-MM-DD

## Contexto
Qué problema hay y qué restricciones existen.

## Decisión
Qué se decide, en una o dos frases.

## Consecuencias
Qué se gana, qué se pierde, qué queda pendiente.
```
