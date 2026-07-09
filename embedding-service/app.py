import base64
import io
import os
from contextlib import asynccontextmanager
from typing import Any, Optional

import torch
import torch.nn.functional as F
from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from pydantic import BaseModel, Field
from transformers import AutoImageProcessor, AutoModel, AutoProcessor

load_dotenv()


class Settings:
    host: str = os.getenv("HOST", "0.0.0.0")
    port: int = int(os.getenv("PORT", "8008"))
    api_key: str = os.getenv("API_KEY", "")
    model_path: str = os.getenv("MODEL_PATH", "google/siglip2-so400m-patch14-384")
    model_name: str = os.getenv("MODEL_NAME", "google/siglip2-so400m-patch14-384")
    model_version: str = os.getenv("MODEL_VERSION", "siglip2-v1")
    device: str = os.getenv("DEVICE", "auto")
    normalize_embeddings: bool = os.getenv("NORMALIZE_EMBEDDINGS", "true").lower() == "true"
    max_batch_size: int = int(os.getenv("MAX_BATCH_SIZE", "8"))
    enable_zero_shot: bool = os.getenv("ENABLE_ZERO_SHOT", "true").lower() == "true"
    milvus_host: str = os.getenv("MILVUS_HOST", "127.0.0.1")
    milvus_port: int = int(os.getenv("MILVUS_PORT", "19530"))
    milvus_collection: str = os.getenv("MILVUS_COLLECTION", "cps_knowledge_image_vector_siglip2_v1")
    milvus_metric_type: str = os.getenv("MILVUS_METRIC_TYPE", "COSINE")
    milvus_index_type: str = os.getenv("MILVUS_INDEX_TYPE", "HNSW")
    milvus_search_ef: int = int(os.getenv("MILVUS_SEARCH_EF", "128"))
    cors_origins: list[str] = [
        item.strip()
        for item in os.getenv("CORS_ORIGINS", "").split(",")
        if item.strip()
    ]


settings = Settings()
runtime: dict[str, Any] = {}


class ImageInput(BaseModel):
    image_base64: Optional[str] = None


class EmbeddingRequest(BaseModel):
    model: Optional[str] = None
    input: list[ImageInput] = Field(default_factory=list)


class ZeroShotRequest(BaseModel):
    model: Optional[str] = None
    image_base64: str
    candidate_labels: list[str] = Field(default_factory=list)


class VectorEnsureRequest(BaseModel):
    collection: Optional[str] = None
    dimension: int
    metricType: Optional[str] = None
    indexType: Optional[str] = None


class VectorCollectionRequest(BaseModel):
    collection: Optional[str] = None


class VectorUpsertRequest(BaseModel):
    collection: Optional[str] = None
    id: int
    caseId: int
    categoryL1Id: Optional[int] = None
    categoryL2Id: Optional[int] = None
    enabled: bool = True
    vector: list[float] = Field(default_factory=list)


class VectorSearchRequest(BaseModel):
    collection: Optional[str] = None
    vector: list[float] = Field(default_factory=list)
    topK: int = 10


class VectorDeleteRequest(BaseModel):
    collection: Optional[str] = None
    ids: list[int] = Field(default_factory=list)


def resolve_device() -> str:
    if settings.device == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    return settings.device


def check_auth(authorization: Optional[str]) -> None:
    if not settings.api_key:
        return
    if authorization != f"Bearer {settings.api_key}":
        raise HTTPException(status_code=401, detail="Unauthorized")


def decode_base64_image(value: str) -> Image.Image:
    if not value:
        raise HTTPException(status_code=400, detail="image_base64 is required")
    if "," in value:
        value = value.split(",", 1)[1]
    try:
        raw = base64.b64decode(value, validate=False)
        return Image.open(io.BytesIO(raw)).convert("RGB")
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid image_base64") from exc


def image_from_bytes(raw: bytes) -> Image.Image:
    try:
        return Image.open(io.BytesIO(raw)).convert("RGB")
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid image file") from exc


def encode_images(images: list[Image.Image]) -> list[list[float]]:
    if not images:
        raise HTTPException(status_code=400, detail="input must contain at least one image")
    if len(images) > settings.max_batch_size:
        raise HTTPException(status_code=400, detail=f"batch size exceeds MAX_BATCH_SIZE={settings.max_batch_size}")

    model = runtime["model"]
    processor = runtime["processor"]
    device = runtime["device"]

    with torch.no_grad():
        inputs = processor(images=images, return_tensors="pt").to(device)
        if hasattr(model, "get_image_features"):
            features = model.get_image_features(**inputs)
        else:
            outputs = model.vision_model(**inputs)
            features = outputs.pooler_output
        if settings.normalize_embeddings:
            features = F.normalize(features, p=2, dim=-1)
        return features.detach().cpu().float().tolist()


