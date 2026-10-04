package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;

/**
 * Canned fix suggestions shared by every AI provider (Gemini, DeepSeek, Claude) as the
 * fallback when the provider is unavailable, so the template text lives in one place.
 */
public final class FallbackSuggestionTemplates {

	private FallbackSuggestionTemplates() {
	}

	/**
	 * Picks a canned remediation text by keyword in the finding message (used when no AI answer is available).
	 */
	public static String forVulnerability(Vulnerability vulnerability) {
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

	private static String getSqlInjectionSuggestion() {
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

	private static String getHardcodedCredentialsSuggestion() {
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

	private static String getNullPointerSuggestion() {
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

	private static String getPathTraversalSuggestion() {
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

	private static String getXssSuggestion() {
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

	private static String getCriticalSuggestion() {
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

	private static String getGenericSuggestion() {
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
}
