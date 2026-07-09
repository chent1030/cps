# CPS 巡检移动端与后端设计

## 1. 范围

本设计覆盖 CPS 巡检的移动端 Vue3 页面、后端 Spring Boot + MyBatis 业务逻辑、流程状态机、数据表、接口、AI 接入点与素材沉淀。

不包含 PC 管理后台、登录认证、用户体系实现。基础数据由已有管理后台维护，本系统读取或对接使用。后端默认每次请求都能获取当前用户 `empNo`。

## 2. 业务流程

主流程：

```text
现场稽查提交问题
-> 反馈节点操作人回复原因&措施，并指定责任员工
-> 责任员工现场整改，并选择上传整改照片的下一节点操作人
-> 上传节点操作人上传整改照片
-> 审核专员关闭，或退回上传整改照片节点
```

每个处理中节点都有一个当前操作人 `currentHandlerEmpNo`。移动端是否允许操作不由前端硬编码判断，而由后端根据状态机返回 `availableActions`。

## 3. 流程状态机

### 3.1 状态

```text
PENDING_FEEDBACK      待反馈人回复原因措施并指定责任员工
PENDING_RECTIFY       待责任员工整改
PENDING_UPLOAD_PROOF  待上传整改照片
PENDING_REVIEW        待审核
CLOSED                已关闭
```

可选扩展状态：

```text
DRAFT                 草稿
CANCELLED             作废
```

### 3.2 动作

```text
SUBMIT                提交问题并派发反馈人
REPLY_ASSIGN          回复原因措施并指定责任员工
RECTIFY               完成现场整改并指定上传照片操作人
UPLOAD_PROOF          上传整改照片
REVIEW_CLOSE          审核关闭
REVIEW_REJECT         审核退回到上传整改照片节点
TRANSFER              当前节点转办
```

### 3.3 流转规则

```text
SUBMIT
-> PENDING_FEEDBACK
-> currentHandlerEmpNo = 按工厂+区域+拉线+工序匹配出的反馈人，可手动修改

PENDING_FEEDBACK + REPLY_ASSIGN
-> PENDING_RECTIFY
-> currentHandlerEmpNo = responsibleEmpNo

PENDING_RECTIFY + RECTIFY
-> PENDING_UPLOAD_PROOF
-> currentHandlerEmpNo = proofEmpNo

PENDING_UPLOAD_PROOF + UPLOAD_PROOF
-> PENDING_REVIEW
-> currentHandlerEmpNo = reviewerEmpNo

PENDING_REVIEW + REVIEW_CLOSE
-> CLOSED
-> currentHandlerEmpNo = null

PENDING_REVIEW + REVIEW_REJECT
-> PENDING_UPLOAD_PROOF
-> currentHandlerEmpNo = proofEmpNo

任意处理中状态 + TRANSFER
-> 状态不变
-> currentHandlerEmpNo = targetEmpNo
```

### 3.4 权限校验

统一校验规则：

```text
当前 empNo 必须等于 currentHandlerEmpNo
关闭状态不允许操作
动作必须属于当前状态允许动作
动作所需字段必须完整
```

移动端详情接口返回：

```json
{
  "status": "PENDING_FEEDBACK",
  "currentHandlerEmpNo": "E10001",
  "availableActions": ["REPLY_ASSIGN", "TRANSFER"]
}
```

## 4. 移动端 Vue3 设计

### 4.1 页面

```text
/issue/create        新建问题
/issue/list          我的问题
/issue/detail/:id    问题详情
```

### 4.2 我的问题

列表 Tab：

```text
待我处理
我发起的
我参与的
已关闭
```

列表卡片字段：

```text
问题编号
当前状态
工厂 / 区域 / 拉线 / 工序
一级分类 / 二级分类
问题描述摘要
当前操作人
提交时间
是否超时
```

查询逻辑：

```text
待我处理：currentHandlerEmpNo = 当前 empNo 且状态未关闭
我发起的：creatorEmpNo = 当前 empNo
我参与的：流程日志中 operatorEmpNo = 当前 empNo，或主表参与人字段包含当前 empNo
已关闭：与当前用户相关且 status = CLOSED
```

### 4.3 新建问题

字段：

```text
问题图片：最多5张，第1张用于AI识别
工厂：单选
区域：按工厂联动
拉线：选择区域后显示
工序：选择区域后显示，可按拉线继续联动
一级分类：AI可识别预填，用户可修改
二级分类：AI可识别预填，用户可修改，固定字典选择
问题描述：文本输入 + 语音转文字填充
反馈人：按工厂+区域+拉线+工序匹配，可手动修改
提交并派发
```

