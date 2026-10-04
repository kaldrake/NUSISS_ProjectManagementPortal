// scan-service/src/main/java/com/portal/scan/entity/ScanSummary.java
package com.portal.scan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "scan_summary")
public class ScanSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false, unique = true)
    private Long scanId;

    @Column(name = "total_vulnerabilities")
    private Integer totalVulnerabilities = 0;

    @Column(name = "blocker_count")
    private Integer blockerCount = 0;

    @Column(name = "critical_count")
    private Integer criticalCount = 0;

    @Column(name = "major_count")
    private Integer majorCount = 0;

    @Column(name = "minor_count")
    private Integer minorCount = 0;

    @Column(name = "info_count")
    private Integer infoCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

    public ScanSummary() {
    }

    public ScanSummary(Long scanId) {
        this.scanId = scanId;
    }

    public Long getId() { return id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Integer getTotalVulnerabilities() { return totalVulnerabilities; }
    public void setTotalVulnerabilities(Integer totalVulnerabilities) { this.totalVulnerabilities = totalVulnerabilities; }

    public Integer getBlockerCount() { return blockerCount; }
    public void setBlockerCount(Integer blockerCount) { this.blockerCount = blockerCount; }

    public Integer getCriticalCount() { return criticalCount; }
    public void setCriticalCount(Integer criticalCount) { this.criticalCount = criticalCount; }

    public Integer getMajorCount() { return majorCount; }
    public void setMajorCount(Integer majorCount) { this.majorCount = majorCount; }

    public Integer getMinorCount() { return minorCount; }
    public void setMinorCount(Integer minorCount) { this.minorCount = minorCount; }

    public Integer getInfoCount() { return infoCount; }
    public void setInfoCount(Integer infoCount) { this.infoCount = infoCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
