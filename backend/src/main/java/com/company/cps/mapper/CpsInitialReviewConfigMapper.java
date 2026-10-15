package com.company.cps.mapper;

import com.company.cps.domain.CpsInitialReviewTriggerConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CpsInitialReviewConfigMapper {

    /** 读取单行 GLOBAL 触发配置（无行=NULL，调用方回退应用配置）。 */
    CpsInitialReviewTriggerConfig findGlobal();

    /** 兜底插入 GLOBAL 行（正常由迁移种子；行被删后 updateConfig 自愈）。 */
    int insertGlobal(CpsInitialReviewTriggerConfig config);

    /** admin 运行时更新触发配置（单行 GLOBAL）。 */
    int updateGlobal(CpsInitialReviewTriggerConfig config);
}
