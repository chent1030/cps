package com.company.cps.mapper;

import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.dto.CpsInitialReviewAdminTaskView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CpsInitialReviewTaskMapper {

    /** 新建初审任务（RUNNING，timeout_at=submitted_at+可配置秒数）。 */
    void insert(CpsInitialReviewTask task);

    CpsInitialReviewTask findById(@Param("id") Long id);

    CpsInitialReviewTask findByIssueAndVersion(@Param("issueId") Long issueId, @Param("versionNo") Integer versionNo);

    CpsInitialReviewTask findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 30s 扫描：RUNNING 且 now≥timeout_at 的任务（→TIMEOUT_OPEN）。 */
    List<CpsInitialReviewTask> findExpiredRunning(@Param("now") LocalDateTime now);

    /** 终态任务但问题仍停在 PENDING_REVIEWER_CONFIG（AC-25：配置完成后继续流转的扫描续路）。 */
    List<CpsInitialReviewTask> findTerminalUnderReviewerConfig();

    /** C-01 投递成功后回写 Python 侧任务引用。 */
    int updateReviewTaskRef(@Param("id") Long id, @Param("reviewTaskRef") String reviewTaskRef);

    int markTimeoutOpen(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 投递失败或 Python 执行异常回调：立即可接管。 */
    int markFailed(@Param("id") Long id, @Param("errorCode") String errorCode, @Param("now") LocalDateTime now);

    int markCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 审核专员人工接管（原因必填）。 */
    int markTakenOver(
            @Param("id") Long id,
            @Param("takenOverBy") String takenOverBy,
            @Param("takenOverName") String takenOverName,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    /** 接管后迟到结果到达：仅留痕，不覆盖裁决。 */
    int markLateResult(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** A4 投递技术重试计数（CAS：仅 RUNNING 任务可计数）。 */
    int incrementRetryCount(@Param("id") Long id);

    /** A4 手动重触发：FAILED/PENDING_DISPATCH → RUNNING，新幂等键，重置投递计时窗口（新投递轮次）。 */
    int markRetriggered(
            @Param("id") Long id,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("submittedAt") LocalDateTime submittedAt,
            @Param("timeoutAt") LocalDateTime timeoutAt
    );

    /** A4 admin 触发记录分页（JOIN 问题状态/初审结果/裁决）。 */
    List<CpsInitialReviewAdminTaskView> findAdminPage(
            @Param("status") String status,
            @Param("issueId") Long issueId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countAdminPage(@Param("status") String status, @Param("issueId") Long issueId);
}
