# Embedding Service 接口文档

## 1. 健康检查

`GET /health`

**入参**：无。

**出参示例**：

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

## 2. 查询模型列表

`GET /v1/models`

**入参**：无。

**出参示例**：

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

## 3. 使用 Base64 生成图片向量

`POST /v1/embeddings`

**入参**：

```json
{
  "model": "google/siglip2-so400m-patch14-384",
  "input": [
    {
      "image_base64": "data:image/jpeg;base64,/9j/4AAQ..."
    }
  ]
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `model` | string | 否 |
| `input` | array | 是 |
| `input[].image_base64` | string | 是 |

**出参示例**：

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

## 4. 使用单个文件生成图片向量

`POST /v1/embeddings/file`

**入参**：`multipart/form-data`

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `file` | file | 是 |
| `model` | string | 否 |

**出参示例**：

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

## 5. 使用多个文件生成图片向量

`POST /v1/embeddings/files`

**入参**：`multipart/form-data`

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `files` | file[] | 是 |
| `model` | string | 否 |

**出参示例**：

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
    },
    {
      "object": "embedding",
      "index": 1,
      "embedding": [0.0789, 0.0123]
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "total_tokens": 0
  }
}
```

## 6. 使用 Base64 进行 Zero-shot 图片分类

`POST /v1/zero-shot-image-classification`

**入参**：

```json
{
  "model": "google/siglip2-so400m-patch14-384",
  "image_base64": "data:image/jpeg;base64,/9j/4AAQ...",
  "candidate_labels": [
    "现场 5S 标识缺失",
    "设备安全 防护缺失"
  ]
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `model` | string | 否 |
| `image_base64` | string | 是 |
| `candidate_labels` | string[] | 是 |

**出参示例**：

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

## 7. 使用单个文件进行 Zero-shot 图片分类

`POST /v1/zero-shot-image-classification/file`

**入参**：`multipart/form-data`

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `file` | file | 是 |
| `candidate_labels` | string[] | 是 |
| `model` | string | 否 |

**出参示例**：

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

## 8. 创建向量集合

`POST /v1/vector/ensure`

**入参**：

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "dimension": 1152,
  "metricType": "COSINE",
  "indexType": "HNSW"
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `collection` | string | 否 |
| `dimension` | integer | 是 |
| `metricType` | string | 否 |
| `indexType` | string | 否 |

**出参示例**：

```json
{
  "status": "ok",
  "collection": "cps_knowledge_image_vector_siglip2_v1"
}
```

## 9. 加载向量集合

`POST /v1/vector/load`

**入参**：

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1"
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `collection` | string | 否 |

**出参示例**：

```json
{
  "status": "ok",
  "collection": "cps_knowledge_image_vector_siglip2_v1"
}
```

## 10. 新增或更新向量

`POST /v1/vector/upsert`

**入参**：

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "id": 501,
  "caseId": 101,
  "categoryL1Id": 10,
  "categoryL2Id": 11,
  "enabled": true,
  "vector": [0.0123, -0.0456]
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `collection` | string | 否 |
| `id` | integer | 是 |
| `caseId` | integer | 是 |
| `categoryL1Id` | integer | 否 |
| `categoryL2Id` | integer | 否 |
| `enabled` | boolean | 否 |
| `vector` | number[] | 是 |

**出参示例**：

```json
{
  "status": "ok",
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "id": 501
}
```

## 11. 检索向量

`POST /v1/vector/search`

**入参**：

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "vector": [0.0123, -0.0456],
  "topK": 5
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `collection` | string | 否 |
| `vector` | number[] | 是 |
| `topK` | integer | 否 |

**出参示例**：

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

## 12. 删除向量

`POST /v1/vector/delete`

**入参**：

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "ids": [501, 502]
}
```

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `collection` | string | 否 |
| `ids` | integer[] | 是 |

**出参示例**：

```json
{
  "status": "ok",
  "deleted": 2
}
```
