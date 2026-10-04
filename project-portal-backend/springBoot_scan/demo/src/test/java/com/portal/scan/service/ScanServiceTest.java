package com.portal.scan.service;

import com.portal.scan.dto.AiSuggestionDTO;
import com.portal.scan.dto.DashboardSummaryDTO;
import com.portal.scan.dto.ScanHistoryDTO;
import com.portal.scan.dto.ScanStatusDTO;
import com.portal.scan.dto.VulnerabilityDTO;
import com.portal.scan.entity.AiSuggestion;
import com.portal.scan.entity.Scan;
import com.portal.scan.entity.ScanSummary;
import com.portal.scan.entity.Vulnerability;
import com.portal.scan.repository.AiSuggestionRepository;
import com.portal.scan.repository.ScanRepository;
import com.portal.scan.repository.ScanSummaryRepository;
import com.portal.scan.repository.VulnerabilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.portal.scan.dto.ScanRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ScanService's read/query side, status updates and AI suggestion regeneration.
 * The long-running scanRepository(...) workflow needs a real clone + SonarQube and is not covered here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanServiceTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private VulnerabilityRepository vulnerabilityRepository;

    @Mock
    private AiSuggestionRepository aiSuggestionRepository;

    @Mock
    private ScanSummaryRepository scanSummaryRepository;

    @Mock
    private SonarQubeScannerService sonarQubeScanner;

    @Mock
    private AiSuggestionRouter aiSuggestionRouter;

    @InjectMocks
    private ScanService scanService;

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    private Vulnerability vulnerability(Long id, String severity) {
        Vulnerability v = new Vulnerability();
        v.setScan(scan(100L, Scan.STATUS_COMPLETED));
        v.setId(id);
        v.setSeverity(severity);
        v.setMessage("some finding");
        v.setStatus("OPEN");
        return v;
    }

    private Scan scan(Long id, String status) {
        Scan s = new Scan();
        s.setId(id);
        s.setScanStatus(status);
        s.setOwnerId(USER);
        s.setStartedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        return s;
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void getDashboardSummary_countsEachSeverity() {
        when(vulnerabilityRepository.findLatestByProjectId(1L, USER)).thenReturn(List.of(
                vulnerability(1L, "BLOCKER"),
                vulnerability(2L, "CRITICAL"), vulnerability(3L, "CRITICAL"),
                vulnerability(4L, "MAJOR"),
                vulnerability(5L, "MINOR"),
                vulnerability(6L, "INFO")));
        when(scanRepository.findTopByProjectIdAndOwnerIdOrderByStartedAtDesc(1L, USER)).thenReturn(Optional.empty());

        DashboardSummaryDTO dto = scanService.getDashboardSummary(1L, USER);

        assertEquals(6, dto.getTotalVulnerabilities());
        assertEquals(1, dto.getBlockerCount());
        assertEquals(2, dto.getCriticalCount());
        assertEquals(1, dto.getMajorCount());
        assertEquals(1, dto.getMinorCount());
        assertEquals(1, dto.getInfoCount());
    }

    @Test
    void getDashboardSummary_includesLatestScanDetails() {
        Scan latest = scan(5L, Scan.STATUS_COMPLETED);
        when(vulnerabilityRepository.findLatestByProjectId(1L, USER)).thenReturn(List.of());
        when(scanRepository.findTopByProjectIdAndOwnerIdOrderByStartedAtDesc(1L, USER)).thenReturn(Optional.of(latest));

        DashboardSummaryDTO dto = scanService.getDashboardSummary(1L, USER);

        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), dto.getLastScanAt());
        assertEquals(Scan.STATUS_COMPLETED, dto.getLastScanStatus());
    }

    @Test
    void getDashboardSummary_hasNoLastScan_whenProjectNeverScanned() {
        when(vulnerabilityRepository.findLatestByProjectId(1L, USER)).thenReturn(List.of());
        when(scanRepository.findTopByProjectIdAndOwnerIdOrderByStartedAtDesc(1L, USER)).thenReturn(Optional.empty());

        DashboardSummaryDTO dto = scanService.getDashboardSummary(1L, USER);

        assertEquals(0, dto.getTotalVulnerabilities());
        assertNull(dto.getLastScanAt());
        assertNull(dto.getLastScanStatus());
    }

    // ---------------------------------------------------------------- scan status + history

    @Test
    void getScanStatus_reportsTotalsAndCriticalCount() {
        when(scanRepository.findById(9L)).thenReturn(Optional.of(scan(9L, Scan.STATUS_SCANNING)));
        when(vulnerabilityRepository.findByScanId(9L)).thenReturn(List.of(
                vulnerability(1L, "BLOCKER"), vulnerability(2L, "MAJOR"), vulnerability(3L, "CRITICAL")));

        ScanStatusDTO dto = scanService.getScanStatus(9L, USER);

        assertEquals(9L, dto.getScanId());
        assertEquals(Scan.STATUS_SCANNING, dto.getStatus());
        assertEquals(3, dto.getTotalVulnerabilities());
        assertEquals(2, dto.getCriticalCount());
    }

    @Test
    void getScanStatus_throws_whenScanDoesNotExist() {
        when(scanRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> scanService.getScanStatus(9L, USER));
    }

    @Test
    void getScanHistory_usesStoredSummary_whenPresent() {
        ScanSummary summary = new ScanSummary(7L);
        summary.setTotalVulnerabilities(10);
        summary.setBlockerCount(2);
        summary.setCriticalCount(3);
        when(scanRepository.findByRepositoryIdAndOwnerIdOrderByStartedAtDesc(4L, USER))
                .thenReturn(List.of(scan(7L, Scan.STATUS_COMPLETED)));
        when(scanSummaryRepository.findByScanId(7L)).thenReturn(Optional.of(summary));

        List<ScanHistoryDTO> history = scanService.getScanHistory(4L, USER);

        assertEquals(1, history.size());
        assertEquals(10, history.get(0).getVulnerabilityCount());
        assertEquals(5, history.get(0).getCriticalCount());
        verify(vulnerabilityRepository, never()).findByScanId(7L);
    }

    @Test
    void getScanHistory_fallsBackToCountingRows_whenNoSummary() {
        when(scanRepository.findByRepositoryIdAndOwnerIdOrderByStartedAtDesc(4L, USER))
                .thenReturn(List.of(scan(7L, Scan.STATUS_COMPLETED)));
        when(scanSummaryRepository.findByScanId(7L)).thenReturn(Optional.empty());
        when(vulnerabilityRepository.findByScanId(7L)).thenReturn(List.of(
                vulnerability(1L, "CRITICAL"), vulnerability(2L, "MINOR"), vulnerability(3L, "MINOR")));

        List<ScanHistoryDTO> history = scanService.getScanHistory(4L, USER);

        assertEquals(3, history.get(0).getVulnerabilityCount());
        assertEquals(1, history.get(0).getCriticalCount());
    }

    @Test
    void getScanHistory_returnsEmptyList_whenRepositoryHasNoScans() {
        when(scanRepository.findByRepositoryIdAndOwnerIdOrderByStartedAtDesc(4L, USER)).thenReturn(List.of());

        assertEquals(0, scanService.getScanHistory(4L, USER).size());
    }

    // ---------------------------------------------------------------- vulnerabilities

    @Test
    void getVulnerabilitiesForProject_mapsLatestScanFindingsToDtos() {
        when(vulnerabilityRepository.findLatestByProjectId(1L, USER))
                .thenReturn(List.of(vulnerability(1L, "CRITICAL"), vulnerability(2L, "MAJOR")));

        List<VulnerabilityDTO> result = scanService.getVulnerabilitiesForProject(1L, USER);

        assertEquals(2, result.size());
        assertEquals("CRITICAL", result.get(0).getSeverity());
        assertEquals("OPEN", result.get(0).getStatus());
    }

    @Test
    void getVulnerabilityById_includesAiSuggestion_whenPresent() {
        Vulnerability v = vulnerability(1L, "CRITICAL");
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setSuggestionText("use a prepared statement");
        suggestion.setConfidenceScore(0.8);
        suggestion.setModelUsed("gemini-test");
        v.setAiSuggestion(suggestion);
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(v));

        VulnerabilityDTO dto = scanService.getVulnerabilityById(1L, USER);

        assertNotNull(dto.getAiSuggestion());
        assertEquals("use a prepared statement", dto.getAiSuggestion().getSuggestionText());
        assertEquals(0.8, dto.getAiSuggestion().getConfidenceScore());
    }

    @Test
    void getVulnerabilityById_hasNoSuggestion_forLowSeverityFinding() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(vulnerability(1L, "MINOR")));

        assertNull(scanService.getVulnerabilityById(1L, USER).getAiSuggestion());
    }

    @Test
    void getVulnerabilityById_throws_whenMissing() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> scanService.getVulnerabilityById(1L, USER));
    }

    @Test
    void updateVulnerabilityStatus_persistsNewStatus() {
        Vulnerability v = vulnerability(1L, "CRITICAL");
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(v));

        scanService.updateVulnerabilityStatus(1L, "RESOLVED", USER);

        assertEquals("RESOLVED", v.getStatus());
        verify(vulnerabilityRepository).save(v);
    }

    @Test
    void updateVulnerabilityStatus_throwsAndSavesNothing_whenMissing() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> scanService.updateVulnerabilityStatus(1L, "RESOLVED", USER));

        verify(vulnerabilityRepository, never()).save(any(Vulnerability.class));
    }

    // ---------------------------------------------------------------- AI suggestion regeneration

    @Test
    void regenerateSuggestion_createsSuggestionFromRouterResult() {
        Vulnerability v = vulnerability(1L, "CRITICAL");
        AiSuggestionResult result = new AiSuggestionResult("fix it", "int x = 1;", 0.9, false);
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(v));
        when(aiSuggestionRepository.findByVulnerabilityId(1L)).thenReturn(Optional.empty());
        when(aiSuggestionRouter.generateFixSuggestion(v)).thenReturn(result);
        when(aiSuggestionRouter.getModelUsed(result)).thenReturn("gemini-test");

        AiSuggestionDTO dto = scanService.regenerateSuggestion(1L, USER);

        assertEquals("fix it", dto.getSuggestionText());
        assertEquals("int x = 1;", dto.getCodeExample());
        assertEquals(0.9, dto.getConfidenceScore());
        assertEquals("gemini-test", dto.getModelUsed());
        verify(aiSuggestionRepository).save(any(AiSuggestion.class));
    }

    @Test
    void regenerateSuggestion_updatesExistingSuggestionInPlace() {
        Vulnerability v = vulnerability(1L, "CRITICAL");
        AiSuggestion existing = new AiSuggestion();
        existing.setId(42L);
        existing.setSuggestionText("old");
        AiSuggestionResult result = new AiSuggestionResult("new text", "", 0.2, true);
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(v));
        when(aiSuggestionRepository.findByVulnerabilityId(1L)).thenReturn(Optional.of(existing));
        when(aiSuggestionRouter.generateFixSuggestion(v)).thenReturn(result);
        when(aiSuggestionRouter.getModelUsed(result)).thenReturn("fallback-template");

        AiSuggestionDTO dto = scanService.regenerateSuggestion(1L, USER);

        assertEquals(42L, dto.getId());
        assertEquals("new text", existing.getSuggestionText());
        assertEquals("fallback-template", dto.getModelUsed());
    }

    @Test
    void regenerateSuggestion_throwsAndCallsNoAi_whenVulnerabilityMissing() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> scanService.regenerateSuggestion(1L, USER));

        verify(aiSuggestionRouter, never()).generateFixSuggestion(any(Vulnerability.class));
    }

    // ---------------------------------------------------------------- ownership (per-user access)

    @Test
    void getScanStatus_throws403_forNonOwner() {
        when(scanRepository.findById(9L)).thenReturn(Optional.of(scan(9L, Scan.STATUS_COMPLETED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> scanService.getScanStatus(9L, OTHER_USER));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getScanStatus_throws403_whenUserIdIsNull() {
        when(scanRepository.findById(9L)).thenReturn(Optional.of(scan(9L, Scan.STATUS_COMPLETED)));

        assertThrows(ResponseStatusException.class, () -> scanService.getScanStatus(9L, null));
    }

    @Test
    void getScanStatus_throws403_forLegacyScanWithoutOwner() {
        Scan legacy = scan(9L, Scan.STATUS_COMPLETED);
        legacy.setOwnerId(null);
        when(scanRepository.findById(9L)).thenReturn(Optional.of(legacy));

        assertThrows(ResponseStatusException.class, () -> scanService.getScanStatus(9L, USER));
    }

    @Test
    void getVulnerabilitiesForScan_throws403AndReadsNothing_forNonOwner() {
        when(scanRepository.findById(9L)).thenReturn(Optional.of(scan(9L, Scan.STATUS_COMPLETED)));

        assertThrows(ResponseStatusException.class, () -> scanService.getVulnerabilitiesForScan(9L, OTHER_USER));

        verify(vulnerabilityRepository, never()).findByScanId(9L);
    }

    @Test
    void getVulnerabilityById_throws403_forNonOwner() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(vulnerability(1L, "CRITICAL")));

        assertThrows(ResponseStatusException.class, () -> scanService.getVulnerabilityById(1L, OTHER_USER));
    }

    @Test
    void updateVulnerabilityStatus_throws403AndSavesNothing_forNonOwner() {
        Vulnerability v = vulnerability(1L, "CRITICAL");
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(v));

        assertThrows(ResponseStatusException.class,
                () -> scanService.updateVulnerabilityStatus(1L, "RESOLVED", OTHER_USER));

        assertEquals("OPEN", v.getStatus());
        verify(vulnerabilityRepository, never()).save(any(Vulnerability.class));
    }

    @Test
    void regenerateSuggestion_throws403AndCallsNoAi_forNonOwner() {
        when(vulnerabilityRepository.findById(1L)).thenReturn(Optional.of(vulnerability(1L, "CRITICAL")));

        assertThrows(ResponseStatusException.class, () -> scanService.regenerateSuggestion(1L, OTHER_USER));

        verify(aiSuggestionRouter, never()).generateFixSuggestion(any(Vulnerability.class));
    }

    // ---------------------------------------------------------------- scan creation and async run

    private ScanRequestDTO scanRequest() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setProjectId(10L);
        request.setRepositoryId(100L);
        request.setRepositoryUrl("https://github.com/acme/app.git");
        request.setBranch("main");
        return request;
    }

    @Test
    void createScan_savesPendingScanOwnedByCaller() {
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> {
            Scan saved = inv.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        Scan created = scanService.createScan(scanRequest(), USER);

        assertEquals(55L, created.getId());
        assertEquals(USER, created.getOwnerId());
        assertEquals(10L, created.getProjectId());
        assertEquals(Scan.STATUS_PENDING, created.getScanStatus());
    }

    @Test
    void runScan_marksScanCompleted_whenAnalysisSucceeds() throws Exception {
        Scan pending = scan(55L, Scan.STATUS_PENDING);
        when(scanRepository.findById(55L)).thenReturn(Optional.of(pending));
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sonarQubeScanner.scanRepository(any(), any(), any())).thenReturn(List.of());

        scanService.runScan(55L, scanRequest());

        assertEquals(Scan.STATUS_COMPLETED, pending.getScanStatus());
        assertNotNull(pending.getCompletedAt());
        verify(scanSummaryRepository).save(any(ScanSummary.class));
    }

    @Test
    void runScan_marksScanFailedWithMessage_whenAnalysisThrows() throws Exception {
        Scan pending = scan(55L, Scan.STATUS_PENDING);
        when(scanRepository.findById(55L)).thenReturn(Optional.of(pending));
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sonarQubeScanner.scanRepository(any(), any(), any())).thenThrow(new RuntimeException("clone failed"));

        scanService.runScan(55L, scanRequest());

        assertEquals(Scan.STATUS_FAILED, pending.getScanStatus());
        assertEquals("clone failed", pending.getErrorMessage());
    }
}
