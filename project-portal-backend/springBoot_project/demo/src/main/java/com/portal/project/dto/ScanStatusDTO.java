// project-service/src/main/java/com/portal/project/dto/ScanStatusDTO.java
package com.portal.project.dto;

import java.time.LocalDateTime;

public class ScanStatusDTO {
    private Long scanId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer totalVulnerabilities;
    private Integer criticalCount;
    
    // Getters and Setters
    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getTotalVulnerabilities() { return totalVulnerabilities; }
    public void setTotalVulnerabilities(Integer totalVulnerabilities) { this.totalVulnerabilities = totalVulnerabilities; }
    
    public Integer getCriticalCount() { return criticalCount; }
    public void setCriticalCount(Integer criticalCount) { this.criticalCount = criticalCount; }
}