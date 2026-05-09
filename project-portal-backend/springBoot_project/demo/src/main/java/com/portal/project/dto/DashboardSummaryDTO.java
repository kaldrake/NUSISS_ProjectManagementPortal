// project-service/src/main/java/com/portal/project/dto/DashboardSummaryDTO.java
package com.portal.project.dto;

import java.time.LocalDateTime;

public class DashboardSummaryDTO {
    private Integer totalVulnerabilities;
    private Integer blockerCount;
    private Integer criticalCount;
    private Integer majorCount;
    private Integer minorCount;
    private Integer infoCount;
    private LocalDateTime lastScanAt;
    private String lastScanStatus;
	public Integer getTotalVulnerabilities() {
		return totalVulnerabilities;
	}
	public void setTotalVulnerabilities(Integer totalVulnerabilities) {
		this.totalVulnerabilities = totalVulnerabilities;
	}
	public Integer getBlockerCount() {
		return blockerCount;
	}
	public void setBlockerCount(Integer blockerCount) {
		this.blockerCount = blockerCount;
	}
	public Integer getCriticalCount() {
		return criticalCount;
	}
	public void setCriticalCount(Integer criticalCount) {
		this.criticalCount = criticalCount;
	}
	public Integer getMajorCount() {
		return majorCount;
	}
	public void setMajorCount(Integer majorCount) {
		this.majorCount = majorCount;
	}
	public Integer getMinorCount() {
		return minorCount;
	}
	public void setMinorCount(Integer minorCount) {
		this.minorCount = minorCount;
	}
	public Integer getInfoCount() {
		return infoCount;
	}
	public void setInfoCount(Integer infoCount) {
		this.infoCount = infoCount;
	}
	public LocalDateTime getLastScanAt() {
		return lastScanAt;
	}
	public void setLastScanAt(LocalDateTime lastScanAt) {
		this.lastScanAt = lastScanAt;
	}
	public String getLastScanStatus() {
		return lastScanStatus;
	}
	public void setLastScanStatus(String lastScanStatus) {
		this.lastScanStatus = lastScanStatus;
	}
    
    // Getters and Setters...
    
}