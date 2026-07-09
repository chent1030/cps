package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.dto.CpsIssueListItemResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpsIssueMapperContractTest {

    @Test
    void listByTabSelectsListFieldsAndUsesCompleteRelatedPredicates() throws IOException {
        String sql = readXml("CpsIssueMapper.xml");

        assertTrue(sql.contains("factory"));
        assertTrue(sql.contains("area"));
        assertTrue(sql.contains("line"));
        assertTrue(sql.contains("process"));
        assertTrue(sql.contains("category_l1_id"));
        assertTrue(sql.contains("category_l2_id"));
        assertTrue(sql.contains("current_handler_emp_name"));
        assertTrue(sql.contains("overdue"));
        assertTrue(sql.contains("#{tab} != 'related'"));
        assertTrue(sql.contains("creator_emp_no = #{empNo}"));
        assertTrue(sql.contains("current_handler_emp_no = #{empNo}"));
        assertTrue(sql.contains("feedback_emp_no = #{empNo}"));
        assertTrue(sql.contains("responsible_emp_no = #{empNo}"));
        assertTrue(sql.contains("proof_emp_no = #{empNo}"));
        assertTrue(sql.contains("reviewer_emp_no = #{empNo}"));
        assertTrue(sql.contains("cps_issue_flow_log"));
        assertTrue(sql.contains("#{tab} != 'closed' OR (status = 'CLOSED'"));
    }

    @Test
    void listItemResponseExposesRequiredFields() throws NoSuchMethodException {
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getFactory").getReturnType());
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getArea").getReturnType());
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getLine").getReturnType());
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getProcess").getReturnType());
        assertEquals(Long.class, CpsIssueListItemResponse.class.getMethod("getCategoryL1Id").getReturnType());
        assertEquals(Long.class, CpsIssueListItemResponse.class.getMethod("getCategoryL2Id").getReturnType());
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getCurrentHandlerEmpName").getReturnType());
        assertEquals(Boolean.class, CpsIssueListItemResponse.class.getMethod("getOverdue").getReturnType());
    }

    @Test
    void attachmentBindingReturnsAffectedRowsAndRejectsForeignBoundAttachments() throws NoSuchMethodException, IOException {
        Method method = CpsIssueAttachmentMapper.class.getMethod(
                "attachToIssue",
                Long.class,
                Long.class,
                String.class,
                int.class,
                String.class,
                String.class
        );
        String sql = readXml("CpsIssueAttachmentMapper.xml");

        assertEquals(int.class, method.getReturnType());
        assertTrue(sql.contains("WHERE id = #{attachmentId}"));
        assertTrue(sql.contains("(issue_id IS NULL OR issue_id = #{issueId})"));
        assertTrue(sql.contains("created_name = #{createdName}"));
    }

    @Test
    void attachmentMapperCanInsertTemporaryUploadBeforeIssueIsCreated() throws NoSuchMethodException, IOException {
        Method method = CpsIssueAttachmentMapper.class.getMethod("insert", CpsIssueAttachment.class);
        String sql = readXml("CpsIssueAttachmentMapper.xml");

        assertEquals(int.class, method.getReturnType());
        assertTrue(sql.contains("INSERT INTO cps_issue_attachment"));
        assertTrue(sql.contains("issue_id"));
        assertTrue(sql.contains("#{issueId}"));
        assertTrue(sql.contains("useGeneratedKeys=\"true\""));
    }

    private static String readXml(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources/mapper", fileName)), StandardCharsets.UTF_8);
    }
}
