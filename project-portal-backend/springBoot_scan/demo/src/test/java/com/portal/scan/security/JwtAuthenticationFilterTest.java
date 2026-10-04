package com.portal.scan.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the scan-service JWT filter: authentication is set from a valid token and the
 * caller's userId is exposed as a request attribute (used to scope data to the owner).
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "bXlTZWNyZXRLZXlUaGF0U2hvdWxkQmVDaGFuZ2VkSW5Qcm9kdWN0aW9u";

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String token(String username, Long userId, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_setsAuthenticationAndUserIdAttribute() throws Exception {
        request.addHeader("Authorization", "Bearer " + token("alice", 7L, 60_000));

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE)).isEqualTo(7L);
    }

    @Test
    void validToken_continuesTheFilterChain() throws Exception {
        request.addHeader("Authorization", "Bearer " + token("alice", 7L, 60_000));

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void noAuthorizationHeader_passesThroughUnauthenticated() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonBearerHeader_isIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void garbageToken_isRejectedWithoutError() throws Exception {
        request.addHeader("Authorization", "Bearer not-a-real-token");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void expiredToken_isNotAuthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer " + token("alice", 7L, -60_000));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE)).isNull();
    }
}
