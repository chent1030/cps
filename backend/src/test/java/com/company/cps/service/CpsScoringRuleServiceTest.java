package com.company.cps.service;

import com.company.cps.domain.CpsScoringRule;
import com.company.cps.dto.CpsScoringRuleRequest;
import com.company.cps.mapper.CpsScoringRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B8 评分规则（PRD §23.1 base-factory 口径）。 */
@ExtendWith(MockitoExtension.class)
class CpsScoringRuleServiceTest {

    @Mock private CpsScoringRuleMapper ruleMapper;
    private CpsScoringRuleService service;

    @BeforeEach
    void setUp() {
        service = new CpsScoringRuleService(ruleMapper);
    }

    @Test
    @DisplayName("parseJsonInt supports score/deduct keys")
    void parseJsonInt() {
        assertEquals(Integer.valueOf(100), CpsScoringRuleService.parseJsonInt("{\"score\":100}", "score"));
        assertEquals(Integer.valueOf(40), CpsScoringRuleService.parseJsonInt("{\"deduct\":40}", "deduct"));
        org.junit.jupiter.api.Assertions.assertNull(CpsScoringRuleService.parseJsonInt("oops", "score"));
        org.junit.jupiter.api.Assertions.assertNull(CpsScoringRuleService.parseJsonInt("{\"score\":\"abc\"}", "score"));
    }

    @Test
    @DisplayName("resolveRules: factory calibration overrides global default")
    void resolveRulesFactoryOverride() {
        CpsScoringRule fRule = rule("base", "F1", "{\"score\":110}", 2);
        when(ruleMapper.findEffectiveByKeyFactory(eq("base"), eq("F1"), any(LocalDate.class))).thenReturn(fRule);
        when(ruleMapper.findEffectiveByKeyFactory(eq("content_mismatch_deduct"), eq("F1"), any())).thenReturn(null);
        when(ruleMapper.findEffectiveByKeyFactory(eq("content_mismatch_deduct"), eq(null), any()))
                .thenReturn(rule("content_mismatch_deduct", null, "{\"deduct\":40}", 1));
        when(ruleMapper.findEffectiveByKeyFactory(eq("evidence_vague_deduct"), eq("F1"), any())).thenReturn(null);
        when(ruleMapper.findEffectiveByKeyFactory(eq("evidence_vague_deduct"), eq(null), any()))
                .thenReturn(rule("evidence_vague_deduct", null, "{\"deduct\":30}", 1));
        when(ruleMapper.findEffectiveByKeyFactory(eq("keywords_missing_deduct"), eq("F1"), any())).thenReturn(null);
        when(ruleMapper.findEffectiveByKeyFactory(eq("keywords_missing_deduct"), eq(null), any()))
                .thenReturn(rule("keywords_missing_deduct", null, "{\"deduct\":20}", 1));
        when(ruleMapper.findEffectiveByKeyFactory(eq("other_deduct"), eq("F1"), any())).thenReturn(null);
        when(ruleMapper.findEffectiveByKeyFactory(eq("other_deduct"), eq(null), any()))
                .thenReturn(rule("other_deduct", null, "{\"deduct\":10}", 1));

        CpsScoringRuleService.RuleSet set = service.resolveRules("F1");
        assertEquals(110, set.base());
        assertEquals(40, set.contentMismatchDeduct());
        assertEquals(30, set.evidenceVagueDeduct());
    }

    @Test
    @DisplayName("resolveRules: missing all → built-in defaults")
    void resolveRulesDefaults() {
        CpsScoringRuleService.RuleSet set = service.resolveRules(null);
        assertEquals(100, set.base());
        assertEquals(40, set.contentMismatchDeduct());
        assertEquals(30, set.evidenceVagueDeduct());
        assertEquals(20, set.keywordsMissingDeduct());
        assertEquals(10, set.otherDeduct());
    }

    @Test
    @DisplayName("upsert rejects unknown ruleKey; requires factory when calibration")
    void upsertValidations() {
        CpsScoringRuleRequest req = new CpsScoringRuleRequest();
        req.setRuleKey("nope");
        assertThrows(IllegalArgumentException.class, () -> service.upsert(req));

        CpsScoringRuleRequest req2 = new CpsScoringRuleRequest();
        req2.setRuleKey("base");
        req2.setRuleValue("{\"score\":100}");
        req2.setFactoryCalibrationFlag(true);
        // factory null + calibration=true → 400
        assertThrows(IllegalArgumentException.class, () -> service.upsert(req2));

        CpsScoringRuleRequest req3 = new CpsScoringRuleRequest();
        req3.setRuleKey("base");
        req3.setRuleValue("{\"score\":100}");
        req3.setFactory("F1");
        // factory non-null + calibration=false → 400
        req3.setFactoryCalibrationFlag(false);
        assertThrows(IllegalArgumentException.class, () -> service.upsert(req3));
    }

    @Test
    @DisplayName("upsert accepts factory calibration with all fields")
    void upsertFactoryCalibration() {
        CpsScoringRuleRequest req = new CpsScoringRuleRequest();
        req.setRuleKey("base");
        req.setFactory("F1");
        req.setRuleValue("{\"score\":110}");
        req.setVersion(2);
        req.setFactoryCalibrationFlag(true);
        req.setUpdatedBy("ADMIN");
        when(ruleMapper.upsert(any())).thenReturn(1);

        Map<String, Object> result = service.upsert(req);
        assertEquals("base", result.get("rule_key"));
        assertEquals("F1", result.get("factory"));
        assertEquals(true, result.get("factory_calibration_flag"));

        ArgumentCaptor<CpsScoringRule> cap = ArgumentCaptor.forClass(CpsScoringRule.class);
        verify(ruleMapper).upsert(cap.capture());
        assertEquals(Integer.valueOf(2), cap.getValue().getVersion());
        assertEquals("ADMIN", cap.getValue().getUpdatedBy());
    }

    private static CpsScoringRule rule(String key, String factory, String value, int version) {
        CpsScoringRule r = new CpsScoringRule();
        r.setRuleKey(key);
        r.setFactory(factory);
        r.setRuleValue(value);
        r.setVersion(version);
        r.setFactoryCalibrationFlag(factory != null);
        return r;
    }
}
