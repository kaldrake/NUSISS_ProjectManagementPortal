// src/main/java/com/example/demo/controller/ProjectController.java
package com.example.demo.controller;

import com.example.demo.dto.request.ProjectCreateDTO;
import com.example.demo.dto.response.ProjectResponseDTO;
import com.example.demo.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    
    @Autowired
    private ProjectService projectService;
    
    // GET /api/projects - Get all projects
    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getAllProjects() {
        List<ProjectResponseDTO> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }
    
    // GET /api/projects?ownerId=123 - Get projects by owner
    @GetMapping(params = "ownerId")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByOwner(
            @RequestParam Long ownerId) {
        List<ProjectResponseDTO> projects = projectService.getProjectsByOwner(ownerId);
        return ResponseEntity.ok(projects);
    }
    
    // GET /api/projects/{id} - Get single project
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectById(@PathVariable Long id) {
        ProjectResponseDTO project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }
    
    // POST /api/projects - Create new project
    @PostMapping
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody ProjectCreateDTO createDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        ProjectResponseDTO newProject = projectService.createProject(createDTO, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newProject);
    }
    
    // PUT /api/projects/{id} - Update project
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectCreateDTO updateDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        ProjectResponseDTO updatedProject = projectService.updateProject(id, updateDTO, ownerId);
        return ResponseEntity.ok(updatedProject);
    }
    
    // DELETE /api/projects/{id} - Delete project
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long ownerId) {
        projectService.deleteProject(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}