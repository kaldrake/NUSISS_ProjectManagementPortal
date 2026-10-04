import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import toast from 'react-hot-toast';
import ProjectsPage from './ProjectsPage';
import CreateProjectModal from './CreateProjectModal';
import { projectService } from '../../services/project.service';

jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/project.service', () => ({
  projectService: { getProjects: jest.fn(), deleteProject: jest.fn(), createProject: jest.fn() },
}));

const service = projectService as unknown as Record<string, jest.Mock>;

const project = (overrides: any = {}) => ({
  id: '1',
  name: 'Portal',
  description: 'Main project',
  ownerId: 1,
  createdAt: '2026-01-02T00:00:00',
  repositories: [{ id: 'r1' }],
  vulnerabilityCount: 4,
  criticalCount: 1,
  ...overrides,
});

const renderPage = (entry: any = '/projects') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <ProjectsPage />
    </MemoryRouter>
  );

describe('ProjectsPage', () => {
  test('lists the projects', async () => {
    service.getProjects.mockResolvedValue([project(), project({ id: '2', name: 'Second', repositories: [], criticalCount: 0 })]);

    renderPage();

    expect(await screen.findByText('Portal')).toBeInTheDocument();
    expect(screen.getByText('Second')).toBeInTheDocument();
    expect(screen.getByText('1 critical')).toBeInTheDocument();
    expect(screen.getByText('1 repo')).toBeInTheDocument();
    expect(screen.getByText('0 repos')).toBeInTheDocument();
  });

  test('shows the empty state and opens the create dialog from it', async () => {
    service.getProjects.mockResolvedValue([]);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Create New Project' }));

    expect(await screen.findByText('Project Name *')).toBeInTheDocument();
  });

  test('shows an error with a retry button when loading fails', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    service.getProjects.mockRejectedValueOnce({ response: { data: { message: 'Server error' } } });

    renderPage();

    expect(await screen.findByText('Server error')).toBeInTheDocument();
    service.getProjects.mockResolvedValue([project()]);
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Portal')).toBeInTheDocument();
  });

  test('uses a generic message when the error has no details', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    service.getProjects.mockRejectedValue(new Error('network'));

    renderPage();

    expect(await screen.findByText('Failed to load projects')).toBeInTheDocument();
    expect(toast.error).toHaveBeenCalledWith('Failed to load projects');
  });

  test('New Project button opens the create dialog', async () => {
    service.getProjects.mockResolvedValue([project()]);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /new project/i }));

    expect(await screen.findByText('Create New Project')).toBeInTheDocument();
  });

  test('opens the create dialog when navigated with openModal state', async () => {
    service.getProjects.mockResolvedValue([project()]);

    renderPage({ pathname: '/projects', state: { openModal: true } });

    expect(await screen.findByText('Create New Project')).toBeInTheDocument();
  });

  test('deletes a project after confirmation', async () => {
    service.getProjects.mockResolvedValue([project()]);
    service.deleteProject.mockResolvedValue(undefined);
    jest.spyOn(window, 'confirm').mockReturnValue(true);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /delete/i }));

    await waitFor(() => expect(service.deleteProject).toHaveBeenCalledWith('1'));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Project deleted successfully'));
  });

  test('does not delete when the confirmation is declined', async () => {
    service.getProjects.mockResolvedValue([project()]);
    jest.spyOn(window, 'confirm').mockReturnValue(false);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /delete/i }));

    expect(service.deleteProject).not.toHaveBeenCalled();
  });

  test('shows an error toast when deleting fails', async () => {
    service.getProjects.mockResolvedValue([project()]);
    service.deleteProject.mockRejectedValue(new Error('boom'));
    jest.spyOn(window, 'confirm').mockReturnValue(true);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /delete/i }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to delete project'));
  });

  test('refreshes the list after a project is created', async () => {
    service.getProjects.mockResolvedValue([project()]);
    service.createProject.mockResolvedValue(project({ id: '9', name: 'Brand new' }));

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /new project/i }));
    fireEvent.change(await screen.findByPlaceholderText(/e\.g\., E-Commerce/), { target: { value: 'Brand new' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Project' }));

    await waitFor(() => expect(service.createProject).toHaveBeenCalled());
    await waitFor(() => expect(service.getProjects.mock.calls.length).toBeGreaterThan(1));
  });
});

describe('CreateProjectModal', () => {
  const setup = (props: any = {}) => {
    const onClose = jest.fn();
    const onSuccess = jest.fn();
    render(<CreateProjectModal isOpen onClose={onClose} onSuccess={onSuccess} {...props} />);
    return { onClose, onSuccess };
  };

  test('renders nothing when closed', () => {
    const { container } = render(<CreateProjectModal isOpen={false} onClose={jest.fn()} onSuccess={jest.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  test('disables submit until a name is entered', () => {
    setup();
    expect(screen.getByRole('button', { name: 'Create Project' })).toBeDisabled();
  });

  test('creates the project and closes', async () => {
    service.createProject.mockResolvedValue(project());
    const { onClose, onSuccess } = setup();

    fireEvent.change(screen.getByPlaceholderText(/e\.g\., E-Commerce/), { target: { value: 'My project' } });
    fireEvent.change(screen.getByPlaceholderText(/Brief description/), { target: { value: 'About it' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Project' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    expect(service.createProject).toHaveBeenCalledWith({ name: 'My project', description: 'About it' });
    expect(onClose).toHaveBeenCalled();
    expect(toast.success).toHaveBeenCalledWith('Project "My project" created');
  });

  test('shows an error toast when creation fails', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    service.createProject.mockRejectedValue(new Error('boom'));
    const { onSuccess } = setup();

    fireEvent.change(screen.getByPlaceholderText(/e\.g\., E-Commerce/), { target: { value: 'My project' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Project' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to create project'));
    expect(onSuccess).not.toHaveBeenCalled();
  });

  test('Cancel and the backdrop both close the dialog', () => {
    const { onClose } = setup();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);

    const backdrop = document.querySelector('.bg-gray-500') as HTMLElement;
    fireEvent.click(backdrop);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
