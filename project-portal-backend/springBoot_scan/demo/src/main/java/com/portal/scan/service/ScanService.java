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
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScanService {
    
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private static final String SEVERITY_BLOCKER = "BLOCKER";
    private static final String SEVERITY_CRITICAL = "CRITICAL";
    // Small pause between AI calls so a scan with many findings stays under the provider rate limit
    private static final long AI_CALL_PAUSE_MS = 500;
    
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found with id: " + scanId));
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
                    attachAiSuggestion(vuln);
                }
            }

            saveScanSummary(scan.getId(), issues);
            finishScan(scan, Scan.STATUS_COMPLETED, null);
            log.info("Scan completed. Found {} vulnerabilities", issues.size());

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Scan failed: {}", e.getMessage(), e);
            finishScan(scan, Scan.STATUS_FAILED, e.getMessage());
        }
    }

    /**
     * Ask the AI provider for a fix for one finding and store it. A failure here must not fail the scan,
     * so errors are logged and the finding is simply left without a suggestion.
     */
    private void attachAiSuggestion(Vulnerability vuln) {
        try {
            AiSuggestionResult result = aiSuggestionRouter.generateFixSuggestion(vuln);
            log.info("AI provider returned (confidence={}): {}", result.getConfidenceScore(), result.getSuggestionText());
            AiSuggestion aiSuggestion = new AiSuggestion(vuln, result.getSuggestionText(), result.getCodeExample());
            aiSuggestion.setConfidenceScore(result.getConfidenceScore());
            aiSuggestion.setModelUsed(aiSuggestionRouter.getModelUsed(result));
            aiSuggestionRepository.save(aiSuggestion);
            Thread.sleep(AI_CALL_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while generating the AI suggestion");
        } catch (Exception e) {
            log.warn("Failed to get AI suggestion: {}", e.getMessage());
        }
    }

    private void finishScan(Scan scan, String status, String errorMessage) {
        scan.setScanStatus(status);
        if (errorMessage != null) {
            scan.setErrorMessage(errorMessage);
        }
        scan.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        scanRepository.save(scan);
    }

    /**
     * Persist per-severity counts for a finished scan (scan_summary table).
     */
    private void saveScanSummary(Long scanId, List<SonarQubeIssue> issues) {
        ScanSummary summary = scanSummaryRepository.findByScanId(scanId).orElse(new ScanSummary(scanId));
        summary.setTotalVulnerabilities(issues.size());
        summary.setBlockerCount((int) issues.stream().filter(i -> SEVERITY_BLOCKER.equals(i.getSeverity())).count());
        summary.setCriticalCount((int) issues.stream().filter(i -> SEVERITY_CRITICAL.equals(i.getSeverity())).count());
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found with id: " + scanId));
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vulnerability not found with id: " + vulnerabilityId));
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
        aiSuggestion.setGeneratedAt(LocalDateTime.now(ZoneId.systemDefault()));
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
        dto.setBlockerCount((int) vulnerabilities.stream().filter(v -> SEVERITY_BLOCKER.equals(v.getSeverity())).count());
        dto.setCriticalCount((int) vulnerabilities.stream().filter(v -> SEVERITY_CRITICAL.equals(v.getSeverity())).count());
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
        boolean result = SEVERITY_BLOCKER.equals(severity) || SEVERITY_CRITICAL.equals(severity);
        log.info("Severity '{}' is critical? {}", severity, result);
        return result;
    }
}