// project-service/src/main/java/com/portal/project/client/ScanServiceClient.java
package com.portal.project.client;

import com.portal.project.dto.VulnerabilityDTO;
import com.portal.project.dto.DashboardSummaryDTO;
import com.portal.project.dto.ScanHistoryDTO;
import com.portal.project.dto.ScanResponseDTO;
import com.portal.project.dto.ScanStatusDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScanServiceClient {
    
    private static final Logger log = LoggerFactory.getLogger(ScanServiceClient.class);
    
    @Value("${scan.service.url:http://localhost:8083}")
    private String scanServiceUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * Get all vulnerabilities for a project (across all scans)
     * GET /api/scans/projects/{projectId}/vulnerabilities
     */
    public List<VulnerabilityDTO> getVulnerabilitiesByProjectId(Long projectId) {
        String url = scanServiceUrl + "/api/scans/projects/" + projectId + "/vulnerabilities";
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<VulnerabilityDTO[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                VulnerabilityDTO[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Fetched {} vulnerabilities for project {}", response.getBody().length, projectId);
                return Arrays.asList(response.getBody());
            }
            
            return Collections.emptyList();
            
        } catch (RestClientException e) {
            log.error("Failed to fetch vulnerabilities for project {}: {}", projectId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get vulnerabilities for a specific scan
     * GET /api/scans/{scanId}/vulnerabilities
     */
    public List<VulnerabilityDTO> getVulnerabilitiesByScanId(Long scanId) {
        String url = scanServiceUrl + "/api/scans/" + scanId + "/vulnerabilities";
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<VulnerabilityDTO[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                VulnerabilityDTO[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Fetched {} vulnerabilities for scan {}", response.getBody().length, scanId);
                return Arrays.asList(response.getBody());
            }
            
            return Collections.emptyList();
            
        } catch (RestClientException e) {
            log.error("Failed to fetch vulnerabilities for scan {}: {}", scanId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get a single vulnerability by ID with its AI suggestion
     * GET /api/scans/vulnerabilities/{vulnerabilityId}
     */
    public VulnerabilityDTO getVulnerabilityById(Long vulnerabilityId) {
        String url = scanServiceUrl + "/api/scans/vulnerabilities/" + vulnerabilityId;
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<VulnerabilityDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                VulnerabilityDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            
            return null;
            
        } catch (RestClientException e) {
            log.error("Failed to fetch vulnerability {}: {}", vulnerabilityId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get dashboard summary for a project
     * GET /api/scans/dashboard/projects/{projectId}/summary
     */
    public DashboardSummaryDTO getDashboardSummary(Long projectId) {
        String url = scanServiceUrl + "/api/scans/dashboard/projects/" + projectId + "/summary";
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<DashboardSummaryDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                DashboardSummaryDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            
            return null;
            
        } catch (RestClientException e) {
            log.error("Failed to fetch dashboard summary for project {}: {}", projectId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get scan history for a repository
     * GET /api/scans/repositories/{repoId}/history
     */
    public List<ScanHistoryDTO> getScanHistory(Long repositoryId) {
        String url = scanServiceUrl + "/api/scans/repositories/" + repositoryId + "/history";
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<ScanHistoryDTO[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ScanHistoryDTO[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
            
            return Collections.emptyList();
            
        } catch (RestClientException e) {
            log.error("Failed to fetch scan history for repository {}: {}", repositoryId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Trigger a new scan
     * POST /api/scans/analyze
     */
    public ScanResponseDTO triggerScan(Long projectId, Long repositoryId, String repositoryUrl, String branch) {
        String url = scanServiceUrl + "/api/scans/analyze";
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Add auth headers
            String token = getJwtToken();
            if (token != null) {
                headers.setBearerAuth(token);
            }
            
            Long userId = getCurrentUserId();
            if (userId != null) {
                headers.set("X-User-Id", String.valueOf(userId));
            }
            
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("projectId", projectId);
            requestBody.put("repositoryId", repositoryId);
            requestBody.put("repositoryUrl", repositoryUrl);
            requestBody.put("branch", branch != null ? branch : "main");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<ScanResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ScanResponseDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Triggered scan for project {} repository {}", projectId, repositoryId);
                return response.getBody();
            }
            
            return null;
            
        } catch (RestClientException e) {
            log.error("Failed to trigger scan for project {}: {}", projectId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get scan status
     * GET /api/scans/{scanId}/status
     */
    public ScanStatusDTO getScanStatus(Long scanId) {
        String url = scanServiceUrl + "/api/scans/" + scanId + "/status";
        
        try {
            HttpEntity<?> entity = createAuthEntity();
            
            ResponseEntity<ScanStatusDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ScanStatusDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            
            return null;
            
        } catch (RestClientException e) {
            log.error("Failed to fetch scan status for scan {}: {}", scanId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Create authenticated HTTP entity with JWT token and User ID
     */
    private HttpEntity<?> createAuthEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String token = getJwtToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        
        Long userId = getCurrentUserId();
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
        }
        
        return new HttpEntity<>(headers);
    }
    
    /**
     * Extract JWT token from the current incoming request's Authorization header
     */
    private String getJwtToken() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String authHeader = attrs.getRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract JWT token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get current user ID from Spring Security context (set by JwtAuthenticationFilter)
     */
    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                // Principal is the username string set in JwtAuthenticationFilter
                return null; // userId not available without DB lookup; X-User-Id header used instead
            }
        } catch (Exception e) {
            log.warn("Could not get current user ID: {}", e.getMessage());
        }
        return null;
    }
}