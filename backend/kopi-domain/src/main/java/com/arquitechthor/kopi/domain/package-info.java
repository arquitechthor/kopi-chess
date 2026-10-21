/// The chess domain: board, positions, moves, rules and the `Game` aggregate.
///
/// This is the innermost ring of the hexagon. It is plain Java with **no
/// dependencies**: no Spring, no persistence, no other Kopi module. It never
/// produces human-readable text either; it emits codes (enums, records) that
/// the adapters translate.
package com.arquitechthor.kopi.domain;
