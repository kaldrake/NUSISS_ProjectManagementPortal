package com.portal.scan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.scan.entity.Vulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.OptionalDouble;

@Service
public class DeepSeekService {

	private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);

	@Value("${deepseek.api.key:}")
	private String apiKey;

	@Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
	private String apiUrl;

	@Value("${deepseek.model:deepseek-chat}")
	private String model;

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public String getModel() {
		return model;
	}

	// Confidence assigned whenever we couldn't get a real, complete answer from the model.
	private static final double FALLBACK_CONFIDENCE = 0.2;

	public AiSuggestionResult generateFixSuggestion(Vulnerability vulnerability) {

		log.info("in generate fix suggestion");
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("DeepSeek API key not configured, returning default suggestion");
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}

		String prompt = buildPrompt(vulnerability);

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(apiKey);

			// response_format json_object: ask DeepSeek to return structured fields
			// (including a self-reported confidence) instead of free-form prose.
			String requestBody = String.format(
					"{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],"
							+ "\"temperature\":0.3,\"max_tokens\":600,"
							+ "\"response_format\":{\"type\":\"json_object\"}}",
					model, escapeJson(prompt));

			HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode choice = root.path("choices").get(0);
			String finishReason = choice.path("finish_reason").asText("stop");
			String content = choice.path("message").path("content").asText();

			// The API envelope is JSON; "content" is itself a JSON string because of
			// response_format above, so it needs a second parse pass.
			JsonNode parsed = objectMapper.readTree(content);
			String explanation = parsed.path("explanation").asText("");
			String steps = parsed.path("steps").asText("");
			String suggestionText = (explanation + "\n\n" + steps).trim();
			String codeExample = parsed.path("code_example").asText("");
			double rawConfidence = parsed.path("confidence").asDouble(0.5);

			double confidence = Math.max(0.0, Math.min(1.0, rawConfidence));
			if ("length".equals(finishReason)) {
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
			log.error("DeepSeek API call failed: {}", e.getMessage());
			return new AiSuggestionResult(FallbackSuggestionTemplates.forVulnerability(vulnerability), "", FALLBACK_CONFIDENCE, true);
		}
	}

	/**
	 * Second call of the double-call scoring: an independent review of an already generated suggestion.
	 * Empty when no key is set or the reply is unusable, so the caller keeps the self-reported confidence.
	 */
	public OptionalDouble judgeFixSuggestion(Vulnerability vulnerability, AiSuggestionResult suggestion) {
		if (apiKey == null || apiKey.isEmpty()) {
			return OptionalDouble.empty();
		}
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(apiKey);

			String requestBody = String.format(
					"{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],"
							+ "\"temperature\":0,\"max_tokens\":300,"
							+ "\"response_format\":{\"type\":\"json_object\"}}",
					model, escapeJson(FixSuggestionJudge.buildPrompt(vulnerability, suggestion)));

			ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST,
					new HttpEntity<>(requestBody, headers), String.class);
			String reply = objectMapper.readTree(response.getBody()).path("choices").get(0).path("message").path("content").asText();
			return FixSuggestionJudge.parseScore(reply);
		} catch (Exception e) {
			log.warn("DeepSeek judge call failed: {}", e.getMessage());
			return OptionalDouble.empty();
		}
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
    
    public boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("DeepSeek API key not configured, cannot test connection");
            return false;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            String requestBody = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"OK\"}],\"max_tokens\":5}";
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.info("DeepSeek API connection test successful");
            } else {
                log.warn("DeepSeek API connection test failed with status: {}", response.getStatusCode());
            }
            return success;
            
        } catch (Exception e) {
            log.error("DeepSeek connection test failed: {}", e.getMessage());
            return false;
        }
    }
}