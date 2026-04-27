// src/main/java/com/example/demo/service/SuggestionService.java
package com.example.demo.service;

import com.example.demo.entity.Suggestion;
import com.example.demo.dto.request.SuggestionCreateDTO;
import com.example.demo.dto.response.SuggestionResponseDTO;
import com.example.demo.repository.SuggestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SuggestionService {
    
    @Autowired
    private SuggestionRepository suggestionRepository;
    
    // Get all suggestions
    public List<SuggestionResponseDTO> getAllSuggestions() {
        return suggestionRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get suggestions by owner
    public List<SuggestionResponseDTO> getSuggestionsByOwner(Long ownerId) {
        return suggestionRepository.findByOwnerId(ownerId)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get single suggestion by ID
    public SuggestionResponseDTO getSuggestionById(Long id) {
        Suggestion suggestion = suggestionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Suggestion not found with id: " + id));
        return convertToDTO(suggestion);
    }
    
    // Create new suggestion
    public SuggestionResponseDTO createSuggestion(SuggestionCreateDTO createDTO, Long ownerId) {
        // Check if suggestion with same name exists for this owner
        if (suggestionRepository.existsByNameAndOwnerId(createDTO.getName(), ownerId)) {
            throw new RuntimeException("Suggestion with name '" + createDTO.getName() + "' already exists");
        }
        
        // Create entity from DTO
        Suggestion suggestion = new Suggestion(
            createDTO.getName(),
            createDTO.getDescription(),
            ownerId
        );
        
        // Set repository info if provided
        if (createDTO.getRepositoryUrl() != null) {
            suggestion.setRepositoryUrl(createDTO.getRepositoryUrl());
        }
        if (createDTO.getRepositoryName() != null) {
            suggestion.setRepositoryName(createDTO.getRepositoryName());
        }
        
        // Save to database
        Suggestion savedSuggestion = suggestionRepository.save(suggestion);
        
        return convertToDTO(savedSuggestion);
    }
    
    // Update suggestion
    public SuggestionResponseDTO updateSuggestion(Long id, SuggestionCreateDTO updateDTO, Long ownerId) {
        Suggestion suggestion = suggestionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Suggestion not found with id: " + id));
        
        // Verify ownership
        if (!suggestion.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to update this suggestion");
        }
        
        // Update fields
        if (updateDTO.getName() != null) {
            suggestion.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            suggestion.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getRepositoryUrl() != null) {
            suggestion.setRepositoryUrl(updateDTO.getRepositoryUrl());
        }
        if (updateDTO.getRepositoryName() != null) {
            suggestion.setRepositoryName(updateDTO.getRepositoryName());
        }
        suggestion.setUpdatedAt(LocalDateTime.now());
        
        Suggestion updatedSuggestion = suggestionRepository.save(suggestion);
        return convertToDTO(updatedSuggestion);
    }
    
    // Delete suggestion
    public void deleteSuggestion(Long id, Long ownerId) {
        Suggestion suggestion = suggestionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Suggestion not found with id: " + id));
        
        // Verify ownership
        if (!suggestion.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to delete this suggestion");
        }
        
        suggestionRepository.deleteById(id);
    }
    
    // Convert Entity to DTO
    private SuggestionResponseDTO convertToDTO(Suggestion suggestion) {
        SuggestionResponseDTO dto = new SuggestionResponseDTO(suggestion);
        
        // You can set owner name by calling User Service here
        // For now, just set ownerId
        dto.setOwnerId(suggestion.getOwnerId());
        
        // TODO: Fetch vulnerability counts from Suggestion Service
        // This would be a REST call to suggestion-service
        
        return dto;
    }
}