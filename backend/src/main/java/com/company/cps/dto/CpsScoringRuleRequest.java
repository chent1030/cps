package com.company.cps.dto;

import java.time.LocalDate;

/** B8 评分规则 PUT 请求（按 rule_key 全量替换 value/effective 窗口）。 */
public class CpsScoringRuleRequest {

    private String ruleKey;
    private String factory;
    private String ruleValue;
    private Integer version;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean factoryCalibrationFlag;
    private String updatedBy;

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
}
