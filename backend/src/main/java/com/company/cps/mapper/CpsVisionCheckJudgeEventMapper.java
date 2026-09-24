package com.company.cps.mapper;

import com.company.cps.domain.CpsVisionCheckJudgeEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsVisionCheckJudgeEventMapper {

    int insert(CpsVisionCheckJudgeEvent event);

    List<CpsVisionCheckJudgeEvent> findByFingerprint(@Param("fingerprint") String fingerprint);
}