def classify_image(image: Image.Image, candidate_labels: list[str]) -> list[dict[str, Any]]:
    if not candidate_labels:
        raise HTTPException(status_code=400, detail="candidate_labels must contain at least one label")
    if "text_processor" not in runtime:
        raise HTTPException(
            status_code=503,
            detail="zero-shot classification is not available because tokenizer/processor files are missing",
        )

    model = runtime["model"]
    processor = runtime["text_processor"]
    device = runtime["device"]

    with torch.no_grad():
        inputs = processor(text=candidate_labels, images=[image], padding="max_length", return_tensors="pt").to(device)
        outputs = model(**inputs)
        logits = outputs.logits_per_image
        probabilities = torch.softmax(logits, dim=1)[0].detach().cpu().float().tolist()

    ranked = [
        {"label": label, "score": float(score)}
        for label, score in zip(candidate_labels, probabilities)
    ]
    ranked.sort(key=lambda item: item["score"], reverse=True)
    return ranked


def normalize_labels(candidate_labels: list[str]) -> list[str]:
    labels: list[str] = []
    for item in candidate_labels:
        if "," in item:
            labels.extend(part.strip() for part in item.split(",") if part.strip())
        else:
            value = item.strip()
            if value:
                labels.append(value)
    return labels


def vector_collection_name(value: Optional[str]) -> str:
    collection = (value or settings.milvus_collection).strip()
    if not collection:
        raise HTTPException(status_code=400, detail="collection is required")
    return collection


def milvus_modules():
    try:
        from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility
        return Collection, CollectionSchema, DataType, FieldSchema, connections, utility
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"PyMilvus is not available: {exc}") from exc


def connect_milvus() -> None:
    _, _, _, _, connections, _ = milvus_modules()
    connections.connect(alias="default", host=settings.milvus_host, port=settings.milvus_port)


def ensure_vector_collection(collection_name: str, dimension: int, metric_type: str, index_type: str):
    if dimension <= 0:
        raise HTTPException(status_code=400, detail="dimension must be greater than 0")
    Collection, CollectionSchema, DataType, FieldSchema, _, utility = milvus_modules()
    connect_milvus()
    if utility.has_collection(collection_name):
        return Collection(collection_name)

    schema = CollectionSchema(
        fields=[
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=False),
            FieldSchema(name="case_id", dtype=DataType.INT64),
            FieldSchema(name="category_l1_id", dtype=DataType.INT64),
            FieldSchema(name="category_l2_id", dtype=DataType.INT64),
            FieldSchema(name="enabled", dtype=DataType.BOOL),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dimension),
        ],
        description="CPS knowledge image vector collection",
    )
    collection = Collection(name=collection_name, schema=schema)
    collection.create_index(
        field_name="embedding",
        index_params={
            "index_type": index_type,
            "metric_type": metric_type,
            "params": {"M": 16, "efConstruction": 200},
        },
    )
    return collection


def load_vector_collection(collection_name: str):
    Collection, _, _, _, _, utility = milvus_modules()
    connect_milvus()
    if not utility.has_collection(collection_name):
        raise HTTPException(status_code=404, detail=f"collection not found: {collection_name}")
    collection = Collection(collection_name)
    collection.load()
    return collection


def validate_vector(vector: list[float]) -> None:
    if not vector:
        raise HTTPException(status_code=400, detail="vector must contain at least one value")


@asynccontextmanager
async def lifespan(_: FastAPI):
    device = resolve_device()
    image_processor = AutoImageProcessor.from_pretrained(settings.model_path)
    model = AutoModel.from_pretrained(settings.model_path)
    model.eval()
    model.to(device)
    runtime["processor"] = image_processor
    runtime["model"] = model
    runtime["device"] = device
    runtime["zero_shot_available"] = False
    if settings.enable_zero_shot:
        try:
            runtime["text_processor"] = AutoProcessor.from_pretrained(settings.model_path)
            runtime["zero_shot_available"] = True
        except Exception as exc:
            runtime["zero_shot_error"] = str(exc)
    yield
    runtime.clear()


app = FastAPI(title="Generic Image Embedding Service", version="1.0.0", lifespan=lifespan)

if settings.cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": settings.model_name,
        "modelVersion": settings.model_version,
        "device": runtime.get("device", "not_loaded"),
        "normalizeEmbeddings": settings.normalize_embeddings,
        "maxBatchSize": settings.max_batch_size,
        "zeroShotAvailable": runtime.get("zero_shot_available", False),
        "zeroShotError": runtime.get("zero_shot_error"),
        "milvusHost": settings.milvus_host,
        "milvusPort": settings.milvus_port,
        "milvusCollection": settings.milvus_collection,
    }


