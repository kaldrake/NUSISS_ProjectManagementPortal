import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import toast from 'react-hot-toast';
import DashboardPage from './DashboardPage';
import ProjectCard from './ProjectCard';
import StatsWidget from './StatsWidget';
import { projectService } from '../../services/project.service';

jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/project.service', () => ({ projectService: { getProjects: jest.fn() } }));

const getProjects = projectService.getProjects as jest.Mock;

const project = (overrides: any = {}) => ({
  id: '1',
  name: 'Portal',
  description: 'Main project',
  ownerId: 1,
  createdAt: '2026-01-02T00:00:00',
  repositories: [{ id: 'r1' }, { id: 'r2' }],
  vulnerabilityCount: 5,
  criticalCount: 2,
  ...overrides,
});

describe('StatsWidget', () => {
  test.each(['projects', 'repositories', 'vulnerabilities', 'critical'] as const)('renders the %s widget', (icon) => {
    render(<StatsWidget title="Title" value={42} icon={icon} color="blue" />);
    expect(screen.getByText('Title')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  test.each(['blue', 'green', 'red', 'orange'] as const)('applies the %s colour', (color) => {
    const { container } = render(<StatsWidget title="T" value={1} icon="projects" color={color} />);
    expect(container.querySelector(`.text-${color}-600`)).toBeInTheDocument();
  });
});

describe('ProjectCard', () => {
  const renderCard = (p: any) =>
    render(
      <MemoryRouter>
        <ProjectCard project={p} />
      </MemoryRouter>
    );

  test('shows name, description, counts and links to the project', () => {
    renderCard(project());
    expect(screen.getByText('Portal')).toBeInTheDocument();
    expect(screen.getByText('Main project')).toBeInTheDocument();
    expect(screen.getByText('2 critical')).toBeInTheDocument();
    expect(screen.getByText('5 vulnerabilities')).toBeInTheDocument();
    expect(screen.getByText('2 repos')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/projects/1');
  });

  test('uses the singular for one repository and hides empty badges', () => {
    renderCard(project({ repositories: [{ id: 'r1' }], vulnerabilityCount: 0, criticalCount: 0, description: undefined }));
    expect(screen.getByText('1 repo')).toBeInTheDocument();
    expect(screen.queryByText(/critical/)).not.toBeInTheDocument();
    expect(screen.queryByText(/vulnerabilities/)).not.toBeInTheDocument();
  });

  test('copes with missing optional fields', () => {
    renderCard({ id: '2', name: 'Bare', ownerId: 1, createdAt: '2026-01-01T00:00:00' });
    expect(screen.getByText('0 repos')).toBeInTheDocument();
  });
});

describe('DashboardPage', () => {
  const renderPage = () =>
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

  test('shows aggregated statistics and recent projects', async () => {
    getProjects.mockResolvedValue([project(), project({ id: '2', name: 'Second', vulnerabilityCount: 3, criticalCount: 1 })]);

    renderPage();

    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Total Projects').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Repositories').nextSibling).toHaveTextContent('4');
    expect(screen.getByText('Vulnerabilities').nextSibling).toHaveTextContent('8');
    expect(screen.getByText('Critical Issues').nextSibling).toHaveTextContent('3');
    expect(screen.getByText('Second')).toBeInTheDocument();
  });

  test('shows only the six most recent projects', async () => {
    getProjects.mockResolvedValue(Array.from({ length: 8 }, (_, i) => project({ id: String(i), name: `Project ${i}` })));

    renderPage();

    await screen.findByText('Project 0');
    expect(screen.getByText('Project 5')).toBeInTheDocument();
    expect(screen.queryByText('Project 6')).not.toBeInTheDocument();
  });

  test('shows the empty state when there are no projects', async () => {
    getProjects.mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText('No projects')).toBeInTheDocument();
  });

  test('shows an error toast when loading fails', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    getProjects.mockRejectedValue(new Error('boom'));

    renderPage();

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to load projects'));
  });

  test('handles projects without optional counts or repositories', async () => {
    getProjects.mockResolvedValue([{ id: '1', name: 'Bare', ownerId: 1, createdAt: '2026-01-01T00:00:00' }]);

    renderPage();

    await screen.findByText('Bare');
    expect(screen.getByText('Total Projects').nextSibling).toHaveTextContent('1');
    expect(screen.getByText('Repositories').nextSibling).toHaveTextContent('0');
    expect(screen.getByText('Vulnerabilities').nextSibling).toHaveTextContent('0');
    expect(screen.getByText('Critical Issues').nextSibling).toHaveTextContent('0');
  });
});
