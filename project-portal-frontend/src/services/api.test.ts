import { loginApi, projectApi, scanApi } from './api';

// The interceptors are registered on each axios instance; call the registered handlers directly.
const requestHandler = (api: typeof loginApi) => (api.interceptors.request as any).handlers[0].fulfilled;
const responseRejected = (api: typeof loginApi) => (api.interceptors.response as any).handlers[0].rejected;

describe('api request interceptor', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test.each([
    ['login', loginApi],
    ['project', projectApi],
    ['scan', scanApi],
  ])('%s api adds the bearer token but no user id header', (_name, api) => {
    localStorage.setItem('access_token', 'jwt-123');
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'alice' }));

    const config = requestHandler(api)({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer jwt-123');
    expect(config.headers['X-User-Id']).toBeUndefined();
  });

  test('leaves the request untouched when nobody is logged in', () => {
    const config = requestHandler(projectApi)({ headers: {} });

    expect(config.headers.Authorization).toBeUndefined();
    expect(config.headers['X-User-Id']).toBeUndefined();
  });
});

describe('api response interceptor', () => {
  const originalLocation = window.location;

  afterEach(() => {
    Object.defineProperty(window, 'location', { value: originalLocation, writable: true });
  });

  test('a 401 clears the session and sends the user to the login page', async () => {
    const location = { href: '/projects' };
    Object.defineProperty(window, 'location', { value: location, writable: true });
    localStorage.setItem('access_token', 'jwt');
    localStorage.setItem('user', '{}');
    const error = { response: { status: 401 } };

    await expect(responseRejected(scanApi)(error)).rejects.toBe(error);

    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(location.href).toBe('/login');
  });

  test('other errors are passed through without touching the session', async () => {
    localStorage.setItem('access_token', 'jwt');
    const error = { response: { status: 500 } };

    await expect(responseRejected(projectApi)(error)).rejects.toBe(error);

    expect(localStorage.getItem('access_token')).toBe('jwt');
  });

  test('network errors without a response are passed through', async () => {
    const error = new Error('Network Error');

    await expect(responseRejected(loginApi)(error)).rejects.toBe(error);
  });
});
