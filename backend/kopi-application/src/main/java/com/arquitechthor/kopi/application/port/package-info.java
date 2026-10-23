/// Outbound ports: what the use cases need from the outside world (storage,
/// bots, ids), as interfaces the adapters implement.
///
/// The clock is not a port of its own: use cases take a `java.time.Clock`,
/// the JDK's standard abstraction for "now" (`Clock.fixed` in tests).
package com.arquitechthor.kopi.application.port;
