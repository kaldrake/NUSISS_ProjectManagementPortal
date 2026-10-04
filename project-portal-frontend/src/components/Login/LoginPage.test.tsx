import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import LoginPage from './LoginPage';
import { authService } from '../../services/auth.service';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));
jest.mock('react-hot-toast', () => ({ __esModule: true, default: { success: jest.fn(), error: jest.fn() } }));
jest.mock('../../services/auth.service', () => ({
  authService: {
    isAuthenticated: jest.fn(),
    login: jest.fn(),
    register: jest.fn(),
    setToken: jest.fn(),
    setUser: jest.fn(),
  },
}));

const auth = authService as unknown as Record<string, jest.Mock>;
const user = { id: 1, username: 'alice', email: 'a@example.com' };

const type = (placeholder: string, value: string) =>
  userEvent.type(screen.getByPlaceholderText(placeholder), value);

describe('LoginPage', () => {
  beforeEach(() => {
    auth.isAuthenticated.mockReturnValue(false);
  });

  test('redirects to the dashboard when already authenticated', () => {
    auth.isAuthenticated.mockReturnValue(true);
    render(<LoginPage />);
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });

  test('requires a username and password', () => {
    render(<LoginPage />);
    fireEvent.submit(screen.getByPlaceholderText('Enter your username').closest('form')!);
    expect(toast.error).toHaveBeenCalledWith('Please enter username and password');
    expect(auth.login).not.toHaveBeenCalled();
  });

  test('logs in, stores the session and goes to the dashboard', async () => {
    auth.login.mockResolvedValue({ token: 'jwt', user });
    render(<LoginPage />);

    type('Enter your username', 'alice');
    type('Enter your password', 'secret1');
    fireEvent.submit(screen.getByPlaceholderText('Enter your password').closest('form')!);

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard'));
    expect(auth.login).toHaveBeenCalledWith('alice', 'secret1');
    expect(auth.setToken).toHaveBeenCalledWith('jwt');
    expect(auth.setUser).toHaveBeenCalledWith(user);
    expect(toast.success).toHaveBeenCalledWith('Login successful!');
  });

  test('shows the server message when login fails', async () => {
    auth.login.mockRejectedValue({ response: { data: { message: 'Invalid credentials' } } });
    render(<LoginPage />);

    type('Enter your username', 'alice');
    type('Enter your password', 'wrong');
    fireEvent.submit(screen.getByPlaceholderText('Enter your password').closest('form')!);

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Invalid credentials'));
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  test('falls back to a generic message when login fails without details', async () => {
    auth.login.mockRejectedValue(new Error('network'));
    render(<LoginPage />);

    type('Enter your username', 'alice');
    type('Enter your password', 'secret1');
    fireEvent.submit(screen.getByPlaceholderText('Enter your password').closest('form')!);

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Login failed'));
  });

  describe('registration', () => {
    const openRegister = () => {
      render(<LoginPage />);
      fireEvent.click(screen.getAllByRole('button', { name: 'Register' })[0]);
    };

    const fill = (u: string, e: string, p: string, c: string) => {
      if (u) type('Choose a username', u);
      if (e) type('your@email.com', e);
      if (p) type('Minimum 6 characters', p);
      if (c) type('Confirm your password', c);
    };

    const submit = () => fireEvent.submit(screen.getByPlaceholderText('Confirm your password').closest('form')!);

    test('requires all fields', () => {
      openRegister();
      fill('alice', '', '', '');
      submit();
      expect(toast.error).toHaveBeenCalledWith('Please fill in all fields');
    });

    test('rejects mismatching passwords', () => {
      openRegister();
      fill('alice', 'a@example.com', 'secret1', 'different');
      submit();
      expect(toast.error).toHaveBeenCalledWith('Passwords do not match');
    });

    test('rejects short passwords', () => {
      openRegister();
      fill('alice', 'a@example.com', 'abc', 'abc');
      submit();
      expect(toast.error).toHaveBeenCalledWith('Password must be at least 6 characters');
    });

    test('registers, stores the session and goes to the dashboard', async () => {
      auth.register.mockResolvedValue({ token: 'jwt', user });
      openRegister();
      fill('alice', 'a@example.com', 'secret1', 'secret1');
      submit();

      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard'));
      expect(auth.register).toHaveBeenCalledWith('alice', 'a@example.com', 'secret1');
      expect(toast.success).toHaveBeenCalledWith('Registration successful!');
    });

    test('shows the server message when registration fails', async () => {
      auth.register.mockRejectedValue({ response: { data: { message: 'Username taken' } } });
      openRegister();
      fill('alice', 'a@example.com', 'secret1', 'secret1');
      submit();

      await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Username taken'));
    });

    test('falls back to a generic message when registration fails without details', async () => {
      auth.register.mockRejectedValue(new Error('network'));
      openRegister();
      fill('alice', 'a@example.com', 'secret1', 'secret1');
      submit();

      await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Registration failed'));
    });

    test('can switch back to the login form', () => {
      openRegister();
      fireEvent.click(screen.getAllByRole('button', { name: 'Login' })[0]);
      expect(screen.getByPlaceholderText('Enter your username')).toBeInTheDocument();
    });
  });
});
