// project-service/src/main/java/com/portal/project/dto/ProjectUpdateDTO.java
package com.portal.project.dto;

import jakarta.validation.constraints.Size;

public class ProjectUpdateDTO {
    
    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}