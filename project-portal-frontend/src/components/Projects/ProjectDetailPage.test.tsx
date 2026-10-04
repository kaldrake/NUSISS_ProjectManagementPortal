import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import toast from 'react-hot-toast';
import ProjectDetailPage from './ProjectDetailPage';
import { projectService } from '../../services/project.service';
import { scanService } from '../../services/scan.service';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));
jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/project.service', () => ({
  projectService: {
    getProject: jest.fn(),
    getRepositories: jest.fn(),
    deleteProject: jest.fn(),
    triggerScan: jest.fn(),
    addRepository: jest.fn(),
  },
}));
jest.mock('../../services/scan.service', () => ({
  scanService: { getVulnerabilitiesByProject: jest.fn() },
}));

const projects = projectService as unknown as Record<string, jest.Mock>;
const scans = scanService as unknown as Record<string, jest.Mock>;

const project = {
  id: '10',
  name: 'Portal',
  description: 'Main project',
  ownerId: 1,
  createdAt: '2026-01-02T03:04:05',
  updatedAt: '2026-02-03T04:05:06',
};

const repository = {
  id: 'r1',
  projectId: '10',
  githubRepoId: 5,
  repoName: 'app',
  repoFullName: 'acme/app',
  repoUrl: 'https://github.com/acme/app',
  defaultBranch: 'main',
  isActive: true,
  createdAt: '2026-01-01T00:00:00',
};

const vulnerability = {
  id: 'v1',
  scanId: 's1',
  severity: 'CRITICAL',
  filePath: 'src/Main.java',
  lineNumber: 3,
  message: 'Hardcoded password',
  status: 'OPEN',
  createdAt: '2026-01-01T00:00:00',
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/projects/10']}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
      </Routes>
    </MemoryRouter>
  );

const loadSuccessfully = () => {
  projects.getProject.mockResolvedValue(project);
  projects.getRepositories.mockResolvedValue([repository]);
  scans.getVulnerabilitiesByProject.mockResolvedValue([vulnerability]);
};

describe('ProjectDetailPage', () => {
  test('loads the project, repositories and vulnerabilities', async () => {
    loadSuccessfully();

    renderPage();

    expect(await screen.findByText('Portal')).toBeInTheDocument();
    expect(screen.getByText('Main project')).toBeInTheDocument();
    expect(screen.getByText('Repositories (1)')).toBeInTheDocument();
    expect(projects.getProject).toHaveBeenCalledWith('10');
    expect(projects.getRepositories).toHaveBeenCalledWith('10');
    expect(scans.getVulnerabilitiesByProject).toHaveBeenCalledWith('10');
  });

  test('shows an error with a retry button when loading fails', async () => {
    projects.getProject.mockRejectedValueOnce({ response: { data: { message: 'Not yours' } } });
    projects.getRepositories.mockResolvedValue([]);
    scans.getVulnerabilitiesByProject.mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText('Not yours')).toBeInTheDocument();
    expect(toast.error).toHaveBeenCalledWith('Failed to load project');

    loadSuccessfully();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Portal')).toBeInTheDocument();
  });

  test('uses a generic message when the error has no details', async () => {
    projects.getProject.mockRejectedValue(new Error('network'));
    projects.getRepositories.mockResolvedValue([]);
    scans.getVulnerabilitiesByProject.mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText('Failed to load project')).toBeInTheDocument();
  });

  test('the Vulnerabilities tab shows the findings', async () => {
    loadSuccessfully();
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /vulnerabilities/i }));

    expect(await screen.findByText('Hardcoded password')).toBeInTheDocument();
  });

  test('the Settings tab shows project details', async () => {
    loadSuccessfully();
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /settings/i }));

    expect(screen.getByText('Project Settings')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
  });

  test('the Settings tab shows "Never" when the project was never updated', async () => {
    projects.getProject.mockResolvedValue({ ...project, updatedAt: undefined });
    projects.getRepositories.mockResolvedValue([]);
    scans.getVulnerabilitiesByProject.mockResolvedValue([]);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /settings/i }));

    expect(screen.getByText('Never')).toBeInTheDocument();
  });

  test('deleting the project asks for confirmation then returns to the project list', async () => {
    loadSuccessfully();
    projects.deleteProject.mockResolvedValue(undefined);
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /settings/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Project' }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/projects'));
    expect(projects.deleteProject).toHaveBeenCalledWith('10');
    expect(toast.success).toHaveBeenCalledWith('Project deleted successfully');
  });

  test('declining the delete confirmation keeps the project', async () => {
    loadSuccessfully();
    jest.spyOn(window, 'confirm').mockReturnValue(false);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /settings/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Project' }));

    expect(projects.deleteProject).not.toHaveBeenCalled();
  });

  test('shows an error toast when deleting fails', async () => {
    loadSuccessfully();
    projects.deleteProject.mockRejectedValue(new Error('boom'));
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /settings/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Project' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to delete project'));
  });

  test('Back to Projects navigates to the list', async () => {
    loadSuccessfully();
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /back to projects/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/projects');
  });

  test('triggering a scan reloads the project data', async () => {
    loadSuccessfully();
    projects.triggerScan.mockResolvedValue(undefined);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Scan Now' }));

    await waitFor(() => expect(projects.triggerScan).toHaveBeenCalledWith('10', 'r1'));
    await waitFor(() => expect(projects.getProject.mock.calls.length).toBeGreaterThan(1));
  });

  test('the Add Repository button opens the add-repository dialog', async () => {
    loadSuccessfully();
    renderPage();

    fireEvent.click((await screen.findAllByRole('button', { name: 'Add Repository' }))[0]);

    expect(await screen.findByText('Add GitHub Repository')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByText('Add GitHub Repository')).not.toBeInTheDocument());
  });

  test('a repository added from the dialog reloads the project data', async () => {
    loadSuccessfully();
    projects.addRepository.mockResolvedValue({});
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 5, name: 'app', full_name: 'acme/app', clone_url: 'https://github.com/acme/app.git', default_branch: 'main' }),
    });
    renderPage();

    fireEvent.click((await screen.findAllByRole('button', { name: 'Add Repository' }))[0]);
    fireEvent.change(await screen.findByPlaceholderText('https://github.com/username/repository'), {
      target: { value: 'https://github.com/acme/app' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
    await screen.findByText(/✓/);
    const addButtons = screen.getAllByRole('button', { name: 'Add Repository' });
    fireEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Repository added successfully'));
    expect(projects.addRepository).toHaveBeenCalled();
    await waitFor(() => expect(projects.getProject.mock.calls.length).toBeGreaterThan(1));
    delete (global as any).fetch;
  });
});
