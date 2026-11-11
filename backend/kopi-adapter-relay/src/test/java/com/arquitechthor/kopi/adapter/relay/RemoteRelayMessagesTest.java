package com.arquitechthor.kopi.adapter.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Wrapping the app's requests and the PC's answers in the relay's
/// messages (`docs/protocolo-rele.md` §10.2), without looking inside.
class RemoteRelayMessagesTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> handled = new ArrayList<>();
    private String answer = "{\"v\":1,\"ok\":true,\"name\":\"PC\"}";
    private final RemoteRelayMessages messages = new RemoteRelayMessages(json, (device, payload) -> {
        handled.add(device + " " + payload);
        return answer;
    });

    private JsonNode tree(String text) {
        return json.readTree(text);
    }

    private static final String REQUEST = """
            {"type": "relay", "from": "dev-1", "fromRole": "device", "fromName": "Móvil", "id": "q7",
             "payload": {"v": 1, "op": "hello", "lang": "es"}, "protocol": 1}""";

    @Test
    void aRequestIsHandedOverAndAnsweredToItsSenderWithTheSameId() {
        var reply = tree(messages.answer(tree(REQUEST), true).orElseThrow());

        assertThat(handled).containsExactly("dev-1 {\"v\":1,\"op\":\"hello\",\"lang\":\"es\"}");
        assertThat(reply.path("type").stringValue()).isEqualTo("relay");
        assertThat(reply.path("to").stringValue()).isEqualTo("dev-1");
        assertThat(reply.path("id").stringValue()).isEqualTo("q7");
        assertThat(reply.path("payload")).isEqualTo(tree(answer));
    }

    @Test
    void withServerModeOffTheAnswerIsServerModeOffAndNothingIsHandled() {
        var reply = tree(messages.answer(tree(REQUEST), false).orElseThrow());

        assertThat(handled).isEmpty();
        assertThat(reply.path("to").stringValue()).isEqualTo("dev-1");
        assertThat(reply.path("payload").path("ok").booleanValue()).isFalse();
        assertThat(reply.path("payload").path("error").stringValue()).isEqualTo("SERVER_MODE_OFF");
    }

    @Test
    void aBrokenAnswerBecomesInternal() {
        answer = "not json";

        var reply = tree(messages.answer(tree(REQUEST), true).orElseThrow());

        assertThat(reply.path("payload").path("error").stringValue()).isEqualTo("INTERNAL");
    }

    @Test
    void onlyRequestsFromADeviceAreAnswered() {
        assertThat(messages.answer(tree("{\"type\": \"game\", \"protocol\": 1}"), true)).isEmpty();
        assertThat(messages.answer(tree("{\"type\": \"pong\"}"), true)).isEmpty();
        assertThat(messages.answer(tree(REQUEST.replace("\"device\"", "\"server\"")), true)).isEmpty();
        assertThat(messages.answer(tree("{\"type\": \"relay\", \"from\": \"dev-1\", \"fromRole\": \"device\","
                + " \"id\": \"q1\", \"payload\": [1]}"), true)).isEmpty();
        assertThat(messages.answer(tree("{\"type\": \"relay\", \"fromRole\": \"device\", \"payload\": {}}"), true))
                .isEmpty();
        assertThat(handled).isEmpty();
    }

    @Test
    void aRequestWithoutIdIsAnsweredWithoutOne() {
        var reply = tree(messages.answer(tree(REQUEST.replace("\"id\": \"q7\",", "")), true).orElseThrow());

        assertThat(reply.has("id")).isFalse();
        assertThat(reply.path("to").stringValue()).isEqualTo("dev-1");
    }
}
