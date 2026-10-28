-- 早期上传路径把「对象键」（cps/xxx.jpg）直接写进了照片/附件列，读取侧长期背着「URL 还是键」的分支。
-- 现在统一为「库里只存可公开访问的绝对 URL」，这里把存量裸对象键补上默认 MinIO 前缀（不改列名、不改语义列名）。
--
-- 默认部署（application.yml：endpoint/public-url = http://127.0.0.1:9000，bucket = cps-attachments）可直接执行。
-- 若环境改了 CPS_STORAGE_PUBLIC_URL / CPS_STORAGE_BUCKET，请改用等价语句，例如：
--   UPDATE cps_inspection_record_item SET photo_object_key = CONCAT('https://minio.example.com/你的桶/', photo_object_key)
--   WHERE photo_object_key IS NOT NULL AND photo_object_key NOT LIKE 'http%';
-- 语句幂等：只处理不以 http 开头的值；updated_at = updated_at 避免清理存量污染审计时间戳。

ALTER TABLE cps_inspection_record_item
    MODIFY COLUMN photo_object_key VARCHAR(2000) NULL COMMENT '照片绝对 URL，多张以逗号分隔';
ALTER TABLE cps_room_check_record_item
    MODIFY COLUMN photo_object_key VARCHAR(2000) NULL COMMENT '照片绝对 URL，多张以逗号分隔';

UPDATE cps_inspection_record_item
SET photo_object_key = CONCAT('http://127.0.0.1:9000/cps-attachments/', photo_object_key),
    updated_at = updated_at
WHERE photo_object_key IS NOT NULL AND photo_object_key NOT LIKE 'http%';

UPDATE cps_room_check_record_item
SET photo_object_key = CONCAT('http://127.0.0.1:9000/cps-attachments/', photo_object_key),
    updated_at = updated_at
WHERE photo_object_key IS NOT NULL AND photo_object_key NOT LIKE 'http%';

UPDATE cps_vision_check_record
SET photo_url = CONCAT('http://127.0.0.1:9000/cps-attachments/', photo_url),
    updated_at = updated_at
WHERE photo_url IS NOT NULL AND photo_url NOT LIKE 'http%';

UPDATE cps_vision_check_record
SET photo_object_key = CONCAT('http://127.0.0.1:9000/cps-attachments/', photo_object_key),
    updated_at = updated_at
WHERE photo_object_key IS NOT NULL AND photo_object_key NOT LIKE 'http%';

UPDATE cps_issue_attachment
SET file_url = CONCAT('http://127.0.0.1:9000/cps-attachments/', file_url)
WHERE file_url IS NOT NULL AND file_url NOT LIKE 'http%';

UPDATE cps_knowledge_case_image
SET file_url = CONCAT('http://127.0.0.1:9000/cps-attachments/', file_url),
    updated_at = updated_at
WHERE file_url IS NOT NULL AND file_url NOT LIKE 'http%';
