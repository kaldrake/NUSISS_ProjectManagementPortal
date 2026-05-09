// project-service/src/main/java/com/portal/project/dto/AiSuggestionDTO.java
package com.portal.project.dto;

import java.time.LocalDateTime;

public class AiSuggestionDTO {
    
    private Long id;
    private String suggestionText;
    private String codeExample;
    private Double confidenceScore;
    private String modelUsed;
    private LocalDateTime generatedAt;
    
    // Constructors
    public AiSuggestionDTO() {}
    
    public AiSuggestionDTO(Long id, String suggestionText, String codeExample, 
                           Double confidenceScore, String modelUsed, LocalDateTime generatedAt) {
        this.id = id;
        this.suggestionText = suggestionText;
        this.codeExample = codeExample;
        this.confidenceScore = confidenceScore;
        this.modelUsed = modelUsed;
        this.generatedAt = generatedAt;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSuggestionText() {
        return suggestionText;
    }
    
    public void setSuggestionText(String suggestionText) {
        this.suggestionText = suggestionText;
    }
    
    public String getCodeExample() {
        return codeExample;
    }
    
    public void setCodeExample(String codeExample) {
        this.codeExample = codeExample;
    }
    
    public Double getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public String getModelUsed() {
        return modelUsed;
    }
    
    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }
    
    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
    
    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}