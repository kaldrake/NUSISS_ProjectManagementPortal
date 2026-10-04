package com.portal.scan.service;

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

	@Value("${gemini.api.key:}")
	private String apiKey;

	@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
	private String apiBaseUrl;

	@Value("${gemini.model:gemini-flash-latest}")
	private String model;

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	// Confidence assigned whenever we couldn't get a real, complete answer from the model.
	private static final double FALLBACK_CONFIDENCE = 0.2;

	public String getModel() {
		return model;
	}

	public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {

		log.info("in generate fix suggestion (gemini)");
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("Gemini API key not configured, returning default suggestion");
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}

		String prompt = buildPrompt(vulnerability);

		try {
			// contents[0].parts[0].text + generationConfig.responseMimeType=application/json —
			// built via Jackson (not string interpolation) so prompt text is escaped correctly.
			ObjectNode requestRoot = objectMapper.createObjectNode();
			ArrayNode contents = requestRoot.putArray("contents");
			ObjectNode contentEntry = contents.addObject();
			ArrayNode parts = contentEntry.putArray("parts");
			parts.addObject().put("text", prompt);

			ObjectNode generationConfig = requestRoot.putObject("generationConfig");
			generationConfig.put("temperature", 0.3);
			// 600 was cutting JSON responses off mid-string on gemini-flash-latest —
			// raised so the model has room to finish the object before hitting the cap.
			generationConfig.put("maxOutputTokens", 2048);
			generationConfig.put("responseMimeType", "application/json");

			String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/" + model + ":generateContent")
					.queryParam("key", apiKey)
					.toUriString();

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestRoot), headers);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

			String rawBody = response.getBody();
			JsonNode root;
			try {
				root = objectMapper.readTree(rawBody);
			} catch (Exception parseEx) {
				log.warn("Gemini HTTP response body was not valid JSON (status={}), raw body: {}", response.getStatusCode(),
						rawBody != null && rawBody.length() > 500 ? rawBody.substring(0, 500) + "...(truncated)" : rawBody);
				throw parseEx;
			}
			JsonNode candidates = root.path("candidates");
			if (!candidates.isArray() || candidates.isEmpty()) {
				// e.g. blocked by promptFeedback.blockReason — no candidate to read
				throw new IllegalStateException("Gemini returned no candidates: " + root.path("promptFeedback"));
			}

			JsonNode candidate = candidates.get(0);
			String finishReason = candidate.path("finishReason").asText("STOP");
			String rawContent = candidate.path("content").path("parts").get(0).path("text").asText();
			String content = stripJsonFence(rawContent);

			// The envelope is JSON; "text" is itself a JSON string because of
			// responseMimeType above, so it needs a second parse pass. Gemini doesn't
			// always honor responseMimeType and can return unstructured prose, or JSON
			// truncated mid-string by maxOutputTokens — when the full object can't be
			// parsed, salvage whatever field values are readable so the user sees real
			// content instead of raw "{ "explanation": ..." syntax.
			JsonNode parsed;
			try {
				parsed = objectMapper.readTree(content);
			} catch (Exception parseEx) {
				log.warn("Gemini did not return valid JSON (likely truncated), raw text: {}",
						rawContent.length() > 500 ? rawContent.substring(0, 500) + "...(truncated)" : rawContent);
				String salvaged = extractPartialJsonFields(content);
				String plainTextSuggestion = salvaged != null ? salvaged
						: (content.isBlank() ? rawContent.trim() : content.trim());
				log.info("Generated AI suggestion (salvaged from incomplete JSON) for vulnerability: {} (confidence={})",
						vulnerability.getId(), 0.35);
				return new AiSuggestionResult(plainTextSuggestion, "", 0.35, false);
			}
			String explanation = parsed.path("explanation").asText("");
			String steps = parsed.path("steps").asText("");
			String suggestionText = (explanation + "\n\n" + steps).trim();
			String codeExample = parsed.path("code_example").asText("");
			double rawConfidence = parsed.path("confidence").asDouble(0.5);

			double confidence = Math.max(0.0, Math.min(1.0, rawConfidence));
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

		} catch (Exception e) {
			log.error("Gemini API call failed: {}", e.getMessage());
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}
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

	private static final Pattern EXPLANATION_FIELD = Pattern.compile("\"explanation\"\\s*:\\s*\"([^\"]*)");
	private static final Pattern STEPS_FIELD = Pattern.compile("\"steps\"\\s*:\\s*\"([^\"]*)");

	/**
	 * Best-effort recovery for JSON truncated mid-string by maxOutputTokens —
	 * pulls out whatever text made it into "explanation"/"steps" before the cutoff,
	 * so the user sees readable prose instead of a dangling "{ "explanation": ...".
	 * Returns null if neither field is present (nothing worth salvaging).
	 */
	private String extractPartialJsonFields(String text) {
		if (text == null) {
			return null;
		}
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
				vulnerability.getVulnerabilityType() != null ? vulnerability.getVulnerabilityType() : "Security Issue",
				vulnerability.getSeverity(), vulnerability.getFilePath(),
				vulnerability.getLineNumber() != null ? vulnerability.getLineNumber() : 0, vulnerability.getMessage());
	}

	public boolean testConnection() {
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("Gemini API key not configured, cannot test connection");
			return false;
		}

		try {
			ObjectNode requestRoot = objectMapper.createObjectNode();
			ArrayNode contents = requestRoot.putArray("contents");
			ObjectNode contentEntry = contents.addObject();
			ArrayNode parts = contentEntry.putArray("parts");
			parts.addObject().put("text", "OK");

			ObjectNode generationConfig = requestRoot.putObject("generationConfig");
			generationConfig.put("maxOutputTokens", 5);

			String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/" + model + ":generateContent")
					.queryParam("key", apiKey)
					.toUriString();

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestRoot), headers);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

			boolean success = response.getStatusCode().is2xxSuccessful();
			if (success) {
				log.info("Gemini API connection test successful");
			} else {
				log.warn("Gemini API connection test failed with status: {}", response.getStatusCode());
			}
			return success;

		} catch (Exception e) {
			log.error("Gemini connection test failed: {}", e.getMessage());
			return false;
		}
	}
}
