// src/main/java/com/example/demo/service/LoginService.java
package com.example.demo.service;

import com.example.demo.entity.Login;
import com.example.demo.dto.request.LoginCreateDTO;
import com.example.demo.dto.response.LoginResponseDTO;
import com.example.demo.repository.LoginRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class LoginService {
    
    @Autowired
    private LoginRepository loginRepository;
    
    // Get all logins
    public List<LoginResponseDTO> getAllLogins() {
        return loginRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get logins by owner
    public List<LoginResponseDTO> getLoginsByOwner(Long ownerId) {
        return loginRepository.findByOwnerId(ownerId)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Get single login by ID
    public LoginResponseDTO getLoginById(Long id) {
        Login login = loginRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Login not found with id: " + id));
        return convertToDTO(login);
    }
    
    // Create new login
    public LoginResponseDTO createLogin(LoginCreateDTO createDTO, Long ownerId) {
        // Check if login with same name exists for this owner
        if (loginRepository.existsByNameAndOwnerId(createDTO.getName(), ownerId)) {
            throw new RuntimeException("Login with name '" + createDTO.getName() + "' already exists");
        }
        
        // Create entity from DTO
        Login login = new Login(
            createDTO.getName(),
            createDTO.getDescription(),
            ownerId
        );
        
        // Set repository info if provided
        if (createDTO.getRepositoryUrl() != null) {
            login.setRepositoryUrl(createDTO.getRepositoryUrl());
        }
        if (createDTO.getRepositoryName() != null) {
            login.setRepositoryName(createDTO.getRepositoryName());
        }
        
        // Save to database
        Login savedLogin = loginRepository.save(login);
        
        return convertToDTO(savedLogin);
    }
    
    // Update login
    public LoginResponseDTO updateLogin(Long id, LoginCreateDTO updateDTO, Long ownerId) {
        Login login = loginRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Login not found with id: " + id));
        
        // Verify ownership
        if (!login.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to update this login");
        }
        
        // Update fields
        if (updateDTO.getName() != null) {
            login.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            login.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getRepositoryUrl() != null) {
            login.setRepositoryUrl(updateDTO.getRepositoryUrl());
        }
        if (updateDTO.getRepositoryName() != null) {
            login.setRepositoryName(updateDTO.getRepositoryName());
        }
        login.setUpdatedAt(LocalDateTime.now());
        
        Login updatedLogin = loginRepository.save(login);
        return convertToDTO(updatedLogin);
    }
    
    // Delete login
    public void deleteLogin(Long id, Long ownerId) {
        Login login = loginRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Login not found with id: " + id));
        
        // Verify ownership
        if (!login.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to delete this login");
        }
        
        loginRepository.deleteById(id);
    }
    
    // Convert Entity to DTO
    private LoginResponseDTO convertToDTO(Login login) {
        LoginResponseDTO dto = new LoginResponseDTO(login);
        
        // You can set owner name by calling User Service here
        // For now, just set ownerId
        dto.setOwnerId(login.getOwnerId());
        
        // TODO: Fetch vulnerability counts from Suggestion Service
        // This would be a REST call to suggestion-service
        
        return dto;
    }
}