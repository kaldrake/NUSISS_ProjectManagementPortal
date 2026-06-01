// scan-service/src/main/java/com/portal/scan/dto/GitHubRepositoryDTO.java
package com.portal.scan.dto;

public class GitHubRepositoryDTO {
    private Long id;
    private String name;
    private String fullName;
    private String htmlUrl;
    private String cloneUrl;
    private String defaultBranch;
    private Boolean isPrivate;
    private String description;
    private String language;
    private Long ownerId;
    
    // Constructors
    public GitHubRepositoryDTO() {}
    
    public GitHubRepositoryDTO(Long id, String fullName, String htmlUrl, String defaultBranch, Boolean isPrivate) {
        this.id = id;
        this.fullName = fullName;
        this.htmlUrl = htmlUrl;
        this.defaultBranch = defaultBranch;
        this.isPrivate = isPrivate;
    }
    
    // Getters and Setters
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
    
    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
}