package com.company.cps.service;

import com.company.cps.config.CpsSpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * C-06 语音转写客户端（Java→Python，POST {base}/api/v1/agent/speech-to-text，同步）。
 *
 * 契约（波次5 Python 侧设计 docs/波次5-Python侧-F1F2C06设计.md）：
 * - 入参：submission_id / field（D-03 白名单 reason|short_term|long_term）/ attempt≥1 /
 *   audio_object_key（RustFS 对象键，Python 内网取流）/ audio_format（缺省后缀推断再 wav）；
 *   幂等键惯例 speech-{submissionId}-{field}-{attempt}。
 * - 响应：status=TRANSCRIBED（text 有值）/ SKIPPED（text=""，metadata.skip_reason 说明原因）；
 *   技术失败 502（不缓存，同幂等键可重试）。
 *
 * 降级约定（F3）：未启用 / 连接失败 / 超时 / 非 2xx / 响应不可解析 ⇒ 返回 degraded 结果（不抛异常），
 * 上层返回降级文案，移动端允许重试或手工输入（PRD §20.2，转写不作点检证据）。
 */
@Component
public class CpsSpeechTranscribeClient {

    /** 转写结果（归一化后）。degraded=true 表示服务不可用，text 无效。 */
    public static class Transcription {
        public final boolean degraded;
        public final String status;        // TRANSCRIBED / SKIPPED / UNAVAILABLE(降级)
        public final String text;          // TRANSCRIBED 有值；SKIPPED 恒空串；降级为 null
        public final String skipReason;    // SKIPPED 的 metadata.skip_reason
        public final Double durationSeconds;
        public final String model;

        private Transcription(boolean degraded, String status, String text, String skipReason,
                              Double durationSeconds, String model) {
            this.degraded = degraded;
            this.status = status;
            this.text = text;
            this.skipReason = skipReason;
            this.durationSeconds = durationSeconds;
            this.model = model;
        }

        public static Transcription transcribed(String text, Double durationSeconds, String model) {
            return new Transcription(false, "TRANSCRIBED", text, null, durationSeconds, model);
        }

        public static Transcription skipped(String reason, Double durationSeconds, String model) {
            return new Transcription(false, "SKIPPED", "", reason, durationSeconds, model);
        }

        public static Transcription unavailable() {
            return new Transcription(true, "UNAVAILABLE", null, null, null, null);
        }
    }

    private final CpsSpeechProperties properties;
    private final RestTemplate client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CpsSpeechTranscribeClient(CpsSpeechProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate（包级可见）。 */
    CpsSpeechTranscribeClient(CpsSpeechProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.client = restTemplate;
    }

    /**
     * 同步转写。按 C-06 契约携带 audio_object_key（Python 内网取流，语音与照片同桶同鉴权）；
     * 幂等键 speech-{submissionId}-{field}-{attempt}。任何失败降级为 UNAVAILABLE，不抛异常。
     */
    public Transcription transcribe(String submissionId, String field, int attempt, String audioObjectKey,
                                     String audioFormat) {
        if (!properties.isEnabled()) {
            return Transcription.unavailable();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("submission_id", submissionId);
        payload.put("field", field);
        payload.put("attempt", attempt);
        payload.put("audio_object_key", audioObjectKey);
        if (audioFormat != null && !audioFormat.isBlank()) {
            payload.put("audio_format", audioFormat);
        }
        payload.put("idempotency_key", "speech-" + submissionId + "-" + field + "-" + attempt);

        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = client.postForObject(properties.getBaseUrl() + properties.getPath(),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception error) {
            return Transcription.unavailable(); // 未起/超时/网络异常/非 2xx ⇒ 降级
        }
        return parse(body);
    }

    /** 契约解析：status=TRANSCRIBED 取 text；SKIPPED 取 metadata.skip_reason；其余一律降级（不伪造文本）。 */
    Transcription parse(String body) {
        if (body == null || body.isBlank()) {
            return Transcription.unavailable();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText("");
            Double duration = root.path("duration_seconds").isNumber()
                    ? root.path("duration_seconds").asDouble() : null;
            String model = root.path("model").asText(null);
            if ("TRANSCRIBED".equals(status)) {
                String text = root.path("text").asText(null);
                if (text == null) {
                    return Transcription.unavailable();
                }
                return Transcription.transcribed(text, duration, model);
            }
            if ("SKIPPED".equals(status)) {
                String skipReason = root.path("metadata").path("skip_reason").asText(null);
                return Transcription.skipped(skipReason, duration, model);
            }
            return Transcription.unavailable();
        } catch (Exception error) {
            return Transcription.unavailable();
        }
    }
}
