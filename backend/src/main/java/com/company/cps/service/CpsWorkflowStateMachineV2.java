package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * V2 整改域状态机（PRD §28，flow_version='v2' 路由）。
 * 旧 CpsWorkflowStateMachine 冻结不动；本机仅服务 v2 单。
 *
 * 人工动作流转表（executeAction 可触达）：
 * - PENDING_FEEDBACK    + REPLY_ASSIGN          → PENDING_RECTIFY   （指派整改办理人）
 * - PENDING_FEEDBACK    + TRANSFER              → PENDING_FEEDBACK
 * - PENDING_RECTIFY     + SUBMIT_RECTIFICATION  → PENDING_AI_REVIEW （提交即锁定版本并触发 AI 初审）
 * - PENDING_RECTIFY     + SAVE_DRAFT            → PENDING_RECTIFY   （暂存：不完整内容、不触发初审）
 * - PENDING_RECTIFY     + TRANSFER              → PENDING_RECTIFY   （转办：仅当前承办人办理，原办理人失权）
 * - PENDING_REVIEW      + REVIEW_CLOSE          → CLOSED            （人工裁决通过关闭；AI 无决定权）
 * - PENDING_REVIEW      + REVIEW_REJECT         → PENDING_RECTIFY   （退回整改人员；重提必须再触发初审）
 *
 * 系统驱动流转（回调/扫描/投递失败触达，见 assertSystemTransition）：
 * - PENDING_AI_REVIEW       → PENDING_REVIEW         （AI 完成/失败可接管/超时可接管，三态差异由任务表承载）
 * - PENDING_AI_REVIEW       → PENDING_REVIEWER_CONFIG（结果就绪但审核员缺失，AC-25 待配置）
 * - PENDING_REVIEWER_CONFIG → PENDING_REVIEW         （配置完成后继续流转，不要求重新提交）
 * - PENDING_REVIEW          → PENDING_AI_REVIEW      （A4 手动重触发回退：仅未裁决版本，失败任务重投）
 */
@Component
public class CpsWorkflowStateMachineV2 {

    private static final Map<CpsIssueStatus, Map<CpsIssueAction, CpsIssueStatus>> TRANSITIONS =
            new EnumMap<>(CpsIssueStatus.class);

    /** 系统驱动流转白名单（非人工动作，不经过 executeAction）。 */
    private static final Set<Map.Entry<CpsIssueStatus, CpsIssueStatus>> SYSTEM_TRANSITIONS = new HashSet<>();

