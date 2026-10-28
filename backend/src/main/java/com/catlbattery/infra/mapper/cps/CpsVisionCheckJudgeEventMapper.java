package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsVisionCheckJudgeEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsVisionCheckJudgeEventMapper {

    int insert(CpsVisionCheckJudgeEvent event);

    List<CpsVisionCheckJudgeEvent> findByFingerprint(@Param("fingerprint") String fingerprint);
}