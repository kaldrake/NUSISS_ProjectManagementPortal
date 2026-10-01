// scan-service/src/main/java/com/portal/scan/repository/ScanSummaryRepository.java
package com.portal.scan.repository;

import com.portal.scan.entity.ScanSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScanSummaryRepository extends JpaRepository<ScanSummary, Long> {

    Optional<ScanSummary> findByScanId(Long scanId);
}
