package com.portal.project.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests - Project Service")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String SECRET =
            "bXlTZWNyZXRLZXlUaGF0U2hvdWxkQmVDaGFuZ2VkSW5Qcm9kdWN0aW9u";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String buildValidToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Request with no Authorization header - passes through without authentication")
    void filter_NoAuthHeader_NoAuthenticationSet() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request with non-Bearer Authorization header - passes through without authentication")
    void filter_BasicAuthHeader_NoAuthenticationSet() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request with valid Bearer token - sets authentication in SecurityContext")
    void filter_ValidBearerToken_SetsAuthentication() throws Exception {
        String token = buildValidToken("testuser");
        request.addHeader("Authorization", "Bearer " + token);

        org.mockito.Mockito.when(jwtUtil.isTokenValid(token)).thenReturn(true);
        org.mockito.Mockito.when(jwtUtil.extractUsername(token)).thenReturn("testuser");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("testuser");
    }

    @Test
    @DisplayName("Request with invalid token - no authentication set")
    void filter_InvalidToken_NoAuthenticationSet() throws Exception {
        request.addHeader("Authorization", "Bearer invalid.token.here");

        org.mockito.Mockito.when(jwtUtil.isTokenValid("invalid.token.here")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Filter always calls next filter in chain")
    void filter_AlwaysCallsFilterChain() throws Exception {
        MockFilterChain mockChain = org.mockito.Mockito.mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, mockChain);

        verify(mockChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Request with expired token - no authentication set")
    void filter_ExpiredToken_NoAuthenticationSet() throws Exception {
        String expiredToken = "expired.token.value";
        request.addHeader("Authorization", "Bearer " + expiredToken);

        org.mockito.Mockito.when(jwtUtil.isTokenValid(expiredToken)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
