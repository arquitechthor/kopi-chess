package com.arquitechthor.kopi.application.port;

/// Answers the requests of the Android app of the same Kopi account while
/// this computer is in "server mode" (ADR-022, `docs/protocolo-remoto.md`).
///
/// The relay carries the requests without looking inside; the remote
/// adapter implements this port and turns each request into a use case.
/// Requests and responses are the `payload` of the relay's messages: JSON
/// objects, kept as text so the relay's side need not know their shape.
public interface RemoteRequestHandler {

    /// The response payload to a request payload. Never throws: a request
    /// it cannot serve gets an error response (`"ok": false`).
    ///
    /// @param device  who sent it: the device's connection at the relay,
    ///                which tells requests from different phones apart
    /// @param payload the request, a JSON object as text
    /// @return the response, a JSON object as text
    String handle(String device, String payload);
}
