package com.portal.project.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil Unit Tests - Project Service")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET =
            "bXlTZWNyZXRLZXlUaGF0U2hvdWxkQmVDaGFuZ2VkSW5Qcm9kdWN0aW9u";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
    }

    private String buildToken(String username, Long userId, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("isTokenValid - valid token returns true")
    void isTokenValid_ValidToken_ReturnsTrue() {
        String token = buildToken("testuser", 1L, 86400000L);
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid - expired token returns false")
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        String token = buildToken("testuser", 1L, -1000L);
        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid - tampered token returns false")
    void isTokenValid_TamperedToken_ReturnsFalse() {
        String token = buildToken("testuser", 1L, 86400000L);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid - completely invalid string returns false")
    void isTokenValid_InvalidString_ReturnsFalse() {
        assertThat(jwtUtil.isTokenValid("not.a.token")).isFalse();
    }

    @Test
    @DisplayName("extractUsername - returns correct username")
    void extractUsername_ReturnsCorrectUsername() {
        String token = buildToken("projectuser", 5L, 86400000L);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("projectuser");
    }

    @Test
    @DisplayName("extractUserId - returns correct userId")
    void extractUserId_ReturnsCorrectUserId() {
        String token = buildToken("projectuser", 99L, 86400000L);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(99L);
    }

    @Test
    @DisplayName("Token signed with different secret is rejected")
    void isTokenValid_DifferentSecret_ReturnsFalse() {
        String differentSecret = "ZGlmZmVyZW50U2VjcmV0S2V5VGhhdElzQWxzb0xvbmdFbm91Z2g=";
        SecretKey differentKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentSecret));
        String token = Jwts.builder()
                .subject("hacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(differentKey)
                .compact();
        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }
}
