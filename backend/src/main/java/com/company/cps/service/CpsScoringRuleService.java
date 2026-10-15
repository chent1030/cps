package com.company.cps.service;

import com.company.cps.domain.CpsScoringRule;
import com.company.cps.dto.CpsScoringRuleRequest;
import com.company.cps.mapper.CpsScoringRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B8 评分规则（PRD §23.1 base-factory 口径）：
 * - 默认：5 条全局规则（base=100 / content_mismatch_deduct=40 /
 *   evidence_vague_deduct=30 / keywords_missing_deduct=20 / other_deduct=10）；
 * - 工厂校准：cps_scoring_rule.factory_calibration_flag=1 且 factory 非空；
 *   同一 rule_key 下，工厂命中即覆盖全局默认；
 * - 版本窗口：effective_from / effective_to + version，按 as-of-date 选最高 version。
 *
 * resolveRules(factory) 返回的 RuleSet 即为评分入口使用的口径值。
 */
@Service
public class CpsScoringRuleService {

    public static final Set<String> KNOWN_RULE_KEYS = Set.of(
            CpsScoringRule.RULE_KEY_BASE,
            CpsScoringRule.RULE_KEY_CONTENT_MISMATCH_DEDUCT,
            CpsScoringRule.RULE_KEY_EVIDENCE_VAGUE_DEDUCT,
            CpsScoringRule.RULE_KEY_KEYWORDS_MISSING_DEDUCT,
            CpsScoringRule.RULE_KEY_OTHER_DEDUCT);

    /** 内置默认（DB 未初始化时的兜底；正常路径读 DB）。 */
    private static final int DEFAULT_BASE = 100;
    private static final int DEFAULT_CONTENT_MISMATCH_DEDUCT = 40;
    private static final int DEFAULT_EVIDENCE_VAGUE_DEDUCT = 30;
    private static final int DEFAULT_KEYWORDS_MISSING_DEDUCT = 20;
    private static final int DEFAULT_OTHER_DEDUCT = 10;

    private final CpsScoringRuleMapper ruleMapper;

