package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.OptionalDouble;

/**
 * Single entry point for AI fix-suggestion generation. ScanService calls this instead of wiring an
 * individual AI provider directly. The active provider is chosen with the {@code ai.provider}
 * property (environment variable AI_PROVIDER): {@code gemini} (default, free tier), {@code claude}
 * or {@code deepseek}. The last two are pay-as-you-go and need their own API credits.
 *
 * <p>Confidence scoring uses two calls (property {@code ai.judge.enabled}, environment variable
 * AI_JUDGE_ENABLED, on by default): call 1 generates the fix, call 2 asks the same provider to review
 * that fix independently and returns a score. The reviewer's score replaces the generator's
 * self-reported confidence. Template fallbacks and incomplete answers (no code example) are not
 * reviewed, and if the review call fails the self-reported confidence is kept.
 */
@Service
public class AiSuggestionRouter {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionRouter.class);

    static final String PROVIDER_GEMINI = "gemini";
    static final String PROVIDER_CLAUDE = "claude";
    static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final String FALLBACK_MODEL_LABEL = "fallback-template";

    private final ClaudeService claudeService;
    private final DeepSeekService deepSeekService;
    private final GeminiService geminiService;
    private final String provider;
    private final boolean judgeEnabled;

    public AiSuggestionRouter(ClaudeService claudeService, DeepSeekService deepSeekService,
            GeminiService geminiService, @Value("${ai.provider:gemini}") String provider,
            @Value("${ai.judge.enabled:true}") boolean judgeEnabled) {
        this.claudeService = claudeService;
        this.deepSeekService = deepSeekService;
        this.geminiService = geminiService;
        this.provider = provider == null ? PROVIDER_GEMINI : provider.trim().toLowerCase();
        this.judgeEnabled = judgeEnabled;
    }

    public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {
        AiSuggestionResult generated = generateWithProvider(vulnerability);
        if (!judgeEnabled || !isReviewable(generated)) {
            return generated;
        }
        OptionalDouble reviewed = judgeWithProvider(vulnerability, generated);
        if (reviewed.isEmpty()) {
            log.warn("Reviewer call gave no score, keeping self-reported confidence {}", generated.getConfidenceScore());
            return generated;
        }
        log.info("Double-call scoring for vulnerability {}: self-reported={}, reviewer={}", vulnerability.getId(),
                generated.getConfidenceScore(), reviewed.getAsDouble());
        return generated.withConfidenceScore(reviewed.getAsDouble());
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

    private AiSuggestionResult generateWithProvider(Vulnerability vulnerability) {
        switch (provider) {
            case PROVIDER_CLAUDE:
                return claudeService.generateFixSuggestion(vulnerability);
            case PROVIDER_DEEPSEEK:
                return deepSeekService.generateFixSuggestion(vulnerability);
            default:
                return geminiService.generateFixSuggestion(vulnerability);
        }
    }

    private OptionalDouble judgeWithProvider(Vulnerability vulnerability, AiSuggestionResult suggestion) {
        switch (provider) {
            case PROVIDER_CLAUDE:
                return claudeService.judgeFixSuggestion(vulnerability, suggestion);
            case PROVIDER_DEEPSEEK:
                return deepSeekService.judgeFixSuggestion(vulnerability, suggestion);
            default:
                return geminiService.judgeFixSuggestion(vulnerability, suggestion);
        }
    }

    /** A real, complete answer: template fallbacks and answers without a code example are scored by heuristics only. */
    private boolean isReviewable(AiSuggestionResult result) {
        return !result.isUsedFallback() && result.getCodeExample() != null && !result.getCodeExample().isBlank();
    }
}
