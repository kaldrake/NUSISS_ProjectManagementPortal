import { projectApi, scanApi, loginApi } from './api';
import { projectService } from './project.service';
import { scanService } from './scan.service';
import { authService } from './auth.service';
import { githubService } from './github.service';

// Replace the axios instances so no HTTP request is made; each test sets the response it needs.
jest.mock('./api', () => ({
  projectApi: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() },
  scanApi: { get: jest.fn(), post: jest.fn(), patch: jest.fn() },
  loginApi: { get: jest.fn(), post: jest.fn() },
}));

const project = projectApi as unknown as Record<string, jest.Mock>;
const scan = scanApi as unknown as Record<string, jest.Mock>;
const login = loginApi as unknown as Record<string, jest.Mock>;

describe('projectService', () => {
  test('getProjects returns the response body', async () => {
    project.get.mockResolvedValue({ data: [{ id: '1' }] });
    expect(await projectService.getProjects()).toEqual([{ id: '1' }]);
    expect(project.get).toHaveBeenCalledWith('/projects');
  });

  test('getProject requests a single project', async () => {
    project.get.mockResolvedValue({ data: { id: '7' } });
    expect(await projectService.getProject('7')).toEqual({ id: '7' });
    expect(project.get).toHaveBeenCalledWith('/projects/7');
  });

  test('createProject posts the payload', async () => {
    project.post.mockResolvedValue({ data: { id: '2', name: 'New' } });
    const result = await projectService.createProject({ name: 'New', description: 'd' });
    expect(result.name).toBe('New');
    expect(project.post).toHaveBeenCalledWith('/projects', { name: 'New', description: 'd' });
  });

  test('updateProject puts the payload', async () => {
    project.put.mockResolvedValue({ data: { id: '2', name: 'Renamed' } });
    await projectService.updateProject('2', { name: 'Renamed' });
    expect(project.put).toHaveBeenCalledWith('/projects/2', { name: 'Renamed' });
  });

  test('deleteProject calls delete', async () => {
    project.delete.mockResolvedValue({});
    await projectService.deleteProject('2');
    expect(project.delete).toHaveBeenCalledWith('/projects/2');
  });

  test('getRepositories lists the repositories of a project', async () => {
    project.get.mockResolvedValue({ data: [{ id: 'r1' }] });
    expect(await projectService.getRepositories('2')).toEqual([{ id: 'r1' }]);
    expect(project.get).toHaveBeenCalledWith('/projects/2/repositories');
  });

  test('addRepository posts the repository data', async () => {
    const data = {
      githubRepoId: 5,
      repoFullName: 'acme/app',
      repoName: 'app',
      repoUrl: 'https://github.com/acme/app',
      cloneUrl: 'https://github.com/acme/app.git',
      defaultBranch: 'main',
    };
    project.post.mockResolvedValue({ data: { id: 'r1' } });
    expect(await projectService.addRepository('2', data)).toEqual({ id: 'r1' });
    expect(project.post).toHaveBeenCalledWith('/projects/2/repositories', data);
  });

  test('removeRepository deletes the repository', async () => {
    project.delete.mockResolvedValue({});
    await projectService.removeRepository('2', 'r1');
    expect(project.delete).toHaveBeenCalledWith('/projects/2/repositories/r1');
  });

  test('triggerScan posts to the scan endpoint', async () => {
    project.post.mockResolvedValue({});
    await projectService.triggerScan('2', 'r1');
    expect(project.post).toHaveBeenCalledWith('/projects/2/repositories/r1/scan');
  });
});

