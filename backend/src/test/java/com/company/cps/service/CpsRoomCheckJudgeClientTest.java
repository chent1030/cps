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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** C-04 判定客户端：契约解析 + 降级路径（PENDING 不抛异常）。 */
class CpsRoomCheckJudgeClientTest {

    private CpsRoomCheckJudgeProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private RustFsStorageService storage;
    private CpsRoomCheckJudgeClient client;

    @BeforeEach
    void setUp() throws Exception {
        properties = new CpsRoomCheckJudgeProperties();
        properties.setBaseUrl("http://judge.example");
        properties.setPath("/api/agent/room-checks/judge");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        storage = mock(RustFsStorageService.class);
        when(storage.publicObjectUrl(anyString())).thenAnswer(inv -> "http://rustfs/" + inv.getArgument(0));
        when(storage.read(anyString())).thenAnswer(inv -> new byte[]{1, 2, 3});
        client = new CpsRoomCheckJudgeClient(properties, storage, restTemplate);
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
    void parsesTwoPhaseResultsPerItem() {
        server.expect(requestTo("http://judge.example/api/agent/room-checks/judge"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"results\":["
                                + "{\"itemId\":1,\"judge\":\"JUDGED\",\"outcome\":\"PASS\",\"reason\":\"ok\"},"
                                + "{\"itemId\":2,\"judge\":\"JUDGED\",\"outcome\":\"FAIL\",\"reason\":\"地面有积水\"},"
                                + "{\"itemId\":3,\"judge\":\"TYPE_MISMATCH\",\"reason\":\"拍到桌面\"}"
                                + "]}", MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1"), item(2, "C2"), item(3, "C3")));
        server.verify();
        assertEquals(3, judged.size());
        assertEquals("PASS", judged.get(0).outcome);
        assertEquals("FAIL", judged.get(1).outcome);
        assertEquals("地面有积水", judged.get(1).reason);
        assertEquals("TYPE_MISMATCH", judged.get(2).outcome);
    }

    @Test
    void missingItemResultFallsBackToUnjudgeable() {
        server.expect(requestTo("http://judge.example/api/agent/room-checks/judge"))
                .andRespond(withSuccess("{\"results\":[{\"itemId\":1,\"outcome\":\"PASS\"}]}", MediaType.APPLICATION_JSON));
        List<CpsRoomCheckJudgeClient.ItemJudge> judged =
                client.judge(record(), Arrays.asList(item(1, "C1"), item(7, "C7")));
        assertEquals("PASS", judged.get(0).outcome);
        assertEquals("UNJUDGEABLE", judged.get(1).outcome);
    }

    @Test
    void connectionFailureDegradesToNull() {
        server.expect(requestTo("http://judge.example/api/agent/room-checks/judge"))
                .andRespond(withServerError());
        assertNull(client.judge(record(), Arrays.asList(item(1, "C1"))));
    }

    @Test
    void malformedBodyDegradesToNull() {
        server.expect(requestTo("http://judge.example/api/agent/room-checks/judge"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertNull(client.judge(record(), Arrays.asList(item(1, "C1"))));
    }

    @Test
    void emptyResultsDegradesToNull() {
        server.expect(requestTo("http://judge.example/api/agent/room-checks/judge"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertNull(client.judge(record(), Arrays.asList(item(1, "C1"))));
    }

    @Test
    void disabledReturnsNullWithoutHttpCall() {
        properties.setEnabled(false);
        assertNull(client.judge(record(), Arrays.asList(item(1, "C1"))));
        server.verify(); // 无期望 ⇒ 无调用
    }
}
