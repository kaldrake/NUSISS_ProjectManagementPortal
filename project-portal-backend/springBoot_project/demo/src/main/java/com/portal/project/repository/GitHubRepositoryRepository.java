// project-service/src/main/java/com/portal/project/repository/GitHubRepositoryRepository.java
package com.portal.project.repository;

import com.portal.project.entity.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, Long> {
    
    /**
     * Find all repositories for a specific project
     */
    List<GitHubRepository> findByProjectId(Long projectId);
    
    /**
     * Find all active repositories for a project
     */
    List<GitHubRepository> findByProjectIdAndIsActiveTrue(Long projectId);
    
    /**
     * Find repository by project ID and repository name
     */
    Optional<GitHubRepository> findByProjectIdAndRepoName(Long projectId, String repoName);
    
    /**
     * Find repository by GitHub repository ID
     */
    Optional<GitHubRepository> findByGithubRepoId(Long githubRepoId);
    
    /**
     * Check if repository exists in a project
     */
    boolean existsByProjectIdAndGithubRepoId(Long projectId, Long githubRepoId);
    
    /**
     * Update last scan timestamp for a repository
     */
    @Modifying
    @Transactional
    @Query("UPDATE GitHubRepository r SET r.lastScanAt = :lastScanAt WHERE r.id = :repositoryId")
    void updateLastScanAt(@Param("repositoryId") Long repositoryId, @Param("lastScanAt") LocalDateTime lastScanAt);
    
    /**
     * Deactivate a repository (soft delete)
     */
    @Modifying
    @Transactional
    @Query("UPDATE GitHubRepository r SET r.isActive = false WHERE r.id = :repositoryId")
    void deactivateRepository(@Param("repositoryId") Long repositoryId);
    
    /**
     * Activate a repository
     */
    @Modifying
    @Transactional
    @Query("UPDATE GitHubRepository r SET r.isActive = true WHERE r.id = :repositoryId")
    void activateRepository(@Param("repositoryId") Long repositoryId);
    
    /**
     * Count repositories by project
     */
    long countByProjectId(Long projectId);
    
    /**
     * Find all repositories that need scanning (never scanned or scanned long ago)
     */
    @Query("SELECT r FROM GitHubRepository r WHERE r.isActive = true AND (r.lastScanAt IS NULL OR r.lastScanAt < :cutoffDate)")
    List<GitHubRepository> findStaleRepositories(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Delete all repositories for a project
     */
    @Modifying
    @Transactional
    void deleteByProjectId(Long projectId);
}