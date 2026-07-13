# Embedding Service API

## 1. Overview

- Default address: `http://<host>:8008`
- Content type: JSON endpoints use `application/json`; file endpoints use `multipart/form-data`.
- Image model: configured through `MODEL_PATH`; the `model` field in a request is returned as response metadata and does not switch the model loaded by the process.
- Image Base64: raw Base64 and `data:image/<type>;base64,...` are both accepted.

## 2. Authentication

When `API_KEY` is configured, all endpoints except `GET /health` require:

```http
Authorization: Bearer <API_KEY>
```

When `API_KEY` is empty, no authorization header is required.

## 3. Common Errors

| Status | Meaning |
| --- | --- |
| `400` | Invalid request, invalid image, empty vector, empty label list, invalid collection dimension, or batch size beyond `MAX_BATCH_SIZE`. |
| `401` | Missing or invalid bearer token when `API_KEY` is enabled. |
| `404` | Requested Milvus collection does not exist. |
| `503` | PyMilvus is unavailable, or zero-shot tokenizer/processor files are unavailable. |

## 4. Service Endpoints

### 4.1 Health Check

`GET /health`

No authentication required.

Response example:

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

### 4.2 List Models

`GET /v1/models`

Response example:

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

## 5. Image Embedding Endpoints

The service uses the loaded vision model to return one embedding vector per input image. If `NORMALIZE_EMBEDDINGS=true`, every vector is L2-normalized.

Common embedding response:

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

### 5.1 Create Embeddings from JSON Base64

`POST /v1/embeddings`

Request body:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `model` | string | No | Response model name; defaults to `MODEL_NAME`. |
| `input` | array | Yes | At least one image; maximum is `MAX_BATCH_SIZE`. |
| `input[].image_base64` | string | Yes | Raw Base64 or data URL. |

```json
{
  "input": [
    { "image_base64": "data:image/jpeg;base64,/9j/4AAQ..." }
  ]
}
```

### 5.2 Create an Embedding from One File

`POST /v1/embeddings/file`

Multipart fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | file | Yes | Image file. |
| `model` | string | No | Response model name. |

```bash
curl http://127.0.0.1:8008/v1/embeddings/file \
  -H 'Authorization: Bearer change-me' \
  -F 'file=@./test.jpg'
```

### 5.3 Create Embeddings from Multiple Files

`POST /v1/embeddings/files`

Multipart fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `files` | file[] | Yes | Repeated image file field; count must not exceed `MAX_BATCH_SIZE`. |
| `model` | string | No | Response model name. |

```bash
curl http://127.0.0.1:8008/v1/embeddings/files \
  -H 'Authorization: Bearer change-me' \
  -F 'files=@./a.jpg' \
  -F 'files=@./b.jpg'
```

## 6. Zero-Shot Image Classification

Zero-shot endpoints require the model directory to include tokenizer/processor files. Check `zeroShotAvailable` from `/health` before calling.

Common response:

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

`data` is ordered by descending score. A candidate label containing commas is split into multiple labels after trimming whitespace.

### 6.1 Classify JSON Base64

`POST /v1/zero-shot-image-classification`

Request body:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `model` | string | No | Response model name. |
| `image_base64` | string | Yes | Raw Base64 or data URL. |
| `candidate_labels` | string[] | Yes | At least one candidate label. |

```json
{
  "image_base64": "data:image/jpeg;base64,/9j/4AAQ...",
  "candidate_labels": ["现场 5S 标识缺失", "设备安全 防护缺失"]
}
```

### 6.2 Classify One File

`POST /v1/zero-shot-image-classification/file`

Multipart fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | file | Yes | Image file. |
| `candidate_labels` | string[] | Yes | Repeated form field containing candidate labels. |
| `model` | string | No | Response model name. |

```bash
curl http://127.0.0.1:8008/v1/zero-shot-image-classification/file \
  -H 'Authorization: Bearer change-me' \
  -F 'file=@./test.jpg' \
  -F 'candidate_labels=现场 5S 标识缺失' \
  -F 'candidate_labels=设备安全 防护缺失'
```

## 7. Vector Endpoints

These endpoints access the configured Milvus instance. `collection` is optional for every vector endpoint and defaults to `MILVUS_COLLECTION`.

### 7.1 Ensure a Collection

`POST /v1/vector/ensure`

Request body:

| Field | Type | Required | Default |
| --- | --- | --- | --- |
| `collection` | string | No | `MILVUS_COLLECTION` |
| `dimension` | integer | Yes | - |
| `metricType` | string | No | `MILVUS_METRIC_TYPE` |
| `indexType` | string | No | `MILVUS_INDEX_TYPE` |

```json
{
  "collection": "cps_knowledge_image_vector_siglip2_v1",
  "dimension": 1152,
  "metricType": "COSINE",
  "indexType": "HNSW"
}
```

Response:

```json
{ "status": "ok", "collection": "cps_knowledge_image_vector_siglip2_v1" }
```

### 7.2 Load a Collection

`POST /v1/vector/load`

Request body:

```json
{ "collection": "cps_knowledge_image_vector_siglip2_v1" }
```

The collection must already exist; otherwise the service returns `404`.

### 7.3 Upsert a Vector

`POST /v1/vector/upsert`

Request body:

| Field | Type | Required | Default |
| --- | --- | --- | --- |
| `collection` | string | No | `MILVUS_COLLECTION` |
| `id` | integer | Yes | - |
| `caseId` | integer | Yes | - |
| `categoryL1Id` | integer | No | `0` |
| `categoryL2Id` | integer | No | `0` |
| `enabled` | boolean | No | `true` |
| `vector` | number[] | Yes | - |

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

If the collection does not exist, it is created using the input vector length as its dimension. Existing IDs are updated when the installed PyMilvus version supports `upsert`; otherwise the service deletes the old ID and inserts a new vector.

### 7.4 Search Vectors

`POST /v1/vector/search`

Request body:

| Field | Type | Required | Default |
| --- | --- | --- | --- |
| `collection` | string | No | `MILVUS_COLLECTION` |
| `vector` | number[] | Yes | - |
| `topK` | integer | No | `10` |

```json
{
  "vector": [0.0123, -0.0456],
  "topK": 5
}
```

Only vectors whose `enabled` value is `true` are searched.

Response:

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

### 7.5 Delete Vectors

`POST /v1/vector/delete`

Request body:

| Field | Type | Required | Default |
| --- | --- | --- | --- |
| `collection` | string | No | `MILVUS_COLLECTION` |
| `ids` | integer[] | Yes | - |

```json
{
  "ids": [501, 502]
}
```

Response:

```json
{ "status": "ok", "deleted": 2 }
```
