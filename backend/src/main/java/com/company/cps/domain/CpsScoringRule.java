package com.company.cps.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B8 评分规则配置（cps_scoring_rule）：
 * - rule_key 唯一：base / content_mismatch_deduct / evidence_vague_deduct /
 *   keywords_missing_deduct / other_deduct（PRD §23.1 种子）；
 * - rule_value JSON 格式：{"score": 100} 或 {"deduct": 40}；
 * - factory_calibration_flag=true 表示该条规则仅在对应 factory 生效（校准规则）；
 * - factory 字段 NULL 表示全局默认规则（base 等通用规则）；
 * - version + effective_from / effective_to 支持规则版本生效窗口。
 */
public class CpsScoringRule {

    public static final String RULE_KEY_BASE = "base";
    public static final String RULE_KEY_CONTENT_MISMATCH_DEDUCT = "content_mismatch_deduct";
    public static final String RULE_KEY_EVIDENCE_VAGUE_DEDUCT = "evidence_vague_deduct";
    public static final String RULE_KEY_KEYWORDS_MISSING_DEDUCT = "keywords_missing_deduct";
    public static final String RULE_KEY_OTHER_DEDUCT = "other_deduct";

    private Long id;
    private String ruleKey;
    private String factory;
    private String ruleValue;
    private Integer version;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean factoryCalibrationFlag;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getRuleValue() { return ruleValue; }
    public void setRuleValue(String ruleValue) { this.ruleValue = ruleValue; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public Boolean getFactoryCalibrationFlag() { return factoryCalibrationFlag; }
    public void setFactoryCalibrationFlag(Boolean factoryCalibrationFlag) {
        this.factoryCalibrationFlag = factoryCalibrationFlag;
    }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
