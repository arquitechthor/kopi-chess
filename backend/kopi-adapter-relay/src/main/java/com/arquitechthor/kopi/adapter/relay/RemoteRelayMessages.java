package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The relay's side of the channel between devices (`docs/protocolo-rele.md`
/// §10.2): takes a `relay` message from the app, hands its `payload` to the
/// [RemoteRequestHandler] and wraps the answer in a `relay` back to the
/// sender, with the same `id`.
///
/// It never looks inside the payload, except to answer `SERVER_MODE_OFF`
/// (`docs/protocolo-remoto.md` §4) when server mode is off.
final class RemoteRelayMessages {

    /// The answer while server mode is off, in the remote protocol's shape.
    static final String SERVER_MODE_OFF =
            "{\"v\":1,\"ok\":false,\"error\":\"SERVER_MODE_OFF\",\"detail\":\"server mode is off\"}";

    private final JsonMapper json;
    private final RemoteRequestHandler handler;

    RemoteRelayMessages(JsonMapper json, RemoteRequestHandler handler) {
        this.json = Objects.requireNonNull(json, "json");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /// Whether `message` is a request from a device: one this class
    /// answers.
    static boolean isRequest(JsonNode message) {
        return "relay".equals(message.path("type").asString(""))
                && "device".equals(message.path("fromRole").asString(""))
                && message.path("from").isString()
                && message.path("payload").isObject();
    }

    /// The message to send back to the relay for a request, if `message`
    /// is one.
    ///
    /// @param serving whether server mode is on now
    Optional<String> answer(JsonNode message, boolean serving) {
        if (!isRequest(message)) {
            return Optional.empty();
        }
        var from = message.path("from").stringValue();
        var payload = serving ? handler.handle(from, json.writeValueAsString(message.path("payload")))
                : SERVER_MODE_OFF;
        var reply = json.createObjectNode().put("type", "relay").put("to", from);
        var id = message.path("id");
        if (id.isString()) {
            reply.put("id", id.stringValue());
        }
        reply.set("payload", object(payload));
        return Optional.of(json.writeValueAsString(reply));
    }

    /// The handler's answer as JSON; a broken one (a bug) becomes
    /// `INTERNAL`, so the app is never left waiting.
    private JsonNode object(String payload) {
        try {
            var node = json.readTree(payload);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JacksonException _) {
            // falls through
        }
        return json.readTree("{\"v\":1,\"ok\":false,\"error\":\"INTERNAL\",\"detail\":\"internal error\"}");
    }
}
