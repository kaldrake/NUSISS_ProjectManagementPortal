package com.portal.scan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.scan.entity.Vulnerability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GeminiService. The outbound HTTP call is replaced with a mocked RestTemplate,
 * so no network access or API key is needed.
 */
class GeminiServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeminiService service;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        service = new GeminiService();
        restTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost/v1beta/models");
        ReflectionTestUtils.setField(service, "model", "gemini-test");
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    private Vulnerability vulnerability(String message, String severity) {
        Vulnerability v = new Vulnerability();
        v.setId(1L);
        v.setMessage(message);
        v.setSeverity(severity);
        v.setFilePath("src/Main.java");
        v.setLineNumber(12);
        v.setVulnerabilityType("VULNERABILITY");
        return v;
    }

    /** Builds the Gemini response envelope: candidates[0].content.parts[0].text = modelText. */
    private String geminiBody(String modelText, String finishReason) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode candidates = root.putArray("candidates");
        ObjectNode candidate = candidates.addObject();
        candidate.put("finishReason", finishReason);
        candidate.putObject("content").putArray("parts").addObject().put("text", modelText);
        return MAPPER.writeValueAsString(root);
    }

    private void respondWith(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    // ---------------------------------------------------------------- configuration

    @Test
    void getModel_returnsConfiguredModel() {
        assertEquals("gemini-test", service.getModel());
    }

    @Test
    void noApiKey_returnsTemplateFallback_withoutCallingApi() {
        ReflectionTestUtils.setField(service, "apiKey", "");

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("possible SQL injection", "CRITICAL"));

        assertTrue(result.isUsedFallback());
        assertEquals(0.2, result.getConfidenceScore());
        verifyNoInteractions(restTemplate);
    }

    // ---------------------------------------------------------------- successful responses

    @Test
    void validJsonResponse_isParsedWithModelConfidence() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"Unsafe query.\",\"steps\":\"Use a prepared statement.\","
                + "\"code_example\":\"ps.setInt(1, id);\",\"confidence\":0.85}", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("sql", "CRITICAL"));

        assertFalse(result.isUsedFallback());
        assertEquals(0.85, result.getConfidenceScore(), 0.0001);
        assertEquals("ps.setInt(1, id);", result.getCodeExample());
        assertTrue(result.getSuggestionText().contains("Unsafe query."));
        assertTrue(result.getSuggestionText().contains("Use a prepared statement."));
    }

    @Test
    void markdownFencedJson_isStrippedAndParsed() throws Exception {
        String fenced = "```json\n{\"explanation\":\"Risk.\",\"steps\":\"Fix it.\","
                + "\"code_example\":\"x();\",\"confidence\":0.7}\n```";
        respondWith(geminiBody(fenced, "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertFalse(result.isUsedFallback());
        assertEquals(0.7, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void confidenceAboveOne_isClampedToOne() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"a\",\"steps\":\"b\",\"code_example\":\"c\",\"confidence\":7}", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertEquals(1.0, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void negativeConfidence_isClampedToZero() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"a\",\"steps\":\"b\",\"code_example\":\"c\",\"confidence\":-3}", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertEquals(0.0, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void maxTokensFinishReason_capsConfidenceAtHalf() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"a\",\"steps\":\"b\",\"code_example\":\"c\",\"confidence\":0.95}",
                "MAX_TOKENS"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertEquals(0.5, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void missingCodeExample_capsConfidenceAtPointFour() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"a\",\"steps\":\"b\",\"confidence\":0.95}", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertEquals(0.4, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void missingConfidenceField_defaultsToHalf() throws Exception {
        respondWith(geminiBody("{\"explanation\":\"a\",\"steps\":\"b\",\"code_example\":\"c\"}", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertEquals(0.5, result.getConfidenceScore(), 0.0001);
    }

    // ---------------------------------------------------------------- degraded responses

    @Test
    void truncatedJson_isSalvagedAtLowConfidence() throws Exception {
        // cut off mid-string, as happens when maxOutputTokens is hit
        respondWith(geminiBody("{\"explanation\": \"Passwords in source are exposed\", \"steps\": \"Move them to env var", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("hardcoded password", "CRITICAL"));

        assertFalse(result.isUsedFallback());
        assertEquals(0.35, result.getConfidenceScore(), 0.0001);
        assertTrue(result.getSuggestionText().contains("Passwords in source are exposed"));
        assertTrue(result.getSuggestionText().contains("Move them to env var"));
    }

    @Test
    void plainTextResponse_isReturnedAsSuggestionAtLowConfidence() throws Exception {
        respondWith(geminiBody("Just validate the input before using it.", "STOP"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertFalse(result.isUsedFallback());
        assertEquals(0.35, result.getConfidenceScore(), 0.0001);
        assertEquals("Just validate the input before using it.", result.getSuggestionText());
    }

    @Test
    void noCandidates_fallsBackToTemplate() {
        respondWith("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertTrue(result.isUsedFallback());
        assertEquals(0.2, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void apiError_fallsBackToTemplate() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertTrue(result.isUsedFallback());
        assertEquals(0.2, result.getConfidenceScore(), 0.0001);
    }

    @Test
    void nonJsonHttpBody_fallsBackToTemplate() {
        respondWith("<html>bad gateway</html>");

        AiSuggestionResult result = service.generateFixSuggestion(vulnerability("anything", "BLOCKER"));

        assertTrue(result.isUsedFallback());
    }

    // ---------------------------------------------------------------- template selection

    @Test
    void fallbackTemplate_isChosenByKeywordInMessage() {
        ReflectionTestUtils.setField(service, "apiKey", "");

        String sql = service.generateFixSuggestion(vulnerability("SQL injection in query", "MAJOR")).getSuggestionText();
        String creds = service.generateFixSuggestion(vulnerability("hardcoded password found", "MAJOR")).getSuggestionText();
        String xss = service.generateFixSuggestion(vulnerability("possible XSS", "MAJOR")).getSuggestionText();

        assertTrue(sql.contains("SQL Injection"));
        assertTrue(creds.contains("Hardcoded Credentials"));
        assertTrue(xss.toLowerCase().contains("xss") || xss.toLowerCase().contains("cross-site"));
    }

    @Test
    void fallbackTemplate_differsForCriticalAndMinorUnmatchedFindings() {
        ReflectionTestUtils.setField(service, "apiKey", "");

        String critical = service.generateFixSuggestion(vulnerability("something odd", "CRITICAL")).getSuggestionText();
        String minor = service.generateFixSuggestion(vulnerability("something odd", "MINOR")).getSuggestionText();

        assertFalse(critical.isBlank());
        assertFalse(minor.isBlank());
        assertFalse(critical.equals(minor));
    }
}
