package com.portal.scan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.scan.entity.Vulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free-tier alternative to DeepSeek/Claude — calls Google's Gemini API
 * (generativelanguage.googleapis.com), which offers a rate-limited free
 * tier via AI Studio keys. Same plain single-turn call, no SDK, no
 * conversation history — mirrors DeepSeekService's shape so it can be
 * swapped in via AiSuggestionRouter with no other code changes.
 */
@Service
public class GeminiService {

	private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

	private static final String PARTS = "parts";
	private static final String CANDIDATES = "candidates";

	// Confidence assigned whenever we couldn't get a real, complete answer from the model.
	private static final double FALLBACK_CONFIDENCE = 0.2;
	// Confidence for text salvaged from JSON that was cut off or wasn't JSON at all.
	private static final double SALVAGED_CONFIDENCE = 0.35;
	private static final int LOG_PREVIEW_LENGTH = 500;

	private static final Pattern EXPLANATION_FIELD = Pattern.compile("\"explanation\"\\s*:\\s*\"([^\"]*)");
	private static final Pattern STEPS_FIELD = Pattern.compile("\"steps\"\\s*:\\s*\"([^\"]*)");

	@Value("${gemini.api.key:}")
	private String apiKey;

	@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
	private String apiBaseUrl;

	@Value("${gemini.model:gemini-flash-latest}")
	private String model;

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public String getModel() {
		return model;
	}

	public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {
		log.info("in generate fix suggestion (gemini)");
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("Gemini API key not configured, returning default suggestion");
			return fallback(vulnerability);
		}