交互顺序：

```text
1. 用户拍照或选择图片，最多5张。
2. 第一张图片上传成功后触发AI识别。
3. AI返回一级分类建议、二级分类建议、原因建议、措施建议。
4. 用户选择工厂、区域、拉线、工序。
5. 后端根据位置匹配反馈人。
6. 用户确认或修改一级/二级分类，填写问题描述。
7. 提交问题。
```

原因&措施不在新建页填写，只保存 AI 建议。反馈节点操作人处理时展示 AI 建议，并填写最终原因分析与整改措施。

### 4.4 问题详情

详情页区域：

```text
基础信息
- 编号、状态、位置、分类、描述、提交人、提交时间

图片区
- 问题图片
- 整改图片

AI识别信息
- AI建议一级分类
- AI建议二级分类
- 人工最终一级分类
- 人工最终二级分类
- AI原因建议
- AI措施建议

流程处理区
- 当前状态说明
- 当前操作人
- 可操作按钮

流转记录
- 提交、转办、回复原因措施、整改、上传照片、审核、退回
```

状态表单：

```text
PENDING_FEEDBACK
- 原因分析
- 整改措施
- 责任员工
- 提交
- 转办

PENDING_RECTIFY
- 整改说明
- 上传整改照片节点操作人
- 完成整改
- 转办

PENDING_UPLOAD_PROOF
- 整改照片，最多5张
- 上传提交
- 转办

PENDING_REVIEW
- 审核意见
- 关闭
- 退回
- 转办，可选
```

### 4.5 前端实现建议

```text
Vue3 + TypeScript
Vue Router 管页面
Pinia 管当前用户、字典缓存、位置缓存
```

建议组件：

```text
IssueForm
ImageUploader
LocationSelector
CategorySelector
ActionPanel
FlowTimeline
```

前端限制图片数量，后端必须二次校验。

## 5. 后端模块

```text
issue            问题单主流程
workflow         状态机、动作校验、流转日志
attachment       图片上传与附件关联
masterdata       工厂/区域/拉线/工序/人员/分类字典查询
assignment       反馈人、审核人匹配
ai               图片识别、分类建议、原因措施建议、素材沉淀
reminder         超时提醒与升级
```

## 6. 数据表设计

### 6.1 cps_issue

问题主表：

```text
id
issue_no
status
factory_id
area_id
line_id
process_id
ai_category_l1_id
ai_category_l2_id
category_l1_id
category_l2_id
category_modified_flag
description
creator_emp_no
feedback_emp_no
responsible_emp_no
proof_emp_no
reviewer_emp_no
current_handler_emp_no
reason_analysis
corrective_measure
rectify_remark
review_opinion
submit_time
close_time
created_at
updated_at
```

字段说明：

```text
ai_category_l1_id / ai_category_l2_id：AI识别建议
category_l1_id / category_l2_id：人工最终选择
category_modified_flag：人工最终分类是否与AI建议不同
current_handler_emp_no：当前节点操作人
```

### 6.2 cps_issue_attachment

附件表：

```text
id
issue_id
stage              ISSUE / PROOF
file_url
file_name
file_type
sort_no
created_by
created_at
```

规则：

```text
同一 issue_id + stage 最多5张
stage = ISSUE 且 sort_no = 1 的图片才触发AI识别
stage = PROOF 的整改照片不做AI识别
```

### 6.3 cps_issue_ai_suggestion

AI 建议表，单独用于后续优化：

```text
id
issue_id
source_attachment_id
ai_category_l1_id
ai_category_l1_name
ai_category_l2_id
ai_category_l2_name
reason_suggestion
measure_suggestion
model_name
model_version
raw_request
raw_response
confidence
created_at
```

反馈节点展示最新 AI 建议。人工最终原因措施写入 `cps_issue.reason_analysis` 和 `cps_issue.corrective_measure`。

### 6.4 cps_issue_flow_log

流程日志：

```text
id
issue_id
from_status
to_status
action
operator_emp_no
from_handler_emp_no
to_handler_emp_no
comment
snapshot_json
created_at
```

`snapshot_json` 记录本次动作提交的关键字段快照，用于审计和追溯。

### 6.5 cps_reminder_rule

提醒规则表，由现有后台维护或本系统只读：

```text
id
node_status
duration_days
need_escalation
escalation_level
enabled
```

