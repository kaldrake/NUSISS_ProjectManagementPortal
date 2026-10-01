package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.SessionRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =============================================
    // REGISTER TESTS
    // =============================================

    @Test
    @DisplayName("POST /api/auth/register - valid data returns 201 with token and user")
    void register_ValidData_Returns201WithToken() throws Exception {
        Map<String, String> request = Map.of(
                "username", "newuser",
                "email", "newuser@example.com",
                "password", "password123"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("newuser"))
                .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.user.role").value("DEVELOPER"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("token");
    }

    @Test
    @DisplayName("POST /api/auth/register - saves session to DB")
    void register_SavesSessionToDatabase() throws Exception {
        Map<String, String> request = Map.of(
                "username", "sessionuser",
                "email", "session@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate username returns 400")
    void register_DuplicateUsername_Returns400() throws Exception {
        createTestUser("existinguser", "existing@example.com");

        Map<String, String> request = Map.of(
                "username", "existinguser",
                "email", "different@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate email returns 400")
    void register_DuplicateEmail_Returns400() throws Exception {
        createTestUser("existinguser", "existing@example.com");

        Map<String, String> request = Map.of(
                "username", "newuser",
                "email", "existing@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/register - password shorter than 6 chars returns 400")
    void register_ShortPassword_Returns400() throws Exception {
        Map<String, String> request = Map.of(
                "username", "newuser",
                "email", "newuser@example.com",
                "password", "123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - missing username returns 400")
    void register_MissingUsername_Returns400() throws Exception {
        Map<String, String> request = Map.of(
                "email", "newuser@example.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =============================================
    // LOGIN TESTS
    // =============================================

    @Test
    @DisplayName("POST /api/auth/login - valid credentials returns 200 with token")
    void login_ValidCredentials_Returns200WithToken() throws Exception {
        createTestUser("testuser", "test@example.com");

        Map<String, String> request = Map.of(
                "username", "testuser",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/auth/login - saves session to DB")
    void login_SavesSessionToDatabase() throws Exception {
        createTestUser("testuser", "test@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "testuser", "password", "password123"))))
                .andExpect(status().isOk());

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/auth/login - wrong password returns 401")
    void login_WrongPassword_Returns401() throws Exception {
        createTestUser("testuser", "test@example.com");

        Map<String, String> request = Map.of(
                "username", "testuser",
                "password", "wrongpassword"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - non-existent user returns 401")
    void login_NonExistentUser_Returns401() throws Exception {
        Map<String, String> request = Map.of(
                "username", "ghost",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login - empty body returns 400")
    void login_EmptyBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // =============================================
    // GET /me TESTS
    // =============================================

    @Test
    @DisplayName("GET /api/auth/me - valid token with active session returns user info")
    void getMe_ValidTokenWithActiveSession_ReturnsUserInfo() throws Exception {
        createTestUser("testuser", "test@example.com");

        String token = loginAndGetToken("testuser", "password123");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    @DisplayName("GET /api/auth/me - no token returns 403")
    void getMe_NoToken_Returns403() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isIn(403, 401));
    }

    @Test
    @DisplayName("GET /api/auth/me - invalid token returns 403")
    void getMe_InvalidToken_Returns403() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isIn(403, 401));
    }

    // =============================================
    // LOGOUT TESTS
    // =============================================

    @Test
    @DisplayName("POST /api/auth/logout - valid token returns 200 and deletes session")
    void logout_WithValidToken_Returns200AndDeletesSession() throws Exception {
        createTestUser("testuser", "test@example.com");
        String token = loginAndGetToken("testuser", "password123");

        assertThat(sessionRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/auth/logout - session deleted means token is rejected on next request")
    void logout_TokenRejectedAfterLogout() throws Exception {
        createTestUser("testuser", "test@example.com");
        String token = loginAndGetToken("testuser", "password123");

        // Confirm /me works before logout
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logout — deletes session from DB
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Same token is now rejected because session no longer exists
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isIn(403, 401));
    }

    @Test
    @DisplayName("POST /api/auth/logout - without token returns 403")
    void logout_WithoutToken_Returns403() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isIn(403, 401));
    }

    @Test
    @DisplayName("POST /api/auth/logout - multiple logins each get own session")
    void logout_MultipleLoginsSeparateSessions() throws Exception {
        createTestUser("testuser", "test@example.com");

        String token1 = loginAndGetToken("testuser", "password123");
        String token2 = loginAndGetToken("testuser", "password123");

        assertThat(sessionRepository.count()).isEqualTo(2);

        // Logout only token1
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());

        // token1 rejected, token2 still valid
        assertThat(sessionRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());
    }

    // =============================================
    // FULL FLOW TESTS
    // =============================================

    @Test
    @DisplayName("Full flow - register then login returns consistent user data")
    void fullFlow_RegisterThenLogin_ReturnsConsistentData() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "flowuser",
                                "email", "flow@example.com",
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "flowuser",
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String registerUserId = objectMapper.readTree(
                registerResult.getResponse().getContentAsString()).get("user").get("id").asText();
        String loginUserId = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("user").get("id").asText();

        assertThat(registerUserId).isEqualTo(loginUserId);
        assertThat(sessionRepository.count()).isEqualTo(2); // one for register, one for login
    }

    // =============================================
    // HELPERS
    // =============================================

    private void createTestUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole("DEVELOPER");
        userRepository.save(user);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("token").asText();
    }
}
