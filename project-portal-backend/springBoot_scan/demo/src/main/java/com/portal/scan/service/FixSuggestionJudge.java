package com.portal.scan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.scan.entity.Vulnerability;

import java.util.OptionalDouble;

/**
 * Shared pieces of the "double call" confidence scoring. Call 1 (the provider's
 * generateFixSuggestion) writes the fix; call 2 sends the finding plus that fix to the model again
 * as an independent reviewer and asks for a 0.0-1.0 score. The reviewer is never shown the
 * generator's own confidence, so the stored score is not the model grading its own answer.
 */
final class FixSuggestionJudge {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private FixSuggestionJudge() {
	}

	static String buildPrompt(Vulnerability vulnerability, AiSuggestionResult suggestion) {
		return String.format("""
				You are a strict, independent security reviewer. A different engineer proposed a fix for the
				finding below. Judge whether the proposed fix is correct and safe. Score it using these criteria:
				- Does it address the reported weakness at the reported location, not a different problem?
				- Is the code example correct, complete and consistent with the written steps?
				- Does it avoid introducing new vulnerabilities or breaking behaviour?
				Respond with ONLY a JSON object (no markdown, no prose outside the JSON) with these exact keys:
				{
				  "score": a number from 0.0 (wrong or unsafe) to 1.0 (correct and complete),
				  "reason": "1 sentence justifying the score"
				}

				Finding
				Type: %s
				Severity: %s
				File: %s
				Line: %d
				Description: %s

				Proposed fix
				%s

				Proposed code example
				%s
				""",
				vulnerability.getVulnerabilityType() != null ? vulnerability.getVulnerabilityType() : "Security Issue",
				vulnerability.getSeverity(), vulnerability.getFilePath(),
				vulnerability.getLineNumber() != null ? vulnerability.getLineNumber() : 0, vulnerability.getMessage(),
				suggestion.getSuggestionText(), suggestion.getCodeExample());
	}

	/** Reads {"score": x} from the reviewer's reply. Empty when the reply is not usable JSON with a numeric score. */
	static OptionalDouble parseScore(String reply) {
		if (reply == null) {
			return OptionalDouble.empty();
		}
		try {
			JsonNode score = MAPPER.readTree(stripFence(reply)).path("score");
			return score.isNumber() ? OptionalDouble.of(clamp(score.asDouble())) : OptionalDouble.empty();
		} catch (Exception e) {
			return OptionalDouble.empty();
		}
	}

	static double clamp(double score) {
		return Math.max(0.0, Math.min(1.0, score));
	}

	private static String stripFence(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline != -1) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence != -1) {
				trimmed = trimmed.substring(0, lastFence);
			}
			trimmed = trimmed.trim();
		}
		return trimmed;
	}
}
