package com.company.cps.service;

import com.company.cps.config.CpsRoomCheckJudgeProperties;
import com.company.cps.domain.CpsInspectionRecord;
import com.company.cps.domain.CpsInspectionRecordItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 非 CPS 巡检提交的 AI 初审客户端（C-04 视觉判定）：
 * PASS/FAIL 采纳；未判定/服务异常/无照片/未启用 ⇒ PENDING（交人工，不伪造判定）。
 */
class CpsInspectionJudgeClientTest {

    private static final String URL = "http://judge.example/api/v1/agent/room-checks/judge";

    private CpsRoomCheckJudgeProperties properties;
    private MockRestServiceServer server;
    private CpsInspectionJudgeClient client;

    @BeforeEach
    void setUp() {
        properties = new CpsRoomCheckJudgeProperties();
        properties.setBaseUrl("http://judge.example");
        properties.setPath("/api/v1/agent/room-checks/judge");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new CpsInspectionJudgeClient(properties, restTemplate);
    }

    private CpsInspectionRecord record() {
        CpsInspectionRecord record = new CpsInspectionRecord();
        record.setId(77L);
        record.setTypeCode("LINE_PATROL");
        record.setAreaCode("A-01");
        record.setCheckEmpNo("E001");
        record.setReviewRound(1);
        return record;
    }

    private CpsInspectionRecordItem photoItem(long checkItemId, String objectKey) {
        CpsInspectionRecordItem item = new CpsInspectionRecordItem();
        item.setId(checkItemId + 100);
        item.setCheckItemId(checkItemId);
        item.setContent("现场照片-" + checkItemId);
        item.setCheckTypes("PHOTO");
        item.setPhotoObjectKey(objectKey);
        item.setDeductScore(10);
        return item;
    }

    @Test
    void judgedPassAndFailAreAdopted() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"photo_urls\":[\"http://127.0.0.1:9000/cps-attachments/cps/ok.jpg\"]")))
                .andRespond(withSuccess("{\"status\":\"JUDGED\",\"verdict\":\"PASS\",\"reason\":\"照片干净\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"JUDGED\",\"verdict\":\"FAIL\",\"reason\":\"地面有油污\"}",
                        MediaType.APPLICATION_JSON));

        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(
                        photoItem(1L, "http://127.0.0.1:9000/cps-attachments/cps/ok.jpg"),
                        photoItem(2L, "http://127.0.0.1:9000/cps-attachments/cps/bad.jpg")), 1);

        assertEquals(2, results.size());
        assertEquals("PASS", results.get(0).outcome);
        assertEquals("照片干净", results.get(0).reason);
        assertEquals("FAIL", results.get(1).outcome);
        server.verify();
    }

    @Test
    void photoUrlIsForwardedAsIs() {
        // 落库即 MinIO 绝对 URL → 原样放进 photo_urls，不再有 photo_object_keys 分支。
        String url = "http://127.0.0.1:9000/cps-attachments/cps/abs.jpg";
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"photo_urls\":[\"" + url + "\"]}", false))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("photo_object_keys"))))
                .andRespond(withSuccess("{\"status\":\"JUDGED\",\"verdict\":\"PASS\"}",
                        MediaType.APPLICATION_JSON));

        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(photoItem(6L, url)), 1);

        assertEquals("PASS", results.get(0).outcome);
        server.verify();
    }

    @Test
    void nonJudgedStatusDegradesToPending() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(photoItem(3L, "cps/pending.jpg")), 1);

        assertEquals(1, results.size());
        assertEquals("PENDING", results.get(0).outcome);
        assertTrue(results.get(0).reason.contains("PENDING"));
    }

    @Test
    void serverErrorDegradesToPendingWithoutThrowing() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(photoItem(4L, "cps/err.jpg")), 2);

        assertEquals("PENDING", results.get(0).outcome);
        assertTrue(results.get(0).reason.contains("judge call failed"));
    }

    @Test
    void itemWithoutPhotoIsNotSentToAi() {
        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(photoItem(5L, null)), 1);

        assertEquals("PENDING", results.get(0).outcome);
        assertTrue(results.get(0).reason.contains("no photo"));
        server.verify();
    }

    @Test
    void disabledServiceNeverCallsAi() {
        properties.setEnabled(false);

        List<CpsInspectionJudgeClient.ItemJudge> results = client.judge(record(),
                Arrays.asList(photoItem(6L, "cps/any.jpg")), 1);

        assertEquals("PENDING", results.get(0).outcome);
        assertTrue(results.get(0).reason.contains("disabled"));
        server.verify();
    }
}
