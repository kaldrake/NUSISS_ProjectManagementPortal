package com.portal.scan.service;

/**
 * Carries the structured output of DeepSeekService.generateFixSuggestion,
 * including a confidence score derived from the model's self-report plus
 * heuristic adjustments (truncation, missing fields, fallback template).
 */
public class AiSuggestionResult {

	private final String suggestionText;
	private final String codeExample;
	private final double confidenceScore;
	private final boolean usedFallback;

	public AiSuggestionResult(String suggestionText, String codeExample, double confidenceScore, boolean usedFallback) {
		this.suggestionText = suggestionText;
		this.codeExample = codeExample;
		this.confidenceScore = confidenceScore;
		this.usedFallback = usedFallback;
	}

	public String getSuggestionText() {
		return suggestionText;
	}

	public String getCodeExample() {
		return codeExample;
	}

	public double getConfidenceScore() {
		return confidenceScore;
	}

	public boolean isUsedFallback() {
		return usedFallback;
	}
}