    public CpsScoringRuleService(CpsScoringRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 评分入口：根据 factory 解析一组扣分口径值。
     * - factory 非空 → 先查该 factory 的工厂校准规则；命中即用；
     * - 未命中 → 回退全局默认（factory=NULL）；
     * - DB 完全无值 → 回退内置默认（保证 B4 不空指针）。
     */
    public RuleSet resolveRules(String factory) {
        LocalDate today = LocalDate.now(CpsWeeklyScoreService.SHANGHAI_ZONE);
        return new RuleSet(
                readIntRule(factory, today, CpsScoringRule.RULE_KEY_BASE, "score", DEFAULT_BASE),
                readIntRule(factory, today, CpsScoringRule.RULE_KEY_CONTENT_MISMATCH_DEDUCT,
                        "deduct", DEFAULT_CONTENT_MISMATCH_DEDUCT),
                readIntRule(factory, today, CpsScoringRule.RULE_KEY_EVIDENCE_VAGUE_DEDUCT,
                        "deduct", DEFAULT_EVIDENCE_VAGUE_DEDUCT),
                readIntRule(factory, today, CpsScoringRule.RULE_KEY_KEYWORDS_MISSING_DEDUCT,
                        "deduct", DEFAULT_KEYWORDS_MISSING_DEDUCT),
                readIntRule(factory, today, CpsScoringRule.RULE_KEY_OTHER_DEDUCT,
                        "deduct", DEFAULT_OTHER_DEDUCT));
    }

    private int readIntRule(String factory, LocalDate asOfDate, String ruleKey,
                            String fieldKey, int fallback) {
        // 1) 工厂命中
        CpsScoringRule rule = ruleMapper.findEffectiveByKeyFactory(ruleKey, factory, asOfDate);
        // 2) 回退全局
        if (rule == null && factory != null) {
            rule = ruleMapper.findEffectiveByKeyFactory(ruleKey, null, asOfDate);
        }
        if (rule == null) {
            return fallback;
        }
        Integer v = parseJsonInt(rule.getRuleValue(), fieldKey);
        return v != null ? v : fallback;
    }

    /** 极简 JSON 整数读取（仅支持 {"score":N} 或 {"deduct":N}）。 */
    static Integer parseJsonInt(String json, String fieldKey) {
        if (json == null) return null;
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return null;
        String inner = s.substring(1, s.length() - 1);
        for (String kv : inner.split(",")) {
            String[] parts = kv.split(":");
            if (parts.length != 2) continue;
            String k = parts[0].trim().replace("\"", "");
            String v = parts[1].trim().replace("\"", "");
            if (k.equals(fieldKey)) {
                try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> listAll() {
        List<CpsScoringRule> rows = ruleMapper.findAll();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (CpsScoringRule r : rows) {
            result.add(toMap(r));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> upsert(CpsScoringRuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String ruleKey = trimToNull(request.getRuleKey());
        if (ruleKey == null) {
            throw new IllegalArgumentException("ruleKey is required");
        }
        if (!KNOWN_RULE_KEYS.contains(ruleKey)) {
            throw new IllegalArgumentException("Unsupported ruleKey: " + ruleKey
                    + " (allowed: " + KNOWN_RULE_KEYS + ")");
        }
        String factory = trimToNull(request.getFactory());
        Boolean factoryCalibration = request.getFactoryCalibrationFlag() != null
                && Boolean.TRUE.equals(request.getFactoryCalibrationFlag());
        if (factoryCalibration && factory == null) {
            throw new IllegalArgumentException(
                    "factory is required when factoryCalibrationFlag=true");
        }
        if (!factoryCalibration && factory != null) {
            throw new IllegalArgumentException(
                    "factory must be null when factoryCalibrationFlag=false");
        }
        String ruleValue = trimToNull(request.getRuleValue());
        if (ruleValue == null) {
            throw new IllegalArgumentException("ruleValue is required");
        }
        Integer version = request.getVersion() == null || request.getVersion() < 1
                ? 1 : request.getVersion();
        LocalDate effectiveFrom = request.getEffectiveFrom();
        LocalDate effectiveTo = request.getEffectiveTo();
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException(
                    "effectiveFrom must be <= effectiveTo");
        }

        CpsScoringRule rule = new CpsScoringRule();
        rule.setRuleKey(ruleKey);
        rule.setFactory(factory);
        rule.setRuleValue(ruleValue);
        rule.setVersion(version);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setFactoryCalibrationFlag(factoryCalibration);
        rule.setUpdatedBy(trimToDefault(request.getUpdatedBy(), "ADMIN"));
        ruleMapper.upsert(rule);
        return toMap(rule);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String trimToDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static Map<String, Object> toMap(CpsScoringRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rule_key", r.getRuleKey());
        m.put("factory", r.getFactory());
        m.put("rule_value", r.getRuleValue());
        m.put("version", r.getVersion());
        m.put("effective_from", r.getEffectiveFrom());
        m.put("effective_to", r.getEffectiveTo());
        m.put("factory_calibration_flag", r.getFactoryCalibrationFlag());
        m.put("updated_by", r.getUpdatedBy());
        m.put("created_at", r.getCreatedAt());
        m.put("updated_at", r.getUpdatedAt());
        return m;
    }

    /** 评分入口用的不可变值集（5 项）。 */
    public static final class RuleSet {
        private final int base;
        private final int contentMismatchDeduct;
        private final int evidenceVagueDeduct;
        private final int keywordsMissingDeduct;
        private final int otherDeduct;

        public RuleSet(int base, int contentMismatchDeduct, int evidenceVagueDeduct,
                       int keywordsMissingDeduct, int otherDeduct) {
            this.base = base;
            this.contentMismatchDeduct = contentMismatchDeduct;
            this.evidenceVagueDeduct = evidenceVagueDeduct;
            this.keywordsMissingDeduct = keywordsMissingDeduct;
            this.otherDeduct = otherDeduct;
        }

        public int base() { return base; }
        public int contentMismatchDeduct() { return contentMismatchDeduct; }
        public int evidenceVagueDeduct() { return evidenceVagueDeduct; }
        public int keywordsMissingDeduct() { return keywordsMissingDeduct; }
        public int otherDeduct() { return otherDeduct; }
    }
}
