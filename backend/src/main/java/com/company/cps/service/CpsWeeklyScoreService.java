package com.company.cps.service;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsScoringRule;
import com.company.cps.domain.CpsWeeklyScore;
import com.company.cps.domain.CpsWeeklyScoreLine;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsWeeklyScoreLineResponse;
import com.company.cps.dto.CpsWeeklyScoreRecomputeRequest;
import com.company.cps.dto.CpsWeeklyScoreResponse;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsWeeklyScoreMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B4 周评分排名（PRD §24）：
 * - 自然周 = 周一 00:00 Asia/Shanghai 至 周日 23:59；
 * - weekStartDate = 该周周一日期；
 * - 一员工一周一条汇总，按 total_score DESC 排名；
 * - region_supervisor_id 由 area + current_handler_emp_no 复合编码（保持可分组）；
 * - 周日 23:59 @Scheduled 重算（{@link CpsWeeklyScoreScheduler}）。
 *
 * 数据来源（本期）：cps_issue 已存在字段；具体扣分口径由
 * {@link CpsScoringRuleService} 决定，本服务只负责汇总与排名。
 */
@Service
public class CpsWeeklyScoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpsWeeklyScoreService.class);
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    /** PRD §23.1 / §24 默认基准分（来自 cps_scoring_rule.base 全局规则）。 */
    public static final int BASE_SCORE_DEFAULT = 100;

    /** 一周最多取多少 issue 行参与 SUM（防止爆炸）。 */
    private static final int MAX_ISSUES_PER_WEEK = 1000;

    private static final Set<String> SUPPORTED_RECOMPUTE_OPERATORS = Set.of(
            "ADMIN", "SYSTEM", "SCHEDULER");

    private final CpsWeeklyScoreMapper scoreMapper;
    private final CpsIssueMapper issueMapper;
    private final CpsScoringRuleService scoringRuleService;

    public CpsWeeklyScoreService(CpsWeeklyScoreMapper scoreMapper,
                                 CpsIssueMapper issueMapper,
                                 CpsScoringRuleService scoringRuleService) {
        this.scoreMapper = scoreMapper;
        this.issueMapper = issueMapper;
        this.scoringRuleService = scoringRuleService;
    }

    /**
     * 给定日期，返回其所属自然周周一日期（Asia/Shanghai 时区口径）。
     * 周一为一周第一天（DayOfWeek.MONDAY）。
     */
    public static LocalDate naturalWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 给定日期，返回其所属自然周周日日期（Asia/Shanghai 时区口径）。
     */
    public static LocalDate naturalWeekEnd(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * 分页列表（admin 端查看）：
     * - weekStartDate 必填；
     * - regionSupervisorId 可选（null=全部）；
     * - page/pageSize 兜底（1-based，size<=200）。
     */
    public CpsAdminPageResponse<CpsWeeklyScoreResponse> list(LocalDate weekStartDate,
                                                             Long regionSupervisorId,
                                                             int page,
                                                             int pageSize,
                                                             boolean includeLines) {
        LocalDate week = naturalWeekStart(weekStartDate);
        int p = page <= 0 ? 1 : page;
        int s = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        int offset = (p - 1) * s;

        List<CpsWeeklyScore> rows = scoreMapper.listByFilters(week, regionSupervisorId, offset, s);
        long total = scoreMapper.countByFilters(week, regionSupervisorId);

        List<CpsWeeklyScoreResponse> items = new ArrayList<>(rows.size());
        int rankStart = offset + 1;
        for (int i = 0; i < rows.size(); i++) {
            CpsWeeklyScore row = rows.get(i);
            CpsWeeklyScoreResponse item = toResponse(row, includeLines);
            item.setRank(rankStart + i);
            items.add(item);
        }
        return new CpsAdminPageResponse<>(items, total, p, s);
    }

    /**
     * 手动触发重算（管理端 POST /api/cps/admin/scores/weekly/recompute）：
     * - request.weekStartDate 可选，null=上周（自然周）;
     * - request.operatorEmpNo 用于 updated_by；
     * - 返回 { weekStartDate, recomputedCount }。
     */
    @Transactional
    public Map<String, Object> recompute(CpsWeeklyScoreRecomputeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        LocalDate target = request.getWeekStartDate() != null
                ? naturalWeekStart(request.getWeekStartDate())
                : naturalWeekStart(LocalDate.now(SHANGHAI_ZONE).minusDays(7));
        String operatorRaw = request.getOperatorEmpNo();
        if (operatorRaw == null || operatorRaw.trim().isEmpty()) {
            throw new IllegalArgumentException("operatorEmpNo is required");
        }
        String operator = operatorRaw.trim().toUpperCase();
        if (!SUPPORTED_RECOMPUTE_OPERATORS.contains(operator)) {
            // 不在白名单 → 一律归 ADMIN（拒绝静默接受任意值）
            operator = "ADMIN";
        }
        int count = recomputeWeek(target, operator, false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weekStartDate", target);
        body.put("recomputedCount", count);
        body.put("operator", operator);
        body.put("naturalWeekFlag", false);
        return body;
    }

    /**
     * 调度器调用入口（{@link CpsWeeklyScoreScheduler}）：
     * - targetWeek=上周（避免打到当前未完成周）；
     * - operator=SYSTEM；
     * - naturalWeekFlag=true。
     */
    @Transactional
    public int recomputeLastNaturalWeek() {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        LocalDate lastWeekStart = naturalWeekStart(today.minusDays(7));
        return recomputeWeek(lastWeekStart, "SYSTEM", true);
    }

    /**
     * 单周重算（事务边界）：
     * 1) 查本周所有 issue 行（最多 MAX_ISSUES_PER_WEEK 条）；
     * 2) 按 emp_no 分组，对每组：
     *    a) 调 scoringRuleService 决定基准分与扣分；
     *    b) 写 cps_weekly_score 头 + 写 cps_weekly_score_line 明细；
     * 3) 返回本周期落地的 header 数量。
     *
     * 该方法为 {@link #recompute} 与 {@link #recomputeLastNaturalWeek} 共用，
     * 单一事务保证头+明细原子落地。
     */
    @Transactional
    public int recomputeWeek(LocalDate weekStartDate, String operator, boolean naturalWeekFlag) {
        LocalDate week = naturalWeekStart(weekStartDate);
        LocalDate weekEnd = naturalWeekEnd(week);

        List<CpsIssue> issues = issueMapper.findForWeeklyScore(week, weekEnd, MAX_ISSUES_PER_WEEK);
        LOGGER.info("Weekly score recompute: week={}, issueRows={}", week, issues.size());

        // 按 emp_no 分组
        Map<String, List<CpsIssue>> byEmp = new LinkedHashMap<>();
        for (CpsIssue issue : issues) {
            if (issue.getCreatorEmpNo() == null) continue;
            byEmp.computeIfAbsent(issue.getCreatorEmpNo(), k -> new ArrayList<>()).add(issue);
        }

        int written = 0;
        for (Map.Entry<String, List<CpsIssue>> entry : byEmp.entrySet()) {
            String empNo = entry.getKey();
            CpsIssue sample = entry.getValue().get(0);

            // 工厂查 scoring rule（factory_calibration 优先）
            String factory = sample.getFactory();
            CpsScoringRuleService.RuleSet rules = scoringRuleService.resolveRules(factory);

            int baseScore = rules.base();
            int totalDelta = 0;
            List<CpsWeeklyScoreLine> lines = new ArrayList<>();
            // base 行
            lines.add(buildLine(null, CpsScoringRule.RULE_KEY_BASE, baseScore,
                    "基准分 base-factory 口径（" + (factory == null ? "默认" : factory) + "）"));

            for (CpsIssue issue : entry.getValue()) {
                // 本期最简扣分口径：按 status 判定扣分项
                //   - CLOSED → content_mismatch_deduct（流程完整）
                //   - 仍在 PENDING_RECTIFY → evidence_vague_deduct
                //   - PENDING_AI_REVIEW → keywords_missing_deduct
                //   - 其它 → other_deduct
                String itemId;
                int delta;
                String reason;
                String status = issue.getStatus() == null ? "" : issue.getStatus().name();
                switch (status) {
                    case "CLOSED":
                        itemId = CpsScoringRule.RULE_KEY_CONTENT_MISMATCH_DEDUCT;
                        delta = -Math.abs(rules.contentMismatchDeduct());
                        reason = "已关闭问题例行扣分（" + issue.getId() + "）";
                        break;
                    case "PENDING_RECTIFY":
                    case "PENDING_FEEDBACK":
                        itemId = CpsScoringRule.RULE_KEY_EVIDENCE_VAGUE_DEDUCT;
                        delta = -Math.abs(rules.evidenceVagueDeduct());
                        reason = "待整改问题（" + issue.getId() + "）";
                        break;
                    case "PENDING_AI_REVIEW":
                        itemId = CpsScoringRule.RULE_KEY_KEYWORDS_MISSING_DEDUCT;
                        delta = -Math.abs(rules.keywordsMissingDeduct());
                        reason = "AI 初审中（" + issue.getId() + "）";
                        break;
                    default:
                        itemId = CpsScoringRule.RULE_KEY_OTHER_DEDUCT;
                        delta = -Math.abs(rules.otherDeduct());
                        reason = "其他状态问题（" + status + "）";
                }
                totalDelta += delta;
                lines.add(buildLine(null, itemId, delta, reason));
            }

            int finalScore = Math.max(0, Math.min(100, baseScore + totalDelta));

            CpsWeeklyScore existing = scoreMapper.findByWeekAndEmp(week, empNo);
            CpsWeeklyScore header = new CpsWeeklyScore();
            header.setWeekStartDate(week);
            header.setEmpNo(empNo);
            header.setEmpName(sample.getCreatorEmpName());
            // region_supervisor_id 暂以 area 的 hashCode（取绝对值后 longValue）做代理占位，
            // 真实接入区域督导主数据后替换。保留可分组聚合语义。
            header.setRegionSupervisorId(areaHash(sample.getArea()));
            header.setRegionSupervisorName(sample.getArea());
            header.setTotalScore(finalScore);
            header.setRoomCheckCount(entry.getValue().size());
            header.setPhotoCount(0);
            header.setNaturalWeekFlag(naturalWeekFlag);
            header.setUpdatedBy(operator);

            if (existing == null) {
                scoreMapper.insertHeader(header);
            } else {
                header.setId(existing.getId());
                scoreMapper.updateHeader(header);
                scoreMapper.deleteLinesByHeader(existing.getId());
            }

            for (CpsWeeklyScoreLine line : lines) {
                line.setWeeklyScoreId(header.getId());
                scoreMapper.insertLine(line);
            }
            written++;
        }

        LOGGER.info("Weekly score recompute done: week={}, writtenHeaders={}", week, written);
        return written;
    }

    private static Long areaHash(String area) {
        if (area == null) return null;
        long h = ((long) area.hashCode()) & 0xffffffffL;
        return h == 0 ? 1L : h;
    }

    private static CpsWeeklyScoreLine buildLine(Long weeklyScoreId, String itemId,
                                                int delta, String reason) {
        CpsWeeklyScoreLine line = new CpsWeeklyScoreLine();
        line.setWeeklyScoreId(weeklyScoreId);
        line.setItemId(itemId);
        line.setScoreDelta(delta);
        line.setReason(reason);
        return line;
    }

    private CpsWeeklyScoreResponse toResponse(CpsWeeklyScore row, boolean includeLines) {
        CpsWeeklyScoreResponse r = new CpsWeeklyScoreResponse();
        r.setId(row.getId());
        r.setWeekStartDate(row.getWeekStartDate());
        r.setEmpNo(row.getEmpNo());
        r.setEmpName(row.getEmpName());
        r.setRegionSupervisorId(row.getRegionSupervisorId());
        r.setRegionSupervisorName(row.getRegionSupervisorName());
        r.setTotalScore(row.getTotalScore());
        r.setRoomCheckCount(row.getRoomCheckCount());
        r.setPhotoCount(row.getPhotoCount());
        r.setNaturalWeekFlag(row.getNaturalWeekFlag());
        if (includeLines && row.getId() != null) {
            List<CpsWeeklyScoreLine> raw = scoreMapper.findLinesByHeader(row.getId());
            List<CpsWeeklyScoreLineResponse> lines = new ArrayList<>(raw.size());
            for (CpsWeeklyScoreLine l : raw) {
                lines.add(new CpsWeeklyScoreLineResponse(
                        l.getId(), l.getItemId(), l.getScoreDelta(), l.getReason()));
            }
            r.setLines(lines);
        }
        return r;
    }
}
