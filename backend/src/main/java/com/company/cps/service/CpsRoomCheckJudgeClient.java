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
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C-04 辅房点检同步判定客户端（Java→Python，POST {base}/api/agent/room-checks/judge）。
 *
 * 契约（basic-project 后端架构文档）：Body=点检项（内容/类型/扣分/配置版本）+照片；
 * 返回 per-item 两阶段结果 TYPE_MISMATCH（须重拍）/ JUDGED(PASS|FAIL+理由) / UNJUDGEABLE（须补拍）；
 * 幂等键 room-judge-{submissionId}-{itemId}-{attempt}。
 *
 * 降级约定：服务未启用 / 连接失败 / 超时 / 非 2xx / 响应不可解析 ⇒ 返回 null（不抛异常），
 * 上层记 judge_result=PENDING、不阻塞提交流程（联调留 J 线）。
 */
@Component
public class CpsRoomCheckJudgeClient {

    /** 单条判定结果（归一化后）。 */
    public static class ItemJudge {
        public final Long itemId;
        public final String outcome;   // CpsRoomCheckJudgeOutcome 名：TYPE_MISMATCH/UNJUDGEABLE/PASS/FAIL
        public final String reason;

        public ItemJudge(Long itemId, String outcome, String reason) {
            this.itemId = itemId;
            this.outcome = outcome;
            this.reason = reason;
        }
    }

    private final CpsRoomCheckJudgeProperties properties;
    private final RustFsStorageService storage;
    private final RestTemplate client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private int attemptSeq = 0;

    @Autowired
    public CpsRoomCheckJudgeClient(CpsRoomCheckJudgeProperties properties, RustFsStorageService storage) {
        this.properties = properties;
        this.storage = storage;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate（包级可见）。 */
    CpsRoomCheckJudgeClient(CpsRoomCheckJudgeProperties properties, RustFsStorageService storage, RestTemplate restTemplate) {
        this.properties = properties;
        this.storage = storage;
        this.client = restTemplate;
    }

    /**
     * 同步判定整单明细。降级返回 null；成功返回 per-item 结果（与入参明细一一对应，缺失项按 UNJUDGEABLE 处理）。
     */
    public List<ItemJudge> judge(CpsRoomCheckRecord record, List<CpsRoomCheckRecordItem> items) {
        if (!properties.isEnabled() || items.isEmpty()) {
            return null;
        }
        String submissionId = String.valueOf(record.getId());
        int attempt = ++attemptSeq;
        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("attempt", attempt);
        payload.put("roomCode", record.getRoomCode());
        payload.put("roomName", record.getRoomName());
        payload.put("checkEmpNo", record.getCheckEmpNo());
        List<Map<String, Object>> itemPayloads = new ArrayList<>();
        for (CpsRoomCheckRecordItem item : items) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("itemId", item.getCheckItemId());
            p.put("itemCode", item.getItemCode());
            p.put("content", item.getContent());
            p.put("photoCategory", item.getPhotoCategory());
            p.put("deductScore", item.getDeductScore());
            p.put("configVersion", item.getConfigVersion());
            p.put("photoUrl", storage.publicObjectUrl(item.getPhotoObjectKey()));
            p.put("photoBase64", readPhotoBase64(item.getPhotoObjectKey()));
            itemPayloads.add(p);
        }
        payload.put("items", itemPayloads);

        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = client.postForObject(properties.getBaseUrl() + properties.getPath(),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception error) {
            return null; // 服务未起/超时/网络异常 ⇒ 降级 PENDING
        }
        return parse(body, items);
    }

    /** 响应不可解析 ⇒ null（降级）；可解析但缺某项 ⇒ 该项 UNJUDGEABLE（宁补拍不误判）。 */
    private List<ItemJudge> parse(String body, List<CpsRoomCheckRecordItem> items) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception error) {
            return null;
        }
        JsonNode results = root.path("results");
        if (!results.isArray() || results.size() == 0) {
            return null;
        }
        Map<Long, ItemJudge> byItemId = new HashMap<>();
        for (JsonNode node : results) {
            Long itemId = node.path("itemId").asLong(0);
            if (itemId == 0) {
                String code = node.path("itemCode").asText(null);
                itemId = items.stream()
                        .filter(i -> code != null && code.equals(i.getItemCode()))
                        .map(CpsRoomCheckRecordItem::getCheckItemId).findFirst().orElse(0L);
            }
            if (itemId == 0) {
                continue;
            }
            byItemId.put(itemId, new ItemJudge(itemId, normalize(node), node.path("reason").asText(null)));
        }
        List<ItemJudge> ordered = new ArrayList<>();
        for (CpsRoomCheckRecordItem item : items) {
            ItemJudge judged = byItemId.get(item.getCheckItemId());
            ordered.add(judged != null ? judged
                    : new ItemJudge(item.getCheckItemId(), "UNJUDGEABLE", "判定服务未返回该项结果，须补拍"));
        }
        return ordered;
    }

    /** 两阶段归一化：TYPE_MISMATCH / UNJUDGEABLE / PASS / FAIL；其余未知值按 UNJUDGEABLE。 */
    private String normalize(JsonNode node) {
        String judge = node.path("judge").asText("");
        if ("TYPE_MISMATCH".equalsIgnoreCase(judge)) {
            return "TYPE_MISMATCH";
        }
        if ("UNJUDGEABLE".equalsIgnoreCase(judge)) {
            return "UNJUDGEABLE";
        }
        String outcome = node.path("outcome").asText("");
        if ("PASS".equalsIgnoreCase(outcome)) {
            return "PASS";
        }
        if ("FAIL".equalsIgnoreCase(outcome)) {
            return "FAIL";
        }
        return "UNJUDGEABLE";
    }

    private String readPhotoBase64(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return null;
        }
        try {
            return Base64.getEncoder().encodeToString(storage.read(objectKey));
        } catch (Exception error) {
            return null; // 读不到照片交给 Python 侧按证据无效处理；Java 不在此处阻塞
        }
    }
}
