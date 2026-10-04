package com.portal.project.controller;

import com.portal.project.dto.DashboardSummaryDTO;
import com.portal.project.dto.GitHubRepositoryDTO;
import com.portal.project.dto.ProjectCreateDTO;
import com.portal.project.dto.ProjectResponseDTO;
import com.portal.project.dto.ProjectUpdateDTO;
import com.portal.project.service.ProjectService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for ProjectController (standalone MockMvc, service mocked).
 * The userId request attribute stands in for what the JWT filter sets in production.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectControllerTest {

    private static final String USER_ATTR = "userId";
    private static final Long USER = 1L;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ProjectResponseDTO project(Long id, String name) {
        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setOwnerId(USER);
        return dto;
    }

    // ---------------------------------------------------------------- projects

    @Test
    void getMyProjects_returnsCallersProjects() throws Exception {
        when(projectService.getProjectsByOwner(USER)).thenReturn(List.of(project(10L, "Portal")));

        mockMvc.perform(get("/api/projects").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Portal"));
    }

    @Test
    void getMyProjects_returns400_whenUserIdAttributeMissing() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isBadRequest());
    }

    @Test
    void getProjectById_returnsProject() throws Exception {
        when(projectService.getProjectById(10L, USER)).thenReturn(project(10L, "Portal"));

        mockMvc.perform(get("/api/projects/10").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Portal"));
    }

    @Test
    void getProjectById_returns403_forNonOwner() throws Exception {
        when(projectService.getProjectById(10L, 2L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no access"));

        mockMvc.perform(get("/api/projects/10").requestAttr(USER_ATTR, 2L))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProject_returns201WithBody() throws Exception {
        when(projectService.createProject(any(ProjectCreateDTO.class), eq(USER))).thenReturn(project(11L, "New"));

        mockMvc.perform(post("/api/projects").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void createProject_returns400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/projects").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).createProject(any(ProjectCreateDTO.class), any());
    }

    @Test
    void updateProject_returnsUpdatedProject() throws Exception {
        when(projectService.updateProject(eq(10L), any(ProjectUpdateDTO.class), eq(USER)))
                .thenReturn(project(10L, "Renamed"));

        mockMvc.perform(put("/api/projects/10").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deleteProject_returns204() throws Exception {
        mockMvc.perform(delete("/api/projects/10").requestAttr(USER_ATTR, USER))
                .andExpect(status().isNoContent());

        verify(projectService).deleteProject(10L, USER);
    }

    // ---------------------------------------------------------------- repositories

    @Test
    void getProjectRepositories_returnsList() throws Exception {
        GitHubRepositoryDTO repo = new GitHubRepositoryDTO();
        repo.setId(100L);
        repo.setRepoName("app");
        when(projectService.getProjectRepositories(10L, USER)).thenReturn(List.of(repo));

        mockMvc.perform(get("/api/projects/10/repositories").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoName").value("app"));
    }

    @Test
    void addRepository_returns201() throws Exception {
        GitHubRepositoryDTO saved = new GitHubRepositoryDTO();
        saved.setId(100L);
        when(projectService.addRepository(eq(10L), any(GitHubRepositoryDTO.class), eq(USER))).thenReturn(saved);

        mockMvc.perform(post("/api/projects/10/repositories").requestAttr(USER_ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubRepoId\":555,\"repoName\":\"app\",\"repoFullName\":\"acme/app\","
                                + "\"cloneUrl\":\"https://github.com/acme/app.git\",\"defaultBranch\":\"main\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    void removeRepository_returns204_andPassesIdsInTheRightOrder() throws Exception {
        mockMvc.perform(delete("/api/projects/10/repositories/100").requestAttr(USER_ATTR, USER))
                .andExpect(status().isNoContent());

        verify(projectService).removeRepository(100L, 10L, USER);
    }

    // ---------------------------------------------------------------- scan trigger

    @Test
    void triggerScan_returns202_andForwardsTokenWithoutBearerPrefix() throws Exception {
        mockMvc.perform(post("/api/projects/10/repositories/100/scan").requestAttr(USER_ATTR, USER)
                        .header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isAccepted());

        verify(projectService).assertProjectOwner(10L, USER);
        verify(projectService).triggerScan(10L, 100L, "abc.def.ghi");
    }

    @Test
    void triggerScan_returns403AndStartsNothing_forNonOwner() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no access"))
                .when(projectService).assertProjectOwner(10L, 2L);

        mockMvc.perform(post("/api/projects/10/repositories/100/scan").requestAttr(USER_ATTR, 2L)
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isForbidden());

        verify(projectService, never()).triggerScan(any(), any(), any());
    }

    @Test
    void triggerScan_returns400_whenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/projects/10/repositories/100/scan").requestAttr(USER_ATTR, USER))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void getDashboardSummary_returnsSummary() throws Exception {
        DashboardSummaryDTO summary = new DashboardSummaryDTO();
        summary.setTotalVulnerabilities(4);
        when(projectService.getProjectDashboardSummary(10L, USER)).thenReturn(summary);

        mockMvc.perform(get("/api/projects/10/dashboard").requestAttr(USER_ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVulnerabilities").value(4));
    }
}
