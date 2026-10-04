// scan-service/src/main/java/com/portal/scan/service/GitHubService.java
package com.portal.scan.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.portal.scan.dto.GitHubRepositoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GitHubService {
    
    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    
    @Value("${github.token:}")
    private String githubToken;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public List<GitHubRepositoryDTO> getUserRepositories(String token) {
        String url = "https://api.github.com/user/repos?per_page=100&sort=updated";
        String authToken = (token != null && !token.isEmpty()) ? token : githubToken;
        
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.setBearerAuth(authToken);
            }
            headers.set("Accept", "application/json");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<GitHubRepo[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                GitHubRepo[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.stream(response.getBody())
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            }
            
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub returned an unexpected response");
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch GitHub repositories: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch GitHub repositories");
        }
    }
    
    public boolean validateRepositoryUrl(String repoUrl, String token) {
        try {
            String apiUrl = repoUrl.replace("github.com", "api.github.com/repos");
            String authToken = (token != null && !token.isEmpty()) ? token : githubToken;
            
            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.setBearerAuth(authToken);
            }
            headers.set("Accept", "application/json");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<GitHubRepo> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                GitHubRepo.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to validate repository URL: {}", e.getMessage());
            return false;
        }
    }
    
    private GitHubRepositoryDTO convertToDTO(GitHubRepo repo) {
        GitHubRepositoryDTO dto = new GitHubRepositoryDTO();
        dto.setId(repo.getId());
        dto.setName(repo.getName());
        dto.setFullName(repo.getFullName());
        dto.setHtmlUrl(repo.getHtmlUrl());
        dto.setCloneUrl(repo.getCloneUrl());
        dto.setDefaultBranch(repo.getDefaultBranch());
        dto.setIsPrivate(repo.isPrivate());
        dto.setDescription(repo.getDescription());
        dto.setLanguage(repo.getLanguage());
        return dto;
    }
    
    /** Subset of GitHub's repository JSON; snake_case names are mapped explicitly. */
    private static class GitHubRepo {
        private Long id;
        private String name;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("html_url")
        private String htmlUrl;
        @JsonProperty("clone_url")
        private String cloneUrl;
        @JsonProperty("default_branch")
        private String defaultBranch;
        @JsonProperty("private")
        private boolean privateRepo;
        private String description;
        private String language;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getHtmlUrl() { return htmlUrl; }
        public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
        public String getCloneUrl() { return cloneUrl; }
        public void setCloneUrl(String cloneUrl) { this.cloneUrl = cloneUrl; }
        public String getDefaultBranch() { return defaultBranch; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public boolean isPrivate() { return privateRepo; }
        public void setPrivate(boolean privateRepo) { this.privateRepo = privateRepo; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}