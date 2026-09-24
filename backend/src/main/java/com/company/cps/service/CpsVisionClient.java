package com.company.cps.service;

import com.company.cps.config.CpsVisionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B6 视觉点检判定客户端（Java → Python basic-project /api/v1/agent/room-checks/judge）：
 * - 60s 超时，不重试（视觉模型本身耗时长，交给异步模型）；
 * - 失败/超时 → 返回 null（service 层写 TIMEOUT_OPENED 流水，不抛异常）；
 * - 契约 body：{fingerprint, roomType, checkItemId, photoUrl, expectedEvidence}
 * - 契约响应：{overall: PASS|PARTIAL|PROBLEM, score: 0-100, reasons: [...]}。
 *
 * 本波仅留契约骨架（TODO：与 Python 端联调时再消费 reasons[]）。
 */
@Component
public class CpsVisionClient {

    private static final Logger log = LoggerFactory.getLogger(CpsVisionClient.class);

    /** 视觉判定结果：overall/score/reasons。 */
    public static class VisionJudgeResult {
        public final String overall;
        public final Integer score;
        public final List<String> reasons;

        public VisionJudgeResult(String overall, Integer score, List<String> reasons) {
            this.overall = overall;
            this.score = score;
            this.reasons = reasons == null ? new ArrayList<>() : reasons;
        }
    }

    private final CpsVisionProperties properties;
    private final RestTemplate client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CpsVisionClient(CpsVisionProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate（包级可见）。 */
    CpsVisionClient(CpsVisionProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.client = restTemplate;
    }

    /**
     * 调用视觉判定。
     * - enabled=false → null（关闭，service 层直接落 TIMEOUT）；
     * - 任何技术失败（连接/超时/非 200/不可解析）→ null + WARN 日志；
     * - 不抛异常。
     */
    public VisionJudgeResult judge(String fingerprint, String roomType, Long checkItemId,
                                   String photoUrl, List<String> expectedEvidence) {
        if (!properties.isEnabled()) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("fingerprint", fingerprint);
        payload.put("roomType", roomType);
        payload.put("checkItemId", checkItemId);
        payload.put("photoUrl", photoUrl);
        payload.put("expectedEvidence", expectedEvidence == null ? List.of() : expectedEvidence);

        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = client.postForObject(properties.getBaseUrl() + properties.getPath(),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception error) {
            log.warn("Vision judge call failed (degraded): fingerprint={} error={}",
                    fingerprint, error.getClass().getSimpleName());
            return null;
        }
        return parse(body);
    }

    /** 解析响应：overall=PASS|PARTIAL|PROBLEM；score=0-100；reasons=[…]。 */
    private VisionJudgeResult parse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception error) {
            log.warn("Vision judge response unparsable: {}", error.getMessage());
            return null;
        }
        String overall = root.path("overall").asText("");
        Integer score = root.path("score").isInt() ? root.path("score").asInt() : null;
        List<String> reasons = new ArrayList<>();
        JsonNode reasonsNode = root.path("reasons");
        if (reasonsNode.isArray()) {
            reasonsNode.forEach(n -> reasons.add(n.asText("")));
        }
        if (!"PASS".equalsIgnoreCase(overall)
                && !"PARTIAL".equalsIgnoreCase(overall)
                && !"PROBLEM".equalsIgnoreCase(overall)) {
            log.warn("Vision judge unknown overall: {}", overall);
            return null;
        }
        return new VisionJudgeResult(overall.toUpperCase(), score, reasons);
    }
}