    static {
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REPLY_ASSIGN, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_FEEDBACK);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.SUBMIT_RECTIFICATION, CpsIssueStatus.PENDING_AI_REVIEW);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.SAVE_DRAFT, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_CLOSE, CpsIssueStatus.CLOSED);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_REJECT, CpsIssueStatus.PENDING_RECTIFY);
        putSystem(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEW);
        putSystem(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEWER_CONFIG);
        putSystem(CpsIssueStatus.PENDING_REVIEWER_CONFIG, CpsIssueStatus.PENDING_REVIEW);
        // A4 手动重触发回退：PENDING_REVIEW → PENDING_AI_REVIEW（仅未裁决版本，服务层双重校验）
        putSystem(CpsIssueStatus.PENDING_REVIEW, CpsIssueStatus.PENDING_AI_REVIEW);
    }

    private static void put(CpsIssueStatus from, CpsIssueAction action, CpsIssueStatus to) {
        TRANSITIONS.computeIfAbsent(from, ignored -> new EnumMap<>(CpsIssueAction.class)).put(action, to);
    }

    private static void putSystem(CpsIssueStatus from, CpsIssueStatus to) {
        SYSTEM_TRANSITIONS.add(new java.util.AbstractMap.SimpleEntry<>(from, to));
    }

    /** 空集表示该状态当前无可人工动作（如 PENDING_AI_REVIEW 版本锁定、PENDING_REVIEWER_CONFIG 待配置）。 */
    public Set<CpsIssueAction> availableActions(CpsIssueStatus status) {
        return TRANSITIONS.getOrDefault(status, Collections.emptyMap()).keySet();
    }

    public CpsIssueStatus nextStatus(CpsIssueStatus status, CpsIssueAction action) {
        CpsIssueStatus next = TRANSITIONS.getOrDefault(status, Collections.emptyMap()).get(action);
        if (next == null) {
            throw new IllegalArgumentException("Action " + action + " is not allowed from status " + status);
        }
        return next;
    }

    /**
     * 提交整改时的落点带上下文：审核员可解析 → PENDING_AI_REVIEW；
     * 无法解析且问题未配置审核员 → PENDING_REVIEWER_CONFIG（提交保留，AC-25 待配置后继续）。
     */
    public CpsIssueStatus resolveSubmissionTarget(boolean reviewerResolved) {
        return reviewerResolved ? CpsIssueStatus.PENDING_AI_REVIEW : CpsIssueStatus.PENDING_REVIEWER_CONFIG;
    }

    /** 校验系统驱动流转（回调/扫描/投递失败）合法，非法即抛错。 */
    public void assertSystemTransition(CpsIssueStatus from, CpsIssueStatus to) {
        boolean allowed = SYSTEM_TRANSITIONS.contains(
                new java.util.AbstractMap.SimpleEntry<>(from, to));
        if (!allowed) {
            throw new IllegalArgumentException("System transition " + from + " -> " + to + " is not allowed");
        }
    }

    /** 判断是否 V2 状态机管理的流转来源状态（用于路由防御：legacy 状态误入 V2 机器时兜底）。 */
    public boolean manages(CpsIssueStatus status) {
        return status != null && EnumSet.of(
                CpsIssueStatus.PENDING_FEEDBACK,
                CpsIssueStatus.PENDING_RECTIFY,
                CpsIssueStatus.PENDING_AI_REVIEW,
                CpsIssueStatus.PENDING_REVIEW,
                CpsIssueStatus.PENDING_REVIEWER_CONFIG,
                CpsIssueStatus.CLOSED
        ).contains(status);
    }

    // ==================== A2 编辑/转办锁定（AC-24/26，PRD §28.3） ====================

    /**
     * 是否允许编辑/转办当前版本（A2/AC-26，PRD §28.3）：
     * 仅“整改办理中、退回整改”（均为 PENDING_RECTIFY）允许编辑或转办。
     * 注意：PENDING_FEEDBACK 阶段的 TRANSFER 是反馈指派转交（反馈人改派），不属于整改转办。
     */
    public boolean isRectifyEditable(CpsIssueStatus status) {
        return status == CpsIssueStatus.PENDING_RECTIFY;
    }

    /**
     * 当前提交版本是否处于锁定态（A2/AC-26，PRD §28.3）：
     * 提交后锁定当前提交版本，AI 初审中（PENDING_AI_REVIEW）、待配置审核员
     * （PENDING_REVIEWER_CONFIG）、待人工审核（PENDING_REVIEW）期间不得继续修改或转办该版本。
     * 这些状态下 TRANSITIONS 无 SAVE_DRAFT/TRANSFER/SUBMIT_RECTIFICATION 出边（锁定即隐式实现），
     * 本方法供服务层守卫与前端提示显式调用。
     */
    public boolean isVersionLocked(CpsIssueStatus status) {
        return status == CpsIssueStatus.PENDING_AI_REVIEW
                || status == CpsIssueStatus.PENDING_REVIEWER_CONFIG
                || status == CpsIssueStatus.PENDING_REVIEW;
    }

    /**
     * 编辑/转办守卫（A2）：版本锁定态（AI 初审中/待配置/待人工审核）执行编辑类动作
     * （SAVE_DRAFT/SUBMIT_RECTIFICATION/TRANSFER）即抛 IllegalStateException，给出明确锁定语义。
     * 非锁定状态下不拦截（合法性由 TRANSITIONS 转移表校验，
     * 例如 PENDING_FEEDBACK+TRANSFER 是反馈指派转交，不属于整改转办，仍按表放行）。
     */
    public void assertRectifyEditable(CpsIssueStatus status, CpsIssueAction action) {
        if (isVersionLocked(status)) {
            throw new IllegalStateException(
                    "Issue version is locked in status " + status
                            + ": edit/transfer not allowed until review finishes (PRD 28.3, AC-26)");
        }
    }
}
