// src/main/java/com/example/demo/service/ProjectService.java
package com.example.demo.service;

import com.example.demo.entity.Project;
import com.example.demo.dto.request.ProjectCreateDTO;
import com.example.demo.dto.response.ProjectResponseDTO;
import com.example.demo.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {
    
    @Autowired
    private ProjectRepository projectRepository;
    
    // Get all projects
    public List<ProjectResponseDTO> getAllProjects() {
        return projectRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get projects by owner
    public List<ProjectResponseDTO> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get single project by ID
    public ProjectResponseDTO getProjectById(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
        return convertToDTO(project);
    }
    
    // Create new project
    public ProjectResponseDTO createProject(ProjectCreateDTO createDTO, String ownerId) {
        // Check if project with same name exists for this owner
        if (projectRepository.existsByNameAndOwnerId(createDTO.getName(), ownerId)) {
            throw new RuntimeException("Project with name '" + createDTO.getName() + "' already exists");
        }
        
        // Create entity from DTO
        Project project = new Project(
            createDTO.getName(),
            createDTO.getDescription(),
            ownerId
        );
        
        // Set repository info if provided
        if (createDTO.getRepositoryUrl() != null) {
            project.setRepositoryUrl(createDTO.getRepositoryUrl());
        }
        if (createDTO.getRepositoryName() != null) {
            project.setRepositoryName(createDTO.getRepositoryName());
        }
        
        // Save to database
        Project savedProject = projectRepository.save(project);
        
        return convertToDTO(savedProject);
    }
    
    // Update project
    public ProjectResponseDTO updateProject(Long id, ProjectCreateDTO updateDTO, Long ownerId) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
        
        // Verify ownership
        if (!project.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to update this project");
        }
        
        // Update fields
        if (updateDTO.getName() != null) {
            project.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            project.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getRepositoryUrl() != null) {
            project.setRepositoryUrl(updateDTO.getRepositoryUrl());
        }
        if (updateDTO.getRepositoryName() != null) {
            project.setRepositoryName(updateDTO.getRepositoryName());
        }
        project.setUpdatedAt(LocalDateTime.now());
        
        Project updatedProject = projectRepository.save(project);
        return convertToDTO(updatedProject);
    }
    
    // Delete project
    public void deleteProject(Long id, Long ownerId) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
        
        // Verify ownership
        if (!project.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to delete this project");
        }
        
        projectRepository.deleteById(id);
    }
    
    // Convert Entity to DTO
    private ProjectResponseDTO convertToDTO(Project project) {
        ProjectResponseDTO dto = new ProjectResponseDTO(project);
        
        // You can set owner name by calling User Service here
        // For now, just set ownerId
        dto.setOwnerId(project.getOwnerId());
        
        // TODO: Fetch vulnerability counts from Suggestion Service
        // This would be a REST call to suggestion-service
        
        return dto;
    }
}