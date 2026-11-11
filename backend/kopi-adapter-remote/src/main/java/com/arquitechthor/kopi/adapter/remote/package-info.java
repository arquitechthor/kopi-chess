/// Inbound adapter for the Android app (ADR-022): turns the requests of the
/// remote protocol (`docs/protocolo-remoto.md`) into use cases, only those
/// on the allow list, and their results into responses. The relay adapter
/// carries the messages; this one never talks to the network.
package com.arquitechthor.kopi.adapter.remote;
