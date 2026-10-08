package com.portal.scan.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.portal.scan.entity.Vulnerability;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.OptionalDouble;

@Service
public class ClaudeService {

	private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

	@Value("${claude.api.key:}")
	private String apiKey;

	@Value("${claude.model:claude-opus-5}")
	private String model;

	private AnthropicClient client;

	// Confidence assigned whenever we couldn't get a real, complete answer from the model.
	private static final double FALLBACK_CONFIDENCE = 0.2;

	@PostConstruct
	private void init() {
		if (apiKey != null && !apiKey.isEmpty()) {
			client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
		}
	}

	public String getModel() {
		return model;
	}

	// Structured output schema — Claude fills these fields directly, so unlike the
	// DeepSeek integration there's no second JSON parse pass over a "content" string.
	private record VulnerabilityFix(
			@JsonPropertyDescription("1 sentence describing the risk") String explanation,
			@JsonPropertyDescription("3-4 step-by-step fix instructions") String steps,
			@JsonPropertyDescription("A code snippet showing the fix") String codeExample,
			@JsonPropertyDescription("How confident you are this fix is correct for this exact code, 0.0 to 1.0") double confidence) {
	}

	// Structured output for the reviewer call of the double-call scoring.
	private record JudgeVerdict(
			@JsonPropertyDescription("0.0 (wrong or unsafe) to 1.0 (correct and complete)") double score,
			@JsonPropertyDescription("1 sentence justifying the score") String reason) {
	}

	/**
	 * Second call of the double-call scoring: an independent review of an already generated suggestion.
	 * Empty when no key is set or the call fails, so the caller keeps the self-reported confidence.
	 */
	public OptionalDouble judgeFixSuggestion(Vulnerability vulnerability, AiSuggestionResult suggestion) {
		if (client == null) {
			return OptionalDouble.empty();
		}
		try {
			StructuredMessageCreateParams<JudgeVerdict> params = MessageCreateParams.builder()
					.model(model)
					.maxTokens(300L)
					.thinking(ThinkingConfigDisabled.builder().build())
					.outputConfig(JudgeVerdict.class)
					.addUserMessage(FixSuggestionJudge.buildPrompt(vulnerability, suggestion))
					.build();

			JudgeVerdict[] holder = new JudgeVerdict[1];
			client.messages().create(params).content().stream()
					.flatMap(cb -> cb.text().stream())
					.findFirst()
					.ifPresent(typed -> holder[0] = typed.text());
			if (holder[0] == null) {
				return OptionalDouble.empty();
			}
			return OptionalDouble.of(FixSuggestionJudge.clamp(holder[0].score()));
		} catch (Exception e) {
			log.warn("Claude judge call failed: {}", e.getMessage());
			return OptionalDouble.empty();
		}
	}

	public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {

		log.info("in generate fix suggestion");
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("Claude API key not configured, returning default suggestion");
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}

		String prompt = buildPrompt(vulnerability);

		try {
			StructuredMessageCreateParams<VulnerabilityFix> params = MessageCreateParams.builder()
					.model(model)
					.maxTokens(600L)
					.thinking(ThinkingConfigDisabled.builder().build())
					.outputConfig(VulnerabilityFix.class)
					.addUserMessage(prompt)
					.build();

			VulnerabilityFix[] holder = new VulnerabilityFix[1];
			client.messages().create(params).content().stream()
					.flatMap(cb -> cb.text().stream())
					.findFirst()
					.ifPresent(typed -> holder[0] = typed.text());

			VulnerabilityFix fix = holder[0];
			if (fix == null) {
				throw new IllegalStateException("Claude returned no structured output");
			}

			String suggestionText = (fix.explanation() + "\n\n" + fix.steps()).trim();
			String codeExample = fix.codeExample() == null ? "" : fix.codeExample();

			double confidence = Math.max(0.0, Math.min(1.0, fix.confidence()));
			if (suggestionText.isBlank() || codeExample.isBlank()) {
				// Model skipped a required field; the answer is incomplete.
				confidence = Math.min(confidence, 0.4);
			}

			log.info("Generated AI suggestion for vulnerability: {} (confidence={})", vulnerability.getId(), confidence);
			return new AiSuggestionResult(suggestionText, codeExample, confidence, false);

		} catch (Exception e) {
			log.error("Claude API call failed: {}", e.getMessage());
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}
	}

	private String buildPrompt(Vulnerability vulnerability) {
		return String.format("""
				You are a security expert. Analyze this vulnerability and respond with the requested fields.

				Type: %s
				Severity: %s
				File: %s
				Line: %d
				Description: %s
				""",
				vulnerability.getVulnerabilityType() != null ? vulnerability.getVulnerabilityType() : "Security Issue",
				vulnerability.getSeverity(), vulnerability.getFilePath(),
				vulnerability.getLineNumber() != null ? vulnerability.getLineNumber() : 0, vulnerability.getMessage());
	}

	public boolean testConnection() {
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("Claude API key not configured, cannot test connection");
			return false;
		}

		try {
			MessageCreateParams params = MessageCreateParams.builder()
					.model(model)
					.maxTokens(5L)
					.addUserMessage("OK")
					.build();

			Message response = client.messages().create(params);
			boolean success = !response.content().isEmpty();
			if (success) {
				log.info("Claude API connection test successful");
			} else {
				log.warn("Claude API connection test returned no content");
			}
			return success;

		} catch (Exception e) {
			log.error("Claude connection test failed: {}", e.getMessage());
			return false;
		}
	}
}
