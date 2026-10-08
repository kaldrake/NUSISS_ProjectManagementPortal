package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixSuggestionJudgeTest {

    private Vulnerability vulnerability() {
        Vulnerability v = new Vulnerability();
        v.setVulnerabilityType("VULNERABILITY");
        v.setSeverity("CRITICAL");
        v.setFilePath("src/Main.java");
        v.setLineNumber(12);
        v.setMessage("possible SQL injection");
        return v;
    }

    @Test
    void buildPrompt_containsFindingAndProposedFix_butNotTheGeneratorConfidence() {
        AiSuggestionResult suggestion = new AiSuggestionResult("Use a prepared statement", "ps.setString(1, name);", 0.987, false);

        String prompt = FixSuggestionJudge.buildPrompt(vulnerability(), suggestion);

        assertTrue(prompt.contains("possible SQL injection"));
        assertTrue(prompt.contains("src/Main.java"));
        assertTrue(prompt.contains("Line: 12"));
        assertTrue(prompt.contains("Use a prepared statement"));
        assertTrue(prompt.contains("ps.setString(1, name);"));
        assertFalse(prompt.contains("0.987"));
    }

    @Test
    void buildPrompt_usesDefaultsForMissingTypeAndLine() {
        Vulnerability v = new Vulnerability();
        v.setMessage("m");

        String prompt = FixSuggestionJudge.buildPrompt(v, new AiSuggestionResult("fix", "code", 0.5, false));

        assertTrue(prompt.contains("Type: Security Issue"));
        assertTrue(prompt.contains("Line: 0"));
    }

    @Test
    void parseScore_readsScoreFromJson() {
        assertEquals(OptionalDouble.of(0.75), FixSuggestionJudge.parseScore("{\"score\": 0.75, \"reason\": \"ok\"}"));
    }

    @Test
    void parseScore_acceptsMarkdownFence() {
        assertEquals(OptionalDouble.of(0.5), FixSuggestionJudge.parseScore("```json\n{\"score\": 0.5}\n```"));
    }

    @Test
    void parseScore_clampsOutOfRangeValues() {
        assertEquals(OptionalDouble.of(1.0), FixSuggestionJudge.parseScore("{\"score\": 7}"));
        assertEquals(OptionalDouble.of(0.0), FixSuggestionJudge.parseScore("{\"score\": -2}"));
    }

    @Test
    void parseScore_isEmptyForUnusableReplies() {
        assertTrue(FixSuggestionJudge.parseScore(null).isEmpty());
        assertTrue(FixSuggestionJudge.parseScore("").isEmpty());
        assertTrue(FixSuggestionJudge.parseScore("not json").isEmpty());
        assertTrue(FixSuggestionJudge.parseScore("{\"reason\": \"no score\"}").isEmpty());
        assertTrue(FixSuggestionJudge.parseScore("{\"score\": \"high\"}").isEmpty());
    }
}
