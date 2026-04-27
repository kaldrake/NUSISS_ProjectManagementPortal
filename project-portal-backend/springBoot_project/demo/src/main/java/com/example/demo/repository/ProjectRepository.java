// src/main/java/com/example/demo/repository/ProjectRepository.java
package com.example.demo.repository;

import com.example.demo.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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