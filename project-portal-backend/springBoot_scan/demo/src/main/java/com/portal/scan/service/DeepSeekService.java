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
			return new AiSuggestionResult(getDefaultSuggestion(vulnerability), "", FALLBACK_CONFIDENCE, true);
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
			return new AiSuggestionResult(getDefaultSuggestion(vulnerability), "", FALLBACK_CONFIDENCE, true);
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

	private String getDefaultSuggestion(Vulnerability vulnerability) {
		String message = vulnerability.getMessage().toLowerCase();
		String severity = vulnerability.getSeverity();

		if (message.contains("sql") || message.contains("injection")) {
			return getSqlInjectionSuggestion();
		}
		if (message.contains("hardcoded") || message.contains("password") || message.contains("credential")) {
			return getHardcodedCredentialsSuggestion();
		}
		if (message.contains("null")) {
			return getNullPointerSuggestion();
		}
		if (message.contains("path traversal") || message.contains("directory traversal")) {
			return getPathTraversalSuggestion();
		}
		if (message.contains("xss") || message.contains("cross-site")) {
			return getXssSuggestion();
		}
		if ("BLOCKER".equals(severity) || "CRITICAL".equals(severity)) {
			return getCriticalSuggestion();
		}
		return getGenericSuggestion();
	}

	private String getSqlInjectionSuggestion() {
		return """
				  **SQL Injection Prevention**

				  **Risk:** SQL injection allows attackers to manipulate database queries, leading to data theft or destruction.

				  **Fix Steps:**
				  1. Use parameterized queries (PreparedStatement)
				  2. Never concatenate user input into SQL strings
				  3. Validate and sanitize all user input
				  4. Use an ORM framework like Hibernate

				  **Code Example:**
				  ```java
				  // Vulnerable code:
				  String query = "SELECT * FROM users WHERE id = " + userId;
				  Statement stmt = conn.createStatement();

				  // Secure code:
				  String query = "SELECT * FROM users WHERE id = ?";
				  PreparedStatement stmt = conn.prepareStatement(query);
				  stmt.setInt(1, userId);
				""";
	}

	private String getHardcodedCredentialsSuggestion() {
		return """
				Remove Hardcoded Credentials

				Risk: Hardcoded passwords in source code are easily discovered.

				Fix Steps:

				Move credentials to environment variables

				Use a secrets manager for production

				Never commit secrets to version control

				Use configuration files with proper access controls

				Code Example:

				properties
				db.password=${DB_PASSWORD}
				bash
				export DB_PASSWORD=secure_password_123
				""";
	}

	private String getNullPointerSuggestion() {
		return """
				Null Pointer Prevention

				Risk: Null pointer exceptions cause application crashes.

				Fix Steps:

				Add null checks before accessing objects

				Use Optional to represent nullable values

				Use @NotNull and @Nullable annotations

				Initialize objects properly

				Code Example:

				java
				// Vulnerable:
				object.method();

				// Secure:
				if (object != null) {
				    object.method();
				}
				""";
	}

	private String getPathTraversalSuggestion() {
		return """
				Path Traversal Prevention

				Risk: Path traversal allows attackers to access unauthorized files.

				Fix Steps:

				Validate and sanitize file paths

				Use a whitelist of allowed paths

				Resolve paths using a secure base directory

				Never use user input directly in file operations

				Code Example:

				java
				Path basePath = Paths.get("/app/files");
				Path resolvedPath = basePath.resolve(userInput).normalize();
				if (!resolvedPath.startsWith(basePath)) {
				    throw new SecurityException("Path traversal detected");
				}
				""";
	}

	private String getXssSuggestion() {
		return """
				Cross-Site Scripting (XSS) Prevention

				Risk: XSS allows attackers to inject malicious scripts into web pages.

				Fix Steps:

				Escape all user-generated content before display

				Use Content Security Policy (CSP) headers

				Validate and sanitize input

				Use framework features for output encoding

				Code Example:

				java
				// Vulnerable:
				out.print("<div>" + userInput + "</div>");

				// Secure:
				String escaped = StringEscapeUtils.escapeHtml4(userInput);
				out.print("<div>" + escaped + "</div>");
				""";
	}

	private String getCriticalSuggestion() {
		return """
				Critical Security Issue Detected

				Action Required Immediately:

				Review the code at the specified location

				Understand the security implications

				Apply appropriate security controls

				Test thoroughly after changes

				Document the fix
				""";
	}

	private String getGenericSuggestion() {
		return """
				Security Fix Recommended

				Steps to Resolve:

				Review the code at the specified location

				Identify the security weakness

				Apply appropriate security controls

				Test the fix thoroughly

				Document the changes made
				""";
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