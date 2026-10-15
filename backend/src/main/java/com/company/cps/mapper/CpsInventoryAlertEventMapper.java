package com.company.cps.mapper;

import com.company.cps.domain.CpsInventoryAlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** E3 库存预警事件（cps_inventory_alert_event；AC-13 合并事件）。 */
@Mapper
public interface CpsInventoryAlertEventMapper {

    int insert(CpsInventoryAlertEvent event);

    int update(CpsInventoryAlertEvent event);

    /** 同物品当前 OPEN 事件（合并事件判定；UNIQUE(open_item_id) 兜底唯一）。 */
    Optional<CpsInventoryAlertEvent> findOpenByItemId(@Param("itemId") Long itemId);

    Optional<CpsInventoryAlertEvent> findById(@Param("id") Long id);

    /** 管理端预警分页列表（状态/物品过滤，last_eval_at 倒序；联查 item 冗余列）。 */
    List<CpsInventoryAlertEvent> page(
            @Param("status") String status,
            @Param("itemId") Long itemId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long count(@Param("status") String status, @Param("itemId") Long itemId);
}