		try {
			ResponseEntity<String> response = restTemplate.exchange(requestUrl(), HttpMethod.POST,
					requestEntity(buildPrompt(vulnerability)), String.class);
			JsonNode candidate = firstCandidate(readEnvelope(response));
			String finishReason = candidate.path("finishReason").asText("STOP");
			String rawContent = candidate.path("content").path(PARTS).get(0).path("text").asText();
			return toSuggestion(rawContent, finishReason, vulnerability);
		} catch (Exception e) {
			log.error("Gemini API call failed: {}", e.getMessage());
			return fallback(vulnerability);
		}
	}

	private AiSuggestionResult fallback(Vulnerability vulnerability) {
		return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
	}

	private String requestUrl() {
		return UriComponentsBuilder.fromUriString(apiBaseUrl + "/" + model + ":generateContent")
				.queryParam("key", apiKey)
				.toUriString();
	}

	/**
	 * contents[0].parts[0].text + generationConfig.responseMimeType=application/json —
	 * built via Jackson (not string interpolation) so prompt text is escaped correctly.
	 */
	private HttpEntity<String> requestEntity(String prompt) throws JsonProcessingException {
		ObjectNode requestRoot = objectMapper.createObjectNode();
		ArrayNode contents = requestRoot.putArray("contents");
		ObjectNode contentEntry = contents.addObject();
		ArrayNode parts = contentEntry.putArray(PARTS);
		parts.addObject().put("text", prompt);

		ObjectNode generationConfig = requestRoot.putObject("generationConfig");
		generationConfig.put("temperature", 0.3);
		// 600 was cutting JSON responses off mid-string on gemini-flash-latest —
		// raised so the model has room to finish the object before hitting the cap.
		generationConfig.put("maxOutputTokens", 2048);
		generationConfig.put("responseMimeType", "application/json");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(objectMapper.writeValueAsString(requestRoot), headers);
	}

	private JsonNode readEnvelope(ResponseEntity<String> response) throws JsonProcessingException {
		String rawBody = response.getBody();
		try {
			return objectMapper.readTree(rawBody);
		} catch (JsonProcessingException parseEx) {
			log.warn("Gemini HTTP response body was not valid JSON (status={}), raw body: {}", response.getStatusCode(),
					preview(rawBody));
			throw parseEx;
		}
	}

	private JsonNode firstCandidate(JsonNode root) {
		JsonNode candidates = root.path(CANDIDATES);
		if (!candidates.isArray() || candidates.isEmpty()) {
			// e.g. blocked by promptFeedback.blockReason — no candidate to read
			throw new IllegalStateException("Gemini returned no candidates: " + root.path("promptFeedback"));
		}
		return candidates.get(0);
	}

	/**
	 * The envelope is JSON; "text" is itself a JSON string because of responseMimeType, so it needs a
	 * second parse pass. Gemini doesn't always honor responseMimeType and can return unstructured prose,
	 * or JSON truncated mid-string by maxOutputTokens — when the full object can't be parsed, salvage
	 * whatever field values are readable so the user sees real content instead of raw JSON syntax.
	 */
	private AiSuggestionResult toSuggestion(String rawContent, String finishReason, Vulnerability vulnerability) {
		String content = stripJsonFence(rawContent);
		JsonNode parsed = parseJson(content);
		if (parsed == null) {
			log.warn("Gemini did not return valid JSON (likely truncated), raw text: {}", preview(rawContent));
			String salvaged = extractPartialJsonFields(content);
			String plainTextSuggestion = salvaged != null ? salvaged : plainText(content, rawContent);
			log.info("Generated AI suggestion (salvaged from incomplete JSON) for vulnerability: {} (confidence={})",
					vulnerability.getId(), SALVAGED_CONFIDENCE);
			return new AiSuggestionResult(plainTextSuggestion, "", SALVAGED_CONFIDENCE, false);
		}

		String explanation = parsed.path("explanation").asText("");
		String steps = parsed.path("steps").asText("");
		String suggestionText = (explanation + "\n\n" + steps).trim();
		String codeExample = parsed.path("code_example").asText("");
		double confidence = Math.max(0.0, Math.min(1.0, parsed.path("confidence").asDouble(0.5)));
		if ("MAX_TOKENS".equals(finishReason)) {
			// Response got cut off — don't trust it as much as a complete answer.
			confidence = Math.min(confidence, 0.5);
		}
		if (suggestionText.isBlank() || codeExample.isBlank()) {
			// Model skipped a required field; the answer is incomplete.
			confidence = Math.min(confidence, 0.4);
		}

		log.info("Generated AI suggestion for vulnerability: {} (confidence={})", vulnerability.getId(), confidence);
		return new AiSuggestionResult(suggestionText, codeExample, confidence, false);
	}

	private JsonNode parseJson(String content) {
		try {
			return objectMapper.readTree(content);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	private String plainText(String content, String rawContent) {
		return content.isBlank() ? rawContent.trim() : content.trim();
	}

	private String preview(String text) {
		if (text != null && text.length() > LOG_PREVIEW_LENGTH) {
			return text.substring(0, LOG_PREVIEW_LENGTH) + "...(truncated)";
		}
		return text;
	}

	/**
	 * Strips a leading/trailing markdown code fence (```json ... ``` or ``` ... ```)
	 * that the model sometimes wraps its answer in despite responseMimeType being set.
	 */
	private String stripJsonFence(String text) {
		if (text == null) {
			return "";
		}
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

	/**
	 * Best-effort recovery for JSON truncated mid-string by maxOutputTokens —
	 * pulls out whatever text made it into "explanation"/"steps" before the cutoff,
	 * so the user sees readable prose instead of a dangling "{ "explanation": ...".
	 * Returns null if neither field is present (nothing worth salvaging).
	 */
	private String extractPartialJsonFields(String text) {
		String explanation = matchGroup(EXPLANATION_FIELD, text);
		String steps = matchGroup(STEPS_FIELD, text);
		if (explanation == null && steps == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		if (explanation != null && !explanation.isBlank()) {
			sb.append(explanation.trim());
		}
		if (steps != null && !steps.isBlank()) {
			if (sb.length() > 0) {
				sb.append("\n\n");
			}
			sb.append(steps.trim());
		}
		return sb.length() > 0 ? sb.toString() : null;
	}

	private String matchGroup(Pattern pattern, String text) {
		Matcher m = pattern.matcher(text);
		return m.find() ? m.group(1) : null;
	}

	private String buildPrompt(Vulnerability vulnerability) {
		String type = vulnerability.getVulnerabilityType() != null ? vulnerability.getVulnerabilityType() : "Security Issue";
		int line = vulnerability.getLineNumber() != null ? vulnerability.getLineNumber() : 0;
		return String.format("""
				You are a security expert. Analyze this vulnerability and respond with ONLY a JSON object
				(no markdown, no prose outside the JSON) with these exact keys:
				{
				  "explanation": "1 sentence describing the risk",
				  "steps": "3-4 step-by-step fix instructions",
				  "code_example": "a code snippet showing the fix",
				  "confidence": a number from 0.0 to 1.0, how confident you are this fix is correct for this exact code
				}

				Type: %s
				Severity: %s
				File: %s
				Line: %d
				Description: %s
				""",
				type, vulnerability.getSeverity(), vulnerability.getFilePath(), line, vulnerability.getMessage());
	}
}
