# Generic Image Embedding Service

通用图片 embedding 服务，不绑定具体业务项目。默认用于 SigLIP2，也可以换成其他 HuggingFace 视觉模型，只要模型支持 `get_image_features` 或 `vision_model`。

## 1. 准备环境

```bash
cd /data/services/embedding-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

GPU 版本 PyTorch 请按服务器 CUDA 版本安装，例如：

```bash
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
```

## 2. 配置 .env

```bash
cp .env.example .env
vim .env
```

示例：

```env
HOST=0.0.0.0
PORT=8008
API_KEY=change-me
MODEL_PATH=/data/models/siglip2-so400m-patch14-384
MODEL_NAME=google/siglip2-so400m-patch14-384
MODEL_VERSION=siglip2-v1
DEVICE=auto
NORMALIZE_EMBEDDINGS=true
MAX_BATCH_SIZE=8
```

图片 embedding 会使用 `AutoImageProcessor`，模型目录至少需要：

```text
config.json
model.safetensors
preprocessor_config.json
```

zero-shot 图片标签分类会额外使用 `AutoProcessor`，模型目录还需要 tokenizer 相关文件。手动下载时建议补齐：

```text
tokenizer.json
tokenizer_config.json
special_tokens_map.json
spiece.model 或 sentencepiece.bpe.model
```

如果这些 tokenizer 文件缺失，服务仍可启动，embedding 接口可用；zero-shot 接口会返回 503，并在 `/health` 里显示 `zeroShotAvailable=false`。

## 3. 指定 GPU

推荐用环境变量指定物理 GPU：

```bash
CUDA_VISIBLE_DEVICES=1 ./run.sh
```

此时程序内看到的是 `cuda:0`，但实际使用物理 1 号卡。

多卡机器更推荐一张卡一个服务实例：

```bash
CUDA_VISIBLE_DEVICES=0 PORT=8008 ./run.sh
CUDA_VISIBLE_DEVICES=1 PORT=8009 ./run.sh
```

## 4. 启动

```bash
chmod +x run.sh
./run.sh
```

健康检查：

```bash
curl http://127.0.0.1:8008/health
```

## 5. JSON base64 调用

```bash
BASE64_IMAGE=$(base64 -w 0 ./test.jpg)

curl http://127.0.0.1:8008/v1/embeddings \
  -H "Authorization: Bearer change-me" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"google/siglip2-so400m-patch14-384\",
    \"input\": [
      {
        \"image_base64\": \"$BASE64_IMAGE\"
      }
    ]
  }"
```

也支持 `data:image/jpeg;base64,...` 前缀。

## 6. Multipart file 调用

单文件：

```bash
curl http://127.0.0.1:8008/v1/embeddings/file \
  -H "Authorization: Bearer change-me" \
  -F "model=google/siglip2-so400m-patch14-384" \
  -F "file=@./test.jpg"
```

多文件：

```bash
curl http://127.0.0.1:8008/v1/embeddings/files \
  -H "Authorization: Bearer change-me" \
  -F "model=google/siglip2-so400m-patch14-384" \
  -F "files=@./a.jpg" \
  -F "files=@./b.jpg"
```

## 7. Zero-shot 图片标签分类

JSON base64：

```bash
BASE64_IMAGE=$(base64 -w 0 ./test.jpg)

curl http://127.0.0.1:8008/v1/zero-shot-image-classification \
  -H "Authorization: Bearer change-me" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"google/siglip2-so400m-patch14-384\",
    \"image_base64\": \"$BASE64_IMAGE\",
    \"candidate_labels\": [
      \"现场 5S 标识缺失\",
      \"质量异常 外观不良\",
      \"设备安全 防护缺失\"
    ]
  }"
```

Multipart file：

```bash
curl http://127.0.0.1:8008/v1/zero-shot-image-classification/file \
  -H "Authorization: Bearer change-me" \
  -F "model=google/siglip2-so400m-patch14-384" \
  -F "file=@./test.jpg" \
  -F "candidate_labels=现场 5S 标识缺失" \
  -F "candidate_labels=质量异常 外观不良" \
  -F "candidate_labels=设备安全 防护缺失"
```

## 8. systemd

```ini
[Unit]
Description=Generic Image Embedding Service
After=network.target

[Service]
WorkingDirectory=/data/services/embedding-service
EnvironmentFile=/data/services/embedding-service/.env
Environment=CUDA_VISIBLE_DEVICES=1
ExecStart=/data/services/embedding-service/venv/bin/python -m uvicorn app:app --host 0.0.0.0 --port 8008 --workers 1
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable embedding-service
sudo systemctl start embedding-service
journalctl -u embedding-service -f
```

## 9. Vector API for Java backend

This service also owns Milvus access so Java does not need `milvus-sdk-java`, gRPC, or protobuf dependencies.

Required `.env` values:

```env
API_KEY=change-me
MILVUS_HOST=127.0.0.1
MILVUS_PORT=19530
MILVUS_COLLECTION=cps_knowledge_image_vector_siglip2_v1
MILVUS_METRIC_TYPE=COSINE
MILVUS_INDEX_TYPE=HNSW
MILVUS_SEARCH_EF=128
```

Java backend values:

```env
CPS_MILVUS_ENABLED=true
CPS_VECTOR_SERVICE_BASE_URL=http://127.0.0.1:8008
CPS_VECTOR_SERVICE_API_KEY=change-me
CPS_MILVUS_COLLECTION=cps_knowledge_image_vector_siglip2_v1
```

Vector endpoints:

```text
POST /v1/vector/ensure
POST /v1/vector/load
POST /v1/vector/upsert
POST /v1/vector/search
POST /v1/vector/delete
```
