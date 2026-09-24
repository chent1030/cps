package com.company.cps.mapper;

import com.company.cps.domain.CpsScoringRule;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CpsScoringRuleMapper {

    /** 全量规则（管理端 GET 用，无 factory 过滤）。 */
    List<CpsScoringRule> findAll();

    /** 单条规则（按主键）。 */
    CpsScoringRule findById(@Param("id") Long id);

    /**
     * 按 ruleKey + factory + as-of-date 选最高 version 且 effective 窗口内：
     * - factory=NULL 视为全局默认；
     * - factory 非空 → 工厂校准规则。
     * 服务层先查 factory 非空（命中即用），否则回退 factory=NULL。
     */
    CpsScoringRule findEffectiveByKeyFactory(@Param("ruleKey") String ruleKey,
                                              @Param("factory") String factory,
                                              @Param("asOfDate") LocalDate asOfDate);

    /** 同 ruleKey 的所有规则（含历史版本）按 version DESC 取最近一条（无论 factory）。 */
    CpsScoringRule findLatestByKey(@Param("ruleKey") String ruleKey);

    /** 工厂校准规则：factory 非空 + factory_calibration_flag=1。 */
    List<CpsScoringRule> findFactoryCalibrations(@Param("factory") String factory);

    /** Upsert：按 (rule_key, factory) UNIQUE，命中则更新全部字段；否则 INSERT。 */
    int upsert(CpsScoringRule rule);
}
