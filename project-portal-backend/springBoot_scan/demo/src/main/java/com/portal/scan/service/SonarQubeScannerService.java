// src/main/java/com/portal/scan/service/SonarQubeScannerService.java
package com.portal.scan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SonarQubeScannerService {
    
    private static final Logger log = LoggerFactory.getLogger(SonarQubeScannerService.class);
    
    @Value("${sonar.host.url:http://localhost:9001}")
    private String sonarHostUrl;
    
    @Value("${sonar.token:}")
    private String sonarToken;
    
    // Absolute executable paths (not looked up through PATH); override with scan.git.path / scan.sonar-scanner.path
    @Value("${scan.git.path:/usr/bin/git}")
    private String gitPath = "/usr/bin/git";

    @Value("${scan.sonar-scanner.path:/usr/local/bin/sonar-scanner}")
    private String sonarScannerPath = "/usr/local/bin/sonar-scanner";

    // Where repositories are cloned; empty means a "scan-workspace" folder in the service user's home
    @Value("${scan.workdir:}")
    private String workDir = "";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public List<SonarQubeIssue> scanRepository(String repoUrl, String branch, String projectKey) throws Exception {
        String repoPath = cloneRepository(repoUrl, branch);
        
        try {
            runSonarScanner(repoPath, projectKey);
            waitForAnalysis(projectKey);
            return fetchIssues(projectKey);
        } finally {
            cleanup(repoPath);
        }
    }
    
    /**
     * Creates a clone directory readable only by the service user, in a private work folder
     * rather than the world-writable system temp directory.
     */
    private Path createScanDirectory() throws IOException {
        Path base = (workDir == null || workDir.isBlank())
            ? Path.of(System.getProperty("user.home"), "scan-workspace")
            : Path.of(workDir);
        Files.createDirectories(base);
        Path dir = base.resolve("scan-" + UUID.randomUUID());
        try {
            return Files.createDirectory(dir,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) {
            // Non-POSIX file system (e.g. a Windows development machine)
            return Files.createDirectory(dir);
        }
    }

    private String cloneRepository(String repoUrl, String branch) throws Exception {
        String repoPath = createScanDirectory().toString();
        
        ProcessBuilder pb = new ProcessBuilder(
            gitPath, "clone", "--branch", branch, "--single-branch", "--depth", "1",
            repoUrl, repoPath
        );
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output for debugging
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("Git: {}", line);
            }
        }
        
        boolean completed = process.waitFor(2, TimeUnit.MINUTES);
        
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("Git clone timed out after 2 minutes");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IllegalStateException("Git clone failed with exit code: " + exitCode);
        }
        
        log.info("Cloned repository to: {}", repoPath);
        return repoPath;
    }
    
    private void runSonarScanner(String repoPath, String projectKey) throws Exception {
        log.info("=== Starting SonarScanner (Local Mode) ===");
        log.info("Project key: {}", projectKey);
        log.info("Repo path: {}", repoPath);
        
        ProcessBuilder pb = new ProcessBuilder(
            sonarScannerPath,
            "-Dsonar.projectKey=" + projectKey,
            "-Dsonar.sources=.",
            "-Dsonar.java.binaries=.",
            "-Dsonar.host.url=" + sonarHostUrl,
            "-Dsonar.login=" + sonarToken,
            "-X"
        );
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);
        
        // Mask the token so it never reaches the logs
        log.info("Running command: {}", pb.command().stream()
            .map(arg -> arg.startsWith("-Dsonar.login=") ? "-Dsonar.login=****" : arg)
            .collect(java.util.stream.Collectors.joining(" ")));
        
        Process process = pb.start();
        
        // Capture ALL output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("SonarScanner: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("SonarScanner failed with exit code: {}", exitCode);
            log.error("Full output:\n{}", output.toString());
            throw new IllegalStateException("SonarScanner failed with exit code: " + exitCode + "\nOutput: " + output);
        }
        
        log.info("SonarScanner completed successfully");
    }
    
    private void waitForAnalysis(String projectKey) throws InterruptedException {
        String url = sonarHostUrl + "/api/ce/activity?component=" + projectKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");

        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);

            String status = latestTaskStatus(url, headers, i + 1);
            if ("SUCCESS".equals(status)) {
                log.info("Analysis completed for: {}", projectKey);
                return;
            }
            if ("FAILED".equals(status)) {
                throw new IllegalStateException("Analysis failed for: " + projectKey);
            }
        }

        throw new IllegalStateException("Timeout waiting for analysis");
    }

    /** Status of the newest analysis task, or null if it cannot be read yet. */
    private String latestTaskStatus(String url, HttpHeaders headers, int attempt) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            JsonNode tasks = objectMapper.readTree(response.getBody()).path("tasks");
            if (tasks.isArray() && tasks.size() > 0) {
                return tasks.get(0).path("status").asText();
            }
        } catch (Exception e) {
            log.warn("Waiting for analysis... attempt {}, error: {}", attempt, e.getMessage());
        }
        log.debug("Waiting for analysis... attempt {}", attempt);
        return null;
    }

    private List<SonarQubeIssue> fetchIssues(String projectKey) throws Exception {
        String url = sonarHostUrl + "/api/issues/search?componentKeys=" + projectKey + 
                     "&types=VULNERABILITY&ps=500&resolved=false";
        
        log.info("Fetching issues from: {}", url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch issues. Status: {}", response.getStatusCode());
                return new ArrayList<>();
            }
            
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issues = root.path("issues");
            
            List<SonarQubeIssue> result = new ArrayList<>();
            for (JsonNode issue : issues) {
                SonarQubeIssue sqIssue = new SonarQubeIssue();
                sqIssue.setRuleId(issue.path("rule").asText());
                sqIssue.setType(issue.path("type").asText());
                sqIssue.setSeverity(issue.path("severity").asText());
                
                // Extract file path from component (format: projectKey:file/path)
                String component = issue.path("component").asText();
                if (component.contains(":")) {
                    sqIssue.setFilePath(component.substring(component.indexOf(":") + 1));
                } else {
                    sqIssue.setFilePath(component);
                }
                
                sqIssue.setLineNumber(issue.path("line").asInt());
                sqIssue.setMessage(issue.path("message").asText());
                result.add(sqIssue);
            }
            
            log.info("Fetched {} issues for project: {}", result.size(), projectKey);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch issues: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private void cleanup(String repoPath) {
        try {
            Files.walk(Path.of(repoPath))
                .sorted((a, b) -> b.compareTo(a))
                .map(Path::toFile)
                .forEach(File::delete);
            log.info("Cleaned up: {}", repoPath);
        } catch (Exception e) {
            log.warn("Failed to cleanup: {}", repoPath, e);
        }
    }
}

class SonarQubeIssue {
    private String ruleId;
    private String type;
    private String severity;
    private String filePath;
    private Integer lineNumber;
    private String message;
    
    // Getters and Setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}