// scan-service/src/main/java/com/portal/scan/service/ScanService.java
package com.portal.scan.service;

import com.portal.scan.dto.*;
import com.portal.scan.entity.*;
import com.portal.scan.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScanService {
    
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    
    @Autowired
    private ScanRepository scanRepository;
    
    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;
    
    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;
    
    @Autowired
    private ScanSummaryRepository scanSummaryRepository;

    @Autowired
    private SonarQubeScannerService sonarQubeScanner;
    
    @Autowired
    private AiSuggestionRouter aiSuggestionRouter;
    
    /**
     * Create the scan record synchronously (status PENDING, owned by the caller) so its id can be
     * returned to the client immediately. The analysis itself runs later in runScan().
     */
    @Transactional
    public Scan createScan(ScanRequestDTO request, Long ownerId) {
        Scan scan = new Scan(
            request.getProjectId(),
            request.getRepositoryId(),
            request.getRepositoryUrl(),
            request.getBranch()
        );
        scan.setOwnerId(ownerId);
        return scanRepository.save(scan);
    }

    @Async
    @Transactional
    public void runScan(Long scanId, ScanRequestDTO request) {
        log.info("Starting scan {} for project {} repository {}", scanId, request.getProjectId(), request.getRepositoryId());

        Scan scan = scanRepository.findById(scanId)
            .orElseThrow(() -> new RuntimeException("Scan not found with id: " + scanId));
        scan.setScanStatus(Scan.STATUS_SCANNING);
        scan = scanRepository.save(scan);

        try {
            String projectKey = "project_" + request.getProjectId() + "_scan_" + scan.getId();
            scan.setSonarqubeProjectKey(projectKey);
            scanRepository.save(scan);
            
            List<SonarQubeIssue> issues = sonarQubeScanner.scanRepository(
                request.getRepositoryUrl(),
                request.getBranch(),
                projectKey
            );
            
            for (SonarQubeIssue issue : issues) {
            	log.info("=== Processing issue ===");
                log.info("Issue severity: '{}'", issue.getSeverity());
                log.info("Is critical? {}", isCriticalSeverity(issue.getSeverity()));
                
                Vulnerability vuln = new Vulnerability(
                    scan,
                    issue.getRuleId(),
                    issue.getType(),
                    issue.getSeverity(),
                    issue.getFilePath(),
                    issue.getLineNumber(),
                    issue.getMessage()
                );
                vulnerabilityRepository.save(vuln);
                
                if (isCriticalSeverity(issue.getSeverity())) {
                    try {
                    	 log.info(">>> CALLING AI SUGGESTION ROUTER NOW <<<");
                        AiSuggestionResult result = aiSuggestionRouter.generateFixSuggestion(vuln);
                        log.info("AI provider returned (confidence={}): {}", result.getConfidenceScore(), result.getSuggestionText());
                        AiSuggestion aiSuggestion = new AiSuggestion(vuln, result.getSuggestionText(), result.getCodeExample());
                        aiSuggestion.setConfidenceScore(result.getConfidenceScore());
                        aiSuggestion.setModelUsed(aiSuggestionRouter.getModelUsed(result));
                        aiSuggestionRepository.save(aiSuggestion);
                        Thread.sleep(500);
                    } catch (Exception e) {
                        log.warn("Failed to get AI suggestion: {}", e.getMessage());
                    }
                }
            }
            
            saveScanSummary(scan.getId(), issues);

            scan.setScanStatus(Scan.STATUS_COMPLETED);
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);

            log.info("Scan completed. Found {} vulnerabilities", issues.size());
            
            ScanResponseDTO response = new ScanResponseDTO();
            response.setScanId(scan.getId());
            response.setProjectId(scan.getProjectId());
            response.setRepositoryId(scan.getRepositoryId());
            response.setStatus("COMPLETED");
            response.setMessage("Scan completed successfully");
            
//            return response;
            
        } catch (Exception e) {
            log.error("Scan failed: {}", e.getMessage(), e);
            scan.setScanStatus(Scan.STATUS_FAILED);
            scan.setErrorMessage(e.getMessage());
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);
            
            ScanResponseDTO response = new ScanResponseDTO();
            response.setScanId(scan.getId());
            response.setProjectId(scan.getProjectId());
            response.setRepositoryId(scan.getRepositoryId());
            response.setStatus("FAILED");
            response.setMessage(e.getMessage());
            
//            return response;
        }
    }
    
    /**
     * Persist per-severity counts for a finished scan (scan_summary table).
     */
    private void saveScanSummary(Long scanId, List<SonarQubeIssue> issues) {
        ScanSummary summary = scanSummaryRepository.findByScanId(scanId).orElse(new ScanSummary(scanId));
        summary.setTotalVulnerabilities(issues.size());
        summary.setBlockerCount((int) issues.stream().filter(i -> "BLOCKER".equals(i.getSeverity())).count());
        summary.setCriticalCount((int) issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count());
        summary.setMajorCount((int) issues.stream().filter(i -> "MAJOR".equals(i.getSeverity())).count());
        summary.setMinorCount((int) issues.stream().filter(i -> "MINOR".equals(i.getSeverity())).count());
        summary.setInfoCount((int) issues.stream().filter(i -> "INFO".equals(i.getSeverity())).count());
        scanSummaryRepository.save(summary);
    }

    /**
     * Load a scan and verify the caller (userId from the JWT) owns it. 403 otherwise.
     */
    private Scan requireOwnedScan(Long scanId, Long userId) {
        Scan scan = scanRepository.findById(scanId)
            .orElseThrow(() -> new RuntimeException("Scan not found with id: " + scanId));
        assertOwner(scan, userId);
        return scan;
    }

    private void assertOwner(Scan scan, Long userId) {
        if (userId == null || !userId.equals(scan.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have access to this scan");
        }
    }

    public ScanStatusDTO getScanStatus(Long scanId, Long userId) {
        Scan scan = requireOwnedScan(scanId, userId);

        ScanStatusDTO dto = new ScanStatusDTO();
        dto.setScanId(scan.getId());
        dto.setStatus(scan.getScanStatus());
        dto.setStartedAt(scan.getStartedAt());
        dto.setCompletedAt(scan.getCompletedAt());
        dto.setErrorMessage(scan.getErrorMessage());
        
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByScanId(scanId);
        dto.setTotalVulnerabilities(vulnerabilities.size());
        
        long criticalCount = vulnerabilities.stream()
            .filter(v -> isCriticalSeverity(v.getSeverity()))
            .count();
        dto.setCriticalCount((int) criticalCount);
        
        return dto;
    }
    
    public List<ScanHistoryDTO> getScanHistory(Long repositoryId, Long userId) {
        List<Scan> scans = scanRepository.findByRepositoryIdAndOwnerIdOrderByStartedAtDesc(repositoryId, userId);
        
        return scans.stream().map(scan -> {
            ScanHistoryDTO dto = new ScanHistoryDTO();
            dto.setId(scan.getId());
            dto.setStatus(scan.getScanStatus());
            dto.setStartedAt(scan.getStartedAt());
            dto.setCompletedAt(scan.getCompletedAt());
            
            // Prefer the stored summary; older scans without one fall back to counting rows
            ScanSummary summary = scanSummaryRepository.findByScanId(scan.getId()).orElse(null);
            if (summary != null) {
                dto.setVulnerabilityCount(summary.getTotalVulnerabilities());
                dto.setCriticalCount(summary.getBlockerCount() + summary.getCriticalCount());
            } else {
                List<Vulnerability> vulns = vulnerabilityRepository.findByScanId(scan.getId());
                dto.setVulnerabilityCount(vulns.size());
                dto.setCriticalCount((int) vulns.stream()
                    .filter(v -> isCriticalSeverity(v.getSeverity()))
                    .count());
            }

            return dto;
        }).collect(Collectors.toList());
    }
    
    public List<VulnerabilityDTO> getVulnerabilitiesForProject(Long projectId, Long userId) {
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findLatestByProjectId(projectId, userId);
        return vulnerabilities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<VulnerabilityDTO> getVulnerabilitiesForScan(Long scanId, Long userId) {
        requireOwnedScan(scanId, userId);
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByScanId(scanId);
        return vulnerabilities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * Load a vulnerability and verify the caller owns the scan it belongs to. 403 otherwise.
     */
    private Vulnerability requireOwnedVulnerability(Long vulnerabilityId, Long userId) {
        Vulnerability vuln = vulnerabilityRepository.findById(vulnerabilityId)
            .orElseThrow(() -> new RuntimeException("Vulnerability not found with id: " + vulnerabilityId));
        assertOwner(vuln.getScan(), userId);
        return vuln;
    }

    public VulnerabilityDTO getVulnerabilityById(Long vulnerabilityId, Long userId) {
        return convertToDTO(requireOwnedVulnerability(vulnerabilityId, userId));
    }

    @Transactional
    public void updateVulnerabilityStatus(Long vulnerabilityId, String status, Long userId) {
        Vulnerability vuln = requireOwnedVulnerability(vulnerabilityId, userId);
        vuln.setStatus(status);
        vulnerabilityRepository.save(vuln);
        log.info("Updated vulnerability {} status to {}", vulnerabilityId, status);
    }
    
    @Transactional
    public AiSuggestionDTO regenerateSuggestion(Long vulnerabilityId, Long userId) {
        Vulnerability vuln = requireOwnedVulnerability(vulnerabilityId, userId);

        AiSuggestionResult result = aiSuggestionRouter.generateFixSuggestion(vuln);

        AiSuggestion aiSuggestion = aiSuggestionRepository.findByVulnerabilityId(vulnerabilityId)
            .orElse(new AiSuggestion());

        aiSuggestion.setVulnerability(vuln);
        aiSuggestion.setSuggestionText(result.getSuggestionText());
        aiSuggestion.setCodeExample(result.getCodeExample());
        aiSuggestion.setConfidenceScore(result.getConfidenceScore());
        aiSuggestion.setModelUsed(aiSuggestionRouter.getModelUsed(result));
        aiSuggestion.setGeneratedAt(LocalDateTime.now());
        aiSuggestionRepository.save(aiSuggestion);
        
        AiSuggestionDTO dto = new AiSuggestionDTO();
        dto.setId(aiSuggestion.getId());
        dto.setSuggestionText(aiSuggestion.getSuggestionText());
        dto.setCodeExample(aiSuggestion.getCodeExample());
        dto.setConfidenceScore(aiSuggestion.getConfidenceScore());
        dto.setModelUsed(aiSuggestion.getModelUsed());
        dto.setGeneratedAt(aiSuggestion.getGeneratedAt());
        
        return dto;
    }
    
    public DashboardSummaryDTO getDashboardSummary(Long projectId, Long userId) {
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findLatestByProjectId(projectId, userId);

        DashboardSummaryDTO dto = new DashboardSummaryDTO();
        dto.setTotalVulnerabilities(vulnerabilities.size());
        dto.setBlockerCount((int) vulnerabilities.stream().filter(v -> "BLOCKER".equals(v.getSeverity())).count());
        dto.setCriticalCount((int) vulnerabilities.stream().filter(v -> "CRITICAL".equals(v.getSeverity())).count());
        dto.setMajorCount((int) vulnerabilities.stream().filter(v -> "MAJOR".equals(v.getSeverity())).count());
        dto.setMinorCount((int) vulnerabilities.stream().filter(v -> "MINOR".equals(v.getSeverity())).count());
        dto.setInfoCount((int) vulnerabilities.stream().filter(v -> "INFO".equals(v.getSeverity())).count());
        
        scanRepository.findTopByProjectIdAndOwnerIdOrderByStartedAtDesc(projectId, userId).ifPresent(scan -> {
            dto.setLastScanAt(scan.getStartedAt());
            dto.setLastScanStatus(scan.getScanStatus());
        });
        
        return dto;
    }
    
    private VulnerabilityDTO convertToDTO(Vulnerability vuln) {
        VulnerabilityDTO dto = new VulnerabilityDTO();
        dto.setId(vuln.getId());
        dto.setSonarqubeRuleId(vuln.getSonarqubeRuleId());
        dto.setVulnerabilityType(vuln.getVulnerabilityType());
        dto.setSeverity(vuln.getSeverity());
        dto.setFilePath(vuln.getFilePath());
        dto.setLineNumber(vuln.getLineNumber());
        dto.setMessage(vuln.getMessage());
        dto.setStatus(vuln.getStatus());
        dto.setCreatedAt(vuln.getCreatedAt());
        
        if (vuln.getAiSuggestion() != null) {
            AiSuggestionDTO aiDto = new AiSuggestionDTO();
            aiDto.setId(vuln.getAiSuggestion().getId());
            aiDto.setSuggestionText(vuln.getAiSuggestion().getSuggestionText());
            aiDto.setCodeExample(vuln.getAiSuggestion().getCodeExample());
            aiDto.setConfidenceScore(vuln.getAiSuggestion().getConfidenceScore());
            aiDto.setModelUsed(vuln.getAiSuggestion().getModelUsed());
            aiDto.setGeneratedAt(vuln.getAiSuggestion().getGeneratedAt());
            dto.setAiSuggestion(aiDto);
        }
        
        return dto;
    }
    
    private boolean isCriticalSeverity(String severity) {
        boolean result = "BLOCKER".equals(severity) || "CRITICAL".equals(severity);
        log.info("Severity '{}' is critical? {}", severity, result);
        return result;
    }
}