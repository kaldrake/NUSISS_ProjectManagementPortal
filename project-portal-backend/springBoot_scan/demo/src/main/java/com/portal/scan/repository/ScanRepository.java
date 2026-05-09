// scan-service/src/main/java/com/portal/scan/repository/ScanRepository.java
package com.portal.scan.repository;

import com.portal.scan.entity.Scan;
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
public interface ScanRepository extends JpaRepository<Scan, Long> {
    
    List<Scan> findByProjectId(Long projectId);
    
    List<Scan> findByRepositoryId(Long repositoryId);
    
    List<Scan> findByRepositoryIdOrderByStartedAtDesc(Long repositoryId);
    
    Optional<Scan> findTopByRepositoryIdOrderByStartedAtDesc(Long repositoryId);
    
    List<Scan> findByScanStatus(String scanStatus);
    
    @Query("SELECT s FROM Scan s WHERE s.scanStatus = :status AND s.startedAt < :cutoffDate")
    List<Scan> findStaleScans(@Param("status") String status, @Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Modifying
    @Transactional
    @Query("UPDATE Scan s SET s.scanStatus = :status WHERE s.id = :scanId")
    void updateScanStatus(@Param("scanId") Long scanId, @Param("status") String status);
}