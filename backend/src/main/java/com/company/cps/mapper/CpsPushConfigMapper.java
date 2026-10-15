package com.company.cps.mapper;

import com.company.cps.domain.CpsPushConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CpsPushConfigMapper {

    /** 读取单行 GLOBAL 配置（无行=null，调用方回退"推送未配置"语义）。 */
    CpsPushConfig findGlobal();

    /**
     * Upsert 单行 GLOBAL（INSERT ... ON DUPLICATE KEY UPDATE）：
     * 始终写 id=CpsPushConfig.SINGLETON_ID，由 chk_cps_push_config_singleton 兜底单行约束。
     */
    int upsert(CpsPushConfig config);
}