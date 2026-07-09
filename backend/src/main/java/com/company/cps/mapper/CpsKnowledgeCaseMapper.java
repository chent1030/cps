package com.company.cps.mapper;

import com.company.cps.domain.CpsKnowledgeCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsKnowledgeCaseMapper {

    /**
     * 根据案例 ID 查询启用状态的知识库案例。
     */
    Optional<CpsKnowledgeCase> findEnabledById(@Param("id") Long id);

    /**
     * 根据案例 ID 查询知识库案例，不限制启停状态。
     */
    Optional<CpsKnowledgeCase> findById(@Param("id") Long id);

    /**
     * 根据案例 ID 列表批量查询启用状态的知识库案例。
     */
    List<CpsKnowledgeCase> findEnabledByIds(@Param("ids") List<Long> ids);

    /**
     * 查询知识库案例维护列表，可按启停状态过滤。
     */
    List<CpsKnowledgeCase> findAll(@Param("enabled") Boolean enabled);

    /**
     * 新增或更新知识库案例主信息，原因和措施不保存在主表。
     */
    int upsert(CpsKnowledgeCase item);

    /**
     * 启用或停用知识库案例。
     */
    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
}
