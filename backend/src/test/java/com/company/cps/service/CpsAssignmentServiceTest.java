package com.company.cps.service;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsAssignmentServiceTest {

    @Mock
    private CpsAreaPersonConfigMapper configMapper;

    private CpsAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new CpsAssignmentService(configMapper);
    }

    @Test
    void feedbackHandlerUsesMostSpecificEnabledRule() {
        CpsAreaPersonConfig broad = config("F1", "A1", null, null, "E_AREA");
        CpsAreaPersonConfig exact = config("F1", "A1", "L1", "P1", "E_PROCESS");
        when(configMapper.findFeedbackCandidates("F1", "A1", "L1", "P1")).thenReturn(Arrays.asList(exact, broad));

        assertEquals("E_PROCESS", service.findFeedbackHandler("F1", "A1", "L1", "P1"));
    }

    @Test
    void feedbackHandlerFallsBackToAreaRule() {
        CpsAreaPersonConfig areaRule = config("F1", "A1", null, null, "E_AREA");
        when(configMapper.findFeedbackCandidates("F1", "A1", "L1", "P1")).thenReturn(Collections.singletonList(areaRule));

        assertEquals("E_AREA", service.findFeedbackHandler("F1", "A1", "L1", "P1"));
    }

    @Test
    void reviewerUsesFactoryAreaRule() {
        CpsAreaPersonConfig rule = config("F1", "A1", null, null, "E_REVIEW");
        when(configMapper.findReviewerCandidates("F1", "A1")).thenReturn(Collections.singletonList(rule));

        assertEquals("E_REVIEW", service.findReviewer("F1", "A1"));
    }

    @Test
    void assignmentReturnsNullWhenNoRuleMatches() {
        when(configMapper.findFeedbackCandidates("F1", "A1", "L1", "P1")).thenReturn(Collections.<CpsAreaPersonConfig>emptyList());
        when(configMapper.findReviewerCandidates("F1", "A1")).thenReturn(Collections.<CpsAreaPersonConfig>emptyList());

        assertNull(service.findFeedbackHandler("F1", "A1", "L1", "P1"));
        assertNull(service.findReviewer("F1", "A1"));
    }

    private static CpsAreaPersonConfig config(
            String factory,
            String area,
            String line,
            String process,
            String empNo
    ) {
        CpsAreaPersonConfig config = new CpsAreaPersonConfig();
        config.setFactory(factory);
        config.setArea(area);
        config.setLine(line);
        config.setProcess(process);
        config.setEmpNo(empNo);
        config.setEnabled(true);
        return config;
    }
}