## 7. 反馈人与审核人匹配

反馈人匹配不包含问题分类，只按位置维度：

```text
工厂 + 区域 + 拉线 + 工序 -> 反馈人
```

建议采用逐级兜底：

```text
1. 工厂 + 区域 + 拉线 + 工序
2. 工厂 + 区域 + 拉线
3. 工厂 + 区域
4. 工厂
```

移动端允许手动修改反馈人。

审核专员匹配可以按工厂、区域或固定配置实现，具体由已有基础数据决定。

## 8. 接口设计

### 8.1 问题接口

```text
POST /api/cps/issues
提交问题

GET /api/cps/issues
我的问题列表，支持 tab/status/keyword/page

GET /api/cps/issues/{id}
问题详情，返回详情 + 附件 + AI建议 + 流程日志 + availableActions

POST /api/cps/issues/{id}/actions
统一流程动作接口
```

动作接口示例：

```json
{
  "action": "REPLY_ASSIGN",
  "reasonAnalysis": "来料混料，现场标识不清",
  "correctiveMeasure": "重新隔离物料并补充标识",
  "responsibleEmpNo": "E10023",
  "comment": "请今天内处理"
}
```

返回：

```json
{
  "issueId": 123,
  "status": "PENDING_RECTIFY",
  "currentHandlerEmpNo": "E10023",
  "availableActions": []
}
```

### 8.2 附件接口

```text
POST /api/cps/attachments
上传图片
```

前端上传后拿到附件 ID，提交问题或提交整改照片时带上附件 ID 列表。后端根据顺序写入 `sort_no`。

### 8.3 基础数据接口

```text
GET /api/cps/master/factories
GET /api/cps/master/areas?factoryId=
GET /api/cps/master/lines?areaId=
GET /api/cps/master/processes?areaId=&lineId=
GET /api/cps/master/categories?parentId=
```

二级分类固定字典选择，通过 `parentId` 按一级分类联动。

### 8.4 派发接口

```text
GET /api/cps/assignment/feedback-handler?factoryId=&areaId=&lineId=&processId=
GET /api/cps/assignment/reviewer?factoryId=&areaId=
```

## 9. AI 与语音

### 9.1 图片识别

规则：

```text
问题图片最多5张
只有第一张图片进入AI识别
AI识别结果包含一级分类、二级分类、原因建议、措施建议
一级分类和二级分类都允许人工修改
```

建议流程：

```text
1. 前端上传第一张问题图片。
2. 前端调用AI识别接口。
3. 后端调用AI服务并返回建议。
4. 提交问题时，后端落 cps_issue 和 cps_issue_ai_suggestion。
```

如果不做草稿记录，AI 建议可先由前端暂存，提交问题时一起传给后端。

### 9.2 语音转文字

问题描述支持语音输入转文字。移动端可以调用浏览器、企业应用容器或后端语音接口，最终将转写文本写入 `description`。

## 10. 素材沉淀

流程关闭后，这些数据成为后续 AI 优化素材：

```text
问题第一张图片
AI建议一级分类
AI建议二级分类
人工最终一级分类
人工最终二级分类
AI原因建议
AI措施建议
人工原因分析
人工整改措施
审核结果
流程耗时
```

前期可不单独建训练样本表，直接通过以下表关联查询：

```text
cps_issue
cps_issue_attachment
cps_issue_ai_suggestion
cps_issue_flow_log
```

如果后续训练需要稳定快照，再增加 `cps_ai_training_sample`。

## 11. 校验规则

```text
提交问题：
- 问题图片 1-5 张
- 工厂、区域、拉线、工序必填
- 一级分类、二级分类必填
- 问题描述必填
- 反馈人必填

回复并指派：
- 当前 empNo 必须是 currentHandlerEmpNo
- 原因分析必填
- 整改措施必填
- 责任员工必填

整改：
- 当前 empNo 必须是 currentHandlerEmpNo
- 整改说明可选
- 上传整改照片节点操作人必填

上传整改照片：
- 当前 empNo 必须是 currentHandlerEmpNo
- 整改照片 1-5 张

审核：
- 当前 empNo 必须是 currentHandlerEmpNo
- 关闭或退回必须写流程日志
```

## 12. 待确认项

以下内容不影响主流程设计，但实现前需要在原项目中对齐：

```text
审核专员匹配规则
工序是否必须依赖拉线联动
附件存储方式
AI服务接口协议
语音转文字实现方式
超时提醒的通知渠道
```
