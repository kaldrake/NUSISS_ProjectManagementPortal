package com.example.demo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil Unit Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET =
            "bXlTZWNyZXRLZXlUaGF0U2hvdWxkQmVDaGFuZ2VkSW5Qcm9kdWN0aW9u";
    private static final long EXPIRATION = 86400000L;
    private static final long EXPIRED = -1000L;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", EXPIRATION);

        userDetails = new User("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
    }

    @Test
    @DisplayName("generateToken - should return non-null token")
    void generateToken_ShouldReturnNonNullToken() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generateToken - token should contain three JWT parts")
    void generateToken_ShouldHaveThreeParts() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername - should return correct username from token")
    void extractUsername_ShouldReturnCorrectUsername() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractUserId - should return correct userId from token")
    void extractUserId_ShouldReturnCorrectUserId() {
        String token = jwtUtil.generateToken(userDetails, 42L, "DEVELOPER");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("isTokenValid - valid token and matching user returns true")
    void isTokenValid_ValidTokenAndMatchingUser_ReturnsTrue() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        assertThat(jwtUtil.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid - valid token but wrong user returns false")
    void isTokenValid_WrongUser_ReturnsFalse() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        UserDetails otherUser = new User("otheruser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        assertThat(jwtUtil.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid - expired token returns false")
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", EXPIRED);
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        assertThat(jwtUtil.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid - tampered token returns false")
    void isTokenValid_TamperedToken_ReturnsFalse() {
        String token = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered, userDetails)).isFalse();
    }

    @Test
    @DisplayName("generateToken - different users produce different tokens")
    void generateToken_DifferentUsers_ProduceDifferentTokens() {
        UserDetails user2 = new User("anotheruser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String token1 = jwtUtil.generateToken(userDetails, 1L, "DEVELOPER");
        String token2 = jwtUtil.generateToken(user2, 2L, "ADMIN");
        assertThat(token1).isNotEqualTo(token2);
    }
}
