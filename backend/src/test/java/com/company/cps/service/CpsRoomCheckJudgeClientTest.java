package com.company.cps.service;

import com.company.cps.config.CpsRoomCheckJudgeProperties;
import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * C-04 判定客户端（波次7 J线对齐后）：单 item snake_case 契约 + photo_object_keys（清单④）
 * + 非 200 一律该 item 降级 PENDING 不抛异常（清单③）。
 */
class CpsRoomCheckJudgeClientTest {

    private static final String URL = "http://judge.example/api/v1/agent/room-checks/judge";

    private CpsRoomCheckJudgeProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private CpsRoomCheckJudgeClient client;

    @BeforeEach
    void setUp() throws Exception {
        properties = new CpsRoomCheckJudgeProperties();
        properties.setBaseUrl("http://judge.example");
        properties.setPath("/api/v1/agent/room-checks/judge");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new CpsRoomCheckJudgeClient(properties, restTemplate);
    }

    private CpsRoomCheckRecord record() {
        CpsRoomCheckRecord record = new CpsRoomCheckRecord();
        record.setId(9L);
        record.setRoomCode("R-01");
        record.setRoomName("配电间");
        record.setCheckEmpNo("E001");
        return record;
    }

    private CpsRoomCheckRecordItem item(long itemId, String code) {
        CpsRoomCheckRecordItem item = new CpsRoomCheckRecordItem();
        item.setCheckItemId(itemId);
        item.setItemCode(code);
        item.setContent("无积水");
        item.setPhotoCategory("地面");
        item.setDeductScore(10);
        item.setConfigVersion(3);
        item.setPhotoObjectKey("cps/room-check/x-" + code + ".jpg");
        return item;
    }

    @Test
    void postsPerItemSnakeCasePayloadWithObjectKeys() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"submission_id\":\"9\",\"item_id\":\"1\",\"attempt\":2,"
                        + "\"item_content\":\"无积水\",\"item_type\":\"地面\","
                        + "\"photo_object_keys\":[\"cps/room-check/x-C1.jpg\"],\"deduction\":10,"
                        + "\"config_version\":\"3\",\"room_name\":\"R-01-配电间\","
                        + "\"idempotency_key\":\"room-judge-9-1-2\"}"))
                .andRespond(withSuccess(
                        "{\"status\":\"JUDGED\",\"verdict\":\"PASS\",\"reason\":\"地面干燥\"}",
                        MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 2);
        server.verify();
        assertEquals(1, judged.size());
        assertEquals("PASS", judged.get(0).outcome);
        assertEquals("地面干燥", judged.get(0).reason);
    }

    @Test
    void parsesJudgedFailTypeMismatchAndUnjudgeable() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"JUDGED\",\"verdict\":\"FAIL\",\"reason\":\"地面有积水\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"TYPE_MISMATCH\",\"reason\":\"拍到桌面\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"UNJUDGEABLE\",\"reason\":\"光线不足\"}", MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged = client.judge(record(),
                Arrays.asList(item(1, "C1"), item(2, "C2"), item(3, "C3")), 1);
        assertEquals(3, judged.size());
        assertEquals("FAIL", judged.get(0).outcome);
        assertEquals("TYPE_MISMATCH", judged.get(1).outcome);
        assertEquals("UNJUDGEABLE", judged.get(2).outcome);
    }

    @Test
    void judgedWithoutVerdictTreatedAsUnjudgeable() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"JUDGED\",\"reason\":\"verdict 丢失\"}", MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 1);
        assertEquals("UNJUDGEABLE", judged.get(0).outcome);
    }

    @Test
    void skippedDegradesItemToPending() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"SKIPPED\",\"reason\":\"model provider not configured\"}",
                MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 1);
        assertEquals("PENDING", judged.get(0).outcome);
        assertTrue(judged.get(0).reason.contains("model provider not configured"));
    }

    @Test
    void serverError502DegradesItemToPendingNotThrow() {
        server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                .body("{\"detail\":{\"error_code\":\"ROOM_JUDGE_MODEL_FAILED\"}}")
                .contentType(MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 1);
        assertEquals("PENDING", judged.get(0).outcome);
        assertTrue(judged.get(0).reason.startsWith("judge service call failed"));
    }

    @Test
    void connectionFailureDegradesItemToPending() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 1);
        assertEquals("PENDING", judged.get(0).outcome);
    }

    @Test
    void malformedBodyDegradesItemToPending() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1")), 1);
        assertEquals("PENDING", judged.get(0).outcome);
    }

    @Test
    void disabledReturnsNullWithoutHttpCall() {
        properties.setEnabled(false);
        assertNull(client.judge(record(), Arrays.asList(item(1, "C1")), 1));
        server.verify(); // 无期望 ⇒ 无调用
    }

    @Test
    void emptyItemsReturnsNull() {
        assertNull(client.judge(record(), List.of(), 1));
    }
}
