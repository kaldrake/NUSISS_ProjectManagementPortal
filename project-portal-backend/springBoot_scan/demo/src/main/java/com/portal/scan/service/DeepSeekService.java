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

	public String generateFixSuggestion(Vulnerability vulnerability) {
		
		log.info("in generate fix suggestion");		
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("DeepSeek API key not configured, returning default suggestion");
			return getDefaultSuggestion(vulnerability);
		}

		String prompt = buildPrompt(vulnerability);

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(apiKey);

			String requestBody = String.format(
					"{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":500}",
					model, escapeJson(prompt));

			HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

			JsonNode root = objectMapper.readTree(response.getBody());
			String suggestion = root.path("choices").get(0).path("message").path("content").asText();

			log.info("Generated AI suggestion for vulnerability: {}", vulnerability.getId());
			return suggestion;

		} catch (Exception e) {
			log.error("DeepSeek API call failed: {}", e.getMessage());
			return getDefaultSuggestion(vulnerability);
		}
	}

	private String buildPrompt(Vulnerability vulnerability) {
		return String.format("""
				You are a security expert. Provide a fix for this vulnerability:

				Type: %s
				Severity: %s
				File: %s
				Line: %d
				Description: %s

				Provide:
				1. A brief explanation of the risk (1 sentence)
				2. Step-by-step fix instructions (3-4 steps)
				3. A code example showing the fix

				Keep response concise, under 500 words.
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