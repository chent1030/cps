-- 文件存储改为 MinIO 后，附件直接返回绝对 URL，前端把多张照片以逗号拼接落库。
-- 单张 URL 约 85 字符，3 张即超过原 varchar(255)，MySQL 严格模式下会报 Data too long，这里统一放宽。
ALTER TABLE cps_inspection_record_item
    MODIFY COLUMN photo_object_key VARCHAR(2000) NULL COMMENT '照片对象键或绝对 URL，多张以逗号分隔',
    MODIFY COLUMN photo_file_name VARCHAR(2000) NULL COMMENT '照片文件名，多张以逗号分隔';

ALTER TABLE cps_room_check_record_item
    MODIFY COLUMN photo_object_key VARCHAR(2000) NULL COMMENT '照片对象键或绝对 URL，多张以逗号分隔',
    MODIFY COLUMN photo_file_name VARCHAR(2000) NULL COMMENT '照片文件名，多张以逗号分隔';

ALTER TABLE cps_vision_check_record
    MODIFY COLUMN photo_object_key VARCHAR(2000) NULL COMMENT '照片对象键或绝对 URL，多张以逗号分隔';
