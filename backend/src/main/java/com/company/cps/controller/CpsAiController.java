package com.company.cps.controller;

import com.company.cps.dto.CpsKnowledgeMatchRequest;
import com.company.cps.dto.CpsKnowledgeMatchResponse;
import com.company.cps.service.CpsAiMatchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cps/ai")
public class CpsAiController {

    private final CpsAiMatchService aiMatchService;

    public CpsAiController(CpsAiMatchService aiMatchService) {
        this.aiMatchService = aiMatchService;
    }

    /**
     * 根据问题照片附件匹配知识库案例，返回推荐分类、原因、措施和候选结果。
     */
    @PostMapping("/match-knowledge")
    public CpsKnowledgeMatchResponse matchKnowledge(@RequestBody CpsKnowledgeMatchRequest request) {
        return aiMatchService.matchKnowledge(request);
    }
}
