-- ============================================================================
-- Phase B2: 巡检问题服务端草稿（防止用户一次填不完）
-- 设计：一个用户可有多份 ACTIVE 草稿；draft_data 存完整表单快照（JSON）；
--       Android 端「暂存」按钮落库，恢复时整包回填 store；提交成功后由
--       CpsIssueService.createIssue 按 draftId 一并清理（同事务，防孤儿草稿）。
-- ============================================================================

CREATE TABLE cps_issue_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  creator_emp_no VARCHAR(40) NOT NULL COMMENT '草稿归属人工号（移动端当前用户）',
  creator_emp_name VARCHAR(80) NULL COMMENT '草稿归属人姓名（冗余展示用）',
  title VARCHAR(200) NULL COMMENT '草稿摘要（列表展示：位置+类型+描述截断）',
  inspection_type_code VARCHAR(40) NULL COMMENT '巡检类型编码（快照冗余，便于列表筛选）',
  factory VARCHAR(40) NULL COMMENT '工厂（快照冗余）',
  area VARCHAR(40) NULL COMMENT '区域（快照冗余）',
  draft_data JSON NOT NULL COMMENT '完整表单快照 JSON：{location,description,resourceNo,inspectionTypeCode,extraData,feedbackEmpNo,feedbackPerson,images,imagePreviewSources,aiSuggestion}',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE=编辑中 / SUBMITTED=已提交归档 / DISCARDED=已废弃',
  submitted_issue_id BIGINT NULL COMMENT '提交成功后对应的问题 ID（SUBMITTED 时回填）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次暂存时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次暂存时间',
  KEY idx_draft_creator_status (creator_emp_no, status),
  KEY idx_draft_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CPS 巡检问题服务端草稿';