@app.get("/v1/models")
def list_models(authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    return {
        "object": "list",
        "data": [
            {
                "id": settings.model_name,
                "object": "model",
                "owned_by": "local",
            }
        ],
    }


@app.post("/v1/embeddings")
def create_embeddings(req: EmbeddingRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    images = [decode_base64_image(item.image_base64 or "") for item in req.input]
    vectors = encode_images(images)
    return embedding_response(req.model or settings.model_name, vectors)


@app.post("/v1/embeddings/file")
async def create_embedding_from_file(
    file: UploadFile = File(...),
    model: Optional[str] = Form(default=None),
    authorization: Optional[str] = Header(default=None),
):
    check_auth(authorization)
    raw = await file.read()
    vectors = encode_images([image_from_bytes(raw)])
    return embedding_response(model or settings.model_name, vectors)


@app.post("/v1/embeddings/files")
async def create_embeddings_from_files(
    files: list[UploadFile] = File(...),
    model: Optional[str] = Form(default=None),
    authorization: Optional[str] = Header(default=None),
):
    check_auth(authorization)
    images: list[Image.Image] = []
    for file in files:
        images.append(image_from_bytes(await file.read()))
    vectors = encode_images(images)
    return embedding_response(model or settings.model_name, vectors)


@app.post("/v1/zero-shot-image-classification")
def zero_shot_classification(req: ZeroShotRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    image = decode_base64_image(req.image_base64)
    outputs = classify_image(image, normalize_labels(req.candidate_labels))
    return classification_response(req.model or settings.model_name, outputs)


@app.post("/v1/zero-shot-image-classification/file")
async def zero_shot_classification_file(
    file: UploadFile = File(...),
    candidate_labels: list[str] = Form(...),
    model: Optional[str] = Form(default=None),
    authorization: Optional[str] = Header(default=None),
):
    check_auth(authorization)
    raw = await file.read()
    outputs = classify_image(image_from_bytes(raw), normalize_labels(candidate_labels))
    return classification_response(model or settings.model_name, outputs)


@app.post("/v1/vector/ensure")
def ensure_vector(req: VectorEnsureRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    collection = ensure_vector_collection(
        vector_collection_name(req.collection),
        req.dimension,
        req.metricType or settings.milvus_metric_type,
        req.indexType or settings.milvus_index_type,
    )
    return {"status": "ok", "collection": collection.name}


@app.post("/v1/vector/load")
def load_vector(req: VectorCollectionRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    collection = load_vector_collection(vector_collection_name(req.collection))
    return {"status": "ok", "collection": collection.name}


@app.post("/v1/vector/upsert")
def upsert_vector(req: VectorUpsertRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    validate_vector(req.vector)
    collection_name = vector_collection_name(req.collection)
    collection = ensure_vector_collection(
        collection_name,
        len(req.vector),
        settings.milvus_metric_type,
        settings.milvus_index_type,
    )
    data = [
        [req.id],
        [req.caseId],
        [req.categoryL1Id or 0],
        [req.categoryL2Id or 0],
        [req.enabled],
        [req.vector],
    ]
    if hasattr(collection, "upsert"):
        collection.upsert(data)
    else:
        collection.delete(f"id in [{req.id}]")
        collection.insert(data)
    collection.flush()
    return {"status": "ok", "collection": collection_name, "id": req.id}


@app.post("/v1/vector/search")
def search_vector(req: VectorSearchRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    validate_vector(req.vector)
    collection = load_vector_collection(vector_collection_name(req.collection))
    result = collection.search(
        data=[req.vector],
        anns_field="embedding",
        param={
            "metric_type": settings.milvus_metric_type,
            "params": {"ef": settings.milvus_search_ef},
        },
        limit=req.topK,
        expr="enabled == true",
        output_fields=["case_id", "category_l1_id", "category_l2_id"],
    )
    hits: list[dict[str, Any]] = []
    for item in result[0]:
        entity = item.entity
        hits.append(
            {
                "imageId": int(item.id),
                "caseId": int(entity.get("case_id")),
                "categoryL1Id": int(entity.get("category_l1_id")),
                "categoryL2Id": int(entity.get("category_l2_id")),
                "score": float(item.score),
            }
        )
    return {"hits": hits}


@app.post("/v1/vector/delete")
def delete_vector(req: VectorDeleteRequest, authorization: Optional[str] = Header(default=None)):
    check_auth(authorization)
    if not req.ids:
        raise HTTPException(status_code=400, detail="ids must contain at least one value")
    collection = load_vector_collection(vector_collection_name(req.collection))
    collection.delete("id in [" + ",".join(str(item) for item in req.ids) + "]")
    collection.flush()
    return {"status": "ok", "deleted": len(req.ids)}


def embedding_response(model_name: str, vectors: list[list[float]]) -> dict[str, Any]:
    return {
        "object": "list",
        "model": model_name,
        "model_version": settings.model_version,
        "data": [
            {
                "object": "embedding",
                "index": index,
                "embedding": vector,
            }
            for index, vector in enumerate(vectors)
        ],
        "usage": {
            "prompt_tokens": 0,
            "total_tokens": 0,
        },
    }


def classification_response(model_name: str, outputs: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "object": "zero_shot_image_classification",
        "model": model_name,
        "model_version": settings.model_version,
        "data": outputs,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host=settings.host, port=settings.port, reload=False)
