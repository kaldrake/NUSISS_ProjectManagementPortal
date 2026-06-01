// src/main/java/com/portal/scan/dto/ScanRequestDTO.java
package com.portal.scan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public class ScanRequestDTO {
    
    @NotNull(message = "Project ID is required")
    private Long projectId;
    
    @NotNull(message = "Repository ID is required")
    private Long repositoryId;
    
    @NotBlank(message = "Repository URL is required")
    private String repositoryUrl;
    
    private String branch;
    
    // Getters and Setters
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}