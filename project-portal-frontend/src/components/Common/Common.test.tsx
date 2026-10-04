import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ErrorAlert from './ErrorAlert';
import LoadingSpinner from './LoadingSpinner';
import SeverityBadge from './SeverityBadge';
import Navbar from './Navbar';
import { useAuth } from '../../hooks/useAuth';

jest.mock('../../hooks/useAuth');

describe('ErrorAlert', () => {
  test('shows the message without a retry button by default', () => {
    render(<ErrorAlert message="Something broke" />);
    expect(screen.getByText('Something broke')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  test('calls onRetry when the retry button is clicked', () => {
    const onRetry = jest.fn();
    render(<ErrorAlert message="Failed" onRetry={onRetry} />);
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

describe('LoadingSpinner', () => {
  test('renders the spinner element', () => {
    const { container } = render(<LoadingSpinner />);
    expect(container.querySelector('.animate-spin')).toBeInTheDocument();
  });
});

describe('SeverityBadge', () => {
  test.each([
    ['BLOCKER', 'Blocker'],
    ['CRITICAL', 'Critical'],
    ['MAJOR', 'Major'],
    ['MINOR', 'Minor'],
    ['INFO', 'Info'],
  ] as const)('%s renders the label %s', (severity, label) => {
    render(<SeverityBadge severity={severity} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe('Navbar', () => {
  const mockedUseAuth = useAuth as jest.Mock;

  test('shows navigation links and the logged-in username', () => {
    mockedUseAuth.mockReturnValue({ user: { username: 'alice' }, logout: jest.fn() });

    render(
      <MemoryRouter initialEntries={['/projects']}>
        <Navbar />
      </MemoryRouter>
    );

    expect(screen.getByText('alice')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Projects' })).toHaveAttribute('href', '/projects');
  });

  test('highlights the link of the current page', () => {
    mockedUseAuth.mockReturnValue({ user: { username: 'alice' }, logout: jest.fn() });

    render(
      <MemoryRouter initialEntries={['/projects']}>
        <Navbar />
      </MemoryRouter>
    );

    expect(screen.getByRole('link', { name: 'Projects' }).className).toContain('border-blue-500');
    expect(screen.getByRole('link', { name: 'Dashboard' }).className).toContain('border-transparent');
  });

  test('logout button calls logout', () => {
    const logout = jest.fn();
    mockedUseAuth.mockReturnValue({ user: null, logout });

    render(
      <MemoryRouter>
        <Navbar />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole('button', { name: /logout/i }));

    expect(logout).toHaveBeenCalled();
  });
});
