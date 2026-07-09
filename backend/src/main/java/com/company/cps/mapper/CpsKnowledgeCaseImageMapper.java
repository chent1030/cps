package com.company.cps.mapper;

import com.company.cps.domain.CpsKnowledgeCaseImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsKnowledgeCaseImageMapper {

    /**
     * 根据素材图片 ID 查询知识库图片记录，并带出案例分类信息。
     */
    Optional<CpsKnowledgeCaseImage> findById(@Param("id") Long id);

    /**
     * 根据案例 ID 查询素材图片列表。
     */
    List<CpsKnowledgeCaseImage> findByCaseId(@Param("caseId") Long caseId);

    /**
     * 根据素材图片 ID 列表批量查询启用案例下的图片记录，并带出原因、措施和分类信息。
     */
    List<CpsKnowledgeCaseImage> findEnabledByIds(@Param("ids") List<Long> ids);

    /**
     * 查询需要同步到向量库的知识库图片。
     */
    List<CpsKnowledgeCaseImage> findSyncCandidates(
            @Param("dimension") Integer dimension,
            @Param("limit") int limit
    );

    /**
     * 新增或更新知识库素材图片；原因和措施跟图片绑定。
     */
    int upsert(CpsKnowledgeCaseImage item);

    /**
     * 将素材图片标记为待同步向量。
     */
    int markVectorPending(@Param("id") Long id);

    /**
     * 标记知识库图片开始向量同步。
     */
    int markVectorProcessing(@Param("id") Long id);

    /**
     * 标记知识库图片向量同步成功，并保存 Milvus 向量 ID 和维度。
     */
    int markVectorSuccess(
            @Param("id") Long id,
            @Param("vectorId") String vectorId,
            @Param("dimension") Integer dimension
    );

    /**
     * 标记知识库图片向量同步失败，并记录失败原因。
     */
    int markVectorFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);
}
