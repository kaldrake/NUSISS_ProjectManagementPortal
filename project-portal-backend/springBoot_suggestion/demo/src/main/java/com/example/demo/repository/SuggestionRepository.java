// src/main/java/com/example/demo/repository/SuggestionRepository.java
package com.example.demo.repository;

import com.example.demo.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    
    // Find suggestions by owner ID
    List<Suggestion> findByOwnerId(Long ownerId);
    
    // Find suggestions by name (contains search)
    List<Suggestion> findByNameContainingIgnoreCase(String name);
    
    // Check if suggestion exists for owner
    boolean existsByNameAndOwnerId(String name, Long ownerId);
}