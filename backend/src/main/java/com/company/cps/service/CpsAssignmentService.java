package com.company.cps.service;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CpsAssignmentService {

    private final CpsAreaPersonConfigMapper configMapper;

    public CpsAssignmentService(CpsAreaPersonConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public String findFeedbackHandler(String factory, String area, String line, String process) {
        List<CpsAreaPersonConfig> candidates = configMapper.findFeedbackCandidates(
                trim(factory),
                trim(area),
                trim(line),
                trim(process)
        );
        for (CpsAreaPersonConfig candidate : candidates) {
            if (!isBlank(candidate.getEmpNo())) {
                return candidate.getEmpNo().trim();
            }
        }
        return null;
    }

    public String findReviewer(String factory, String area) {
        List<CpsAreaPersonConfig> candidates = configMapper.findReviewerCandidates(trim(factory), trim(area));
        for (CpsAreaPersonConfig candidate : candidates) {
            if (!isBlank(candidate.getEmpNo())) {
                return candidate.getEmpNo().trim();
            }
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
