package com.company.cps.service;

import com.company.cps.domain.CpsWeeklyReportRun;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * C5/C8 管理端周报代理（PRD §21.3；AC-04/05）。
 *
 * <p>Java 侧不持久化周报运行表（数据源在 Python PG）。admin 端查询/下载代理 Python C-05；
 * 仅做参数透传 + 响应归一化（CpsWeeklyReportRun DTO），不缓存（按需取）。
 *
 * <p>权限：复用现有 principal 机制（operatorEmpNo 参数由 controller 注入）。
 * admin 弱鉴权风险沿用波次 2 标注；生产前需补统一鉴权拦截。
 */
@Service
public class CpsWeeklyReportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CpsAgentFrameworkClient agentFrameworkClient;

    public CpsWeeklyReportService(CpsAgentFrameworkClient agentFrameworkClient) {
        this.agentFrameworkClient = agentFrameworkClient;
    }

    /** 列表查询：按类型/周期/状态过滤后归一化为 CpsWeeklyReportRun 列表。 */
    public List<CpsWeeklyReportRun> listRuns(String inspectionType, String status,
                                             LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (periodStart != null && periodEnd != null && !periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("periodStart must be before periodEnd (window left-closed right-open)");
        }
        List<Map<String, Object>> raw = agentFrameworkClient.listWeeklyReportRuns(
                inspectionType,
                status,
                periodStart == null ? null : periodStart.format(ISO),
                periodEnd == null ? null : periodEnd.format(ISO));
        if (raw == null) return Collections.emptyList();
        List<CpsWeeklyReportRun> out = new ArrayList<>(raw.size());
        for (Map<String, Object> m : raw) {
            out.add(mapRow(m));
        }
        return out;
    }

    /** 受控下载（流式转发）：admin 端 controller 负责写入 HttpServletResponse。 */
    public CpsAgentFrameworkClient.WeeklyReportFile downloadFile(String runId) {
        if (runId == null || runId.trim().isEmpty()) {
            throw new IllegalArgumentException("runId is required");
        }
        return agentFrameworkClient.downloadWeeklyReportFile(runId.trim());
    }

    private CpsWeeklyReportRun mapRow(Map<String, Object> m) {
        CpsWeeklyReportRun run = new CpsWeeklyReportRun();
        run.setRunId(stringOrNull(m.get("run_id")));
        run.setRunNo(stringOrNull(m.get("run_no")));
        run.setInspectionType(stringOrNull(m.get("inspection_type")));
        run.setPeriodStart(parseLocal(m.get("period_start")));
        run.setPeriodEnd(parseLocal(m.get("period_end")));
        run.setStartedAt(parseLocal(m.get("started_at")));
        run.setFinishedAt(parseLocal(m.get("finished_at")));
        run.setStatus(stringOrNull(m.get("status")));
        run.setFailStage(stringOrNull(m.get("fail_stage")));
        run.setFailReason(stringOrNull(m.get("fail_reason")));
        run.setFileName(stringOrNull(m.get("file_name")));
        run.setFileKey(stringOrNull(m.get("file_key")));
        run.setFileSize(longOrNull(m.get("file_size")));
        run.setSourcePlanRef(stringOrNull(m.get("source_plan_ref")));
        run.setPushStatus(stringOrNull(m.get("push_status")));
        run.setPushChannel(stringOrNull(m.get("push_channel")));
        run.setPushedAt(parseLocal(m.get("pushed_at")));
        run.setRetryCount(intOrNull(m.get("retry_count")));
        return run;
    }

    private static String stringOrNull(Object v) {
        if (v == null) return null;
        return String.valueOf(v);
    }

    private static Long longOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseLocal(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        // Python ISO 可能带 Z 或 +08:00 偏移；LocalDateTime 不接受时区——按业务约定视为 Asia/Shanghai 入库值。
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
