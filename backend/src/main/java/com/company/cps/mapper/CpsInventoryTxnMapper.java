package com.company.cps.mapper;

import com.company.cps.domain.CpsInventoryTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** E2 出入库流水（cps_inventory_txn；AC-33）。 */
@Mapper
public interface CpsInventoryTxnMapper {

    int insert(CpsInventoryTxn txn);

    /** 管理端流水分页查询（itemId/txnType 过滤，id 倒序最新在前；联查 item 冗余列）。 */
    List<CpsInventoryTxn> page(
            @Param("itemId") Long itemId,
            @Param("txnType") String txnType,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long count(@Param("itemId") Long itemId, @Param("txnType") String txnType);
}
