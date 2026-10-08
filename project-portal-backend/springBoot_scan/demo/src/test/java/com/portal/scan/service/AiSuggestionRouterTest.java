package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiSuggestionRouterTest {

    private ClaudeService claudeService;
    private DeepSeekService deepSeekService;
    private GeminiService geminiService;
    private AiSuggestionRouter router;

    @BeforeEach
    void setUp() {
        claudeService = Mockito.mock(ClaudeService.class);
        deepSeekService = Mockito.mock(DeepSeekService.class);
        geminiService = Mockito.mock(GeminiService.class);
        router = new AiSuggestionRouter(claudeService, deepSeekService, geminiService, "gemini", false);
    }

    @Test
    void generateFixSuggestion_delegatesToActiveProvider_gemini() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult expected = new AiSuggestionResult("fix", "code", 0.8, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(expected);

        AiSuggestionResult actual = router.generateFixSuggestion(vulnerability);

        assertSame(expected, actual);
        verify(geminiService).generateFixSuggestion(vulnerability);
    }

    @Test
    void generateFixSuggestion_doesNotCallStandbyProviders() {
        Vulnerability vulnerability = new Vulnerability();
        when(geminiService.generateFixSuggestion(vulnerability))
                .thenReturn(new AiSuggestionResult("fix", "code", 0.8, false));

        router.generateFixSuggestion(vulnerability);

        verifyNoInteractions(claudeService, deepSeekService);
    }

    @Test
    void getModelUsed_returnsFallbackLabel_whenTemplateWasUsed() {
        AiSuggestionResult fallback = new AiSuggestionResult("template", "", 0.2, true);

        assertEquals("fallback-template", router.getModelUsed(fallback));
    }

    @Test
    void getModelUsed_returnsActiveModelName_whenAiAnswered() {
        when(geminiService.getModel()).thenReturn("gemini-test-model");
        AiSuggestionResult real = new AiSuggestionResult("fix", "code", 0.8, false);

        assertEquals("gemini-test-model", router.getModelUsed(real));
    }

    @Test
    void claudeProvider_routesToClaude() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult expected = new AiSuggestionResult("fix", "code", 0.9, false);
        when(claudeService.generateFixSuggestion(vulnerability)).thenReturn(expected);
        when(claudeService.getModel()).thenReturn("claude-test");
        AiSuggestionRouter claudeRouter = new AiSuggestionRouter(claudeService, deepSeekService, geminiService, "Claude", false);

        assertSame(expected, claudeRouter.generateFixSuggestion(vulnerability));
        assertEquals("claude-test", claudeRouter.getModelUsed(expected));
        verifyNoInteractions(geminiService, deepSeekService);
    }

    @Test
    void deepseekProvider_routesToDeepSeek() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult expected = new AiSuggestionResult("fix", "code", 0.9, false);
        when(deepSeekService.generateFixSuggestion(vulnerability)).thenReturn(expected);
        when(deepSeekService.getModel()).thenReturn("deepseek-test");
        AiSuggestionRouter deepSeekRouter = new AiSuggestionRouter(claudeService, deepSeekService, geminiService, " deepseek ", false);

        assertSame(expected, deepSeekRouter.generateFixSuggestion(vulnerability));
        assertEquals("deepseek-test", deepSeekRouter.getModelUsed(expected));
        verifyNoInteractions(geminiService, claudeService);
    }

    @Test
    void unknownOrMissingProvider_fallsBackToGemini() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult expected = new AiSuggestionResult("fix", "code", 0.9, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(expected);

        assertSame(expected, new AiSuggestionRouter(claudeService, deepSeekService, geminiService, "something-else", false)
                .generateFixSuggestion(vulnerability));
        assertSame(expected, new AiSuggestionRouter(claudeService, deepSeekService, geminiService, null, false)
                .generateFixSuggestion(vulnerability));
    }

    // ---------------------------------------------------------------- double-call scoring

    private AiSuggestionRouter judgingRouter(String provider) {
        return new AiSuggestionRouter(claudeService, deepSeekService, geminiService, provider, true);
    }

    @Test
    void doubleCall_replacesSelfReportedConfidenceWithReviewerScore() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult generated = new AiSuggestionResult("fix", "code", 0.95, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(generated);
        when(geminiService.judgeFixSuggestion(vulnerability, generated)).thenReturn(OptionalDouble.of(0.4));

        AiSuggestionResult actual = judgingRouter("gemini").generateFixSuggestion(vulnerability);

        assertEquals(0.4, actual.getConfidenceScore());
        assertEquals("fix", actual.getSuggestionText());
        assertEquals("code", actual.getCodeExample());
        assertFalse(actual.isUsedFallback());
    }

    @Test
    void doubleCall_keepsSelfReportedConfidence_whenReviewerGivesNoScore() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult generated = new AiSuggestionResult("fix", "code", 0.8, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(generated);
        when(geminiService.judgeFixSuggestion(vulnerability, generated)).thenReturn(OptionalDouble.empty());

        assertSame(generated, judgingRouter("gemini").generateFixSuggestion(vulnerability));
    }

    @Test
    void doubleCall_skipsReview_forTemplateFallback() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult fallback = new AiSuggestionResult("template", "", 0.2, true);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(fallback);

        assertSame(fallback, judgingRouter("gemini").generateFixSuggestion(vulnerability));
        verify(geminiService, never()).judgeFixSuggestion(any(), any());
    }

    @Test
    void doubleCall_skipsReview_whenAnswerHasNoCodeExample() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult salvaged = new AiSuggestionResult("partial text", "", 0.35, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(salvaged);

        assertSame(salvaged, judgingRouter("gemini").generateFixSuggestion(vulnerability));
        verify(geminiService, never()).judgeFixSuggestion(any(), any());
    }

    @Test
    void doubleCall_disabled_makesOnlyOneCall() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult generated = new AiSuggestionResult("fix", "code", 0.8, false);
        when(geminiService.generateFixSuggestion(vulnerability)).thenReturn(generated);

        assertSame(generated, router.generateFixSuggestion(vulnerability));
        verify(geminiService, never()).judgeFixSuggestion(any(), any());
    }

    @Test
    void doubleCall_usesTheSameProviderForTheReview() {
        Vulnerability vulnerability = new Vulnerability();
        AiSuggestionResult generated = new AiSuggestionResult("fix", "code", 0.9, false);
        when(claudeService.generateFixSuggestion(vulnerability)).thenReturn(generated);
        when(claudeService.judgeFixSuggestion(vulnerability, generated)).thenReturn(OptionalDouble.of(0.7));
        when(deepSeekService.generateFixSuggestion(vulnerability)).thenReturn(generated);
        when(deepSeekService.judgeFixSuggestion(vulnerability, generated)).thenReturn(OptionalDouble.of(0.6));

        assertEquals(0.7, judgingRouter("claude").generateFixSuggestion(vulnerability).getConfidenceScore());
        assertEquals(0.6, judgingRouter("deepseek").generateFixSuggestion(vulnerability).getConfidenceScore());
        verifyNoInteractions(geminiService);
    }
}
