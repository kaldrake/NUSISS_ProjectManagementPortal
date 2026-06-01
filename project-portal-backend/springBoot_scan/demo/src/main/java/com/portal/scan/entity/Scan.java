// scan-service/src/main/java/com/portal/scan/entity/Scan.java
package com.portal.scan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scans")
public class Scan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    
    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;
    
    @Column(name = "repository_url", nullable = false)
    private String repositoryUrl;
    
    @Column(name = "branch")
    private String branch = "main";
    
    @Column(name = "scan_status", length = 20)
    private String scanStatus = "PENDING";
    
    @Column(name = "sonarqube_project_key")
    private String sonarqubeProjectKey;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Vulnerability> vulnerabilities = new ArrayList<>();
    
    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SCANNING = "SCANNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    
    public Scan() {
        this.startedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
    
    public Scan(Long projectId, Long repositoryId, String repositoryUrl, String branch) {
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.repositoryUrl = repositoryUrl;
        this.branch = branch != null ? branch : "main";
        this.scanStatus = STATUS_PENDING;
        this.startedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    
    public String getSonarqubeProjectKey() { return sonarqubeProjectKey; }
    public void setSonarqubeProjectKey(String sonarqubeProjectKey) { this.sonarqubeProjectKey = sonarqubeProjectKey; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public List<Vulnerability> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<Vulnerability> vulnerabilities) { this.vulnerabilities = vulnerabilities; }
}