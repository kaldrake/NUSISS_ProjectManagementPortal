// scan-service/src/main/java/com/portal/scan/controller/ScanController.java
package com.portal.scan.controller;

import com.portal.scan.dto.*;
import com.portal.scan.entity.Scan;
import com.portal.scan.security.JwtAuthenticationFilter;
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
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("GET /api/github/repositories - Fetching repos for user: {}", userId);

        // The Authorization header carries the portal JWT, not a GitHub token, so the
        // service falls back to the server-side github.token setting.
        List<GitHubRepositoryDTO> repos = gitHubService.getUserRepositories(null);
        return ResponseEntity.ok(repos);
    }
    
    /**
     * POST /api/github/validate - Validate a GitHub repository URL
     */
    @PostMapping("/github/validate")
    public ResponseEntity<ValidateResponse> validateRepository(@RequestBody ValidateRequest request) {

        log.info("POST /api/github/validate - Validating URL: {}", request.getUrl());

        // Authorization header is the portal JWT, so it must not be forwarded to GitHub
        boolean isValid = gitHubService.validateRepositoryUrl(request.getUrl(), null);
        
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
    public ResponseEntity<ScanResponseDTO> analyzeRepository(
            @Valid @RequestBody ScanRequestDTO request,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        // Create the scan record first so its id can be returned, then analyse in the background
        Scan scan = scanService.createScan(request, userId);
        scanService.runScan(scan.getId(), request);

        // Create response object
        ScanResponseDTO response = new ScanResponseDTO();
        response.setScanId(scan.getId());
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
    public ResponseEntity<ScanStatusDTO> getScanStatus(
            @PathVariable Long scanId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        ScanStatusDTO status = scanService.getScanStatus(scanId, userId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * GET /api/scans/{scanId}/vulnerabilities - Get vulnerabilities for a scan
     */
    @GetMapping("/scans/{scanId}/vulnerabilities")
    public ResponseEntity<List<VulnerabilityDTO>> getScanVulnerabilities(
            @PathVariable Long scanId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        List<VulnerabilityDTO> vulnerabilities = scanService.getVulnerabilitiesForScan(scanId, userId);
        return ResponseEntity.ok(vulnerabilities);
    }
    
    /**
     * GET /api/scans/repositories/{repoId}/history - Get scan history for a repository
     */
    @GetMapping("/scans/repositories/{repoId}/history")
    public ResponseEntity<List<ScanHistoryDTO>> getScanHistory(
            @PathVariable Long repoId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        List<ScanHistoryDTO> history = scanService.getScanHistory(repoId, userId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * GET /api/scans/projects/{projectId}/vulnerabilities - Get all vulnerabilities for a project
     */
    @GetMapping("/scans/projects/{projectId}/vulnerabilities")
    public ResponseEntity<List<VulnerabilityDTO>> getProjectVulnerabilities(
            @PathVariable Long projectId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        List<VulnerabilityDTO> vulnerabilities = scanService.getVulnerabilitiesForProject(projectId, userId);
        return ResponseEntity.ok(vulnerabilities);
    }
    
    /**
     * GET /api/scans/vulnerabilities/{vulnerabilityId} - Get vulnerability by ID
     */
    @GetMapping("/scans/vulnerabilities/{vulnerabilityId}")
    public ResponseEntity<VulnerabilityDTO> getVulnerabilityById(
            @PathVariable Long vulnerabilityId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        VulnerabilityDTO vulnerability = scanService.getVulnerabilityById(vulnerabilityId, userId);
        return ResponseEntity.ok(vulnerability);
    }
    
    /**
     * PATCH /api/scans/vulnerabilities/{vulnerabilityId}/status - Update vulnerability status
     */
    @PatchMapping("/scans/vulnerabilities/{vulnerabilityId}/status")
    public ResponseEntity<Void> updateVulnerabilityStatus(
            @PathVariable Long vulnerabilityId,
            @RequestParam String status,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        scanService.updateVulnerabilityStatus(vulnerabilityId, status, userId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * POST /api/scans/vulnerabilities/{vulnerabilityId}/suggestion/regenerate - Regenerate AI suggestion
     */
    @PostMapping("/scans/vulnerabilities/{vulnerabilityId}/suggestion/regenerate")
    public ResponseEntity<AiSuggestionDTO> regenerateSuggestion(
            @PathVariable Long vulnerabilityId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        AiSuggestionDTO suggestion = scanService.regenerateSuggestion(vulnerabilityId, userId);
        return ResponseEntity.ok(suggestion);
    }
    
    /**
     * GET /api/scans/dashboard/projects/{projectId}/summary - Dashboard summary
     */
    @GetMapping("/scans/dashboard/projects/{projectId}/summary")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary(
            @PathVariable Long projectId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        DashboardSummaryDTO summary = scanService.getDashboardSummary(projectId, userId);
        return ResponseEntity.ok(summary);
    }
    
    
    // =============================================
    // HELPER METHODS
    // =============================================
    
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