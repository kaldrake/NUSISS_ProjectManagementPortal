package com.portal.scan.service;

import com.portal.scan.entity.Vulnerability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackSuggestionTemplatesTest {

    private Vulnerability vulnerability(String message, String severity) {
        Vulnerability v = new Vulnerability();
        v.setMessage(message);
        v.setSeverity(severity);
        return v;
    }

    private String pick(String message) {
        return FallbackSuggestionTemplates.forVulnerability(vulnerability(message, "MINOR"));
    }

    @Test
    void sqlInjectionKeywords_pickTheSqlTemplate() {
        assertTrue(pick("Possible SQL query built from input").contains("SQL Injection"));
        assertTrue(pick("Injection risk").contains("SQL Injection"));
    }

    @Test
    void credentialKeywords_pickTheCredentialsTemplate() {
        assertTrue(pick("Hardcoded value").contains("Hardcoded Credentials"));
        assertTrue(pick("Password in source").contains("Hardcoded Credentials"));
        assertTrue(pick("Credential leak").contains("Hardcoded Credentials"));
    }

    @Test
    void nullKeyword_picksTheNullPointerTemplate() {
        assertTrue(pick("Possible null dereference").contains("Null Pointer"));
    }

    @Test
    void traversalKeywords_pickThePathTraversalTemplate() {
        String path = pick("Path traversal in file name");
        assertEquals(path, pick("Directory traversal detected"));
        assertNotEquals(pick("something unrelated"), path);
    }

    @Test
    void xssKeywords_pickTheXssTemplate() {
        assertTrue(pick("Reflected XSS").contains("Cross-Site Scripting"));
        assertEquals(pick("xss"), pick("Cross-site scripting"));
    }

    @Test
    void unmatchedFindings_getTheCriticalOrGenericTemplateBySeverity() {
        String critical = FallbackSuggestionTemplates.forVulnerability(vulnerability("odd", "CRITICAL"));
        String blocker = FallbackSuggestionTemplates.forVulnerability(vulnerability("odd", "BLOCKER"));
        String generic = FallbackSuggestionTemplates.forVulnerability(vulnerability("odd", "MINOR"));

        assertEquals(critical, blocker);
        assertNotEquals(critical, generic);
        assertTrue(generic.contains("Security Fix Recommended"));
    }
}
