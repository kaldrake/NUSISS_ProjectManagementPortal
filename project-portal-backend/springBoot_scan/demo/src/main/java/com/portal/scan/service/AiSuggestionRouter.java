package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single entry point for AI fix-suggestion generation. ScanService calls this
 * instead of wiring an individual AI provider directly, so switching providers
 * (or adding a new one) is a one-line change here rather than a change at every
 * call site.
 *
 * The active provider is chosen by ai.provider (env AI_PROVIDER): gemini (default),
 * deepseek or claude. DeepSeek and Claude are pay-as-you-go and need account credit.
 */
@Service
public class AiSuggestionRouter {

    private final ClaudeService claudeService;
    private final DeepSeekService deepSeekService;
    private final GeminiService geminiService;

    @Value("${ai.provider:gemini}")
    private String aiProvider;

    public AiSuggestionRouter(ClaudeService claudeService, DeepSeekService deepSeekService,
            GeminiService geminiService) {
        this.claudeService = claudeService;
        this.deepSeekService = deepSeekService;
        this.geminiService = geminiService;
    }

    public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {
        switch (aiProvider.trim().toLowerCase()) {
            case "deepseek":
                return deepSeekService.generateFixSuggestion(vulnerability);
            case "claude":
                return claudeService.generateFixSuggestion(vulnerability);
            default:
                return geminiService.generateFixSuggestion(vulnerability);
        }
    }

    public String getModelUsed(AiSuggestionResult result) {
        if (result.isUsedFallback()) {
            return "fallback-template";
        }
        switch (aiProvider.trim().toLowerCase()) {
            case "deepseek":
                return deepSeekService.getModel();
            case "claude":
                return claudeService.getModel();
            default:
                return geminiService.getModel();
        }
    }
}
