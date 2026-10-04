package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single entry point for AI fix-suggestion generation. ScanService calls this instead of wiring an
 * individual AI provider directly. The active provider is chosen with the {@code ai.provider}
 * property (environment variable AI_PROVIDER): {@code gemini} (default, free tier), {@code claude}
 * or {@code deepseek}. The last two are pay-as-you-go and need their own API credits.
 */
@Service
public class AiSuggestionRouter {

    static final String PROVIDER_GEMINI = "gemini";
    static final String PROVIDER_CLAUDE = "claude";
    static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final String FALLBACK_MODEL_LABEL = "fallback-template";

    private final ClaudeService claudeService;
    private final DeepSeekService deepSeekService;
    private final GeminiService geminiService;
    private final String provider;

    public AiSuggestionRouter(ClaudeService claudeService, DeepSeekService deepSeekService,
            GeminiService geminiService, @Value("${ai.provider:gemini}") String provider) {
        this.claudeService = claudeService;
        this.deepSeekService = deepSeekService;
        this.geminiService = geminiService;
        this.provider = provider == null ? PROVIDER_GEMINI : provider.trim().toLowerCase();
    }

    public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {
        switch (provider) {
            case PROVIDER_CLAUDE:
                return claudeService.generateFixSuggestion(vulnerability);
            case PROVIDER_DEEPSEEK:
                return deepSeekService.generateFixSuggestion(vulnerability);
            default:
                return geminiService.generateFixSuggestion(vulnerability);
        }
    }

    public String getModelUsed(AiSuggestionResult result) {
        if (result.isUsedFallback()) {
            return FALLBACK_MODEL_LABEL;
        }
        switch (provider) {
            case PROVIDER_CLAUDE:
                return claudeService.getModel();
            case PROVIDER_DEEPSEEK:
                return deepSeekService.getModel();
            default:
                return geminiService.getModel();
        }
    }
}
