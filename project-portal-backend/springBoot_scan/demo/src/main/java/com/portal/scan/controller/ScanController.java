// scan-service/src/main/java/com/portal/scan/controller/ScanController.java
package com.portal.scan.controller;

import com.portal.scan.dto.*;
import com.portal.scan.service.GitHubService;
import com.portal.scan.service.ScanService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ScanController {
    
    private static final Logger log = LoggerFactory.getLogger(ScanController.class);
    
    @Autowired
    private ScanService scanService;
    
    @Autowired
    private GitHubService gitHubService;
    
    // =============================================
    // GITHUB ENDPOINTS
    // =============================================
    
    /**
     * GET /api/github/repositories - Get user's GitHub repositories
     */
    @GetMapping("/github/repositories")
    public ResponseEntity<List<GitHubRepositoryDTO>> getUserRepositories(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        log.info("GET /api/github/repositories - Fetching repos for user: {}", userId);
        
        // Extract GitHub token from Authorization header
        String githubToken = extractGitHubToken(authorization);
        List<GitHubRepositoryDTO> repos = gitHubService.getUserRepositories(githubToken);
        return ResponseEntity.ok(repos);
    }
    
    /**
     * POST /api/github/validate - Validate a GitHub repository URL
     */
    @PostMapping("/github/validate")
    public ResponseEntity<ValidateResponse> validateRepository(
            @RequestBody ValidateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        log.info("POST /api/github/validate - Validating URL: {}", request.getUrl());
        
        String githubToken = extractGitHubToken(authorization);
        boolean isValid = gitHubService.validateRepositoryUrl(request.getUrl(), githubToken);
        
        ValidateResponse response = new ValidateResponse();
        response.setValid(isValid);
        if (isValid) {
            // Extract repo name from URL
            String url = request.getUrl();
            String repoName = url.substring(url.lastIndexOf('/') + 1);
            response.setRepoName(repoName);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // =============================================
    // SCAN ENDPOINTS
    // =============================================
    
    /**
     * POST /api/scans/analyze - Trigger a new scan
     */
    @PostMapping("/scans/analyze")
    public ResponseEntity<ScanResponseDTO> analyzeRepository(@Valid @RequestBody ScanRequestDTO request) {
        scanService.scanRepository(request);
        
        // Create response object
        ScanResponseDTO response = new ScanResponseDTO();
        response.setStatus("ACCEPTED");
        response.setMessage("Scan started successfully");
        response.setProjectId(request.getProjectId());
        response.setRepositoryId(request.getRepositoryId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    /**
     * GET /api/scans/{scanId}/status - Get scan status
     */
    @GetMapping("/scans/{scanId}/status")
    public ResponseEntity<ScanStatusDTO> getScanStatus(@PathVariable Long scanId) {
        ScanStatusDTO status = scanService.getScanStatus(scanId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * GET /api/scans/{scanId}/vulnerabilities - Get vulnerabilities for a scan
     */
    @GetMapping("/scans/{scanId}/vulnerabilities")
    public ResponseEntity<List<VulnerabilityDTO>> getScanVulnerabilities(@PathVariable Long scanId) {
        List<VulnerabilityDTO> vulnerabilities = scanService.getVulnerabilitiesForScan(scanId);
        return ResponseEntity.ok(vulnerabilities);
    }
    
    /**
     * GET /api/scans/repositories/{repoId}/history - Get scan history for a repository
     */
    @GetMapping("/scans/repositories/{repoId}/history")
    public ResponseEntity<List<ScanHistoryDTO>> getScanHistory(@PathVariable Long repoId) {
        List<ScanHistoryDTO> history = scanService.getScanHistory(repoId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * GET /api/scans/projects/{projectId}/vulnerabilities - Get all vulnerabilities for a project
     */
    @GetMapping("/scans/projects/{projectId}/vulnerabilities")
    public ResponseEntity<List<VulnerabilityDTO>> getProjectVulnerabilities(@PathVariable Long projectId) {
        List<VulnerabilityDTO> vulnerabilities = scanService.getVulnerabilitiesForProject(projectId);
        return ResponseEntity.ok(vulnerabilities);
    }
    
    /**
     * GET /api/scans/vulnerabilities/{vulnerabilityId} - Get vulnerability by ID
     */
    @GetMapping("/scans/vulnerabilities/{vulnerabilityId}")
    public ResponseEntity<VulnerabilityDTO> getVulnerabilityById(@PathVariable Long vulnerabilityId) {
        VulnerabilityDTO vulnerability = scanService.getVulnerabilityById(vulnerabilityId);
        return ResponseEntity.ok(vulnerability);
    }
    
    /**
     * PATCH /api/scans/vulnerabilities/{vulnerabilityId}/status - Update vulnerability status
     */
    @PatchMapping("/scans/vulnerabilities/{vulnerabilityId}/status")
    public ResponseEntity<Void> updateVulnerabilityStatus(
            @PathVariable Long vulnerabilityId,
            @RequestParam String status) {
        scanService.updateVulnerabilityStatus(vulnerabilityId, status);
        return ResponseEntity.ok().build();
    }
    
    /**
     * POST /api/scans/vulnerabilities/{vulnerabilityId}/suggestion/regenerate - Regenerate AI suggestion
     */
    @PostMapping("/scans/vulnerabilities/{vulnerabilityId}/suggestion/regenerate")
    public ResponseEntity<AiSuggestionDTO> regenerateSuggestion(@PathVariable Long vulnerabilityId) {
        AiSuggestionDTO suggestion = scanService.regenerateSuggestion(vulnerabilityId);
        return ResponseEntity.ok(suggestion);
    }
    
    /**
     * GET /api/scans/dashboard/projects/{projectId}/summary - Dashboard summary
     */
    @GetMapping("/scans/dashboard/projects/{projectId}/summary")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary(@PathVariable Long projectId) {
        DashboardSummaryDTO summary = scanService.getDashboardSummary(projectId);
        return ResponseEntity.ok(summary);
    }
    
    
    // =============================================
    // HELPER METHODS
    // =============================================
    
    private String extractGitHubToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return "";
    }
    
    // Inner classes for validate endpoint
    static class ValidateRequest {
        private String url;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
    
    static class ValidateResponse {
        private boolean valid;
        private String repoName;
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getRepoName() { return repoName; }
        public void setRepoName(String repoName) { this.repoName = repoName; }
    }
}