package com.company.cps.service;

import com.company.cps.domain.CpsIssueAiMatch;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeMatchRequest;
import com.company.cps.dto.CpsKnowledgeMatchResponse;
import com.company.cps.dto.CpsMatchedCaseResponse;
import com.company.cps.mapper.CpsIssueAiMatchMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CpsAiMatchService {

    private static final int TOP_K = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CpsIssueAttachmentMapper attachmentMapper;
    private final CpsKnowledgeCaseImageMapper imageMapper;
    private final CpsKnowledgeCaseMapper caseMapper;
    private final CpsIssueAiMatchMapper matchMapper;
    private final ImageEmbeddingClient embeddingClient;
    private final MilvusVectorService milvusVectorService;

    public CpsAiMatchService(
            CpsIssueAttachmentMapper attachmentMapper,
            CpsKnowledgeCaseImageMapper imageMapper,
            CpsKnowledgeCaseMapper caseMapper,
            CpsIssueAiMatchMapper matchMapper,
            ImageEmbeddingClient embeddingClient,
            MilvusVectorService milvusVectorService
    ) {
        this.attachmentMapper = attachmentMapper;
        this.imageMapper = imageMapper;
        this.caseMapper = caseMapper;
        this.matchMapper = matchMapper;
        this.embeddingClient = embeddingClient;
        this.milvusVectorService = milvusVectorService;
    }

    public CpsKnowledgeMatchResponse matchKnowledge(CpsKnowledgeMatchRequest request) {
        if (request.getAttachmentId() == null) {
            throw new IllegalArgumentException("attachmentId is required");
        }
        CpsIssueAttachment attachment = attachmentMapper.findById(request.getAttachmentId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + request.getAttachmentId()));
        ImageEmbeddingResult embedding = embeddingClient.embedImage(attachment.getFileUrl());
        List<MilvusSearchHit> hits = milvusVectorService.searchSimilarImages(embedding.getVector(), TOP_K);
        List<ImageScore> scores = aggregateScores(hits);
        List<Long> imageIds = scores.stream().map(ImageScore::getImageId).collect(Collectors.toList());
        Map<Long, CpsKnowledgeCaseImage> images = imageIds.isEmpty()
                ? Collections.emptyMap()
                : imageMapper.findEnabledByIds(imageIds).stream().collect(Collectors.toMap(CpsKnowledgeCaseImage::getId, Function.identity()));
        List<Long> caseIds = scores.stream().map(ImageScore::getCaseId).collect(Collectors.toList());
        Map<Long, CpsKnowledgeCase> cases = caseIds.isEmpty()
                ? Collections.emptyMap()
                : caseMapper.findEnabledByIds(caseIds).stream().collect(Collectors.toMap(CpsKnowledgeCase::getId, Function.identity()));
        List<CpsMatchedCaseResponse> matchedCases = hydrateImages(scores, images, cases);
        CpsKnowledgeMatchResponse response = toResponse(matchedCases, embedding);
        response.setSourceAttachmentId(request.getAttachmentId());
        insertMatchRecord(request.getAttachmentId(), response, embedding, hits);
        return response;
    }

    private static List<ImageScore> aggregateScores(List<MilvusSearchHit> hits) {
        Map<Long, ImageScore> scores = new LinkedHashMap<>();
        for (MilvusSearchHit hit : hits) {
            if (hit.getImageId() == null || hit.getCaseId() == null) {
                continue;
            }
            ImageScore current = scores.get(hit.getCaseId());
            if (current == null || hit.getScore() > current.getScore()) {
                scores.put(hit.getCaseId(), new ImageScore(hit.getImageId(), hit.getCaseId(), hit.getScore()));
            }
        }
        return scores.values().stream()
                .sorted(Comparator.comparingDouble(ImageScore::getScore).reversed())
                .collect(Collectors.toList());
    }

    private static List<CpsMatchedCaseResponse> hydrateImages(
            List<ImageScore> scores,
            Map<Long, CpsKnowledgeCaseImage> images,
            Map<Long, CpsKnowledgeCase> cases
    ) {
        List<CpsMatchedCaseResponse> responses = new ArrayList<>();
        for (ImageScore score : scores) {
            CpsKnowledgeCaseImage item = images.get(score.getImageId());
            CpsKnowledgeCase knowledgeCase = cases.get(score.getCaseId());
            if (item == null || knowledgeCase == null) {
                continue;
            }
            CpsMatchedCaseResponse response = new CpsMatchedCaseResponse();
            response.setImageId(item.getId());
            response.setCaseId(item.getCaseId());
            response.setConfidence(confidence(score.getScore()));
            response.setCategoryL1Id(knowledgeCase.getCategoryL1Id());
            response.setCategoryL2Id(knowledgeCase.getCategoryL2Id());
            response.setCategoryL1Name(knowledgeCase.getCategoryL1Name());
            response.setCategoryL2Name(knowledgeCase.getCategoryL2Name());
            response.setReasonSuggestion(item.getReason());
            response.setMeasureSuggestion(item.getMeasure());
            responses.add(response);
        }
        return responses;
    }

    private static CpsKnowledgeMatchResponse toResponse(List<CpsMatchedCaseResponse> matchedCases, ImageEmbeddingResult embedding) {
        CpsKnowledgeMatchResponse response = new CpsKnowledgeMatchResponse();
        response.setModelName(embedding.getModel());
        response.setModelVersion(embedding.getVersion());
        response.setMatchedCases(matchedCases);
        if (!matchedCases.isEmpty()) {
            CpsMatchedCaseResponse best = matchedCases.get(0);
            response.setMatchedCaseId(best.getCaseId());
            response.setMatchedImageId(best.getImageId());
            response.setConfidence(best.getConfidence());
            response.setCategoryL1Id(best.getCategoryL1Id());
            response.setCategoryL2Id(best.getCategoryL2Id());
            response.setCategoryL1Name(best.getCategoryL1Name());
            response.setCategoryL2Name(best.getCategoryL2Name());
            response.setReasonSuggestion(best.getReasonSuggestion());
            response.setMeasureSuggestion(best.getMeasureSuggestion());
        }
        return response;
    }

    private void insertMatchRecord(Long attachmentId, CpsKnowledgeMatchResponse response, ImageEmbeddingResult embedding, List<MilvusSearchHit> hits) {
        CpsIssueAiMatch match = new CpsIssueAiMatch();
        match.setSourceAttachmentId(attachmentId);
        match.setMatchedCaseId(response.getMatchedCaseId());
        match.setConfidence(response.getConfidence());
        match.setAiCategoryL1Id(response.getCategoryL1Id());
        match.setAiCategoryL2Id(response.getCategoryL2Id());
        match.setAiCategoryL1Name(response.getCategoryL1Name());
        match.setAiCategoryL2Name(response.getCategoryL2Name());
        match.setReasonSuggestion(response.getReasonSuggestion());
        match.setMeasureSuggestion(response.getMeasureSuggestion());
        match.setTopkJson(json(hits));
        match.setRawRequest(embedding.getRawRequest());
        match.setRawResponse(embedding.getRawResponse());
        match.setCreatedAt(LocalDateTime.now());
        matchMapper.insert(match);
    }

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI match topK", e);
        }
    }

    private static BigDecimal confidence(double score) {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private static class ImageScore {
        private final Long imageId;
        private final Long caseId;
        private final double score;

        ImageScore(Long imageId, Long caseId, double score) {
            this.imageId = imageId;
            this.caseId = caseId;
            this.score = score;
        }

        Long getImageId() {
            return imageId;
        }

        Long getCaseId() {
            return caseId;
        }

        double getScore() {
            return score;
        }
    }
}
