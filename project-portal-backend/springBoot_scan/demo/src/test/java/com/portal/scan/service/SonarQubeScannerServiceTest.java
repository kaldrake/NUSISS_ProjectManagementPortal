package com.portal.scan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for the parts of SonarQubeScannerService that do not need a real SonarQube server:
 * issue parsing, analysis polling, clone failure handling, and temp-dir cleanup. SonarQube's
 * REST API is replaced by a MockRestServiceServer.
 */
class SonarQubeScannerServiceTest {

    private static final String HOST = "http://sonar.test:9000";

    private SonarQubeScannerService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new SonarQubeScannerService();
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "sonarHostUrl", HOST);
        ReflectionTestUtils.setField(service, "sonarToken", "test-token");
    }

    private static final String ISSUES_URL =
            HOST + "/api/issues/search?componentKeys=proj&types=VULNERABILITY&ps=500&resolved=false";

    // ---------------------------------------------------------------- fetchIssues

    @Test
    void fetchIssues_mapsSonarIssuesToScannerIssues() {
        String body = "{\"issues\":[{\"rule\":\"java:S2068\",\"type\":\"VULNERABILITY\",\"severity\":\"BLOCKER\","
                + "\"component\":\"proj:src/Main.java\",\"line\":12,\"message\":\"Hardcoded password\"}]}";
        server.expect(requestTo(ISSUES_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic dGVzdC10b2tlbjo="))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertEquals(1, issues.size());
        SonarQubeIssue issue = issues.get(0);
        assertEquals("java:S2068", issue.getRuleId());
        assertEquals("VULNERABILITY", issue.getType());
        assertEquals("BLOCKER", issue.getSeverity());
        assertEquals("src/Main.java", issue.getFilePath());
        assertEquals(12, issue.getLineNumber());
        assertEquals("Hardcoded password", issue.getMessage());
        server.verify();
    }

    @Test
    void fetchIssues_keepsComponentAsIs_whenItHasNoProjectPrefix() {
        String body = "{\"issues\":[{\"rule\":\"r\",\"type\":\"VULNERABILITY\",\"severity\":\"MAJOR\","
                + "\"component\":\"plain/path.java\",\"line\":1,\"message\":\"m\"}]}";
        server.expect(requestTo(ISSUES_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertEquals("plain/path.java", issues.get(0).getFilePath());
    }

    @Test
    void fetchIssues_returnsEmptyList_whenThereAreNoIssues() {
        server.expect(requestTo(ISSUES_URL)).andRespond(withSuccess("{\"issues\":[]}", MediaType.APPLICATION_JSON));

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertTrue(issues.isEmpty());
    }

    @Test
    void fetchIssues_returnsEmptyList_whenSonarReturnsAnError() {
        server.expect(requestTo(ISSUES_URL)).andRespond(withServerError());

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertTrue(issues.isEmpty());
    }

    @Test
    void fetchIssues_returnsEmptyList_whenSonarRejectsTheToken() {
        server.expect(requestTo(ISSUES_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertTrue(issues.isEmpty());
    }

    @Test
    void fetchIssues_returnsEmptyList_whenBodyIsNotJson() {
        server.expect(requestTo(ISSUES_URL)).andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        List<SonarQubeIssue> issues = ReflectionTestUtils.invokeMethod(service, "fetchIssues", "proj");

        assertTrue(issues.isEmpty());
    }

    // ---------------------------------------------------------------- waitForAnalysis

    @Test
    void waitForAnalysis_returns_whenTheFirstPollReportsSuccess() throws Exception {
        server.expect(requestTo(HOST + "/api/ce/activity?component=proj"))
                .andRespond(withSuccess("{\"tasks\":[{\"status\":\"SUCCESS\"}]}", MediaType.APPLICATION_JSON));

        ReflectionTestUtils.invokeMethod(service, "waitForAnalysis", "proj");

        server.verify();
    }

    // ---------------------------------------------------------------- clone

    @Test
    void scanRepository_fails_whenTheRepositoryCannotBeCloned() {
        assertThrows(Exception.class,
                () -> service.scanRepository("file:///definitely/not/a/repo", "main", "proj"));
    }

    private static boolean gitIsAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void cloneRepository_clonesALocalRepository(@TempDir Path origin) throws Exception {
        assumeTrue(gitIsAvailable(), "git is not installed");
        run(origin, "git", "init", "-b", "main");
        run(origin, "git", "-c", "user.email=t@example.com", "-c", "user.name=t", "commit", "--allow-empty", "-m", "init");

        String clonePath = ReflectionTestUtils.invokeMethod(service, "cloneRepository",
                "file://" + origin.toAbsolutePath(), "main");

        try {
            assertTrue(Files.exists(Path.of(clonePath, ".git")));
        } finally {
            ReflectionTestUtils.invokeMethod(service, "cleanup", clonePath);
        }
        assertFalse(Files.exists(Path.of(clonePath)));
    }

    private static void run(Path dir, String... command) throws Exception {
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertEquals(0, p.waitFor(), "command failed: " + String.join(" ", command));
    }

    // ---------------------------------------------------------------- scanner process + cleanup

    @Test
    void runSonarScanner_fails_whenTheScannerCannotRun(@TempDir Path workDir) {
        // sonar-scanner is not installed (or has no server to talk to) in the unit-test environment
        assertThrows(Exception.class,
                () -> ReflectionTestUtils.invokeMethod(service, "runSonarScanner", workDir.toString(), "proj"));
    }

    @Test
    void cleanup_deletesTheDirectoryTree(@TempDir Path parent) throws Exception {
        Path dir = Files.createDirectories(parent.resolve("scan/sub"));
        Files.writeString(dir.resolve("file.txt"), "x");
        Path root = parent.resolve("scan");

        ReflectionTestUtils.invokeMethod(service, "cleanup", root.toString());

        assertFalse(Files.exists(root));
    }

    @Test
    void cleanup_doesNotThrow_whenThePathDoesNotExist() {
        ReflectionTestUtils.invokeMethod(service, "cleanup", "/tmp/this-path-does-not-exist-" + System.nanoTime());
    }
}
