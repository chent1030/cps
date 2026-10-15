package com.company.cps.service;

import com.company.cps.config.CpsRoomCheckJudgeProperties;
import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * C-04 辅房点检同步判定客户端（Java→Python，POST {base}/api/v1/agent/room-checks/judge，波次7 J线联调对齐）。
 *
 * 契约（basic-project 波次4 B6C04 设计 §1 + C7 冻结 schema）：**单 item 一次调用**，snake_case：
 * {submission_id, item_id, attempt, item_content, item_type, photo_object_keys[1..8], deduction,
 *  config_version, room_name, idempotency_key=room-judge-{submissionId}-{itemId}-{attempt}}；
 * 200 响应 {status: TYPE_MISMATCH|JUDGED|UNJUDGEABLE|SKIPPED, verdict: PASS|FAIL, reason, evidence, ...}。
 * Python 侧自带 RustFS 取图，Java 不再回传 photoBase64（清单④：大图 payload 切 object_key 模式）。
 * 错误语义：403/422/502（detail.error_code）⇒ 该 item 按 PENDING 降级（清单③：非 200 一律降级不报错）。
 *
 * 降级约定：服务未启用 / 无明细 ⇒ 返回 null（整单降级）；单 item 调用失败 / SKIPPED ⇒ 该 item
 * outcome=PENDING（不伪造判定），上层不阻塞流程、由 admin rejudge 入口补判（清单⑤）。
 */
@Component
public class CpsRoomCheckJudgeClient {

    /** 单条判定结果（归一化后）：TYPE_MISMATCH/UNJUDGEABLE/PASS/FAIL，PENDING=该 item 暂无判定（降级）。 */
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
    public CpsRoomCheckJudgeClient(CpsRoomCheckJudgeProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate（包级可见）。 */
    CpsRoomCheckJudgeClient(CpsRoomCheckJudgeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.client = restTemplate;
    }

    /**
     * 同步判定整单明细（逐 item 调用 C-04）。服务未启用 / 无明细 ⇒ null（整单降级）；
     * 否则返回与入参一一对应的结果，单 item 技术失败 / SKIPPED ⇒ 该项 PENDING（降级待补判）。
     *
     * @param attempt 判定轮数（幂等键组成部分，submit/rejudge 各取新值，见 CpsRoomCheckService）
     */
    public List<ItemJudge> judge(CpsRoomCheckRecord record, List<CpsRoomCheckRecordItem> items, int attempt) {
        if (!properties.isEnabled() || items.isEmpty()) {
            return null;
        }
        String submissionId = String.valueOf(record.getId());
        List<ItemJudge> results = new ArrayList<>();
        for (CpsRoomCheckRecordItem item : items) {
            results.add(judgeItem(record, item, submissionId, attempt));
        }
        return results;
    }

    /** 单 item 判定：任何技术失败（连接/超时/非 200/不可解析）⇒ PENDING 降级，不抛异常。 */
    private ItemJudge judgeItem(CpsRoomCheckRecord record, CpsRoomCheckRecordItem item,
                                String submissionId, int attempt) {
        String itemId = String.valueOf(item.getCheckItemId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("submission_id", submissionId);
        payload.put("item_id", itemId);
        payload.put("attempt", attempt);
        payload.put("item_content", item.getContent());
        payload.put("item_type", item.getPhotoCategory());
        payload.put("photo_object_keys", item.getPhotoObjectKey() == null
                ? List.of() : List.of(item.getPhotoObjectKey()));
        payload.put("deduction", item.getDeductScore());
        payload.put("config_version", item.getConfigVersion() == null ? null : String.valueOf(item.getConfigVersion()));
        payload.put("room_name", record.getRoomCode() + "-" + record.getRoomName());
        payload.put("idempotency_key", "room-judge-" + submissionId + "-" + itemId + "-" + attempt);

        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = client.postForObject(properties.getBaseUrl() + properties.getPath(),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception error) {
            return new ItemJudge(item.getCheckItemId(), "PENDING",
                    "judge service call failed (degraded): " + error.getClass().getSimpleName());
        }
        return parse(body, item);
    }

    /** 200 响应解析：status=JUDGED 按 verdict 取 PASS/FAIL；SKIPPED / 不可解析 ⇒ PENDING（不伪造）。 */
    private ItemJudge parse(String body, CpsRoomCheckRecordItem item) {
        if (body == null || body.trim().isEmpty()) {
            return pending(item, "judge service returned empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception error) {
            return pending(item, "judge response unparsable");
        }
        String status = root.path("status").asText("");
        String reason = root.path("reason").asText(null);
        if ("JUDGED".equalsIgnoreCase(status)) {
            String verdict = root.path("verdict").asText("");
            if ("PASS".equalsIgnoreCase(verdict)) {
                return new ItemJudge(item.getCheckItemId(), "PASS", reason);
            }
            if ("FAIL".equalsIgnoreCase(verdict)) {
                return new ItemJudge(item.getCheckItemId(), "FAIL", reason);
            }
            return new ItemJudge(item.getCheckItemId(), "UNJUDGEABLE",
                    "verdict missing for JUDGED status (treated as unjudgeable): " + reason);
        }
        if ("TYPE_MISMATCH".equalsIgnoreCase(status)) {
            return new ItemJudge(item.getCheckItemId(), "TYPE_MISMATCH", reason);
        }
        if ("UNJUDGEABLE".equalsIgnoreCase(status)) {
            return new ItemJudge(item.getCheckItemId(), "UNJUDGEABLE", reason);
        }
        if ("SKIPPED".equalsIgnoreCase(status)) {
            return pending(item, "judge skipped by python side (model/provider/rustfs not configured): "
                    + root.path("reason").asText(""));
        }
        return pending(item, "unknown judge status: " + status);
    }

    private ItemJudge pending(CpsRoomCheckRecordItem item, String reason) {
        return new ItemJudge(item.getCheckItemId(), "PENDING", reason);
    }
}
