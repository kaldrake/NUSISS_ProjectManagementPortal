// project-service/src/main/java/com/portal/project/dto/GitHubRepositoryDTO.java
package com.portal.project.dto;

import java.time.LocalDateTime;

public class GitHubRepositoryDTO {
    
    private Long id;
    private Long projectId;
    private Long githubRepoId;
    private String repoName;
    private String repoFullName;
    private String repoUrl;
    private String cloneUrl;
    private String defaultBranch;
    private Boolean isActive;
    private LocalDateTime lastScanAt;
    private LocalDateTime createdAt;
    private Integer vulnerabilityCount;
    
    // Constructors
    public GitHubRepositoryDTO() {}
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getProjectId() {
        return projectId;
    }
    
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
    
    public Long getGithubRepoId() {
        return githubRepoId;
    }
    
    public void setGithubRepoId(Long githubRepoId) {
        this.githubRepoId = githubRepoId;
    }
    
    public String getRepoName() {
        return repoName;
    }
    
    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
    
    public String getRepoFullName() {
        return repoFullName;
    }
    
    public void setRepoFullName(String repoFullName) {
        this.repoFullName = repoFullName;
    }
    
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public String getCloneUrl() {
        return cloneUrl;
    }
    
    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }
    
    public String getDefaultBranch() {
        return defaultBranch;
    }
    
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getLastScanAt() {
        return lastScanAt;
    }
    
    public void setLastScanAt(LocalDateTime lastScanAt) {
        this.lastScanAt = lastScanAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Integer getVulnerabilityCount() {
        return vulnerabilityCount;
    }
    
    public void setVulnerabilityCount(Integer vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }
}