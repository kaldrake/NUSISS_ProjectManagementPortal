import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import toast from 'react-hot-toast';
import AddRepositoryModal from './AddRepositoryModal';
import { projectService } from '../../services/project.service';

jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/project.service', () => ({ projectService: { addRepository: jest.fn() } }));

const service = projectService as unknown as Record<string, jest.Mock>;

const githubRepo = {
  id: 555,
  name: 'app',
  full_name: 'acme/app',
  clone_url: 'https://github.com/acme/app.git',
  default_branch: 'develop',
};

const setup = () => {
  const onClose = jest.fn();
  const onSuccess = jest.fn();
  render(<AddRepositoryModal isOpen onClose={onClose} onSuccess={onSuccess} projectId="10" existingRepos={[]} />);
  return { onClose, onSuccess };
};

const urlInput = () => screen.getByPlaceholderText('https://github.com/username/repository');
const validateButton = () => screen.getByRole('button', { name: 'Validate' });

const mockFetch = (response: any) => {
  (global as any).fetch = jest.fn().mockResolvedValue(response);
};

describe('AddRepositoryModal', () => {
  afterEach(() => {
    delete (global as any).fetch;
  });

  test('renders nothing when closed', () => {
    const { container } = render(
      <AddRepositoryModal isOpen={false} onClose={jest.fn()} onSuccess={jest.fn()} projectId="10" existingRepos={[]} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  test('Validate and Add are disabled until a repository is entered and validated', () => {
    setup();
    expect(validateButton()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Add Repository' })).toBeDisabled();
  });

  test('rejects URLs that are not GitHub repositories', async () => {
    mockFetch({ ok: true, json: async () => githubRepo });
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://example.com/not-github' } });
    fireEvent.click(validateButton());

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Invalid GitHub URL format'));
    expect((global as any).fetch).not.toHaveBeenCalled();
  });

  test('reports a repository that cannot be found', async () => {
    mockFetch({ ok: false });
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/missing' } });
    fireEvent.click(validateButton());

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Repository not found or not accessible'));
  });

  test('reports a failed validation request', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    (global as any).fetch = jest.fn().mockRejectedValue(new Error('offline'));
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to validate repository'));
  });

  test('validates a repository, strips .git and shows its full name and default branch', async () => {
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app.git' } });
    fireEvent.click(validateButton());

    expect(await screen.findByText(/acme\/app/)).toBeInTheDocument();
    expect((global as any).fetch).toHaveBeenCalledWith('https://api.github.com/repos/acme/app');
    expect(screen.getByPlaceholderText('develop')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add Repository' })).toBeEnabled();
  });

  test('editing the URL after validating invalidates the result', async () => {
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());
    await screen.findByText(/acme\/app/);
    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/other' } });

    expect(screen.getByRole('button', { name: 'Add Repository' })).toBeDisabled();
  });

  test('adds the repository using the detected default branch when none is typed', async () => {
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    service.addRepository.mockResolvedValue({});
    const { onClose, onSuccess } = setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());
    await screen.findByText(/acme\/app/);
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    expect(service.addRepository).toHaveBeenCalledWith('10', {
      githubRepoId: 555,
      repoFullName: 'acme/app',
      repoName: 'app',
      repoUrl: 'https://github.com/acme/app',
      cloneUrl: 'https://github.com/acme/app.git',
      defaultBranch: 'develop',
    });
    expect(onClose).toHaveBeenCalled();
    expect(toast.success).toHaveBeenCalledWith('Repository added successfully');
  });

  test('a branch typed by the user overrides the default', async () => {
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    service.addRepository.mockResolvedValue({});
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());
    await screen.findByText(/acme\/app/);
    fireEvent.change(screen.getByPlaceholderText('develop'), { target: { value: '  release  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));

    await waitFor(() => expect(service.addRepository).toHaveBeenCalled());
    expect(service.addRepository.mock.calls[0][1].defaultBranch).toBe('release');
  });

  test('shows the server message when adding fails', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    service.addRepository.mockRejectedValue({ response: { data: { message: 'Repository already exists' } } });
    const { onSuccess } = setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());
    await screen.findByText(/acme\/app/);
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Repository already exists'));
    expect(onSuccess).not.toHaveBeenCalled();
  });

  test('falls back to a generic message when adding fails without details', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    mockFetch({ ok: true, json: async () => ({ ...githubRepo }) });
    service.addRepository.mockRejectedValue(new Error('boom'));
    setup();

    fireEvent.change(urlInput(), { target: { value: 'https://github.com/acme/app' } });
    fireEvent.click(validateButton());
    await screen.findByText(/acme\/app/);
    fireEvent.click(screen.getByRole('button', { name: 'Add Repository' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to add repository'));
  });

  test('Cancel and the backdrop close the dialog', () => {
    const { onClose } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fireEvent.click(document.querySelector('.bg-gray-500') as HTMLElement);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
