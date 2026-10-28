package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsApprovalInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CpsApprovalInstanceMapper {
    /** 按 bizType + bizId 查（唯一键） */
    CpsApprovalInstance findByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 按主键查 */
    CpsApprovalInstance findById(@Param("id") Long id);

    /**
     * 分页查询。
     * status 为 null 时查全部；keyword 模糊匹配 bizTitle/bizCreatorEmpName/rejectReason。
     * bizType 为 null 时查全部类型。
     */
    List<CpsApprovalInstance> findPage(@Param("status") String status,
                                       @Param("bizType") String bizType,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    long countPage(@Param("status") String status,
                   @Param("bizType") String bizType,
                   @Param("keyword") String keyword);

    /**
     * UPSERT：同一 bizType+bizId 已存在则更新，否则插入。
     * 用 ON DUPLICATE KEY UPDATE + UNIQUE KEY uk_biz 兜底并发。
     */
    void upsert(CpsApprovalInstance instance);

    /**
     * 状态机转移：status → toStatus + 更新 last_action_at + 写终态时间。
     * 失败返回 0（status 已变更，乐观锁提示）。
     */
    int updateStatusCas(@Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("toStatus") String toStatus,
                        @Param("lastActionAt") LocalDateTime lastActionAt,
                        @Param("approvedAt") LocalDateTime approvedAt,
                        @Param("rejectedAt") LocalDateTime rejectedAt,
                        @Param("rejectReason") String rejectReason,
                        @Param("currentApproverEmpNo") String currentApproverEmpNo,
                        @Param("currentApproverEmpName") String currentApproverEmpName);

    /** 同步业务的 creator/factory/area（重提或编辑后回灌） */
    int updateBizMeta(@Param("id") Long id,
                      @Param("bizTitle") String bizTitle,
                      @Param("bizCreatorEmpNo") String bizCreatorEmpNo,
                      @Param("bizCreatorEmpName") String bizCreatorEmpName,
                      @Param("bizFactory") String bizFactory,
                      @Param("bizArea") String bizArea,
                      @Param("bizSnapshot") String bizSnapshot,
                      @Param("lastActionAt") LocalDateTime lastActionAt);
}
