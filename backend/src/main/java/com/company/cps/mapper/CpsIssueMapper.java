package com.company.cps.mapper;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsIssueListItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsIssueMapper {

    /**
     * 新增巡检问题主记录。
     */
    void insert(CpsIssue issue);

    /**
     * 根据问题 ID 查询问题主记录。
     */
    Optional<CpsIssue> findById(@Param("id") Long id);

    /**
     * 更新问题流程相关字段，包括状态、处理人、原因措施、审核意见和关闭时间。
     */
    void updateWorkflowFields(CpsIssue issue);

    void updateAgentInspectionId(@Param("id") Long id, @Param("agentInspectionId") String agentInspectionId);

    /** 系统驱动流转：仅更新状态与更新时间（V2 AI 初审推进/待配置）。 */
    void updateStatus(@Param("id") Long id, @Param("status") CpsIssueStatus status, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /** 系统驱动流转：审核员路由落位（V2 AI 完成/接管后，当前处理人切到审核员）。 */
    void updateReviewRouting(
            @Param("id") Long id,
            @Param("reviewerEmpNo") String reviewerEmpNo,
            @Param("reviewerEmpName") String reviewerEmpName,
            @Param("status") CpsIssueStatus status,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    /**
     * 按页签查询当前用户的问题列表。
     */
    List<CpsIssueListItemResponse> listByTab(
            @Param("tab") String tab,
            @Param("empNo") String empNo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<CpsIssueListItemResponse> listForAdmin(
            @Param("status") CpsIssueStatus status,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("currentHandler") String currentHandler,
            @Param("createdFrom") String createdFrom,
            @Param("createdTo") String createdTo,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<CpsIssueListItemResponse> listForAdminExport(
            @Param("status") CpsIssueStatus status,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("currentHandler") String currentHandler,
            @Param("createdFrom") String createdFrom,
            @Param("createdTo") String createdTo,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    long countForAdmin(
            @Param("status") CpsIssueStatus status,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("currentHandler") String currentHandler,
            @Param("createdFrom") String createdFrom,
            @Param("createdTo") String createdTo,
            @Param("keyword") String keyword
    );

    long countOpenIssues();

    long countByStatus(@Param("status") CpsIssueStatus status);

    long countOverdueIssues();

    long countClosedThisMonth();
}
