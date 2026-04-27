// src/main/java/com/example/demo/controller/LoginController.java
package com.example.demo.controller;

import com.example.demo.dto.request.LoginCreateDTO;
import com.example.demo.dto.response.LoginResponseDTO;
import com.example.demo.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/login")
public class LoginController {
    
    @Autowired
    private LoginService loginService;
    
    // GET /api/logins - Get all logins
    @GetMapping
    public ResponseEntity<List<LoginResponseDTO>> getAllLogins() {
        List<LoginResponseDTO> logins = loginService.getAllLogins();
        return ResponseEntity.ok(logins);
    }
    
    // GET /api/logins?ownerId=123 - Get logins by owner
    @GetMapping(params = "ownerId")
    public ResponseEntity<List<LoginResponseDTO>> getLoginsByOwner(
            @RequestParam Long ownerId) {
        List<LoginResponseDTO> logins = loginService.getLoginsByOwner(ownerId);
        return ResponseEntity.ok(logins);
    }
    
    // GET /api/logins/{id} - Get single login
    @GetMapping("/{id}")
    public ResponseEntity<LoginResponseDTO> getLoginById(@PathVariable Long id) {
        LoginResponseDTO login = loginService.getLoginById(id);
        return ResponseEntity.ok(login);
    }
    
    // POST /api/logins - Create new login
    @PostMapping
    public ResponseEntity<LoginResponseDTO> createLogin(
            @Valid @RequestBody LoginCreateDTO createDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        LoginResponseDTO newLogin = loginService.createLogin(createDTO, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newLogin);
    }
    
    // PUT /api/logins/{id} - Update login
    @PutMapping("/{id}")
    public ResponseEntity<LoginResponseDTO> updateLogin(
            @PathVariable Long id,
            @Valid @RequestBody LoginCreateDTO updateDTO,
            @RequestHeader("X-User-Id") Long ownerId) {
        LoginResponseDTO updatedLogin = loginService.updateLogin(id, updateDTO, ownerId);
        return ResponseEntity.ok(updatedLogin);
    }
    
    // DELETE /api/logins/{id} - Delete login
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLogin(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long ownerId) {
        loginService.deleteLogin(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}