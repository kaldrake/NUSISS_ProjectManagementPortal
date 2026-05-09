// project-service/src/main/java/com/portal/project/service/ProjectService.java
package com.portal.project.service;

import com.portal.project.client.ScanServiceClient;
import com.portal.project.dto.*;
import com.portal.project.entity.GitHubRepository;
import com.portal.project.entity.Project;
import com.portal.project.repository.GitHubRepositoryRepository;
import com.portal.project.repository.ProjectRepository;
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
public class ProjectService {
    
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private GitHubRepositoryRepository gitHubRepositoryRepository;
    
    @Autowired
    private ScanServiceClient scanServiceClient;
    
    // =============================================
    // PROJECT CRUD OPERATIONS
    // =============================================
    
    /**
     * Get all projects
     */
    public List<ProjectResponseDTO> getAllProjects() {
        log.info("Fetching all projects");
        
        List<Project> projects = projectRepository.findAll();
        return projects.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get projects by owner ID
     */
    public List<ProjectResponseDTO> getProjectsByOwner(Long ownerId) {
        log.info("Fetching projects for owner: {}", ownerId);
        
        List<Project> projects = projectRepository.findByOwnerId(ownerId);
        return projects.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get project by ID
     */
    public ProjectResponseDTO getProjectById(Long projectId) {
        log.info("Fetching project by ID: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        return convertToDTO(project);
    }
    
    /**
     * Create a new project
     */
    @Transactional
    public ProjectResponseDTO createProject(ProjectCreateDTO createDTO, Long ownerId) {
        log.info("Creating project '{}' for owner: {}", createDTO.getName(), ownerId);
        
        // Check if project with same name exists for this owner
        if (projectRepository.existsByNameAndOwnerId(createDTO.getName(), ownerId)) {
            throw new RuntimeException("Project with name '" + createDTO.getName() + "' already exists");
        }
        
        Project project = new Project();
        project.setName(createDTO.getName());
        project.setDescription(createDTO.getDescription());
        project.setOwnerId(ownerId);
        
        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with ID: {}", savedProject.getId());
        
        return convertToDTO(savedProject);
    }
    
    /**
     * Update an existing project
     */
    @Transactional
    public ProjectResponseDTO updateProject(Long projectId, ProjectUpdateDTO updateDTO, Long ownerId) {
        log.info("Updating project ID: {} for owner: {}", projectId, ownerId);
        
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        // Verify ownership
        if (!project.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to update this project");
        }
        
        if (updateDTO.getName() != null && !updateDTO.getName().isEmpty()) {
            // Check if new name conflicts with existing project
            if (!updateDTO.getName().equals(project.getName()) &&
                projectRepository.existsByNameAndOwnerId(updateDTO.getName(), ownerId)) {
                throw new RuntimeException("Project with name '" + updateDTO.getName() + "' already exists");
            }
            project.setName(updateDTO.getName());
        }
        
        if (updateDTO.getDescription() != null) {
            project.setDescription(updateDTO.getDescription());
        }
        
        project.setUpdatedAt(LocalDateTime.now());
        
        Project updatedProject = projectRepository.save(project);
        log.info("Project updated successfully: {}", updatedProject.getId());
        
        return convertToDTO(updatedProject);
    }
    
    /**
     * Delete a project
     */
    @Transactional
    public void deleteProject(Long projectId, Long ownerId) {
        log.info("Deleting project ID: {} for owner: {}", projectId, ownerId);
        
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        // Verify ownership
        if (!project.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to delete this project");
        }
        
        // Delete associated repositories first (cascade should handle, but explicit for safety)
        gitHubRepositoryRepository.deleteByProjectId(projectId);
        
        // Delete the project
        projectRepository.delete(project);
        log.info("Project deleted successfully: {}", projectId);
    }
    
    // =============================================
    // REPOSITORY OPERATIONS
    // =============================================
    
    /**
     * Get all repositories for a project
     */
    public List<GitHubRepositoryDTO> getProjectRepositories(Long projectId) {
        log.info("Fetching repositories for project ID: {}", projectId);
        
        // Verify project exists
        projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        List<GitHubRepository> repositories = gitHubRepositoryRepository.findByProjectId(projectId);
        return repositories.stream()
            .map(this::convertToRepositoryDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Add a repository to a project
     */
    @Transactional
    public GitHubRepositoryDTO addRepository(Long projectId, GitHubRepositoryDTO repositoryDTO) {
        log.info("Adding repository '{}' to project ID: {}", repositoryDTO.getRepoFullName(), projectId);
        
        // Verify project exists
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        // Check if repository already exists in this project
        if (gitHubRepositoryRepository.existsByProjectIdAndGithubRepoId(projectId, repositoryDTO.getGithubRepoId())) {
            throw new RuntimeException("Repository already exists in this project");
        }
        
        GitHubRepository repository = new GitHubRepository();
        repository.setProjectId(projectId);
        repository.setGithubRepoId(repositoryDTO.getGithubRepoId());
        repository.setRepoName(repositoryDTO.getRepoName());
        repository.setRepoFullName(repositoryDTO.getRepoFullName());
        repository.setRepoUrl(repositoryDTO.getRepoUrl());
        repository.setCloneUrl(repositoryDTO.getCloneUrl());
        
        if (repositoryDTO.getDefaultBranch() != null) {
            repository.setDefaultBranch(repositoryDTO.getDefaultBranch());
        }
        
        GitHubRepository savedRepository = gitHubRepositoryRepository.save(repository);
        log.info("Repository added successfully with ID: {}", savedRepository.getId());
        
        return convertToRepositoryDTO(savedRepository);
    }
    
    /**
     * Remove a repository from a project
     */
    @Transactional
    public void removeRepository(Long repositoryId, Long projectId) {
        log.info("Removing repository ID: {} from project ID: {}", repositoryId, projectId);
        
        GitHubRepository repository = gitHubRepositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new RuntimeException("Repository not found with id: " + repositoryId));
        
        // Verify repository belongs to the project
        if (!repository.getProjectId().equals(projectId)) {
            throw new RuntimeException("Repository does not belong to project: " + projectId);
        }
        
        gitHubRepositoryRepository.delete(repository);
        log.info("Repository removed successfully: {}", repositoryId);
    }
    
    // =============================================
    // SCAN OPERATIONS
    // =============================================
    
    /**
     * Trigger a scan for a repository
     */
    @Async
    public void triggerScan(Long projectId, Long repositoryId) {
        log.info("Triggering scan for project ID: {}, repository ID: {}", projectId, repositoryId);
        
        try {
            // Get repository details
            GitHubRepository repository = gitHubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repositoryId));
            
            // Verify repository belongs to project
            if (!repository.getProjectId().equals(projectId)) {
                throw new RuntimeException("Repository does not belong to project: " + projectId);
            }
            
            // Call Scan Service
            ScanResponseDTO scanResponse = scanServiceClient.triggerScan(
                projectId,
                repositoryId,
                repository.getCloneUrl(),
                repository.getDefaultBranch()
            );
            
            if (scanResponse != null && scanResponse.getScanId() != null) {
                log.info("Scan triggered successfully with ID: {}", scanResponse.getScanId());
                
                // Update last scan timestamp
                gitHubRepositoryRepository.updateLastScanAt(repositoryId, LocalDateTime.now());
            } else {
                log.error("Failed to trigger scan for repository: {}", repositoryId);
            }
            
        } catch (Exception e) {
            log.error("Error triggering scan for repository {}: {}", repositoryId, e.getMessage());
        }
    }
    
    /**
     * Get dashboard summary for a project (calls Scan Service)
     */
    public DashboardSummaryDTO getProjectDashboardSummary(Long projectId) {
        log.info("Fetching dashboard summary for project ID: {}", projectId);
        
        // Verify project exists
        projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        
        // Call Scan Service for dashboard summary
        return scanServiceClient.getDashboardSummary(projectId);
    }
    
    // =============================================
    // CONVERSION METHODS
    // =============================================
    
    /**
     * Convert Project entity to DTO with repositories and vulnerability data
     */
    private ProjectResponseDTO convertToDTO(Project project) {
        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setOwnerId(project.getOwnerId());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        
        // Fetch repositories for this project
        List<GitHubRepository> repositories = gitHubRepositoryRepository.findByProjectId(project.getId());
        dto.setRepositoryCount(repositories.size());
        
        log.info("jason check repo size" + dto.getRepositoryCount());        
        // Convert repositories to DTOs
        List<GitHubRepositoryDTO> repositoryDTOs = repositories.stream()
            .map(this::convertToRepositoryDTO)
            .collect(Collectors.toList());
        dto.setRepositories(repositoryDTOs);
        
        // Get vulnerability data from Scan Service
        try {
            List<VulnerabilityDTO> vulnerabilities = scanServiceClient
                .getVulnerabilitiesByProjectId(project.getId());
            
            if (vulnerabilities != null) {
                dto.setVulnerabilityCount(vulnerabilities.size());
                
                long criticalCount = vulnerabilities.stream()
                    .filter(v -> "CRITICAL".equals(v.getSeverity()) || "BLOCKER".equals(v.getSeverity()))
                    .count();
                dto.setCriticalCount((int) criticalCount);
                
                // Optionally attach full vulnerability list
                 dto.setVulnerabilities(vulnerabilities);
            } else {
                dto.setVulnerabilityCount(0);
                dto.setCriticalCount(0);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch vulnerabilities for project {}: {}", project.getId(), e.getMessage());
            dto.setVulnerabilityCount(0);
            dto.setCriticalCount(0);
        }
        
        return dto;
    }
    
    /**
     * Convert GitHubRepository entity to DTO
     */
    private GitHubRepositoryDTO convertToRepositoryDTO(GitHubRepository repository) {
        GitHubRepositoryDTO dto = new GitHubRepositoryDTO();
        dto.setId(repository.getId());
        dto.setProjectId(repository.getProjectId());
        dto.setGithubRepoId(repository.getGithubRepoId());
        dto.setRepoName(repository.getRepoName());
        dto.setRepoFullName(repository.getRepoFullName());
        dto.setRepoUrl(repository.getRepoUrl());
        dto.setCloneUrl(repository.getCloneUrl());
        dto.setDefaultBranch(repository.getDefaultBranch());
        dto.setIsActive(repository.getIsActive());
        dto.setLastScanAt(repository.getLastScanAt());
        dto.setCreatedAt(repository.getCreatedAt());
        return dto;
    }
}