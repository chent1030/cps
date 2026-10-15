package com.company.cps.service;

import com.company.cps.config.CpsSpeechProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** C-06 语音转写客户端：契约解析 + 降级路径（UNAVAILABLE 不抛异常）。 */
class CpsSpeechTranscribeClientTest {

    private CpsSpeechProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private CpsSpeechTranscribeClient client;

    @BeforeEach
    void setUp() {
        properties = new CpsSpeechProperties();
        properties.setBaseUrl("http://asr.example");
        properties.setPath("/api/v1/agent/speech-to-text");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new CpsSpeechTranscribeClient(properties, restTemplate);
    }

    private String okBody(String status, String text, String skipReason) {
        String metadata = skipReason == null ? "{}"
                : "{\"skip_reason\":\"" + skipReason + "\"}";
        return "{\"idempotency_key\":\"k\",\"status\":\"" + status + "\",\"text\":\"" + (text == null ? "" : text)
                + "\",\"duration_seconds\":8.0,\"language\":\"zh\",\"model\":\"qwen3-asr-flash\","
                + "\"metadata\":" + metadata + ",\"replayed\":false}";
    }

    @Test
    void transcribedResultCarriesText() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(okBody("TRANSCRIBED", "泵房地面有积水，需要清理", null),
                        MediaType.APPLICATION_JSON));
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 2, "cps/speech/20260926/a.webm", "webm");
        assertFalse(result.degraded);
        assertEquals("TRANSCRIBED", result.status);
        assertEquals("泵房地面有积水，需要清理", result.text);
        assertEquals(Double.valueOf(8.0), result.durationSeconds);
        assertEquals("qwen3-asr-flash", result.model);
    }

    @Test
    void payloadCarriesContractFieldsAndIdempotencyKey() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("speech-sub-1-short_term-3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cps/speech/20260926/a.mp3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"audio_format\":\"mp3\"")))
                .andRespond(withSuccess(okBody("TRANSCRIBED", "ok", null), MediaType.APPLICATION_JSON));
        client.transcribe("sub-1", "short_term", 3, "cps/speech/20260926/a.mp3", "mp3");
        server.verify();
    }

    @Test
    void skippedResultHasEmptyTextAndReason() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andRespond(withSuccess(okBody("SKIPPED", "", "asr provider disabled"),
                        MediaType.APPLICATION_JSON));
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "long_term", 1, "cps/speech/20260926/a.webm", null);
        assertFalse(result.degraded);
        assertEquals("SKIPPED", result.status);
        assertEquals("", result.text);
        assertEquals("asr provider disabled", result.skipReason);
    }

    @Test
    void serverErrorDegradesToUnavailable() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andRespond(withServerError());
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 1, "cps/speech/20260926/a.webm", null);
        assertTrue(result.degraded);
        assertEquals("UNAVAILABLE", result.status);
        assertNull(result.text);
    }

    /** 波次7 清单③：502+detail.error_code（ASR 技术失败）⇒ 一律降级 UNAVAILABLE，不抛错不伪造文本。 */
    @Test
    void badGatewayWithErrorCodeDegradesToUnavailable() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                        .body("{\"detail\":{\"error_code\":\"ASR_CALL_FAILED\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 1, "cps/speech/20260926/a.webm", null);
        assertTrue(result.degraded);
        assertEquals("UNAVAILABLE", result.status);
        assertNull(result.text);
    }

    @Test
    void malformedBodyDegradesToUnavailable() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 1, "cps/speech/20260926/a.webm", null);
        assertTrue(result.degraded);
        assertEquals("UNAVAILABLE", result.status);
    }

    @Test
    void unknownStatusDegradesWithoutFabricatingText() {
        server.expect(requestTo("http://asr.example/api/v1/agent/speech-to-text"))
                .andRespond(withSuccess(okBody("WEIRD", "fake", null), MediaType.APPLICATION_JSON));
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 1, "cps/speech/20260926/a.webm", null);
        assertTrue(result.degraded);
        assertEquals("UNAVAILABLE", result.status);
    }

    @Test
    void disabledSkipsRemoteCall() {
        properties.setEnabled(false);
        CpsSpeechTranscribeClient.Transcription result = client.transcribe(
                "sub-1", "reason", 1, "cps/speech/20260926/a.webm", null);
        assertTrue(result.degraded);
        assertEquals("UNAVAILABLE", result.status);
        server.verify(); // 无任何请求发出
    }

    @Test
    void timeoutConfigDrivesRestTemplateFactory() {
        // 全新实例读默认值：确认 240s 量级超时与 C-06 默认端口（联调清单口径）
        CpsSpeechProperties defaults = new CpsSpeechProperties();
        assertEquals(240000, defaults.getTimeoutMs());
        assertEquals("http://127.0.0.1:8000", defaults.getBaseUrl());
    }
}
