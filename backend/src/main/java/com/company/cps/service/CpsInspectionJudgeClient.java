package com.company.cps.service;

import com.company.cps.config.CpsRoomCheckJudgeProperties;
import com.company.cps.domain.CpsInspectionRecord;
import com.company.cps.domain.CpsInspectionRecordItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 非 CPS 巡检提交时的 AI 初审（C-04 视觉判定，Python /api/v1/agent/room-checks/judge）。
 *
 * <p>与 {@link CpsRoomCheckJudgeClient} 同一 Python 契约（单 item 一次调用、snake_case），
 * 区别只在主体是 {@code cps_inspection_record}（统一巡检记录）而非辅房点检记录。
 * 照片：{@code cps_inspection_record_item.photo_object_key} 落库的就是 MinIO 绝对 URL，
 * 原样放进 {@code photo_urls}，不再做对象键/URL 判定。
 * 扣分策略仍在 Java：判定 FAIL 的项按其 deduct_score 扣分，得分 = max(0, 100 - Σ扣分)。
 *
 * <p>降级约定：服务未启用 / item 无照片 / 调用失败 / 非 JUDGED ⇒ 该项 PENDING（不伪造判定），
 * 复核人看到「AI 未出结果」后按经验人工裁决，流程不阻塞。
 */
@Component
public class CpsInspectionJudgeClient {

    private static final Logger log = LoggerFactory.getLogger(CpsInspectionJudgeClient.class);

    /** 单条判定结果：PASS/FAIL/PENDING（PENDING=无结论，降级）。 */
    public static class ItemJudge {
        public final Long itemId;
        public final String outcome;
        public final String reason;

        public ItemJudge(Long itemId, String outcome, String reason) {
            this.itemId = itemId;
            this.outcome = outcome;
            this.reason = reason;
        }
    }

    private final CpsRoomCheckJudgeProperties properties;
    private final RestTemplate client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CpsInspectionJudgeClient(CpsRoomCheckJudgeProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate。 */
    CpsInspectionJudgeClient(CpsRoomCheckJudgeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.client = restTemplate;
    }

    /**
     * 逐项判定（attempt = reviewRound，作为幂等键组成部分）。
     *
     * @return 与入参一一对应的结果；服务未启用或服务不可用 ⇒ 每项 PENDING
     */
    public List<ItemJudge> judge(CpsInspectionRecord record, List<CpsInspectionRecordItem> items, int attempt) {
        List<ItemJudge> results = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }
        if (!properties.isEnabled()) {
            for (CpsInspectionRecordItem item : items) {
                results.add(new ItemJudge(item.getCheckItemId(), "PENDING", "judge service disabled"));
            }
            return results;
        }
        for (CpsInspectionRecordItem item : items) {
            results.add(judgeItem(record, item, attempt));
        }
        return results;
    }

    private ItemJudge judgeItem(CpsInspectionRecord record, CpsInspectionRecordItem item, int attempt) {
        if (item.getPhotoObjectKey() == null || item.getPhotoObjectKey().trim().isEmpty()) {
            // 无回传照片的项 C-04 无从判定，交人工
            return new ItemJudge(item.getCheckItemId(), "PENDING", "no photo for AI judge");
        }
        String submissionId = String.valueOf(record.getId());
        String itemId = String.valueOf(item.getCheckItemId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("submission_id", submissionId);
        payload.put("item_id", itemId);
        payload.put("attempt", attempt);
        payload.put("item_content", item.getContent());
        payload.put("item_type", item.getCheckTypes() == null ? "PHOTO" : item.getCheckTypes());
        // 落库即完整图片地址（MinIO 绝对 URL），直接作为 photo_urls 下发。
        payload.put("photo_urls", List.of(item.getPhotoObjectKey().trim()));
        payload.put("deduction", item.getDeductScore());
        payload.put("config_version", item.getConfigVersion() == null ? null : String.valueOf(item.getConfigVersion()));
        payload.put("room_name", nameOf(record));
        payload.put("idempotency_key", "insp-judge-" + submissionId + "-" + itemId + "-" + attempt);

        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = client.postForObject(properties.getBaseUrl() + properties.getPath(),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception error) {
            log.warn("Inspection AI judge degraded: recordId={} itemId={} cause={}",
                    record.getId(), item.getCheckItemId(), error.getClass().getSimpleName());
            return new ItemJudge(item.getCheckItemId(), "PENDING", "judge call failed");
        }
        return parse(body, item);
    }

    private ItemJudge parse(String body, CpsInspectionRecordItem item) {
        if (body == null || body.trim().isEmpty()) {
            return new ItemJudge(item.getCheckItemId(), "PENDING", "empty judge response");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText("");
            if (!"JUDGED".equals(status)) {
                return new ItemJudge(item.getCheckItemId(), "PENDING",
                        "judge status=" + (status.isEmpty() ? "UNKNOWN" : status));
            }
            String verdict = root.path("verdict").asText("");
            String reason = root.path("reason").asText("");
            if ("PASS".equals(verdict) || "FAIL".equals(verdict)) {
                return new ItemJudge(item.getCheckItemId(), verdict, reason);
            }
            return new ItemJudge(item.getCheckItemId(), "PENDING", "judge verdict=" + verdict);
        } catch (Exception error) {
            return new ItemJudge(item.getCheckItemId(), "PENDING", "unparsable judge response");
        }
    }

    private String nameOf(CpsInspectionRecord record) {
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("type", record.getTypeCode());
        parts.put("area", record.getAreaName() == null ? record.getAreaCode() : record.getAreaName());
        return String.valueOf(parts.get("type")) + "-" + String.valueOf(parts.get("area"));
    }
}
