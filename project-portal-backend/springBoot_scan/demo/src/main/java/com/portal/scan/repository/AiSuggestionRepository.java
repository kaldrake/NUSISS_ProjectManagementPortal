// scan-service/src/main/java/com/portal/scan/repository/AiSuggestionRepository.java
package com.portal.scan.repository;

import com.portal.scan.entity.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
    
    Optional<AiSuggestion> findByVulnerabilityId(Long vulnerabilityId);
}