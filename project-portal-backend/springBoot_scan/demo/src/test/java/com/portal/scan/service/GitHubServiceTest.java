package com.portal.scan.service;

import com.portal.scan.dto.GitHubRepositoryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for GitHubService. A MockRestServiceServer replaces the network, so the real
 * RestTemplate (and its JSON mapping) is exercised without calling GitHub.
 */
class GitHubServiceTest {

    private static final String REPOS_URL = "https://api.github.com/user/repos?per_page=100&sort=updated";

    private GitHubService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new GitHubService();
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "githubToken", "");
    }

    // ---------------------------------------------------------------- getUserRepositories

    @Test
    void getUserRepositories_mapsGitHubResponse_andSendsGivenToken() {
        server.expect(requestTo(REPOS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("[{\"id\":1,\"name\":\"app\",\"full_name\":\"acme/app\",\"clone_url\":\"https://github.com/acme/app.git\",\"default_branch\":\"develop\",\"private\":true,\"description\":\"demo\",\"language\":\"Java\"}]",
                        MediaType.APPLICATION_JSON));

        List<GitHubRepositoryDTO> repos = service.getUserRepositories("user-token");

        assertEquals(1, repos.size());
        assertEquals(1L, repos.get(0).getId());
        assertEquals("app", repos.get(0).getName());
        assertEquals("acme/app", repos.get(0).getFullName());
        assertEquals("https://github.com/acme/app.git", repos.get(0).getCloneUrl());
        assertEquals("develop", repos.get(0).getDefaultBranch());
        assertTrue(repos.get(0).getIsPrivate());
        server.verify();
    }

    @Test
    void getUserRepositories_usesConfiguredToken_whenNoneIsGiven() {
        ReflectionTestUtils.setField(service, "githubToken", "server-token");
        server.expect(requestTo(REPOS_URL))
                .andExpect(header("Authorization", "Bearer server-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(service.getUserRepositories(null).isEmpty());
        server.verify();
    }

    @Test
    void getUserRepositories_sendsNoAuthorization_whenNoTokenAtAll() {
        server.expect(requestTo(REPOS_URL))
                .andExpect(request -> assertFalse(request.getHeaders().containsKey("Authorization")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.getUserRepositories("");

        server.verify();
    }

    @Test
    void getUserRepositories_throws502_whenGitHubFails() {
        server.expect(requestTo(REPOS_URL)).andRespond(withServerError());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getUserRepositories("tok"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void getUserRepositories_throws502_whenGitHubRejectsTheToken() {
        server.expect(requestTo(REPOS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getUserRepositories("bad"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void getUserRepositories_throws502_whenResponseHasNoBody() {
        server.expect(requestTo(REPOS_URL)).andRespond(withSuccess());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getUserRepositories("tok"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- validateRepositoryUrl

    @Test
    void validateRepositoryUrl_returnsTrue_forExistingRepository() {
        server.expect(requestTo("https://api.github.com/repos/acme/app"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"app\"}", MediaType.APPLICATION_JSON));

        assertTrue(service.validateRepositoryUrl("https://github.com/acme/app", null));
        server.verify();
    }

    @Test
    void validateRepositoryUrl_returnsFalse_whenRepositoryNotFound() {
        server.expect(requestTo("https://api.github.com/repos/acme/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(service.validateRepositoryUrl("https://github.com/acme/missing", null));
    }

    @Test
    void validateRepositoryUrl_sendsTheGivenToken() {
        server.expect(requestTo("https://api.github.com/repos/acme/app"))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(service.validateRepositoryUrl("https://github.com/acme/app", "tok"));
        server.verify();
    }
}
