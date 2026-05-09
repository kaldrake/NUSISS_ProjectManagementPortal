// scan-service/src/main/java/com/portal/scan/service/ScanService.java
package com.portal.scan.service;

import com.portal.scan.dto.*;
import com.portal.scan.entity.*;
import com.portal.scan.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private SonarQubeScannerService sonarQubeScanner;
    
    @Autowired
    private DeepSeekService deepSeekService;
    
    @Async
    @Transactional
    public ScanResponseDTO scanRepository(ScanRequestDTO request) {
        log.info("Starting scan for project {} repository {}", request.getProjectId(), request.getRepositoryId());
        
        Scan scan = new Scan(
            request.getProjectId(),
            request.getRepositoryId(),
            request.getRepositoryUrl(),
            request.getBranch()
        );
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
                    try {
                        String suggestionText = deepSeekService.generateFixSuggestion(vuln);
                        AiSuggestion aiSuggestion = new AiSuggestion(vuln, suggestionText, "");
                        aiSuggestionRepository.save(aiSuggestion);
                        Thread.sleep(500);
                    } catch (Exception e) {
                        log.warn("Failed to get AI suggestion: {}", e.getMessage());
                    }
                }
            }
            
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
            
            return response;
            
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
            
            return response;
        }
    }
    
    public ScanStatusDTO getScanStatus(Long scanId) {
        Scan scan = scanRepository.findById(scanId)
            .orElseThrow(() -> new RuntimeException("Scan not found with id: " + scanId));
        
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
    
    public List<ScanHistoryDTO> getScanHistory(Long repositoryId) {
        List<Scan> scans = scanRepository.findByRepositoryIdOrderByStartedAtDesc(repositoryId);
        
        return scans.stream().map(scan -> {
            ScanHistoryDTO dto = new ScanHistoryDTO();
            dto.setId(scan.getId());
            dto.setStatus(scan.getScanStatus());
            dto.setStartedAt(scan.getStartedAt());
            dto.setCompletedAt(scan.getCompletedAt());
            
            List<Vulnerability> vulns = vulnerabilityRepository.findByScanId(scan.getId());
            dto.setVulnerabilityCount(vulns.size());
            
            long criticalCount = vulns.stream()
                .filter(v -> isCriticalSeverity(v.getSeverity()))
                .count();
            dto.setCriticalCount((int) criticalCount);
            
            return dto;
        }).collect(Collectors.toList());
    }
    
    public List<VulnerabilityDTO> getVulnerabilitiesForProject(Long projectId) {
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByScanProjectId(projectId);
        return vulnerabilities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    
    public List<VulnerabilityDTO> getVulnerabilitiesForScan(Long scanId) {
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByScanId(scanId);
        return vulnerabilities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    
    public VulnerabilityDTO getVulnerabilityById(Long vulnerabilityId) {
        Vulnerability vuln = vulnerabilityRepository.findById(vulnerabilityId)
            .orElseThrow(() -> new RuntimeException("Vulnerability not found with id: " + vulnerabilityId));
        return convertToDTO(vuln);
    }
    
    @Transactional
    public void updateVulnerabilityStatus(Long vulnerabilityId, String status) {
        Vulnerability vuln = vulnerabilityRepository.findById(vulnerabilityId)
            .orElseThrow(() -> new RuntimeException("Vulnerability not found with id: " + vulnerabilityId));
        vuln.setStatus(status);
        vulnerabilityRepository.save(vuln);
        log.info("Updated vulnerability {} status to {}", vulnerabilityId, status);
    }
    
    @Transactional
    public AiSuggestionDTO regenerateSuggestion(Long vulnerabilityId) {
        Vulnerability vuln = vulnerabilityRepository.findById(vulnerabilityId)
            .orElseThrow(() -> new RuntimeException("Vulnerability not found with id: " + vulnerabilityId));
        
        String suggestionText = deepSeekService.generateFixSuggestion(vuln);
        
        AiSuggestion aiSuggestion = aiSuggestionRepository.findByVulnerabilityId(vulnerabilityId)
            .orElse(new AiSuggestion());
        
        aiSuggestion.setVulnerability(vuln);
        aiSuggestion.setSuggestionText(suggestionText);
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
    
    public DashboardSummaryDTO getDashboardSummary(Long projectId) {
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByScanProjectId(projectId);
        
        DashboardSummaryDTO dto = new DashboardSummaryDTO();
        dto.setTotalVulnerabilities(vulnerabilities.size());
        dto.setBlockerCount((int) vulnerabilities.stream().filter(v -> "BLOCKER".equals(v.getSeverity())).count());
        dto.setCriticalCount((int) vulnerabilities.stream().filter(v -> "CRITICAL".equals(v.getSeverity())).count());
        dto.setMajorCount((int) vulnerabilities.stream().filter(v -> "MAJOR".equals(v.getSeverity())).count());
        dto.setMinorCount((int) vulnerabilities.stream().filter(v -> "MINOR".equals(v.getSeverity())).count());
        dto.setInfoCount((int) vulnerabilities.stream().filter(v -> "INFO".equals(v.getSeverity())).count());
        
        scanRepository.findTopByRepositoryIdOrderByStartedAtDesc(1L).ifPresent(scan -> {
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
        return "BLOCKER".equals(severity) || "CRITICAL".equals(severity);
    }
}