describe('scanService', () => {
  test('getVulnerabilitiesByProject', async () => {
    scan.get.mockResolvedValue({ data: [{ id: 'v1' }] });
    expect(await scanService.getVulnerabilitiesByProject('2')).toEqual([{ id: 'v1' }]);
    expect(scan.get).toHaveBeenCalledWith('/scans/projects/2/vulnerabilities');
  });

  test('getVulnerabilitiesByRepo', async () => {
    scan.get.mockResolvedValue({ data: [] });
    await scanService.getVulnerabilitiesByRepo('r1');
    expect(scan.get).toHaveBeenCalledWith('/scans/repositories/r1/vulnerabilities');
  });

  test('getVulnerabilityById', async () => {
    scan.get.mockResolvedValue({ data: { id: 'v1' } });
    expect(await scanService.getVulnerabilityById('v1')).toEqual({ id: 'v1' });
    expect(scan.get).toHaveBeenCalledWith('/scans/vulnerabilities/v1');
  });

  test('updateVulnerabilityStatus sends the status as a query parameter', async () => {
    scan.patch.mockResolvedValue({});
    await scanService.updateVulnerabilityStatus('v1', 'RESOLVED');
    expect(scan.patch).toHaveBeenCalledWith('/scans/vulnerabilities/v1/status', null, {
      params: { status: 'RESOLVED' },
    });
  });

  test('getScans returns the scan history', async () => {
    scan.get.mockResolvedValue({ data: [{ id: 's1' }] });
    expect(await scanService.getScans('r1')).toEqual([{ id: 's1' }]);
    expect(scan.get).toHaveBeenCalledWith('/scans/repositories/r1/history');
  });

  test('getScanStatus', async () => {
    scan.get.mockResolvedValue({ data: { status: 'COMPLETED' } });
    expect(await scanService.getScanStatus('9')).toEqual({ status: 'COMPLETED' });
    expect(scan.get).toHaveBeenCalledWith('/scans/9/status');
  });

  test('getScanVulnerabilities', async () => {
    scan.get.mockResolvedValue({ data: [{ id: 'v1' }] });
    expect(await scanService.getScanVulnerabilities('9')).toEqual([{ id: 'v1' }]);
    expect(scan.get).toHaveBeenCalledWith('/scans/9/vulnerabilities');
  });

  test('getDashboardSummary', async () => {
    scan.get.mockResolvedValue({ data: { totalVulnerabilities: 3 } });
    expect(await scanService.getDashboardSummary('2')).toEqual({ totalVulnerabilities: 3 });
    expect(scan.get).toHaveBeenCalledWith('/scans/dashboard/projects/2/summary');
  });
});

describe('githubService', () => {
  test('getRepositories', async () => {
    scan.get.mockResolvedValue({ data: [{ id: 1 }] });
    expect(await githubService.getRepositories()).toEqual([{ id: 1 }]);
    expect(scan.get).toHaveBeenCalledWith('/github/repositories');
  });

  test('validateRepoUrl', async () => {
    scan.post.mockResolvedValue({ data: { valid: true, repoName: 'app' } });
    expect(await githubService.validateRepoUrl('https://github.com/acme/app')).toEqual({ valid: true, repoName: 'app' });
    expect(scan.post).toHaveBeenCalledWith('/github/validate', { url: 'https://github.com/acme/app' });
  });
});

describe('authService', () => {
  const user = { id: 1, username: 'alice', email: 'a@example.com' };
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', { value: originalLocation, writable: true });
  });

  test('token helpers store and clear the token and user', () => {
    authService.setToken('abc');
    authService.setUser(user);
    expect(authService.getToken()).toBe('abc');
    expect(authService.getUser()).toEqual(user);
    expect(authService.isAuthenticated()).toBe(true);

    authService.clearToken();
    expect(authService.getToken()).toBeNull();
    expect(authService.getUser()).toBeNull();
    expect(authService.isAuthenticated()).toBe(false);
  });

  test('getUser returns null for corrupted stored data', () => {
    localStorage.setItem('user', '{not json');
    expect(authService.getUser()).toBeNull();
  });

  test('login stores the token and user from the response', async () => {
    login.post.mockResolvedValue({ data: { token: 'jwt', user } });
    const result = await authService.login('alice', 'secret');
    expect(result.token).toBe('jwt');
    expect(login.post).toHaveBeenCalledWith('/auth/login', { username: 'alice', password: 'secret' });
    expect(authService.getToken()).toBe('jwt');
    expect(authService.getUser()).toEqual(user);
  });

  test('login stores nothing when the response has no token', async () => {
    login.post.mockResolvedValue({ data: { user } });
    await authService.login('alice', 'secret');
    expect(authService.getToken()).toBeNull();
  });

  test('register stores the token and user from the response', async () => {
    login.post.mockResolvedValue({ data: { token: 'jwt2', user } });
    await authService.register('alice', 'a@example.com', 'secret');
    expect(login.post).toHaveBeenCalledWith('/auth/register', {
      username: 'alice',
      email: 'a@example.com',
      password: 'secret',
    });
    expect(authService.getToken()).toBe('jwt2');
  });

  test('register stores nothing when the response has no token', async () => {
    login.post.mockResolvedValue({ data: {} });
    await authService.register('alice', 'a@example.com', 'secret');
    expect(authService.getToken()).toBeNull();
  });

  test('getCurrentUser returns the profile', async () => {
    login.get.mockResolvedValue({ data: user });
    expect(await authService.getCurrentUser()).toEqual(user);
    expect(login.get).toHaveBeenCalledWith('/auth/me');
  });

  test('logout clears the session and redirects to the login page', () => {
    const replace = jest.fn();
    Object.defineProperty(window, 'location', { value: { replace }, writable: true });
    authService.setToken('abc');

    authService.logout();

    expect(authService.getToken()).toBeNull();
    expect(replace).toHaveBeenCalledWith('/login');
  });
});
