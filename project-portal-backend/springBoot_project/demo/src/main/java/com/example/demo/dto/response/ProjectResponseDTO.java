// src/main/java/com/example/demo/dto/response/ProjectResponseDTO.java
package com.example.demo.dto.response;

import java.time.LocalDateTime;

import com.example.demo.entity.Project;

public class ProjectResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerName;  // Can be populated from User Service
    private String repositoryUrl;
    private String repositoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int vulnerabilityCount;
    private int criticalCount;
    
    // Default constructor
    public ProjectResponseDTO() {}
    
    // Constructor from entity
    public ProjectResponseDTO(Project project) {
        this.id = project.getId();
        this.name = project.getName();
        this.description = project.getDescription();
        this.ownerId = project.getOwnerId();
        this.repositoryUrl = project.getRepositoryUrl();
        this.repositoryName = project.getRepositoryName();
        this.createdAt = project.getCreatedAt();
        this.updatedAt = project.getUpdatedAt();
        this.vulnerabilityCount = 0;
        this.criticalCount = 0;
    }
    
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
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getRepositoryName() {
        return repositoryName;
    }
    
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
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
    
    public int getVulnerabilityCount() {
        return vulnerabilityCount;
    }
    
    public void setVulnerabilityCount(int vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }
    
    public int getCriticalCount() {
        return criticalCount;
    }
    
    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }
}