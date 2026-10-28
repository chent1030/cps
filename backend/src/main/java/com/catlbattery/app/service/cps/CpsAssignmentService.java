package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsAreaPersonConfig;
import com.catlbattery.infra.mapper.cps.CpsAreaPersonConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CpsAssignmentService {

    private final CpsAreaPersonConfigMapper configMapper;

    public CpsAssignmentService(CpsAreaPersonConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 匹配问题反馈处理人：按工厂/区域/拉线/工序精确查找。
     * 直接返回数据库行（含 empNo + empName），由 controller 把这两个字段打包成响应，
     * 不再使用任何"伪造"empName 的占位。
     */
    public CpsAreaPersonConfig findFeedbackHandlerRow(String factory, String area, String line, String process) {
        List<CpsAreaPersonConfig> candidates = configMapper.findFeedbackCandidates(
                trim(factory),
                trim(area),
                trim(line),
                trim(process)
        );
        return candidates.stream()
                .filter(c -> c.getEmpNo() != null && !c.getEmpNo().trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * 匹配审核人：按工厂/区域查找。
     */
    public CpsAreaPersonConfig findReviewerRow(String factory, String area) {
        List<CpsAreaPersonConfig> candidates = configMapper.findReviewerCandidates(trim(factory), trim(area));
        return candidates.stream()
                .filter(c -> c.getEmpNo() != null && !c.getEmpNo().trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * 兼容旧调用方：返回 empNo 字符串。
     */
    public String findFeedbackHandler(String factory, String area, String line, String process) {
        CpsAreaPersonConfig row = findFeedbackHandlerRow(factory, area, line, process);
        return row == null ? null : row.getEmpNo();
    }

    /**
     * 兼容旧调用方：返回 empNo 字符串。
     */
    public String findReviewer(String factory, String area) {
        CpsAreaPersonConfig row = findReviewerRow(factory, area);
        return row == null ? null : row.getEmpNo();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
