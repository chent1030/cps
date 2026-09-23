package com.company.cps.service;

import com.company.cps.config.CpsAgentFrameworkProperties;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.dto.CpsIssueCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * C-01/C-03 契约与 P0 附件转发回归：
 * 新附件（content NULL、仅 object_key）经 resolver 流读取后必须以 base64 出现在 evidence 载荷中——
 * 旧实现此处静默跳过导致线上无图巡检。
 */
class CpsAgentFrameworkClientTest {

    private static final String BASE_URL = "http://python.test/api/v1";

    private CpsAttachmentContentResolver contentResolver;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private CpsAgentFrameworkClient client;

    @BeforeEach
    void setUp() {
        contentResolver = mock(CpsAttachmentContentResolver.class);
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        CpsAgentFrameworkProperties properties = new CpsAgentFrameworkProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setTimeoutMs(2000);
        client = new CpsAgentFrameworkClient(properties, contentResolver, restTemplate);
    }

    @Test
    void legacyCreateAndStartForwardsRustfsAttachmentAsEvidence() {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(9L);
        attachment.setFileName("before.png");
        attachment.setFileType("image/png");
        // P0 断点场景：content 为 NULL，仅 object_key
        attachment.setContent(null);
        attachment.setFileUrl("cps/before-9.png");
        when(contentResolver.resolve(attachment)).thenReturn("rustfs-bytes".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + "/cps/inspections"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":7,\"version\":1}", MediaType.APPLICATION_JSON));
        // 关键断点回归：必须出现 evidence POST 且带 base64 内容（旧代码此处被静默跳过）
        server.expect(requestTo(BASE_URL + "/cps/inspections/7/evidence"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.kind").value("before"))
                .andExpect(jsonPath("$.content_base64")
                        .value(Base64.getEncoder().encodeToString("rustfs-bytes".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess("{\"version\":2}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/cps/inspections/7/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String inspectionId = client.createAndStart(1L, "E001", createRequest(),
                Collections.singletonList(attachment));
        assertEquals("7", inspectionId);
        server.verify();
    }

    @Test
    void resolverFailureAbortsCreateInsteadOfSilentSkip() {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(9L);
        attachment.setFileUrl("cps/missing.png");
        attachment.setContent(null);
        when(contentResolver.resolve(attachment)).thenThrow(new IllegalStateException("rustfs unavailable"));

        server.expect(requestTo(BASE_URL + "/cps/inspections"))
                .andRespond(withSuccess("{\"id\":7,\"version\":1}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> client.createAndStart(1L, "E001", createRequest(), Collections.singletonList(attachment)));
        server.verify();
    }

    @Test
    void triggerInitialReviewPostsFrozenContractPayloadWithObjectKeys() {
        // 波次7 J线（C7 冻结 schema，additionalProperties=false）：issue_id/submission_id/version_no 必填；
        // 附件 AttachmentRef.object_key 优先（file_url 即 RustFS key），无 object_key 才回退 content_base64
        CpsRectificationSubmission submission = submission();
        CpsIssue issue = issue();
        CpsIssueAttachment before = attachment(31L, "cps/before-1.png");
        CpsIssueAttachment after = attachment(32L, "cps/after-1.png");

        server.expect(requestTo(BASE_URL + "/agent/rectifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issue_id").value("101"))
                .andExpect(jsonPath("$.submission_id").value("55"))
                .andExpect(jsonPath("$.version_no").value(2))
                .andExpect(jsonPath("$.reason").value("root cause"))
                .andExpect(jsonPath("$.short_term_measure").value("short"))
                .andExpect(jsonPath("$.long_term_measure").value("long"))
                .andExpect(jsonPath("$.before_attachments[0].attachment_id").value("31"))
                .andExpect(jsonPath("$.before_attachments[0].object_key").value("cps/before-1.png"))
                .andExpect(jsonPath("$.before_attachments[0].file_name").value("before-1.png"))
                .andExpect(jsonPath("$.after_attachments[0].object_key").value("cps/after-1.png"))
                .andExpect(jsonPath("$.issue_snapshot.issue_id").value("101"))
                .andExpect(jsonPath("$.issue_snapshot.factory").value("F1"))
                .andExpect(jsonPath("$.issue_snapshot.description").value("desc"))
                .andExpect(jsonPath("$.idempotency_key").doesNotExist())
                .andExpect(jsonPath("$.callback").doesNotExist())
                .andExpect(jsonPath("$.responsible_emp_no").doesNotExist())
                .andExpect(jsonPath("$.before_images").doesNotExist())
                .andRespond(withSuccess("{\"review_task_ref\":\"rr-77\",\"task_id\":\"cps-rectify-101-v2\"}",
                        MediaType.APPLICATION_JSON));

        String reviewTaskRef = client.triggerInitialReview(submission, issue,
                Collections.singletonList(before), Collections.singletonList(after));
        assertEquals("rr-77", reviewTaskRef);
        server.verify();
    }

    @Test
    void triggerInitialReviewFallsBackToBase64WhenObjectKeyMissing() {
        CpsIssueAttachment legacy = attachment(33L, null);
        when(contentResolver.resolve(legacy)).thenReturn("legacy-img".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE_URL + "/agent/rectifications"))
                .andExpect(jsonPath("$.before_attachments[0].content_base64")
                        .value(Base64.getEncoder().encodeToString("legacy-img".getBytes(StandardCharsets.UTF_8))))
                .andExpect(jsonPath("$.before_attachments[0].object_key").doesNotExist())
                .andRespond(withSuccess("{\"review_task_ref\":\"rr-78\"}", MediaType.APPLICATION_JSON));
        String ref = client.triggerInitialReview(submission(), issue(),
                Collections.singletonList(legacy), null);
        assertEquals("rr-78", ref);
        server.verify();
    }

    @Test
    void triggerInitialReviewAcceptsTaskIdFieldAsRefFallback() {
        server.expect(requestTo(BASE_URL + "/agent/rectifications"))
                .andRespond(withSuccess("{\"task_id\":\"alt-48\"}", MediaType.APPLICATION_JSON));
        String reviewTaskRef = client.triggerInitialReview(submission(), issue(), null, null);
        assertEquals("alt-48", reviewTaskRef);
        server.verify();
    }

    @Test
    void initialReviewStatusQueriesRemoteRef() {
        server.expect(requestTo(BASE_URL + "/agent/rectifications/rr-77"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"running\"}", MediaType.APPLICATION_JSON));
        Map<String, Object> status = client.initialReviewStatus("rr-77");
        assertEquals("running", status.get("state"));
        server.verify();
    }

    @Test
    void disabledClientSkipsDelivery() {
        CpsAgentFrameworkProperties properties = new CpsAgentFrameworkProperties();
        properties.setEnabled(false);
        CpsAgentFrameworkClient disabled = new CpsAgentFrameworkClient(properties, contentResolver, restTemplate);
        assertEquals(null, disabled.triggerInitialReview(submission(), issue(), null, null));
        assertEquals(Boolean.FALSE, disabled.initialReviewStatus("rr-1").get("enabled"));
        server.verify();
    }

    private CpsIssueCreateRequest createRequest() {
        CpsIssueCreateRequest request = new CpsIssueCreateRequest();
        request.setDescription("line defect");
        request.setFactory("F1");
        request.setArea("A1");
        request.setLine("L1");
        request.setProcess("P1");
        request.setFeedbackEmpNo("E001");
        return request;
    }

    private CpsRectificationSubmission submission() {
        CpsRectificationSubmission submission = new CpsRectificationSubmission();
        submission.setId(55L);
        submission.setIssueId(101L);
        submission.setVersionNo(2);
        submission.setReason("root cause");
        submission.setShortTermMeasure("short");
        submission.setLongTermMeasure("long");
        submission.setResponsibleEmpNo("E777");
        return submission;
    }

    private CpsIssue issue() {
        CpsIssue issue = new CpsIssue();
        issue.setId(101L);
        issue.setFactory("F1");
        issue.setArea("A1");
        issue.setLine("L1");
        issue.setProcess("P1");
        issue.setDescription("desc");
        return issue;
    }

    private CpsIssueAttachment attachment(Long id, String objectKey) {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(id);
        attachment.setFileName(objectKey == null ? "legacy-" + id + ".png"
                : objectKey.substring(objectKey.lastIndexOf('/') + 1));
        attachment.setFileType("image/png");
        attachment.setFileUrl(objectKey);
        attachment.setContent(null);
        return attachment;
    }
}
