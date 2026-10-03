package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        router = new AiSuggestionRouter(claudeService, deepSeekService, geminiService);
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
}
