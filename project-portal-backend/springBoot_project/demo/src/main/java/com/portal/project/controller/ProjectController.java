// src/main/java/com/portal/project/controller/ProjectController.java
package com.portal.project.controller;

import com.portal.project.client.ScanServiceClient;
import com.portal.project.dto.GitHubRepositoryDTO;
import com.portal.project.dto.ProjectCreateDTO;
import com.portal.project.dto.ProjectResponseDTO;
import com.portal.project.dto.ProjectUpdateDTO;
import com.portal.project.dto.DashboardSummaryDTO;
import com.portal.project.security.JwtAuthenticationFilter;
import com.portal.project.service.ProjectService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    private ProjectService projectService;

    // =============================================
    // PROJECT CRUD ENDPOINTS
    // =============================================

    /**
     * GET /api/projects - Get the caller's projects (userId comes from the JWT, not a client header)
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getMyProjects(
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        log.info("GET /api/projects - Fetching projects for user: {}", userId);
        List<ProjectResponseDTO> projects = projectService.getProjectsByOwner(userId);
        return ResponseEntity.ok(projects);
    }

    /**
     * GET /api/projects/{id} - Get single project by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectById(
            @PathVariable Long id,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        log.info("GET /api/projects/{} - Fetching project by ID", id);
        ProjectResponseDTO project = projectService.getProjectById(id, userId);
        return ResponseEntity.ok(project);
    }

    /**
     * POST /api/projects - Create new project
     */
    @PostMapping
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody ProjectCreateDTO createDTO,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("POST /api/projects - Creating project '{}' for user: {}", createDTO.getName(), userId);
        ProjectResponseDTO newProject = projectService.createProject(createDTO, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newProject);
    }

    /**
     * PUT /api/projects/{id} - Update an existing project
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateDTO updateDTO,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("PUT /api/projects/{} - Updating project for user: {}", id, userId);
        ProjectResponseDTO updatedProject = projectService.updateProject(id, updateDTO, userId);
        return ResponseEntity.ok(updatedProject);
    }

    /**
     * DELETE /api/projects/{id} - Delete project
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("DELETE /api/projects/{} - Deleting project for user: {}", id, userId);
        projectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    // =============================================
    // REPOSITORY ENDPOINTS
    // =============================================

    /**
     * GET /api/projects/{projectId}/repositories - Get all repositories for a project
     */
    @GetMapping("/{projectId}/repositories")
    public ResponseEntity<List<GitHubRepositoryDTO>> getProjectRepositories(
            @PathVariable Long projectId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        log.info("GET /api/projects/{}/repositories - Fetching repositories", projectId);
        List<GitHubRepositoryDTO> repositories = projectService.getProjectRepositories(projectId, userId);
        return ResponseEntity.ok(repositories);
    }

    /**
     * POST /api/projects/{projectId}/repositories - Add a repository to a project
     */
    @PostMapping("/{projectId}/repositories")
    public ResponseEntity<GitHubRepositoryDTO> addRepository(
            @PathVariable Long projectId,
            @Valid @RequestBody GitHubRepositoryDTO repositoryDTO,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("POST /api/projects/{}/repositories - Adding repository: {}", projectId, repositoryDTO.getRepoFullName());
        GitHubRepositoryDTO newRepository = projectService.addRepository(projectId, repositoryDTO, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newRepository);
    }

    /**
     * DELETE /api/projects/{projectId}/repositories/{repositoryId} - Remove a repository from a project
     */
    @DeleteMapping("/{projectId}/repositories/{repositoryId}")
    public ResponseEntity<Void> removeRepository(
            @PathVariable Long projectId,
            @PathVariable Long repositoryId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {

        log.info("DELETE /api/projects/{}/repositories/{} - Removing repository", projectId, repositoryId);
        projectService.removeRepository(repositoryId, projectId, userId);
        return ResponseEntity.noContent().build();
    }

    // =============================================
    // SCAN TRIGGER ENDPOINT
    // =============================================

    /**
     * POST /api/projects/{projectId}/repositories/{repositoryId}/scan - Trigger a scan for a specific repository
     */
    @PostMapping("/{projectId}/repositories/{repositoryId}/scan")
    public ResponseEntity<Void> triggerScan(
            @PathVariable Long projectId,
            @PathVariable Long repositoryId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestHeader("Authorization") String authorization) {

        log.info("POST /api/projects/{}/repositories/{}/scan - Triggering scan", projectId, repositoryId);
        // Check ownership synchronously so a non-owner gets 403; the scan itself runs @Async
        projectService.assertProjectOwner(projectId, userId);
        // Forward the caller's JWT explicitly: the async thread has no access to the request
        projectService.triggerScan(projectId, repositoryId, authorization.substring(7));
        return ResponseEntity.accepted().build();
    }

    // =============================================
    // DASHBOARD ENDPOINT
    // =============================================

    /**
     * GET /api/projects/{projectId}/dashboard - Get dashboard summary for a project
     */
    @GetMapping("/{projectId}/dashboard")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary(
            @PathVariable Long projectId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) Long userId) {
        log.info("GET /api/projects/{}/dashboard - Fetching dashboard summary", projectId);
        DashboardSummaryDTO summary = projectService.getProjectDashboardSummary(projectId, userId);
        return ResponseEntity.ok(summary);
    }
}