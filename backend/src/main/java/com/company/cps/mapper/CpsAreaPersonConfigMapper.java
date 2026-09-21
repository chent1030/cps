package com.company.cps.mapper;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.dto.CpsOptionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsAreaPersonConfigMapper {

    List<CpsAreaPersonConfig> findFeedbackCandidates(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process
    );

    List<CpsAreaPersonConfig> findReviewerCandidates(@Param("factory") String factory, @Param("area") String area);

    List<CpsAreaPersonConfig> findAll(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("enabled") Boolean enabled
    );

    List<CpsAreaPersonConfig> findAdminPage(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<CpsAreaPersonConfig> findAdminExport(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    long countAdmin(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line,
            @Param("process") String process,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    Optional<CpsAreaPersonConfig> findById(@Param("id") Long id);

    Optional<CpsAreaPersonConfig> findOperator(@Param("empNo") String empNo, @Param("empName") String empName);

    int upsert(CpsAreaPersonConfig config);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    List<CpsOptionResponse> findFactoryOptions();

    List<CpsOptionResponse> findAreaOptions(@Param("factory") String factory);

    List<CpsOptionResponse> findLineOptions(@Param("factory") String factory, @Param("area") String area);

    List<CpsOptionResponse> findProcessOptions(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("line") String line
    );
}
