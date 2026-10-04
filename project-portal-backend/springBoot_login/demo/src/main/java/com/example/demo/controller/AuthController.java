package com.example.demo.controller;

import com.example.demo.dto.auth.LoginRequestDTO;
import com.example.demo.dto.auth.RegisterRequestDTO;
import com.example.demo.entity.Session;
import com.example.demo.entity.User;
import com.example.demo.repository.SessionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String MESSAGE = "message";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserDetailsService userDetailsService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(MESSAGE, "Invalid username or password"));
        }

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails, user.getId());

        saveSession(user.getId(), token);

        return ResponseEntity.ok(buildAuthResponse(token, user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername()))
            return ResponseEntity.badRequest().body(Map.of(MESSAGE, "Username is already taken"));
        if (userRepository.existsByEmail(request.getEmail()))
            return ResponseEntity.badRequest().body(Map.of(MESSAGE, "Email is already registered"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("DEVELOPER");
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails, user.getId());

        saveSession(user.getId(), token);

        return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(token, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        sessionRepository.deleteByJwtToken(token);
        return ResponseEntity.ok(Map.of(MESSAGE, "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                USERNAME, user.getUsername(),
                EMAIL, user.getEmail(),
                "role", user.getRole()
        ));
    }

    private void saveSession(Long userId, String token) {
        Session session = new Session();
        session.setUserId(userId);
        session.setJwtToken(token);
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getExpirationMs() / 1000));
        sessionRepository.save(session);
    }

    private Map<String, Object> buildAuthResponse(String token, User user) {
        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        USERNAME, user.getUsername(),
                        EMAIL, user.getEmail(),
                        "role", user.getRole()
                )
        );
    }
}
