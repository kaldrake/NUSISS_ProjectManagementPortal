// src/main/java/com/example/demo/repository/ProjectRepository.java
package com.portal.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portal.project.entity.Project;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    // Find projects by owner ID
    List<Project> findByOwnerId(Long ownerId);
    
    // Find projects by name (contains search)
    List<Project> findByNameContainingIgnoreCase(String name);
    
    // Check if project exists for owner
    boolean existsByNameAndOwnerId(String name, Long ownerId);
}