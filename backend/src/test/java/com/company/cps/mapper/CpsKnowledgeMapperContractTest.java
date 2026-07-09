package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAiMatch;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpsKnowledgeMapperContractTest {

    @Test
    void syncCandidatesCheckStatusAndDimension() throws NoSuchMethodException, IOException {
        Method method = CpsKnowledgeCaseImageMapper.class.getMethod(
                "findSyncCandidates",
                Integer.class,
                int.class
        );
        String sql = readXml("CpsKnowledgeCaseImageMapper.xml");

        assertEquals(List.class, method.getReturnType());
        assertTrue(sql.contains("kc.enabled = 1"));
        assertTrue(sql.contains("ki.vector_status != 'SUCCESS'"));
        assertTrue(sql.contains("ki.embedding_dim IS NULL OR ki.embedding_dim != #{dimension}"));
    }

    @Test
    void imageStatusUpdatesAreIdempotentAndRecordDimension() throws NoSuchMethodException, IOException {
        Method success = CpsKnowledgeCaseImageMapper.class.getMethod(
                "markVectorSuccess",
                Long.class,
                String.class,
                Integer.class
        );
        String sql = readXml("CpsKnowledgeCaseImageMapper.xml");

        assertEquals(int.class, success.getReturnType());
        assertTrue(sql.contains("vector_status = 'SUCCESS'"));
        assertTrue(sql.contains("milvus_vector_id = #{vectorId}"));
        assertTrue(sql.contains("embedding_dim = #{dimension}"));
        assertTrue(sql.contains("WHERE id = #{id}"));
    }

    @Test
    void caseMapperFindsEnabledCasesByIds() throws NoSuchMethodException, IOException {
        Method method = CpsKnowledgeCaseMapper.class.getMethod("findEnabledByIds", List.class);
        String sql = readXml("CpsKnowledgeCaseMapper.xml");

        assertEquals(List.class, method.getReturnType());
        assertTrue(sql.contains("enabled = 1"));
        assertTrue(sql.contains("<foreach"));
    }

    @Test
    void caseMapperSupportsAdminMaintenanceWithoutReasonAndMeasure() throws NoSuchMethodException, IOException {
        Method list = CpsKnowledgeCaseMapper.class.getMethod("findAll", Boolean.class);
        Method upsert = CpsKnowledgeCaseMapper.class.getMethod("upsert", CpsKnowledgeCase.class);
        Method enabled = CpsKnowledgeCaseMapper.class.getMethod("setEnabled", Long.class, Boolean.class);
        String sql = readXml("CpsKnowledgeCaseMapper.xml");

        assertEquals(List.class, list.getReturnType());
        assertEquals(int.class, upsert.getReturnType());
        assertEquals(int.class, enabled.getReturnType());
        assertTrue(sql.contains("FROM cps_knowledge_case"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("scope_remark"));
        assertTrue(!sql.contains("reason"));
        assertTrue(!sql.contains("measure"));
    }

    @Test
    void imageMapperSupportsMaterialMaintenanceAndImageBoundSuggestion() throws NoSuchMethodException, IOException {
        Method byCase = CpsKnowledgeCaseImageMapper.class.getMethod("findByCaseId", Long.class);
        Method byImages = CpsKnowledgeCaseImageMapper.class.getMethod("findEnabledByIds", List.class);
        Method upsert = CpsKnowledgeCaseImageMapper.class.getMethod("upsert", CpsKnowledgeCaseImage.class);
        Method pending = CpsKnowledgeCaseImageMapper.class.getMethod("markVectorPending", Long.class);
        String sql = readXml("CpsKnowledgeCaseImageMapper.xml");

        assertEquals(List.class, byCase.getReturnType());
        assertEquals(List.class, byImages.getReturnType());
        assertEquals(int.class, upsert.getReturnType());
        assertEquals(int.class, pending.getReturnType());
        assertTrue(sql.contains("reason"));
        assertTrue(sql.contains("measure"));
        assertTrue(sql.contains("vector_status = 'PENDING'"));
        assertTrue(sql.contains("JOIN cps_knowledge_case kc ON kc.id = ki.case_id"));
        assertTrue(!sql.contains("kc.category_l1_id"));
        assertTrue(!sql.contains("kc.category_l2_id"));
    }

    @Test
    void imageEntityContainsOnlyImageTableFields() {
        assertThrows(NoSuchMethodException.class, () -> CpsKnowledgeCaseImage.class.getMethod("getCategoryL1Id"));
        assertThrows(NoSuchMethodException.class, () -> CpsKnowledgeCaseImage.class.getMethod("getCategoryL2Id"));
        assertThrows(NoSuchMethodException.class, () -> CpsKnowledgeCaseImage.class.getMethod("getCategoryL1Name"));
        assertThrows(NoSuchMethodException.class, () -> CpsKnowledgeCaseImage.class.getMethod("getCategoryL2Name"));
        assertThrows(NoSuchMethodException.class, () -> CpsKnowledgeCaseImage.class.getMethod("getEnabled"));
    }

    @Test
    void categoryMapperSupportsEnabledHierarchyQueries() throws NoSuchMethodException, IOException {
        Method children = CpsProblemCategoryMapper.class.getMethod("findEnabledByParentId", Long.class);
        Method upsert = CpsProblemCategoryMapper.class.getMethod("upsert", com.company.cps.domain.CpsProblemCategory.class);
        String sql = readXml("CpsProblemCategoryMapper.xml");

        assertEquals(List.class, children.getReturnType());
        assertEquals(int.class, upsert.getReturnType());
        assertTrue(sql.contains("FROM cps_problem_category"));
        assertTrue(sql.contains("enabled = 1"));
        assertTrue(sql.contains("parent_id"));
        assertTrue(sql.contains("COALESCE(#{parentId}, 0)"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    void areaPersonConfigMapperOrdersBySpecificity() throws NoSuchMethodException, IOException {
        Method feedback = CpsAreaPersonConfigMapper.class.getMethod(
                "findFeedbackCandidates",
                String.class,
                String.class,
                String.class,
                String.class
        );
        Method reviewer = CpsAreaPersonConfigMapper.class.getMethod("findReviewerCandidates", String.class, String.class);
        Method areas = CpsAreaPersonConfigMapper.class.getMethod("findAreaOptions", String.class);
        Method lines = CpsAreaPersonConfigMapper.class.getMethod("findLineOptions", String.class, String.class);
        Method processes = CpsAreaPersonConfigMapper.class.getMethod("findProcessOptions", String.class, String.class, String.class);
        String sql = readXml("CpsAreaPersonConfigMapper.xml");

        assertEquals(List.class, feedback.getReturnType());
        assertEquals(List.class, reviewer.getReturnType());
        assertEquals(List.class, areas.getReturnType());
        assertEquals(List.class, lines.getReturnType());
        assertEquals(List.class, processes.getReturnType());
        assertTrue(sql.contains("FROM cps_area_person_config"));
        assertTrue(sql.contains("emp_no IS NOT NULL"));
        assertTrue(sql.contains("match_level DESC"));
        assertTrue(sql.contains("match_level ASC"));
        assertTrue(sql.contains("factory AS value"));
        assertTrue(sql.contains("area AS value"));
        assertTrue(sql.contains("line AS value"));
        assertTrue(sql.contains("process AS value"));
        assertTrue(!sql.contains("CRC32"));
    }

    @Test
    void aiMatchMapperInsertsFullMatchRecord() throws NoSuchMethodException, IOException {
        Method method = CpsIssueAiMatchMapper.class.getMethod("insert", CpsIssueAiMatch.class);
        String sql = readXml("CpsIssueAiMatchMapper.xml");

        assertEquals(int.class, method.getReturnType());
        assertTrue(sql.contains("source_attachment_id"));
        assertTrue(sql.contains("matched_case_id"));
        assertTrue(sql.contains("confidence"));
        assertTrue(sql.contains("topk_json"));
        assertTrue(sql.contains("raw_request"));
        assertTrue(sql.contains("raw_response"));
    }

    @Test
    void attachmentMapperCanFindUploadedFileForEmbedding() throws NoSuchMethodException, IOException {
        Method method = CpsIssueAttachmentMapper.class.getMethod("findById", Long.class);
        assertEquals(Optional.class, method.getReturnType());
        assertTrue(readXml("CpsIssueAttachmentMapper.xml").contains("FROM cps_issue_attachment"));
    }

    private static String readXml(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources/mapper", fileName)), StandardCharsets.UTF_8);
    }
}
