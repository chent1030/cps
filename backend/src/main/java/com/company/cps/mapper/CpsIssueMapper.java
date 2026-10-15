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
     * B4 周评分数据源：自然周内（created_at 落在 [weekStart, weekEnd+1)）的 issue 行，
     * 上限 limit 防爆。返回字段为 CpsIssue 全量（service 仅用 status/factory/area/empNo/empName）。
     */
    List<CpsIssue> findForWeeklyScore(@Param("weekStart") java.time.LocalDate weekStart,
                                      @Param("weekEnd") java.time.LocalDate weekEnd,
                                      @Param("limit") int limit);

    /**
     * 更新问题流程相关字段，包括状态、处理人、原因措施、审核意见和关闭时间。
     *
     * <p>A2 乐观锁（AC-24）：WHERE 追加 lock_version CAS 校验并自增；
     * 返回受影响行数，0 表示并发冲突（他人已先提交修改），调用方应提示刷新重试。
     */
    int updateWorkflowFields(CpsIssue issue);

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

    /** 系统回退（A4 手动重触发）：PENDING_REVIEW → PENDING_AI_REVIEW 并清空当前处理人；CAS 限定原状态。 */
    int updateStatusClearHandler(
            @Param("id") Long id,
            @Param("fromStatus") CpsIssueStatus fromStatus,
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

    /**
     * I 线记忆体系消费：分页查询问题全集（与 listForAdmin 共用 adminIssueColumns/adminIssueFilter）。
     * 字段集对齐 CpsIssueListItemResponse，用于记忆体系构建"问题上下文"。
     */
    List<CpsIssueListItemResponse> findMemoryPage(
            @Param("factory") String factory,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("status") CpsIssueStatus status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countMemory(
            @Param("factory") String factory,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("status") CpsIssueStatus status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );
}
