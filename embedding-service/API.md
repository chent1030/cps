# Embedding Service 接口文档

## 1. 概述

- 默认地址：`http://<host>:8008`
- 内容类型：JSON 接口使用 `application/json`；文件接口使用 `multipart/form-data`。
- 图片模型：通过 `MODEL_PATH` 配置；请求中的 `model` 字段仅作为响应元数据返回，不会切换服务进程已加载的模型。
- 图片 Base64：支持裸 Base64 字符串和 `data:image/<type>;base64,...` 格式。

## 2. 鉴权

配置 `API_KEY` 后，除 `GET /health` 外的所有接口都需要以下请求头：

```http
Authorization: Bearer <API_KEY>
```

当 `API_KEY` 为空时，无需携带鉴权请求头。

## 3. 通用错误

| 状态码 | 含义 |
| --- | --- |
| `400` | 请求无效、图片无效、向量为空、标签列表为空、集合维度无效，或批量数量超过 `MAX_BATCH_SIZE`。 |
| `401` | 启用 `API_KEY` 时，未提供或提供了无效的 Bearer Token。 |
| `404` | 请求的 Milvus 集合不存在。 |
| `503` | PyMilvus 不可用，或 zero-shot 所需的分词器/处理器文件不可用。 |

## 4. 服务接口

### 4.1 健康检查

`GET /health`

无需鉴权。

响应示例：

```json
{
  "status": "ok",
  "model": "google/siglip2-so400m-patch14-384",
  "modelVersion": "siglip2-v1",
  "device": "cuda",
  "normalizeEmbeddings": true,
  "maxBatchSize": 8,
  "zeroShotAvailable": true,
  "zeroShotError": null,
  "milvusHost": "127.0.0.1",
  "milvusPort": 19530,
  "milvusCollection": "cps_knowledge_image_vector_siglip2_v1"
}
```

### 4.2 查询模型列表

`GET /v1/models`

响应示例：

```json
{
  "object": "list",
  "data": [
    {
      "id": "google/siglip2-so400m-patch14-384",
      "object": "model",
      "owned_by": "local"
    }
  ]
}
```

## 5. 图片向量接口

服务使用已加载的视觉模型，为每张输入图片返回一个 embedding 向量。设置 `NORMALIZE_EMBEDDINGS=true` 时，所有向量都会进行 L2 归一化。

通用响应：

```json
{
  "object": "list",
  "model": "google/siglip2-so400m-patch14-384",
  "model_version": "siglip2-v1",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0123, -0.0456]
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "total_tokens": 0
  }
}
```

### 5.1 使用 JSON Base64 生成向量

`POST /v1/embeddings`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `model` | string | 否 | 响应中的模型名称，默认使用 `MODEL_NAME`。 |
| `input` | array | 是 | 至少包含一张图片，最多 `MAX_BATCH_SIZE` 张。 |
| `input[].image_base64` | string | 是 | 裸 Base64 或 data URL。 |

```json
{
  "input": [
    { "image_base64": "data:image/jpeg;base64,/9j/4AAQ..." }
  ]
}
```

### 5.2 使用单个文件生成向量

`POST /v1/embeddings/file`

Multipart 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 图片文件。 |
| `model` | string | 否 | 响应中的模型名称。 |

```bash
curl http://127.0.0.1:8008/v1/embeddings/file \
  -H 'Authorization: Bearer change-me' \
  -F 'file=@./test.jpg'
```

### 5.3 使用多个文件生成向量

`POST /v1/embeddings/files`

Multipart 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `files` | file[] | 是 | 重复提交的图片文件字段，数量不能超过 `MAX_BATCH_SIZE`。 |
| `model` | string | 否 | 响应中的模型名称。 |

```bash
curl http://127.0.0.1:8008/v1/embeddings/files \
  -H 'Authorization: Bearer change-me' \
  -F 'files=@./a.jpg' \
  -F 'files=@./b.jpg'
```

## 6. Zero-shot 图片分类

Zero-shot 接口要求模型目录包含分词器和处理器文件。调用前请通过 `/health` 中的 `zeroShotAvailable` 确认功能可用。

通用响应：

