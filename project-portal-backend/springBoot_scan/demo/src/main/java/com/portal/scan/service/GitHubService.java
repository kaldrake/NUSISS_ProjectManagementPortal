// scan-service/src/main/java/com/portal/scan/service/GitHubService.java
package com.portal.scan.service;

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
    
    private static class GitHubRepo {
        private Long id;
        private String name;
        private String full_name;
        private String html_url;
        private String clone_url;
        private String default_branch;
        private boolean _private;
        private String description;
        private String language;
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFullName() { return full_name; }
        public void setFullName(String full_name) { this.full_name = full_name; }
        public String getHtmlUrl() { return html_url; }
        public void setHtmlUrl(String html_url) { this.html_url = html_url; }
        public String getCloneUrl() { return clone_url; }
        public void setCloneUrl(String clone_url) { this.clone_url = clone_url; }
        public String getDefaultBranch() { return default_branch; }
        public void setDefaultBranch(String default_branch) { this.default_branch = default_branch; }
        public boolean isPrivate() { return _private; }
        public void setPrivate(boolean _private) { this._private = _private; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}