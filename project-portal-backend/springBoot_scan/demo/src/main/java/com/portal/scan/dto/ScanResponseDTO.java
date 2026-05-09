// src/main/java/com/portal/scan/dto/ScanResponseDTO.java
package com.portal.scan.dto;

public class ScanResponseDTO {
    private Long scanId;
    private Long projectId;
    private Long repositoryId;
    private String status;
    private String message;
    
    public ScanResponseDTO() {}
    
    public ScanResponseDTO(Long scanId, Long projectId, Long repositoryId, String status, String message) {
        this.scanId = scanId;
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.status = status;
        this.message = message;
    }
    
    // Getters and Setters
    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }
    
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}