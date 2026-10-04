package com.example.demo.security;

import com.example.demo.repository.SessionRepository;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests - Login Service with Session Validation")
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private SessionRepository sessionRepository;
    @Mock private UserDetailsService userDetailsService;
    @InjectMocks private JwtAuthenticationFilter filter;

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
    @DisplayName("Request with Bearer token - valid signature AND valid session - sets authentication")
    void filter_ValidTokenAndValidSession_SetsAuthentication() throws Exception {
        String token = buildValidToken("testuser");
        request.addHeader("Authorization", "Bearer " + token);

        // Mock both signature validation AND session validation to pass
        org.springframework.security.core.userdetails.User mockUserDetails =
                new org.springframework.security.core.userdetails.User("testuser", "pass",
                        org.springframework.security.core.authority.AuthorityUtils.NO_AUTHORITIES);

        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(mockUserDetails);
        when(jwtUtil.isTokenValid(token, mockUserDetails)).thenReturn(true);
        when(sessionRepository.existsByJwtTokenAndExpiresAtAfter(eq(token), any(LocalDateTime.class)))
                .thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Request with valid signature but NO session - no authentication set")
    void filter_ValidSignatureButNoSession_NoAuthenticationSet() throws Exception {
        String token = buildValidToken("testuser");
        request.addHeader("Authorization", "Bearer " + token);

        org.springframework.security.core.userdetails.User mockUserDetails =
                new org.springframework.security.core.userdetails.User("testuser", "pass",
                        org.springframework.security.core.authority.AuthorityUtils.NO_AUTHORITIES);

        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(mockUserDetails);
        when(jwtUtil.isTokenValid(token, mockUserDetails)).thenReturn(true);
        when(sessionRepository.existsByJwtTokenAndExpiresAtAfter(eq(token), any(LocalDateTime.class)))
                .thenReturn(false); // Session doesn't exist (logged out)

        filter.doFilterInternal(request, response, filterChain);

        // Without session, authentication should not be set
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request with invalid signature - no authentication set regardless of session")
    void filter_InvalidSignature_NoAuthenticationSet() throws Exception {
        String token = "invalid.token.value";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.extractUsername(token)).thenThrow(new RuntimeException("Invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Filter continues chain regardless of authentication result")
    void filter_AlwaysCallsFilterChain() throws Exception {
        MockFilterChain mockChain = org.mockito.Mockito.mock(MockFilterChain.class);
        filter.doFilterInternal(request, response, mockChain);
        org.mockito.Mockito.verify(mockChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Request with non-Bearer auth header - passes through without authentication")
    void filter_BasicAuthHeader_NoAuthenticationSet() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
