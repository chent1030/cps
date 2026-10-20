-- ============================================================
-- Phase A1.1: 密码登录 + 页面权限 + 审计日志
-- 给 cps_admin_user 加密码/状态/权限字段；新建 cps_admin_audit_log
-- 现有账号（陈涛 61016968）首次迁移默认密码 = ChangeMe@123
-- 必须登录后修改密码（must_change_password=1）；赋所有页面权限
-- ============================================================

-- 1) cps_admin_user 加字段
ALTER TABLE cps_admin_user
  ADD COLUMN password_hash       VARCHAR(128) NOT NULL DEFAULT ''      COMMENT 'SHA-256(密码+":"+salt) 十六进制',
  ADD COLUMN salt                VARCHAR(32)  NOT NULL DEFAULT ''      COMMENT '每用户独立 salt',
  ADD COLUMN must_change_password TINYINT(1)  NOT NULL DEFAULT 1       COMMENT '1=必须修改密码（首次登录/管理员重置后）',
  ADD COLUMN status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LOCKED',
  ADD COLUMN page_permissions    JSON         NULL                      COMMENT '允许访问的页面 key 数组；["*"] 表示超管（所有页面）',
  ADD COLUMN remark              VARCHAR(200) NULL                      COMMENT '备注',
  ADD COLUMN last_login_at       DATETIME     NULL                      COMMENT '最近一次登录时间';

-- 2) 给已有账号写入默认密码 + 全权限（首次登录强制改密）
--    密码 = "ChangeMe@123"   salt = 固定迁移 salt
--    hash = CpsPasswordUtil.hash() 的输出：
--           SHA-256 迭代 10000 次 (plain + ":" + salt) 的十六进制
--    预计算值 = 96c729a07f22d08c78628477e2a6948ede5a456db46529877dee002ebdb09f96
UPDATE cps_admin_user
   SET password_hash        = '96c729a07f22d08c78628477e2a6948ede5a456db46529877dee002ebdb09f96',
       salt                 = 'CPS_MIGRATION_2026',
       must_change_password = 1,
       status               = 'ACTIVE',
       page_permissions     = JSON_ARRAY('*')
 WHERE password_hash = '' OR password_hash IS NULL;

-- 3) 新建审计日志表
CREATE TABLE cps_admin_audit_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT                  COMMENT '主键',
  emp_no        VARCHAR(40)  NOT NULL                              COMMENT '操作人（系统操作则为 SYSTEM）',
  emp_name      VARCHAR(80)  NULL                                  COMMENT '操作人姓名（冗余）',
  action        VARCHAR(40)  NOT NULL                              COMMENT 'LOGIN_SUCCESS/LOGIN_FAIL/LOGOUT/PASSWORD_CHANGE/USER_CREATE/USER_UPDATE/USER_DISABLE/USER_ENABLE/USER_RESET_PASSWORD/PERMISSION_CHANGE',
  target_type   VARCHAR(40)  NULL                                  COMMENT 'USER/AUTH/PERMISSION 等',
  target_id     VARCHAR(64)  NULL                                  COMMENT '目标对象 ID（账号 emp_no 或 IP 等）',
  detail        VARCHAR(2000) NULL                                 COMMENT '变更前/后 JSON 或可读说明',
  ip            VARCHAR(64)  NULL                                  COMMENT '客户端 IP',
  user_agent    VARCHAR(255) NULL                                  COMMENT '浏览器 UA',
  status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS'            COMMENT 'SUCCESS/FAILED',
  error_message VARCHAR(500) NULL                                  COMMENT '失败原因',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '发生时间',
  INDEX idx_audit_emp_created   (emp_no, created_at),
  INDEX idx_audit_action_created (action, created_at),
  INDEX idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '管理端审计日志';
