package com.portal.project.service;

import com.portal.project.client.ScanServiceClient;
import com.portal.project.dto.GitHubRepositoryDTO;
import com.portal.project.dto.ProjectCreateDTO;
import com.portal.project.dto.ProjectResponseDTO;
import com.portal.project.dto.ProjectUpdateDTO;
import com.portal.project.dto.ScanResponseDTO;
import com.portal.project.dto.VulnerabilityDTO;
import com.portal.project.entity.GitHubRepository;
import com.portal.project.entity.Project;
import com.portal.project.repository.GitHubRepositoryRepository;
import com.portal.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProjectService: ownership enforcement, CRUD rules, vulnerability
 * counts on the project DTO, and the async scan trigger.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    private static final Long OWNER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final Long PROJECT_ID = 10L;
    private static final Long REPO_ID = 100L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Mock
    private ScanServiceClient scanServiceClient;

    @InjectMocks
    private ProjectService projectService;

    private Project ownedProject() {
        Project project = new Project("Portal", "desc", OWNER);
        project.setId(PROJECT_ID);
        return project;
    }

    private GitHubRepository repositoryOf(Long projectId) {
        GitHubRepository repo = new GitHubRepository();
        repo.setId(REPO_ID);
        repo.setProjectId(projectId);
        repo.setCloneUrl("https://github.com/acme/app.git");
        repo.setDefaultBranch("main");
        return repo;
    }

    private VulnerabilityDTO vulnerability(String severity) {
        VulnerabilityDTO dto = new VulnerabilityDTO();
        dto.setSeverity(severity);
        return dto;
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void getProjectById_returnsProject_whenCallerIsOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        ProjectResponseDTO result = projectService.getProjectById(PROJECT_ID, OWNER);

        assertEquals(PROJECT_ID, result.getId());
        assertEquals("Portal", result.getName());
        assertEquals(OWNER, result.getOwnerId());
    }

    @Test
    void getProjectById_throws403_whenCallerIsNotOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> projectService.getProjectById(PROJECT_ID, OTHER_USER));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getProjectById_throws403_whenUserIdIsNull() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> projectService.getProjectById(PROJECT_ID, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getProjectById_throws_whenProjectDoesNotExist() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> projectService.getProjectById(PROJECT_ID, OWNER));
    }

    @Test
    void assertProjectOwner_throws403_forNonOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(ResponseStatusException.class, () -> projectService.assertProjectOwner(PROJECT_ID, OTHER_USER));
    }

    @Test
    void getProjectDashboardSummary_doesNotCallScanService_forNonOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(ResponseStatusException.class,
                () -> projectService.getProjectDashboardSummary(PROJECT_ID, OTHER_USER));

        verifyNoInteractions(scanServiceClient);
    }

    @Test
    void getProjectRepositories_throws403_forNonOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(ResponseStatusException.class,
                () -> projectService.getProjectRepositories(PROJECT_ID, OTHER_USER));

        verify(gitHubRepositoryRepository, never()).findByProjectId(anyLong());
    }

    // ---------------------------------------------------------------- create / update / delete

    @Test
    void createProject_savesProjectWithCallerAsOwner() {
        ProjectCreateDTO dto = new ProjectCreateDTO();
        dto.setName("New project");
        dto.setDescription("about it");
        when(projectRepository.existsByNameAndOwnerId("New project", OWNER)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project saved = inv.getArgument(0);
            saved.setId(PROJECT_ID);
            return saved;
        });

        ProjectResponseDTO result = projectService.createProject(dto, OWNER);

        assertEquals(PROJECT_ID, result.getId());
        assertEquals("New project", result.getName());
        assertEquals(OWNER, result.getOwnerId());
    }

    @Test
    void createProject_throws_whenNameAlreadyUsedByOwner() {
        ProjectCreateDTO dto = new ProjectCreateDTO();
        dto.setName("Portal");
        when(projectRepository.existsByNameAndOwnerId("Portal", OWNER)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> projectService.createProject(dto, OWNER));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void updateProject_throws_whenCallerIsNotOwner() {
        ProjectUpdateDTO dto = new ProjectUpdateDTO();
        dto.setName("Renamed");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(RuntimeException.class, () -> projectService.updateProject(PROJECT_ID, dto, OTHER_USER));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void updateProject_appliesNewNameAndDescription_forOwner() {
        ProjectUpdateDTO dto = new ProjectUpdateDTO();
        dto.setName("Renamed");
        dto.setDescription("new desc");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(projectRepository.existsByNameAndOwnerId("Renamed", OWNER)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponseDTO result = projectService.updateProject(PROJECT_ID, dto, OWNER);

        assertEquals("Renamed", result.getName());
        assertEquals("new desc", result.getDescription());
    }

    @Test
    void updateProject_throws_whenNewNameConflictsWithAnotherProject() {
        ProjectUpdateDTO dto = new ProjectUpdateDTO();
        dto.setName("Taken");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(projectRepository.existsByNameAndOwnerId("Taken", OWNER)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> projectService.updateProject(PROJECT_ID, dto, OWNER));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void deleteProject_throwsAndDeletesNothing_forNonOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(RuntimeException.class, () -> projectService.deleteProject(PROJECT_ID, OTHER_USER));

        verify(gitHubRepositoryRepository, never()).deleteByProjectId(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void deleteProject_removesRepositoriesAndProject_forOwner() {
        Project project = ownedProject();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        projectService.deleteProject(PROJECT_ID, OWNER);

        verify(gitHubRepositoryRepository).deleteByProjectId(PROJECT_ID);
        verify(projectRepository).delete(project);
    }

    // ---------------------------------------------------------------- repositories

    @Test
    void addRepository_throws403AndSavesNothing_forNonOwner() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));

        assertThrows(ResponseStatusException.class,
                () -> projectService.addRepository(PROJECT_ID, new GitHubRepositoryDTO(), OTHER_USER));

        verify(gitHubRepositoryRepository, never()).save(any(GitHubRepository.class));
    }

    @Test
    void addRepository_throws_whenRepositoryAlreadyLinked() {
        GitHubRepositoryDTO dto = new GitHubRepositoryDTO();
        dto.setGithubRepoId(555L);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(gitHubRepositoryRepository.existsByProjectIdAndGithubRepoId(PROJECT_ID, 555L)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> projectService.addRepository(PROJECT_ID, dto, OWNER));

        verify(gitHubRepositoryRepository, never()).save(any(GitHubRepository.class));
    }

    @Test
    void addRepository_savesRepositoryAgainstProject_forOwner() {
        GitHubRepositoryDTO dto = new GitHubRepositoryDTO();
        dto.setGithubRepoId(555L);
        dto.setRepoName("app");
        dto.setCloneUrl("https://github.com/acme/app.git");
        dto.setDefaultBranch("develop");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(gitHubRepositoryRepository.existsByProjectIdAndGithubRepoId(PROJECT_ID, 555L)).thenReturn(false);
        when(gitHubRepositoryRepository.save(any(GitHubRepository.class))).thenAnswer(inv -> {
            GitHubRepository saved = inv.getArgument(0);
            saved.setId(REPO_ID);
            return saved;
        });

        GitHubRepositoryDTO result = projectService.addRepository(PROJECT_ID, dto, OWNER);

        assertEquals(REPO_ID, result.getId());
        assertEquals(PROJECT_ID, result.getProjectId());
        assertEquals("develop", result.getDefaultBranch());
    }

    @Test
    void removeRepository_throws_whenRepositoryBelongsToAnotherProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repositoryOf(999L)));

        assertThrows(RuntimeException.class, () -> projectService.removeRepository(REPO_ID, PROJECT_ID, OWNER));

        verify(gitHubRepositoryRepository, never()).delete(any(GitHubRepository.class));
    }

    @Test
    void removeRepository_deletesRepository_forOwner() {
        GitHubRepository repo = repositoryOf(PROJECT_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(ownedProject()));
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));

        projectService.removeRepository(REPO_ID, PROJECT_ID, OWNER);

        verify(gitHubRepositoryRepository).delete(repo);
    }

    // ---------------------------------------------------------------- project list + vulnerability counts

    @Test
    void getProjectsByOwner_countsVulnerabilitiesAndCriticalOnes() {
        when(projectRepository.findByOwnerId(OWNER)).thenReturn(List.of(ownedProject()));
        when(scanServiceClient.getVulnerabilitiesByProjectId(PROJECT_ID)).thenReturn(List.of(
                vulnerability("BLOCKER"), vulnerability("CRITICAL"), vulnerability("MAJOR"), vulnerability("INFO")));

        List<ProjectResponseDTO> result = projectService.getProjectsByOwner(OWNER);

        assertEquals(1, result.size());
        assertEquals(4, result.get(0).getVulnerabilityCount());
        assertEquals(2, result.get(0).getCriticalCount());
    }

    @Test
    void getProjectsByOwner_reportsZeroCounts_whenScanServiceFails() {
        when(projectRepository.findByOwnerId(OWNER)).thenReturn(List.of(ownedProject()));
        when(scanServiceClient.getVulnerabilitiesByProjectId(PROJECT_ID)).thenThrow(new RuntimeException("scan down"));

        List<ProjectResponseDTO> result = projectService.getProjectsByOwner(OWNER);

        assertEquals(0, result.get(0).getVulnerabilityCount());
        assertEquals(0, result.get(0).getCriticalCount());
    }

    @Test
    void getProjectsByOwner_returnsEmptyList_whenOwnerHasNoProjects() {
        when(projectRepository.findByOwnerId(OWNER)).thenReturn(List.of());

        assertEquals(0, projectService.getProjectsByOwner(OWNER).size());
    }

    // ---------------------------------------------------------------- async scan trigger

    @Test
    void triggerScan_updatesLastScanAt_whenScanServiceReturnsScanId() {
        ScanResponseDTO response = new ScanResponseDTO();
        response.setScanId(77L);
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repositoryOf(PROJECT_ID)));
        when(scanServiceClient.triggerScan(eq(PROJECT_ID), eq(REPO_ID), anyString(), anyString(), eq("jwt")))
                .thenReturn(response);

        projectService.triggerScan(PROJECT_ID, REPO_ID, "jwt");

        verify(gitHubRepositoryRepository).updateLastScanAt(eq(REPO_ID), any(LocalDateTime.class));
    }

    @Test
    void triggerScan_doesNotUpdateLastScanAt_whenResponseHasNoScanId() {
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repositoryOf(PROJECT_ID)));
        when(scanServiceClient.triggerScan(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new ScanResponseDTO());

        projectService.triggerScan(PROJECT_ID, REPO_ID, "jwt");

        verify(gitHubRepositoryRepository, never()).updateLastScanAt(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void triggerScan_doesNotCallScanService_whenRepositoryBelongsToAnotherProject() {
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repositoryOf(999L)));

        projectService.triggerScan(PROJECT_ID, REPO_ID, "jwt");

        verifyNoInteractions(scanServiceClient);
    }

    @Test
    void triggerScan_swallowsScanServiceErrors() {
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repositoryOf(PROJECT_ID)));
        when(scanServiceClient.triggerScan(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("scan service unreachable"));

        // runs on a background thread in production: the error must be logged, not propagated
        projectService.triggerScan(PROJECT_ID, REPO_ID, "jwt");

        verify(gitHubRepositoryRepository, never()).updateLastScanAt(anyLong(), any(LocalDateTime.class));
    }
}
