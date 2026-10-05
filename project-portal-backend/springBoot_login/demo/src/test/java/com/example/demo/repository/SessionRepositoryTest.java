package com.example.demo.repository;

import com.example.demo.entity.Session;
import com.example.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("SessionRepository Unit Tests")
class SessionRepositoryTest {

    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;

    private User testUser;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser = userRepository.save(testUser);

        testSession = new Session();
        testSession.setUserId(testUser.getId());
        testSession.setJwtToken("test-jwt-token-123");
        testSession.setExpiresAt(LocalDateTime.now().plusHours(24));
        testSession = sessionRepository.save(testSession);
    }

    @Test
    @DisplayName("findByJwtToken - returns session for valid token")
    void findByJwtToken_ValidToken_ReturnsSession() {
        var result = sessionRepository.findByJwtToken("test-jwt-token-123");
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("findByJwtToken - returns empty for non-existent token")
    void findByJwtToken_NonExistentToken_ReturnsEmpty() {
        var result = sessionRepository.findByJwtToken("non-existent-token");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsByJwtTokenAndExpiresAtAfter - returns true for valid session")
    void existsByJwtTokenAndExpiresAtAfter_ValidSession_ReturnsTrue() {
        boolean exists = sessionRepository.existsByJwtTokenAndExpiresAtAfter(
                "test-jwt-token-123", LocalDateTime.now());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByJwtTokenAndExpiresAtAfter - returns false for expired session")
    void existsByJwtTokenAndExpiresAtAfter_ExpiredSession_ReturnsFalse() {
        testSession.setExpiresAt(LocalDateTime.now().minusHours(1));
        sessionRepository.save(testSession);

        boolean exists = sessionRepository.existsByJwtTokenAndExpiresAtAfter(
                "test-jwt-token-123", LocalDateTime.now());
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("deleteByJwtToken - removes session by token")
    void deleteByJwtToken_ValidToken_DeletesSession() {
        assertThat(sessionRepository.count()).isEqualTo(1);

        sessionRepository.deleteByJwtToken("test-jwt-token-123");

        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteByUserId - removes all sessions for user")
    void deleteByUserId_ValidUserId_DeletesAllUserSessions() {
        // Add another session for same user
        Session session2 = new Session();
        session2.setUserId(testUser.getId());
        session2.setJwtToken("another-token-456");
        session2.setExpiresAt(LocalDateTime.now().plusHours(24));
        sessionRepository.save(session2);

        assertThat(sessionRepository.count()).isEqualTo(2);

        sessionRepository.deleteByUserId(testUser.getId());

        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteExpiredSessions - removes only expired sessions")
    void deleteExpiredSessions_RemovesOnlyExpired() {
        // Add expired session
        Session expiredSession = new Session();
        expiredSession.setUserId(testUser.getId());
        expiredSession.setJwtToken("expired-token");
        expiredSession.setExpiresAt(LocalDateTime.now().minusHours(1));
        sessionRepository.save(expiredSession);

        assertThat(sessionRepository.count()).isEqualTo(2);

        sessionRepository.deleteExpiredSessions(LocalDateTime.now());

        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(sessionRepository.findByJwtToken("test-jwt-token-123")).isPresent();
    }
}
