-- =====================================================================
-- 点检项照片分类：补齐存量英文编码，修复「编辑老点检项报 400」
--
-- 背景：cps_check_item 里 J-C1/J-C2/J-C3 的 photo_category 是早期英文编码
--       GROUND / FIRE_EXTINGUISHER / PANEL；而 PHOTO_CATEGORY 字典在
--       V20261016 拆表后只剩中文条目（现场全景/设备状态/消防器材/仪表读数），
--       CpsCheckItemService 对 photoCategory 做「启用字典强校验」，
--       于是编辑这些历史点检项会返回 400：
--       「photoCategory not in enabled dict PHOTO_CATEGORY: GROUND」。
--
-- 处理：只做加法——把三个历史编码补进 PHOTO_CATEGORY 字典（非内置、可停用），
--       不改动任何点检项业务数据，也不放宽后端校验口径。
-- 幂等：NOT EXISTS 保证重复执行（或手工重跑）不会插入重复条目。
-- =====================================================================

INSERT INTO cps_dict (type_id, item_value, item_label, sort_no, builtin, remark, created_by, updated_by)
SELECT t.id, v.item_value, v.item_label, v.sort_no, 0,
       '历史点检项照片分类（兼容存量英文编码）', 'SYSTEM', 'SYSTEM'
FROM cps_dict_type t
JOIN (
    SELECT 'GROUND'            AS item_value, '地面'   AS item_label, 11 AS sort_no
    UNION ALL SELECT 'FIRE_EXTINGUISHER', '灭火器', 12
    UNION ALL SELECT 'PANEL',             '配电柜', 13
) v
WHERE t.type_code = 'PHOTO_CATEGORY'
  AND NOT EXISTS (
      SELECT 1 FROM cps_dict d
      WHERE d.type_id = t.id AND d.item_value = v.item_value
  );