```json
{
  "object": "zero_shot_image_classification",
  "model": "google/siglip2-so400m-patch14-384",
  "model_version": "siglip2-v1",
  "data": [
    { "label": "设备安全 防护缺失", "score": 0.83 },
    { "label": "现场 5S 标识缺失", "score": 0.17 }
  ]
}
```

`data` 按得分从高到低排序。候选标签中包含逗号时，服务会按逗号拆分并去除首尾空白。

### 6.1 使用 JSON Base64 分类

`POST /v1/zero-shot-image-classification`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `model` | string | 否 | 响应中的模型名称。 |
| `image_base64` | string | 是 | 裸 Base64 或 data URL。 |
| `candidate_labels` | string[] | 是 | 至少提供一个候选标签。 |

```json
{
  "image_base64": "data:image/jpeg;base64,/9j/4AAQ...",
  "candidate_labels": ["现场 5S 标识缺失", "设备安全 防护缺失"]
}
```

### 6.2 使用单个文件分类

`POST /v1/zero-shot-image-classification/file`

Multipart 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 图片文件。 |
| `candidate_labels` | string[] | 是 | 重复提交的候选标签表单字段。 |
| `model` | string | 否 | 响应中的模型名称。 |

```bash
curl http://127.0.0.1:8008/v1/zero-shot-image-classification/file \
  -H 'Authorization: Bearer change-me' \
  -F 'file=@./test.jpg' \
  -F 'candidate_labels=现场 5S 标识缺失' \
  -F 'candidate_labels=设备安全 防护缺失'
```

## 7. 向量库接口

这些接口访问已配置的 Milvus 实例。所有向量接口的 `collection` 都是可选字段，默认使用 `MILVUS_COLLECTION`。

### 7.1 确保集合存在

`POST /v1/vector/ensure`

请求体：

| 字段 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- |
| `collection` | string | 否 | `MILVUS_COLLECTION` |
| `dimension` | integer | 是 | - |
| `metricType` | string | 否 | `MILVUS_METRIC_TYPE` |
| `indexType` | string | 否 | `MILVUS_INDEX_TYPE` |

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "dimension": 1152,
  "metricType": "COSINE",
  "indexType": "HNSW"
}
```

响应：

```json
{ "status": "ok", "collection": "cps_knowledge_image_vector_siglip2_v1" }
```

### 7.2 加载集合

`POST /v1/vector/load`

请求体：

```json
{ "collection": "cps_knowledge_image_vector_siglip2_v1" }
```

集合必须已存在，否则服务返回 `404`。

### 7.3 新增或更新向量

`POST /v1/vector/upsert`

请求体：

| 字段 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- |
| `collection` | string | 否 | `MILVUS_COLLECTION` |
| `id` | integer | 是 | - |
| `caseId` | integer | 是 | - |
| `categoryL1Id` | integer | 否 | `0` |
| `categoryL2Id` | integer | 否 | `0` |
| `enabled` | boolean | 否 | `true` |
| `vector` | number[] | 是 | - |

```json
{
  "id": 501,
  "caseId": 101,
  "categoryL1Id": 10,
  "categoryL2Id": 11,
  "enabled": true,
  "vector": [0.0123, -0.0456]
}
```

集合不存在时，服务会以输入向量长度作为维度创建集合。已存在的 ID 在当前 PyMilvus 支持 `upsert` 时直接更新；否则服务会先删除旧 ID，再插入新向量。

### 7.4 检索向量

`POST /v1/vector/search`

请求体：

| 字段 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- |
| `collection` | string | 否 | `MILVUS_COLLECTION` |
| `vector` | number[] | 是 | - |
| `topK` | integer | 否 | `10` |

```json
{
  "vector": [0.0123, -0.0456],
  "topK": 5
}
```

仅检索 `enabled` 为 `true` 的向量。

响应：

```json
{
  "hits": [
    {
      "imageId": 501,
      "caseId": 101,
      "categoryL1Id": 10,
      "categoryL2Id": 11,
      "score": 0.9123
    }
  ]
}
```

### 7.5 删除向量

`POST /v1/vector/delete`

请求体：

| 字段 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- |
| `collection` | string | 否 | `MILVUS_COLLECTION` |
| `ids` | integer[] | 是 | - |

```json
{
  "ids": [501, 502]
}
```

响应：

```json
{ "status": "ok", "deleted": 2 }
```
