// src/main/java/com/portal/scan/dto/ScanHistoryDTO.java
package com.portal.scan.dto;

import java.time.LocalDateTime;

public class ScanHistoryDTO {
    private Long id;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer vulnerabilityCount;
    private Integer criticalCount;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public Integer getVulnerabilityCount() { return vulnerabilityCount; }
    public void setVulnerabilityCount(Integer vulnerabilityCount) { this.vulnerabilityCount = vulnerabilityCount; }
    
    public Integer getCriticalCount() { return criticalCount; }
    public void setCriticalCount(Integer criticalCount) { this.criticalCount = criticalCount; }
}