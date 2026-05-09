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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SonarQubeScannerService {
    
    private static final Logger log = LoggerFactory.getLogger(SonarQubeScannerService.class);
    
    @Value("${sonar.host.url:http://localhost:9000}")
    private String sonarHostUrl;
    
    @Value("${sonar.token:}")
    private String sonarToken;
    
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
    
    private String cloneRepository(String repoUrl, String branch) throws Exception {
        String repoPath = Files.createTempDirectory("sonar-scan-").toString();
        
        ProcessBuilder pb = new ProcessBuilder(
            "git", "clone", "--branch", branch, "--single-branch", "--depth", "1",
            repoUrl, repoPath
        );
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        if(process.waitFor(2, TimeUnit.MINUTES))        {
        	int exitCode = process.exitValue();

        	if (exitCode != 0) 
	            throw new RuntimeException("Git clone failed with exit code: " + exitCode);
        }
        
        log.info("Cloned repository to: {}", repoPath);
        return repoPath;
    }
    
    private void runSonarScanner(String repoPath, String projectKey) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "sonar-scanner",
            "-Dsonar.projectKey=" + projectKey,
            "-Dsonar.sources=.",
            "-Dsonar.host.url=" + sonarHostUrl,
            "-Dsonar.login=" + sonarToken
        );
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("SonarScanner: {}", line);
            }
        }
        
        boolean exitCode = process.waitFor(5, TimeUnit.MINUTES);
        if (exitCode) {
            throw new RuntimeException("SonarScanner failed with exit code: " + exitCode);
        }
        
        log.info("SonarScanner completed for project: {}", projectKey);
    }
    
    private void waitForAnalysis(String projectKey) throws Exception {
        String url = sonarHostUrl + "/api/ce/activity?component=" + projectKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");
        
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode tasks = root.path("tasks");
            
            if (tasks.isArray() && tasks.size() > 0) {
                String status = tasks.get(0).path("status").asText();
                if ("SUCCESS".equals(status)) {
                    log.info("Analysis completed for: {}", projectKey);
                    return;
                } else if ("FAILED".equals(status)) {
                    throw new RuntimeException("Analysis failed for: " + projectKey);
                }
            }
            
            log.debug("Waiting for analysis... attempt {}", i + 1);
        }
        
        throw new RuntimeException("Timeout waiting for analysis");
    }
    
    private List<SonarQubeIssue> fetchIssues(String projectKey) throws Exception {
        String url = sonarHostUrl + "/api/issues/search?componentKeys=" + projectKey + 
                     "&types=VULNERABILITY,SECURITY_HOTSPOT&ps=500&resolved=false";
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");
        
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode issues = root.path("issues");
        
        List<SonarQubeIssue> result = new ArrayList<>();
        for (JsonNode issue : issues) {
            SonarQubeIssue sqIssue = new SonarQubeIssue();
            sqIssue.setRuleId(issue.path("rule").asText());
            sqIssue.setType(issue.path("type").asText());
            sqIssue.setSeverity(issue.path("severity").asText());
            sqIssue.setFilePath(issue.path("component").asText());
            sqIssue.setLineNumber(issue.path("line").asInt());
            sqIssue.setMessage(issue.path("message").asText());
            result.add(sqIssue);
        }
        
        log.info("Fetched {} issues for project: {}", result.size(), projectKey);
        return result;
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