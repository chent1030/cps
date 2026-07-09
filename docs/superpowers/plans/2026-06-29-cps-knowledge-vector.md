# CPS Knowledge Vector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a stable backend knowledge-case image vector matching pipeline using MySQL as source of truth, NewAPI for image embeddings, and Milvus as the vector index.

**Architecture:** Spring Boot services own orchestration and MyBatis persistence. NewAPI is hidden behind `ImageEmbeddingClient`; Milvus is hidden behind `MilvusVectorService`; matching logic remains testable without network calls by injecting fakes.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis, Flyway, JUnit 5, Mockito, Milvus Java SDK, NewAPI-compatible embedding endpoint.

---

### Task 1: Schema And Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260629__cps_knowledge_vector.sql`
- Create: `backend/src/main/java/com/company/cps/domain/CpsKnowledgeCase.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsKnowledgeCaseImage.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueAiMatch.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsVectorStatus.java`

- [ ] Add tables for knowledge cases, knowledge images, and AI match records.
- [ ] Add Java domain objects with camel-case properties matching MyBatis underscore mapping.
- [ ] Run `mvn.cmd -q test` and verify compilation.

### Task 2: Mapper Contracts

**Files:**
- Create: `backend/src/main/java/com/company/cps/mapper/CpsKnowledgeCaseMapper.java`
- Create: `backend/src/main/java/com/company/cps/mapper/CpsKnowledgeCaseImageMapper.java`
- Create: `backend/src/main/java/com/company/cps/mapper/CpsIssueAiMatchMapper.java`
- Create: `backend/src/test/java/com/company/cps/mapper/CpsKnowledgeMapperContractTest.java`

- [ ] Write contract tests that assert sync candidate SQL checks status, model, version, dimension, and file hash.
- [ ] Run mapper contract test and verify it fails because mapper methods do not exist.
- [ ] Add mapper methods for finding cases, finding images, selecting sync candidates, marking processing/success/failed, and inserting match records.
- [ ] Run mapper contract test and verify it passes.

### Task 3: NewAPI Embedding Client

**Files:**
- Create: `backend/src/main/java/com/company/cps/config/CpsAiProperties.java`
- Create: `backend/src/main/java/com/company/cps/service/ImageEmbeddingClient.java`
- Create: `backend/src/main/java/com/company/cps/service/ImageEmbeddingResult.java`
- Create: `backend/src/main/java/com/company/cps/service/NewApiImageEmbeddingClient.java`
- Create: `backend/src/test/java/com/company/cps/service/NewApiImageEmbeddingClientTest.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] Write tests with `MockRestServiceServer` for parsing embeddings and rejecting wrong dimensions.
- [ ] Run the test and verify it fails because the client does not exist.
- [ ] Implement the client using `RestClient`, configured base URL, API key, model, version, dimension, and timeout.
- [ ] Run the test and verify it passes.

### Task 4: Milvus Abstraction And Sync Service

**Files:**
- Create: `backend/src/main/java/com/company/cps/config/CpsMilvusProperties.java`
- Create: `backend/src/main/java/com/company/cps/service/MilvusVectorService.java`
- Create: `backend/src/main/java/com/company/cps/service/NoopMilvusVectorService.java`
- Create: `backend/src/main/java/com/company/cps/service/KnowledgeVectorSyncService.java`
- Create: `backend/src/main/java/com/company/cps/bootstrap/CpsKnowledgeVectorBootstrap.java`
- Create: `backend/src/test/java/com/company/cps/service/KnowledgeVectorSyncServiceTest.java`

- [ ] Write tests proving bootstrap creates collection, syncs candidates, upserts image vectors, and marks failures without crashing normal business startup.
- [ ] Run the test and verify it fails because services do not exist.
- [ ] Implement the service against interfaces, using idempotent upsert and mapper status updates.
- [ ] Run the test and verify it passes.

### Task 5: AI Match Service And Controller

**Files:**
- Create: `backend/src/main/java/com/company/cps/dto/CpsKnowledgeMatchRequest.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsKnowledgeMatchResponse.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsMatchedCaseResponse.java`
- Create: `backend/src/main/java/com/company/cps/controller/CpsAiController.java`
- Create: `backend/src/main/java/com/company/cps/service/CpsAiMatchService.java`
- Create: `backend/src/test/java/com/company/cps/service/CpsAiMatchServiceTest.java`

- [ ] Write tests proving first-photo attachment lookup, embedding, Milvus TopK, case-score aggregation, MySQL case hydration, and match record insertion.
- [ ] Run the test and verify it fails because the service does not exist.
- [ ] Implement the service and controller.
- [ ] Run the test and verify it passes.

### Task 6: Full Verification

**Files:**
- All backend files touched above.

- [ ] Run `mvn.cmd -q test`.
- [ ] Fix any failures with focused tests first.
- [ ] Confirm no model deployment code exists in Spring Boot.
- [ ] Confirm NewAPI/Milvus are isolated behind interfaces and can be disabled in local config.

