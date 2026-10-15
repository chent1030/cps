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
            CpsWeeklyReportRun run = mapRow(m);
            // 波次7 J线：周期窗口过滤在 Java 侧执行（Python 仅支持 report_type/status/period 过滤）
            if (!withinPeriod(run, periodStart, periodEnd)) {
                continue;
            }
            out.add(run);
        }
        return out;
    }

    /** 周期窗口过滤：run 周期起点（window_start）落在 [periodStart, periodEnd) 内；边界值缺失时保留。 */
    private static boolean withinPeriod(CpsWeeklyReportRun run, LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (periodStart == null || periodEnd == null) return true;
        LocalDateTime windowStart = run.getPeriodStart();
        if (windowStart == null) return true;
        return !windowStart.isBefore(periodStart) && windowStart.isBefore(periodEnd);
    }

    /** 受控下载（流式转发）：admin 端 controller 负责写入 HttpServletResponse。 */
    public CpsAgentFrameworkClient.WeeklyReportFile downloadFile(String runId) {
        if (runId == null || runId.trim().isEmpty()) {
            throw new IllegalArgumentException("runId is required");
        }
        return agentFrameworkClient.downloadWeeklyReportFile(runId.trim());
    }

    /** 波次7 J线（C7 冻结 schema）：Python 行字段 report_type/window_start/window_end/archive_object_key/
     *  archive_bytes/error_code/push_status；旧字段名作回退兼容。 */
    private CpsWeeklyReportRun mapRow(Map<String, Object> m) {
        CpsWeeklyReportRun run = new CpsWeeklyReportRun();
        run.setRunId(stringOrNull(m.get("run_id")));
        run.setRunNo(stringOrNull(m.get("run_no")));
        run.setInspectionType(stringOrNull(firstNonNull(m.get("report_type"), m.get("inspection_type"))));
        run.setPeriodStart(parseLocal(firstNonNull(m.get("window_start"), m.get("period_start"))));
        run.setPeriodEnd(parseLocal(firstNonNull(m.get("window_end"), m.get("period_end"))));
        run.setStartedAt(parseLocal(m.get("started_at")));
        run.setFinishedAt(parseLocal(m.get("finished_at")));
        run.setStatus(stringOrNull(m.get("status")));
        run.setFailStage(stringOrNull(m.get("fail_stage")));
        run.setFailReason(stringOrNull(firstNonNull(m.get("error_code"), m.get("fail_reason"))));
        String fileKey = stringOrNull(firstNonNull(m.get("archive_object_key"), m.get("file_key")));
        run.setFileName(stringOrNull(firstNonNull(m.get("file_name"), basenameOrNull(fileKey))));
        run.setFileKey(fileKey);
        run.setFileSize(longOrNull(firstNonNull(m.get("archive_bytes"), m.get("file_size"))));
        run.setSourcePlanRef(stringOrNull(m.get("source_plan_ref")));
        run.setPushStatus(stringOrNull(m.get("push_status")));
        run.setPushChannel(stringOrNull(m.get("push_channel")));
        run.setPushedAt(parseLocal(m.get("pushed_at")));
        run.setRetryCount(intOrNull(m.get("retry_count")));
        return run;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    /** object key 尾段作文件名（weekly-reports/xxx.pdf → xxx.pdf）。 */
    private static String basenameOrNull(String objectKey) {
        if (objectKey == null) return null;
        int idx = objectKey.lastIndexOf('/');
        return idx >= 0 ? objectKey.substring(idx + 1) : objectKey;
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
        // Python ISO 可能带 Z 或 +00:00/+08:00 偏移；LocalDateTime 不接受时区——按业务约定视为 Asia/Shanghai 入库值。
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
            // 带显式偏移（如 window_start "2026-09-13T16:00:00+00:00"）——转 Local 后再解析
            try {
                return java.time.OffsetDateTime.parse(String.valueOf(v)).toLocalDateTime();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
