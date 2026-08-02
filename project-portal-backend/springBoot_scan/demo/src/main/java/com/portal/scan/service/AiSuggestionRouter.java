package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.springframework.stereotype.Service;

/**
 * Single entry point for AI fix-suggestion generation. ScanService calls this
 * instead of wiring an individual AI provider directly, so switching providers
 * (or adding a new one) is a one-line change here rather than a change at every
 * call site.
 */
@Service
public class AiSuggestionRouter {

    private final ClaudeService claudeService;
    private final DeepSeekService deepSeekService;
    private final GeminiService geminiService;

    public AiSuggestionRouter(ClaudeService claudeService, DeepSeekService deepSeekService,
            GeminiService geminiService) {
        this.claudeService = claudeService;
        this.deepSeekService = deepSeekService;
        this.geminiService = geminiService;
    }

    public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {
        // DeepSeek route disabled — pay-as-you-go balance ran out (402 Insufficient Balance, see session.txt)
        // return deepSeekService.generateFixSuggestion(vulnerability);

        // Claude route disabled — pay-as-you-go, no free tier (needs Console credits, separate from claude.ai Pro)
        // return claudeService.generateFixSuggestion(vulnerability);

        return geminiService.generateFixSuggestion(vulnerability);
    }

    public String getModelUsed(AiSuggestionResult result) {
        if (result.isUsedFallback()) {
            return "fallback-template";
        }
        // DeepSeek route disabled
        // return deepSeekService.getModel();

        // Claude route disabled
        // return claudeService.getModel();

        return geminiService.getModel();
    }
}
