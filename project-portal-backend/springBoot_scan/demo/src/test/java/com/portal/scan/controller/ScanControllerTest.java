package com.portal.scan.controller;

import com.portal.scan.dto.AiSuggestionDTO;
import com.portal.scan.dto.DashboardSummaryDTO;
import com.portal.scan.dto.GitHubRepositoryDTO;
import com.portal.scan.dto.ScanHistoryDTO;
import com.portal.scan.dto.ScanRequestDTO;
import com.portal.scan.dto.ScanStatusDTO;
import com.portal.scan.dto.VulnerabilityDTO;
import com.portal.scan.entity.Scan;
import com.portal.scan.service.GitHubService;
import com.portal.scan.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for ScanController (standalone MockMvc, services mocked). The userId request
 * attribute stands in for what the JWT filter sets in production.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanControllerTest {

    private static final String USER_ATTR = "userId";
    private static final Long USER = 1L;

    @Mock
    private ScanService scanService;

    @Mock
    private GitHubService gitHubService;

    @InjectMocks
    private ScanController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static final String ANALYZE_BODY =
            "{\"projectId\":10,\"repositoryId\":100,\"repositoryUrl\":\"https://github.com/acme/app.git\",\"branch\":\"main\"}";

    // ---------------------------------------------------------------- analyze

    @Test
    void analyze_returns202WithScanId_andStartsBackgroundRun() throws Exception {
        Scan created = new Scan();
        created.setId(55L);
        when(scanService.createScan(any(ScanRequestDTO.class), eq(USER))).thenReturn(created);

        mockMvc.perform(post("/api/scans/analyze").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON).content(ANALYZE_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scanId").value(55))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.projectId").value(10))
                .andExpect(jsonPath("$.repositoryId").value(100));

        verify(scanService).runScan(eq(55L), any(ScanRequestDTO.class));
    }

    @Test
    void analyze_returns400AndCreatesNothing_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/scans/analyze").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":10}"))
                .andExpect(status().isBadRequest());

        verify(scanService, never()).createScan(any(), any());
    }

    @Test
    void analyze_returns400_whenUserIdAttributeMissing() throws Exception {
        mockMvc.perform(post("/api/scans/analyze")
                        .contentType(MediaType.APPLICATION_JSON).content(ANALYZE_BODY))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- reads

    @Test
    void getScanStatus_returnsStatus() throws Exception {
        ScanStatusDTO dto = new ScanStatusDTO();
        dto.setScanId(9L);
        dto.setStatus("COMPLETED");
        when(scanService.getScanStatus(9L, USER)).thenReturn(dto);

        mockMvc.perform(get("/api/scans/9/status").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getScanStatus_returns403_forNonOwner() throws Exception {
        when(scanService.getScanStatus(9L, 2L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no access"));

        mockMvc.perform(get("/api/scans/9/status").requestAttr(USER_ATTR, 2L))
                .andExpect(status().isForbidden());
    }

    @Test
    void getScanVulnerabilities_returnsList() throws Exception {
        VulnerabilityDTO v = new VulnerabilityDTO();
        v.setSeverity("CRITICAL");
        when(scanService.getVulnerabilitiesForScan(9L, USER)).thenReturn(List.of(v));

        mockMvc.perform(get("/api/scans/9/vulnerabilities").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"));
    }

    @Test
    void getScanHistory_returnsList() throws Exception {
        ScanHistoryDTO h = new ScanHistoryDTO();
        h.setId(3L);
        when(scanService.getScanHistory(4L, USER)).thenReturn(List.of(h));

        mockMvc.perform(get("/api/scans/repositories/4/history").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void getProjectVulnerabilities_returnsList() throws Exception {
        VulnerabilityDTO v = new VulnerabilityDTO();
        v.setSeverity("BLOCKER");
        when(scanService.getVulnerabilitiesForProject(10L, USER)).thenReturn(List.of(v));

        mockMvc.perform(get("/api/scans/projects/10/vulnerabilities").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("BLOCKER"));
    }

    @Test
    void getVulnerabilityById_returnsVulnerability() throws Exception {
        VulnerabilityDTO v = new VulnerabilityDTO();
        v.setId(3L);
        when(scanService.getVulnerabilityById(3L, USER)).thenReturn(v);

        mockMvc.perform(get("/api/scans/vulnerabilities/3").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void getDashboardSummary_returnsSummary() throws Exception {
        DashboardSummaryDTO summary = new DashboardSummaryDTO();
        summary.setTotalVulnerabilities(6);
        when(scanService.getDashboardSummary(10L, USER)).thenReturn(summary);

        mockMvc.perform(get("/api/scans/dashboard/projects/10/summary").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVulnerabilities").value(6));
    }

    // ---------------------------------------------------------------- writes

    @Test
    void updateVulnerabilityStatus_passesStatusAndCaller() throws Exception {
        mockMvc.perform(patch("/api/scans/vulnerabilities/3/status").param("status", "RESOLVED")
                        .requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk());

        verify(scanService).updateVulnerabilityStatus(3L, "RESOLVED", USER);
    }

    @Test
    void updateVulnerabilityStatus_returns400_whenStatusParamMissing() throws Exception {
        mockMvc.perform(patch("/api/scans/vulnerabilities/3/status").requestAttr(USER_ATTR, USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void regenerateSuggestion_returnsNewSuggestion() throws Exception {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();
        suggestion.setModelUsed("gemini-test");
        when(scanService.regenerateSuggestion(3L, USER)).thenReturn(suggestion);

        mockMvc.perform(post("/api/scans/vulnerabilities/3/suggestion/regenerate").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelUsed").value("gemini-test"));
    }

    @Test
    void regenerateSuggestion_returns403_forNonOwner() throws Exception {
        when(scanService.regenerateSuggestion(3L, 2L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no access"));

        mockMvc.perform(post("/api/scans/vulnerabilities/3/suggestion/regenerate").requestAttr(USER_ATTR, 2L))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- github endpoints

    @Test
    void getUserRepositories_returnsRepositoriesFromService() throws Exception {
        GitHubRepositoryDTO repo = new GitHubRepositoryDTO();
        repo.setName("app");
        when(gitHubService.getUserRepositories(null)).thenReturn(List.of(repo));

        mockMvc.perform(get("/api/github/repositories").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("app"));
    }

    @Test
    void getUserRepositories_returns502_whenGitHubFails() throws Exception {
        when(gitHubService.getUserRepositories(null))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub down"));

        mockMvc.perform(get("/api/github/repositories").requestAttr(USER_ATTR, USER))
                .andExpect(status().isBadGateway());
    }

    @Test
    void validateRepository_returnsRepoName_whenValid() throws Exception {
        when(gitHubService.validateRepositoryUrl("https://github.com/acme/app", null)).thenReturn(true);

        mockMvc.perform(post("/api/github/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.repoName").value("app"));
    }

    @Test
    void validateRepository_returnsInvalid_whenGitHubRejectsTheUrl() throws Exception {
        when(gitHubService.validateRepositoryUrl("https://github.com/acme/missing", null)).thenReturn(false);

        mockMvc.perform(post("/api/github/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/missing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
