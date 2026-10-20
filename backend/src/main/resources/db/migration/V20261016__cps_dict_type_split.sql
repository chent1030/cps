-- 波次：字典双表化（类型表 + 条目表）
-- 口径编号：B1-DICT-02
-- 背景：V20261015 落地的 cps_dict 为单表（dict_type 字符串列），类型中文名/说明/启停无元数据落库，
--       扩展自定义类型时页面只能显示类型原码。本迁移拆为经典双表：
--       cps_dict_type（类型：编码/中文名/说明/内置保护/启停/排序）
--       cps_dict（条目：挂 type_id 外键，UNIQUE(type_id, item_value)）
-- 搬迁策略：先建类型表并按编码回填 type_id，再收紧约束并删除 dict_type 字符串列（不留冗余，避免双写漂移）。

CREATE TABLE cps_dict_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    type_code   VARCHAR(64)  NOT NULL COMMENT '类型编码，大写蛇形，如 ROOM_TYPE',
    type_name   VARCHAR(128) NOT NULL COMMENT '类型中文名，如 房型',
    description VARCHAR(255) NULL COMMENT '用途说明',
    builtin     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '内置类型：不可删/不可停/不可改码（可改名称/说明/排序）',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用：停用后该组不再进入下拉与后端校验',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_by  VARCHAR(64)  NULL COMMENT '创建人',
    updated_by  VARCHAR(64)  NULL COMMENT '更新人',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cps_dict_type_code (type_code)
) COMMENT ='字典类型表';

INSERT INTO cps_dict_type (type_code, type_name, description, builtin, enabled, sort_no, created_by, updated_by) VALUES
('ROOM_TYPE',      '房型',     '房间房型与点检项适用房型（后端强校验）', 1, 1, 1, 'SYSTEM', 'SYSTEM'),
('RISK_LEVEL',     '风险等级', '房间风险等级（内置三项不可删/停）',       1, 1, 2, 'SYSTEM', 'SYSTEM'),
('PHOTO_CATEGORY', '照片分类', '点检项拍照分类（后端强校验）',           1, 1, 3, 'SYSTEM', 'SYSTEM'),
('PLAN_TYPE',      '计划类型', '巡检计划类型下拉（仅展示，后端不校验）',  1, 1, 4, 'SYSTEM', 'SYSTEM'),
('FREQUENCY',      '频次',     '任务频次下拉（仅展示，后端不校验）',      1, 1, 5, 'SYSTEM', 'SYSTEM');

-- 条目表挂外键：先加可空列，按编码回填，再收紧为 NOT NULL + UNIQUE + FK，最后删除字符串列与旧唯一键
ALTER TABLE cps_dict ADD COLUMN type_id BIGINT NULL AFTER id;

UPDATE cps_dict i JOIN cps_dict_type t ON t.type_code = i.dict_type SET i.type_id = t.id;

ALTER TABLE cps_dict
    MODIFY COLUMN type_id BIGINT NOT NULL COMMENT '字典类型 id（cps_dict_type.id）',
    ADD UNIQUE KEY uk_cps_dict_type_item (type_id, item_value),
    ADD CONSTRAINT fk_cps_dict_type FOREIGN KEY (type_id) REFERENCES cps_dict_type (id),
    DROP INDEX uk_cps_dict_type_value,
    DROP COLUMN dict_type;
