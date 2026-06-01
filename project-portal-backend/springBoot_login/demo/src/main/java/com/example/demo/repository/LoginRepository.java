// src/main/java/com/example/demo/repository/LoginRepository.java
package com.example.demo.repository;

import com.example.demo.entity.Login;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoginRepository extends JpaRepository<Login, Long> {
    
    // Find projects by owner ID
    List<Login> findByOwnerId(Long ownerId);
    
    // Find projects by name (contains search)
    List<Login> findByNameContainingIgnoreCase(String name);
    
    // Check if project exists for owner
    boolean existsByNameAndOwnerId(String name, Long ownerId);
}