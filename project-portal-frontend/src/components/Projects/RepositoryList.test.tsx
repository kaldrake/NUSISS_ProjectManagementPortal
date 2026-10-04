import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import toast from 'react-hot-toast';
import RepositoryList from './RepositoryList';
import { projectService } from '../../services/project.service';

jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/project.service', () => ({
  projectService: { triggerScan: jest.fn(), removeRepository: jest.fn() },
}));

const service = projectService as unknown as Record<string, jest.Mock>;

const repo = (overrides: any = {}) => ({
  id: 'r1',
  projectId: '10',
  githubRepoId: 5,
  repoName: 'app',
  repoFullName: 'acme/app',
  repoUrl: 'https://github.com/acme/app',
  defaultBranch: 'main',
  isActive: true,
  createdAt: '2026-01-01T00:00:00',
  ...overrides,
});

const setup = (repositories: any[]) => {
  const props = { onAddRepo: jest.fn(), onScanTriggered: jest.fn(), onRefresh: jest.fn() };
  render(<RepositoryList projectId="10" repositories={repositories} {...props} />);
  return props;
};

describe('RepositoryList', () => {
  test('shows an empty state with an add button', () => {
    const props = setup([]);
    expect(screen.getByText('No repositories')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));
    expect(props.onAddRepo).toHaveBeenCalled();
  });

  test('lists repositories with branch, last scan and critical badge', () => {
    setup([repo({ lastScanAt: '2026-02-01T10:00:00', criticalCount: 3 }), repo({ id: 'r2', repoFullName: 'acme/other', isActive: false })]);

    expect(screen.getByText('Repositories (2)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'acme/app' })).toHaveAttribute('href', 'https://github.com/acme/app');
    expect(screen.getAllByText('Branch: main')).toHaveLength(2);
    expect(screen.getByText(/Last scan:/)).toBeInTheDocument();
    expect(screen.getByText('3 critical')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  test('the add button in the header calls onAddRepo', () => {
    const props = setup([repo()]);
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));
    expect(props.onAddRepo).toHaveBeenCalled();
  });

  test('Scan Now triggers a scan and notifies the parent', async () => {
    service.triggerScan.mockResolvedValue(undefined);
    const props = setup([repo()]);

    fireEvent.click(screen.getByRole('button', { name: 'Scan Now' }));

    await waitFor(() => expect(props.onScanTriggered).toHaveBeenCalled());
    expect(service.triggerScan).toHaveBeenCalledWith('10', 'r1');
    expect(toast.success).toHaveBeenCalledWith('Scan triggered successfully');
  });

  test('shows an error toast when the scan cannot be triggered', async () => {
    service.triggerScan.mockRejectedValue(new Error('boom'));
    const props = setup([repo()]);

    fireEvent.click(screen.getByRole('button', { name: 'Scan Now' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to trigger scan'));
    expect(props.onScanTriggered).not.toHaveBeenCalled();
  });

  test('removes a repository after confirmation', async () => {
    service.removeRepository.mockResolvedValue(undefined);
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    const props = setup([repo()]);

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(props.onRefresh).toHaveBeenCalled());
    expect(service.removeRepository).toHaveBeenCalledWith('10', 'r1');
    expect(toast.success).toHaveBeenCalledWith('Repository removed');
  });

  test('does not remove when the confirmation is declined', () => {
    jest.spyOn(window, 'confirm').mockReturnValue(false);
    setup([repo()]);

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));

    expect(service.removeRepository).not.toHaveBeenCalled();
  });

  test('shows an error toast when removal fails', async () => {
    service.removeRepository.mockRejectedValue(new Error('boom'));
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    setup([repo()]);

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to remove repository'));
  });
});
