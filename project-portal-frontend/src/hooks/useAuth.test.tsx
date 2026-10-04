import { renderHook, act, waitFor } from '@testing-library/react';
import { useAuth } from './useAuth';
import { authService } from '../services/auth.service';

jest.mock('../services/auth.service', () => ({
  authService: {
    getToken: jest.fn(),
    getUser: jest.fn(),
    logout: jest.fn(),
  },
}));

const auth = authService as unknown as Record<string, jest.Mock>;

describe('useAuth', () => {
  test('is unauthenticated when there is no token', async () => {
    auth.getToken.mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  test('is authenticated with the stored user when a token exists', async () => {
    const user = { id: 1, username: 'alice', email: 'a@example.com' };
    auth.getToken.mockReturnValue('jwt');
    auth.getUser.mockReturnValue(user);

    const { result } = renderHook(() => useAuth());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual(user);
  });

  test('logout clears the session state and calls the auth service', async () => {
    auth.getToken.mockReturnValue('jwt');
    auth.getUser.mockReturnValue({ id: 1, username: 'alice', email: 'a@example.com' });
    const { result } = renderHook(() => useAuth());
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

    act(() => {
      result.current.logout();
    });

    expect(auth.logout).toHaveBeenCalled();
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });
});
