# CPS 知识案例库与图片向量检索设计

## 目标

业务提供分类、标准照片素材、原因和措施后，系统在启动时把可用素材同步到 Milvus。员工填报时只识别第一张照片，通过 Milvus 查找相似标准素材，再回 MySQL 读取对应分类、原因和措施，作为 AI 建议返回给移动端。

## 架构

```text
MySQL 知识案例主数据
  -> Spring Boot 启动同步
  -> NewAPI 图片 embedding
  -> Milvus 图片向量索引

员工上传第一张照片
  -> Spring Boot 调 NewAPI 获取图片向量
  -> Milvus TopK 检索
  -> MySQL 查询命中案例
  -> 返回分类、原因、措施、置信度
  -> 员工确认结果写入 AI 匹配记录
```

## 关键原则

- MySQL 是主数据，保存分类、原因、措施、素材状态和员工确认结果。
- Milvus 只作为图片向量检索索引，保存图片 ID、案例 ID、分类 ID、启用状态和向量。
- 后端不部署模型，只调用 NewAPI 获取 image embedding。
- 标准素材和员工照片必须使用同一个模型、版本和预处理逻辑。
- Milvus collection 按模型版本隔离，例如 `cps_knowledge_image_vector_siglip2_v1`。
- 项目启动时只同步待同步、失败重试、文件变更或模型版本变更的素材，不能每次重复插入。
- 同步使用 upsert，Milvus 主键使用 `cps_knowledge_case_image.id`。
- 适用区域仅作为业务说明字段，不参与过滤。

## 数据表

### cps_knowledge_case

保存知识案例主数据。

```text
id
case_code
case_title
category_l1_id
category_l2_id
category_l1_name
category_l2_name
reason
measure
scope_remark
enabled
created_by
created_at
updated_at
```

### cps_knowledge_case_image

保存案例素材图片和向量同步状态。

```text
id
case_id
file_url
file_name
file_hash
last_vector_file_hash
milvus_vector_id
embedding_model
embedding_version
embedding_dim
vector_status: PENDING / PROCESSING / SUCCESS / FAILED
vector_error_msg
vector_retry_count
vector_updated_at
sort_no
created_at
updated_at
```

### cps_issue_ai_match

保存 AI 匹配建议和员工最终确认结果。

```text
id
issue_id
source_attachment_id
matched_case_id
confidence
ai_category_l1_id
ai_category_l2_id
ai_category_l1_name
ai_category_l2_name
reason_suggestion
measure_suggestion
topk_json
model_name
model_version
raw_request
raw_response
confirmed_category_l1_id
confirmed_category_l2_id
confirmed_reason
confirmed_measure
created_at
```

## Milvus Collection

```text
collection: cps_knowledge_image_vector_siglip2_v1

字段：
- id: Int64 primary key，对应 cps_knowledge_case_image.id
- case_id: Int64
- category_l1_id: Int64
- category_l2_id: Int64
- enabled: Bool
- embedding: FloatVector，维度按 NewAPI 返回模型配置固定
```

索引建议：

```text
metric_type: COSINE
index_type: HNSW
M: 16
efConstruction: 200
efSearch: 64 或 128
```

## 后端模块

```text
config
  CpsAiProperties
  CpsMilvusProperties

domain
  CpsKnowledgeCase
  CpsKnowledgeCaseImage
  CpsIssueAiMatch
  CpsVectorStatus

dto
  CpsKnowledgeMatchRequest
  CpsKnowledgeMatchResponse
  CpsMatchedCaseResponse

service
  ImageEmbeddingClient
  NewApiImageEmbeddingClient
  MilvusVectorService
  KnowledgeVectorSyncService
  CpsAiMatchService

bootstrap
  CpsKnowledgeVectorBootstrap
```

## 接口

### 员工拍照匹配

```text
POST /api/cps/ai/match-knowledge
```

入参：

```json
{
  "attachmentId": 501,
  "description": "周转箱没有状态标识"
}
```

出参：

```json
{
  "matchedCaseId": 12,
  "confidence": "0.86",
  "categoryL1Id": 100,
  "categoryL2Id": 101,
  "categoryL1Name": "现场 5S",
  "categoryL2Name": "标识缺失",
  "reasonSuggestion": "现场周转容器状态标识未及时补充。",
  "measureSuggestion": "补充状态标识并纳入班前点检。",
  "modelName": "google/siglip2-so400m-patch14-384",
  "modelVersion": "siglip2-v1",
  "matchedCases": []
}
```

### 手动同步单张素材

```text
POST /api/cps/knowledge/images/{imageId}/embedding
```

## 置信度规则

```text
confidence >= 0.85
  自动带出分类、原因、措施

0.65 <= confidence < 0.85
  带出建议，但前端提示请确认

confidence < 0.65
  不自动填原因措施，只展示候选或让员工手选
```

