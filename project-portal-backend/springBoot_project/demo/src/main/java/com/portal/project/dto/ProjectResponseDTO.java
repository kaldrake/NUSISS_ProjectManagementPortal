// project-service/src/main/java/com/portal/project/dto/ProjectResponseDTO.java
package com.portal.project.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ProjectResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Repository data
    private Integer repositoryCount;
    private List<GitHubRepositoryDTO> repositories;  // ✅ Updated type
    
    // Vulnerability summary
    private Integer vulnerabilityCount;
    private Integer criticalCount;
    private List<VulnerabilityDTO> vulnerabilities;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Integer getRepositoryCount() {
        return repositoryCount;
    }
    
    public void setRepositoryCount(Integer repositoryCount) {
        this.repositoryCount = repositoryCount;
    }
    
    public List<GitHubRepositoryDTO> getRepositories() {
        return repositories;
    }
    
    public void setRepositories(List<GitHubRepositoryDTO> repositories) {
        this.repositories = repositories;
    }
    
    public Integer getVulnerabilityCount() {
        return vulnerabilityCount;
    }
    
    public void setVulnerabilityCount(Integer vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }
    
    public Integer getCriticalCount() {
        return criticalCount;
    }
    
    public void setCriticalCount(Integer criticalCount) {
        this.criticalCount = criticalCount;
    }
    
    public List<VulnerabilityDTO> getVulnerabilities() {
        return vulnerabilities;
    }
    
    public void setVulnerabilities(List<VulnerabilityDTO> vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }
}