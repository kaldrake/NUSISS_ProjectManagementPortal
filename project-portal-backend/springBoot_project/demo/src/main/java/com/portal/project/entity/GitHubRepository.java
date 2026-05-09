// project-service/src/main/java/com/portal/project/entity/GitHubRepository.java
package com.portal.project.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
public class GitHubRepository {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    
    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;
    
    @Column(name = "repo_name", nullable = false, length = 100)
    private String repoName;
    
    @Column(name = "repo_full_name", nullable = false, length = 200)
    private String repoFullName;
    
    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;
    
    @Column(name = "clone_url", length = 500)
    private String cloneUrl;
    
    @Column(name = "default_branch", length = 50)
    private String defaultBranch = "main";
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "last_scan_at")
    private LocalDateTime lastScanAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Constructors
    public GitHubRepository() {
        this.createdAt = LocalDateTime.now();
    }
    
    public GitHubRepository(Long projectId, Long githubRepoId, String repoName, 
                            String repoFullName, String repoUrl, String cloneUrl) {
        this.projectId = projectId;
        this.githubRepoId = githubRepoId;
        this.repoName = repoName;
        this.repoFullName = repoFullName;
        this.repoUrl = repoUrl;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = "main";
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }
    
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
}