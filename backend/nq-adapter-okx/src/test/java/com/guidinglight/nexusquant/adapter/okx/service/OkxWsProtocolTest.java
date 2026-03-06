package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * OkxWsProtocolTest 覆盖 PR-W1/W2 的协议构造与解析基础能力。
 */
class OkxWsProtocolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 验证 subscribe 报文包含 channel 与 instType。
     */
    @Test
    void shouldBuildSubscribePayload() {
        String payload = OkxWsProtocol.buildSubscribeMessage(
                MAPPER,
                List.of(new OkxWsSubscription("orders", "SPOT"), new OkxWsSubscription("account", null))
        );

        assertTrue(payload.contains("\"op\":\"subscribe\""));
        assertTrue(payload.contains("\"channel\":\"orders\""));
        assertTrue(payload.contains("\"instType\":\"SPOT\""));
        assertTrue(payload.contains("\"channel\":\"account\""));
    }

    /**
     * 验证 login 报文会携带签名字段。
     */
    @Test
    void shouldBuildLoginPayloadWithSign() {
        String payload = OkxWsProtocol.buildLoginMessage(
                MAPPER,
                new OkxApiCredentials("api-key", "secret-key", "passphrase"),
                new OkxRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-05T00:00:00Z"), ZoneOffset.UTC)
        );

        assertTrue(payload.contains("\"op\":\"login\""));
        assertTrue(payload.contains("\"apiKey\":\"api-key\""));
        assertTrue(payload.contains("\"timestamp\":\"1772668800\""));
        assertTrue(payload.contains("\"sign\":\""));
    }

    /**
     * 验证消息解析可以识别 login/subscribe/pong。
     */
    @Test
    void shouldParseInboundMessageKinds() {
        OkxWsProtocol.ParsedMessage loginSuccess = OkxWsProtocol.parseInboundMessage(
                MAPPER,
                "{\"event\":\"login\",\"code\":\"0\",\"msg\":\"\"}"
        );
        OkxWsProtocol.ParsedMessage subscribeFailed = OkxWsProtocol.parseInboundMessage(
                MAPPER,
                "{\"event\":\"subscribe\",\"code\":\"60012\",\"msg\":\"error\",\"arg\":{\"channel\":\"orders\"}}"
        );
        OkxWsProtocol.ParsedMessage pong = OkxWsProtocol.parseInboundMessage(MAPPER, "pong");

        assertEquals(OkxWsProtocol.MessageKind.LOGIN_SUCCESS, loginSuccess.kind());
        assertEquals(OkxWsProtocol.MessageKind.SUBSCRIBE_FAILED, subscribeFailed.kind());
        assertEquals("orders", subscribeFailed.channel());
        assertEquals(OkxWsProtocol.MessageKind.PONG, pong.kind());
    }

    /**
     * 验证 W2 业务消息提取可以保留 channel 与 data 明细。
     */
    @Test
    void shouldExtractBusinessMessages() {
        List<OkxWsBusinessMessage> messages = OkxWsProtocol.extractBusinessMessages(
                MAPPER,
                "{\"arg\":{\"channel\":\"orders\"},\"data\":[{\"clOrdId\":\"abc\",\"ordId\":\"123\"}]}"
        );

        assertEquals(1, messages.size());
        OkxWsBusinessMessage message = messages.get(0);
        assertEquals("orders", message.channel());
        assertEquals(1, message.dataItems().size());
        assertEquals("abc", message.dataItems().get(0).path("clOrdId").asText());
    }

    /**
     * 验证重连退避计算符合指数增长与上限截断。
     */
    @Test
    void shouldComputeReconnectDelayWithCap() {
        assertEquals(1000L, OkxWsProtocol.reconnectDelayMs(1, 1000L, 30_000L));
        assertEquals(2000L, OkxWsProtocol.reconnectDelayMs(2, 1000L, 30_000L));
        assertEquals(30_000L, OkxWsProtocol.reconnectDelayMs(10, 1000L, 30_000L));
    }

    /**
     * 验证空/非法报文提取时不会抛异常。
     */
    @Test
    void shouldReturnEmptyWhenPayloadInvalid() {
        assertTrue(OkxWsProtocol.extractBusinessMessages(MAPPER, "").isEmpty());
        assertFalse(OkxWsProtocol.extractBusinessMessages(
                MAPPER,
                "{\"arg\":{\"channel\":\"orders\"},\"data\":{\"ordId\":\"1\"}}"
        ).isEmpty());
        assertTrue(OkxWsProtocol.extractBusinessMessages(MAPPER, "{invalid").isEmpty());
    }
}
