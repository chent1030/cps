package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.config.CpsMilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "cps.milvus", name = "enabled", havingValue = "true")
public class MilvusSdkVectorService implements MilvusVectorService {

    private static final String FIELD_ID = "id";
    private static final String FIELD_CASE_ID = "case_id";
    private static final String FIELD_CATEGORY_L1_ID = "category_l1_id";
    private static final String FIELD_CATEGORY_L2_ID = "category_l2_id";
    private static final String FIELD_ENABLED = "enabled";
    private static final String FIELD_EMBEDDING = "embedding";

    private final MilvusServiceClient client;
    private final CpsMilvusProperties milvusProperties;
    private final CpsAiProperties aiProperties;

    @Autowired
    public MilvusSdkVectorService(CpsMilvusProperties milvusProperties, CpsAiProperties aiProperties) {
        this(
                new MilvusServiceClient(ConnectParam.newBuilder()
                        .withHost(milvusProperties.getHost())
                        .withPort(milvusProperties.getPort())
                        .build()),
                milvusProperties,
                aiProperties
        );
    }

    MilvusSdkVectorService(MilvusServiceClient client, CpsMilvusProperties milvusProperties, CpsAiProperties aiProperties) {
        this.client = client;
        this.milvusProperties = milvusProperties;
        this.aiProperties = aiProperties;
    }

    @Override
    public void ensureCollection() {
        R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .build());
        assertOk(hasCollection);
        if (Boolean.TRUE.equals(hasCollection.getData())) {
            return;
        }

        assertOk(client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .withDescription("CPS knowledge image vector collection")
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_ID)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(false)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CASE_ID)
                        .withDataType(DataType.Int64)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CATEGORY_L1_ID)
                        .withDataType(DataType.Int64)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CATEGORY_L2_ID)
                        .withDataType(DataType.Int64)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_ENABLED)
                        .withDataType(DataType.Bool)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_EMBEDDING)
                        .withDataType(DataType.FloatVector)
                        .withDimension(aiProperties.getEmbedding().getDimension())
                        .build())
                .build()));

        assertOk(client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .withFieldName(FIELD_EMBEDDING)
                .withIndexType(indexType())
                .withMetricType(metricType())
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build()));
    }

    @Override
    public void loadCollection() {
        assertOk(client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .build()));
    }

    @Override
    public void upsertKnowledgeImage(Long imageId, Long caseId, Long categoryL1Id, Long categoryL2Id, boolean enabled, List<Float> vector) {
        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(FIELD_ID, Collections.singletonList(imageId)),
                new InsertParam.Field(FIELD_CASE_ID, Collections.singletonList(caseId)),
                new InsertParam.Field(FIELD_CATEGORY_L1_ID, Collections.singletonList(categoryL1Id == null ? 0L : categoryL1Id)),
                new InsertParam.Field(FIELD_CATEGORY_L2_ID, Collections.singletonList(categoryL2Id == null ? 0L : categoryL2Id)),
                new InsertParam.Field(FIELD_ENABLED, Collections.singletonList(enabled)),
                new InsertParam.Field(FIELD_EMBEDDING, Collections.singletonList(vector))
        );
        R<MutationResult> result = client.upsert(UpsertParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .withFields(fields)
                .build());
        assertOk(result);
    }

    @Override
    public List<MilvusSearchHit> searchSimilarImages(List<Float> vector, int topK) {
        R<SearchResults> result = client.search(SearchParam.newBuilder()
                .withCollectionName(milvusProperties.getCollection())
                .withMetricType(metricType())
                .withTopK(topK)
                .withVectors(Collections.singletonList(vector))
                .withVectorFieldName(FIELD_EMBEDDING)
                .withOutFields(Arrays.asList(FIELD_ID, FIELD_CASE_ID, FIELD_CATEGORY_L1_ID, FIELD_CATEGORY_L2_ID))
                .withExpr(FIELD_ENABLED + " == true")
                .withParams("{\"ef\":128}")
                .build());
        assertOk(result);

        SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<MilvusSearchHit> hits = new ArrayList<>(scores.size());
        for (SearchResultsWrapper.IDScore score : scores) {
            hits.add(new MilvusSearchHit(
                    asLong(score.get(FIELD_ID)),
                    asLong(score.get(FIELD_CASE_ID)),
                    asLong(score.get(FIELD_CATEGORY_L1_ID)),
                    asLong(score.get(FIELD_CATEGORY_L2_ID)),
                    score.getScore()
            ));
        }
        return hits;
    }

    private MetricType metricType() {
        return MetricType.valueOf(milvusProperties.getMetricType());
    }

    private IndexType indexType() {
        return IndexType.valueOf(milvusProperties.getIndexType());
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static void assertOk(R<?> result) {
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus operation failed: " + result.getMessage());
        }
    }
}
