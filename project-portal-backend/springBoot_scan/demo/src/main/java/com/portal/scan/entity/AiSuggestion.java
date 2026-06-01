// scan-service/src/main/java/com/portal/scan/entity/AiSuggestion.java
package com.portal.scan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_suggestions")
public class AiSuggestion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vulnerability_id", nullable = false, unique = true)
    private Vulnerability vulnerability;
    
    @Column(name = "suggestion_text", columnDefinition = "TEXT", nullable = false)
    private String suggestionText;
    
    @Column(name = "code_example", columnDefinition = "TEXT")
    private String codeExample;
    
    @Column(name = "confidence_score")
    private Double confidenceScore = 0.85;
    
    @Column(name = "model_used")
    private String modelUsed = "deepseek-chat";
    
    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
    
    public AiSuggestion() {
        this.generatedAt = LocalDateTime.now();
    }
    
    public AiSuggestion(Vulnerability vulnerability, String suggestionText, String codeExample) {
        this.vulnerability = vulnerability;
        this.suggestionText = suggestionText;
        this.codeExample = codeExample;
        this.confidenceScore = 0.85;
        this.generatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Vulnerability getVulnerability() { return vulnerability; }
    public void setVulnerability(Vulnerability vulnerability) { this.vulnerability = vulnerability; }
    
    public String getSuggestionText() { return suggestionText; }
    public void setSuggestionText(String suggestionText) { this.suggestionText = suggestionText; }
    
    public String getCodeExample() { return codeExample; }
    public void setCodeExample(String codeExample) { this.codeExample = codeExample; }
    
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}