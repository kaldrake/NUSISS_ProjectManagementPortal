// src/main/java/com/example/demo/controller/SuggestionController.java
package com.example.demo.controller;

import com.example.demo.dto.request.SuggestionCreateDTO;
import com.example.demo.dto.response.SuggestionResponseDTO;
import com.example.demo.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {
    
    @Autowired
    private SuggestionService suggestionService;
    
    // GET /api/suggestions - Get all suggestions
    @GetMapping
    public ResponseEntity<List<SuggestionResponseDTO>> getAllSuggestions() {
        List<SuggestionResponseDTO> suggestions = suggestionService.getAllSuggestions();
        return ResponseEntity.ok(suggestions);
    }
    
    // GET /api/suggestions?ownerId=123 - Get suggestions by owner
    @GetMapping(params = "ownerId")
    public ResponseEntity<List<SuggestionResponseDTO>> getSuggestionsByOwner(
            @RequestParam Long ownerId) {
        List<SuggestionResponseDTO> suggestions = suggestionService.getSuggestionsByOwner(ownerId);
        return ResponseEntity.ok(suggestions);
    }
    
    // GET /api/suggestions/{id} - Get single suggestion
    @GetMapping("/{id}")
    public ResponseEntity<SuggestionResponseDTO> getSuggestionById(@PathVariable Long id) {
        SuggestionResponseDTO suggestion = suggestionService.getSuggestionById(id);
        return ResponseEntity.ok(suggestion);
    }
    
    // POST /api/suggestions - Create new suggestion
    @PostMapping
    public ResponseEntity<SuggestionResponseDTO> createSuggestion(
            @Valid @RequestBody SuggestionCreateDTO createDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        SuggestionResponseDTO newSuggestion = suggestionService.createSuggestion(createDTO, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newSuggestion);
    }
    
    // PUT /api/suggestions/{id} - Update suggestion
    @PutMapping("/{id}")
    public ResponseEntity<SuggestionResponseDTO> updateSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody SuggestionCreateDTO updateDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        SuggestionResponseDTO updatedSuggestion = suggestionService.updateSuggestion(id, updateDTO, ownerId);
        return ResponseEntity.ok(updatedSuggestion);
    }
    
    // DELETE /api/suggestions/{id} - Delete suggestion
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSuggestion(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long ownerId) {
        suggestionService.deleteSuggestion(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}